#include "vulkan_renderer.h"
#include "spirv_shaders.h"

#include <android/log.h>
#include <android/native_window_jni.h>

#include <cmath>
#include <cstring>
#include <vector>

#define LOG_TAG "GoldSrcVulkan"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define VK_CHECK(call)                                                        \
    do {                                                                      \
        VkResult vk_result = (call);                                          \
        if (vk_result != VK_SUCCESS) {                                        \
            LOGE("%s -> %d", #call, static_cast<int>(vk_result));             \
            return false;                                                     \
        }                                                                     \
    } while (0)

namespace {

struct Mat4 {
    float m[16];
};

Mat4 mat_mul(const Mat4& a, const Mat4& b) {
    Mat4 result{};
    for (int column = 0; column < 4; column++) {
        for (int row = 0; row < 4; row++) {
            float sum = 0.0f;
            for (int k = 0; k < 4; k++) {
                sum += a.m[k * 4 + row] * b.m[column * 4 + k];
            }
            result.m[column * 4 + row] = sum;
        }
    }
    return result;
}

Mat4 mat_perspective(float fovYRadians, float aspect, float nearPlane, float farPlane) {
    Mat4 result{};
    const float tanHalf = std::tan(fovYRadians / 2.0f);
    result.m[0] = 1.0f / (aspect * tanHalf);
    result.m[5] = -1.0f / tanHalf;
    result.m[10] = farPlane / (nearPlane - farPlane);
    result.m[11] = -1.0f;
    result.m[14] = nearPlane * farPlane / (nearPlane - farPlane);
    return result;
}

Mat4 mat_look_at(float eyeX, float eyeY, float eyeZ,
                 float targetX, float targetY, float targetZ,
                 float upX, float upY, float upZ) {
    float fx = targetX - eyeX;
    float fy = targetY - eyeY;
    float fz = targetZ - eyeZ;
    const float fLen = std::sqrt(fx * fx + fy * fy + fz * fz);
    if (fLen > 0.0001f) {
        fx /= fLen;
        fy /= fLen;
        fz /= fLen;
    }

    float sx = fy * upZ - fz * upY;
    float sy = fz * upX - fx * upZ;
    float sz = fx * upY - fy * upX;
    const float sLen = std::sqrt(sx * sx + sy * sy + sz * sz);
    if (sLen > 0.0001f) {
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;
    }

    const float ux = sy * fz - sz * fy;
    const float uy = sz * fx - sx * fz;
    const float uz = sx * fy - sy * fx;

    Mat4 result{};
    result.m[0] = sx;
    result.m[4] = sy;
    result.m[8] = sz;
    result.m[12] = -(sx * eyeX + sy * eyeY + sz * eyeZ);
    result.m[1] = ux;
    result.m[5] = uy;
    result.m[9] = uz;
    result.m[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ);
    result.m[2] = -fx;
    result.m[6] = -fy;
    result.m[10] = -fz;
    result.m[14] = fx * eyeX + fy * eyeY + fz * eyeZ;
    result.m[15] = 1.0f;
    return result;
}

struct VkState {
    ANativeWindow* window = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamily = 0;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    std::vector<VkImage> images;
    std::vector<VkImageView> imageViews;
    std::vector<VkFramebuffer> framebuffers;
    VkFormat format = VK_FORMAT_R8G8B8A8_UNORM;
    VkExtent2D extent = {1, 1};
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkFormat depthFormat = VK_FORMAT_UNDEFINED;
    VkImage depthImage = VK_NULL_HANDLE;
    VkDeviceMemory depthMemory = VK_NULL_HANDLE;
    VkImageView depthView = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkBuffer vertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory vertexMemory = VK_NULL_HANDLE;
    uint32_t vertexCount = 0;
    float centerX = 0.0f;
    float centerY = 0.0f;
    float centerZ = 0.0f;
    float radius = 256.0f;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    std::vector<VkCommandBuffer> commandBuffers;
    VkSemaphore imageAvailable = VK_NULL_HANDLE;
    VkSemaphore renderFinished = VK_NULL_HANDLE;
    VkFence inFlight = VK_NULL_HANDLE;
    bool ready = false;
    char deviceName[256] = "not initialized";
};

VkState g;

uint32_t clamp_extent(uint32_t value, uint32_t minimum, uint32_t maximum) {
    if (value < minimum) {
        return minimum;
    }
    if (value > maximum) {
        return maximum;
    }
    return value;
}

uint32_t find_memory_type(uint32_t typeBits, VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties memoryProperties{};
    vkGetPhysicalDeviceMemoryProperties(g.physical, &memoryProperties);
    for (uint32_t index = 0; index < memoryProperties.memoryTypeCount; index++) {
        if ((typeBits & (1u << index))
            && (memoryProperties.memoryTypes[index].propertyFlags & properties) == properties) {
            return index;
        }
    }
    return UINT32_MAX;
}

VkFormat pick_depth_format() {
    const VkFormat candidates[] = {
            VK_FORMAT_D32_SFLOAT,
            VK_FORMAT_D24_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM,
    };
    for (VkFormat format : candidates) {
        VkFormatProperties properties{};
        vkGetPhysicalDeviceFormatProperties(g.physical, format, &properties);
        if (properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {
            return format;
        }
    }
    return VK_FORMAT_UNDEFINED;
}

bool format_has_stencil(VkFormat format) {
    return format == VK_FORMAT_D24_UNORM_S8_UINT || format == VK_FORMAT_D32_SFLOAT_S8_UINT;
}

bool create_depth_resources() {
    g.depthFormat = pick_depth_format();
    if (g.depthFormat == VK_FORMAT_UNDEFINED) {
        LOGE("no depth format with depth-stencil attachment support");
        return false;
    }

    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = g.depthFormat;
    imageInfo.extent.width = g.extent.width;
    imageInfo.extent.height = g.extent.height;
    imageInfo.extent.depth = 1;
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VK_CHECK(vkCreateImage(g.device, &imageInfo, nullptr, &g.depthImage));

    VkMemoryRequirements memoryRequirements{};
    vkGetImageMemoryRequirements(g.device, g.depthImage, &memoryRequirements);

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memoryRequirements.size;
    allocInfo.memoryTypeIndex = find_memory_type(memoryRequirements.memoryTypeBits,
                                                 VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (allocInfo.memoryTypeIndex == UINT32_MAX) {
        LOGE("no device-local memory type for depth image");
        return false;
    }
    VK_CHECK(vkAllocateMemory(g.device, &allocInfo, nullptr, &g.depthMemory));
    VK_CHECK(vkBindImageMemory(g.device, g.depthImage, g.depthMemory, 0));

    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = g.depthImage;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = g.depthFormat;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
    if (format_has_stencil(g.depthFormat)) {
        viewInfo.subresourceRange.aspectMask |= VK_IMAGE_ASPECT_STENCIL_BIT;
    }
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;
    VK_CHECK(vkCreateImageView(g.device, &viewInfo, nullptr, &g.depthView));
    return true;
}

void destroy_depth_resources() {
    if (g.depthView != VK_NULL_HANDLE) {
        vkDestroyImageView(g.device, g.depthView, nullptr);
        g.depthView = VK_NULL_HANDLE;
    }
    if (g.depthImage != VK_NULL_HANDLE) {
        vkDestroyImage(g.device, g.depthImage, nullptr);
        g.depthImage = VK_NULL_HANDLE;
    }
    if (g.depthMemory != VK_NULL_HANDLE) {
        vkFreeMemory(g.device, g.depthMemory, nullptr);
        g.depthMemory = VK_NULL_HANDLE;
    }
}

VkShaderModule create_shader_module(const uint32_t* code, size_t byteSize) {
    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = byteSize;
    createInfo.pCode = code;
    VkShaderModule module = VK_NULL_HANDLE;
    if (vkCreateShaderModule(g.device, &createInfo, nullptr, &module) != VK_SUCCESS) {
        LOGE("vkCreateShaderModule failed");
    }
    return module;
}

bool create_pipeline() {
    VkShaderModule vertexModule = create_shader_module(
            kTriangleVertSpv, sizeof(kTriangleVertSpv));
    VkShaderModule fragmentModule = create_shader_module(
            kTriangleFragSpv, sizeof(kTriangleFragSpv));
    if (vertexModule == VK_NULL_HANDLE || fragmentModule == VK_NULL_HANDLE) {
        return false;
    }

    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vertexModule;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = fragmentModule;
    stages[1].pName = "main";

    VkVertexInputBindingDescription binding{};
    binding.binding = 0;
    binding.stride = 6 * sizeof(float);
    binding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription attributes[2]{};
    attributes[0].location = 0;
    attributes[0].binding = 0;
    attributes[0].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributes[0].offset = 0;
    attributes[1].location = 1;
    attributes[1].binding = 0;
    attributes[1].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributes[1].offset = 3 * sizeof(float);

    VkPipelineVertexInputStateCreateInfo vertexInput{};
    vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vertexInput.vertexBindingDescriptionCount = 1;
    vertexInput.pVertexBindingDescriptions = &binding;
    vertexInput.vertexAttributeDescriptionCount = 2;
    vertexInput.pVertexAttributeDescriptions = attributes;

    VkPipelineInputAssemblyStateCreateInfo inputAssembly{};
    inputAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkPipelineViewportStateCreateInfo viewportState{};
    viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.scissorCount = 1;

    VkPipelineRasterizationStateCreateInfo rasterization{};
    rasterization.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rasterization.polygonMode = VK_POLYGON_MODE_FILL;
    rasterization.cullMode = VK_CULL_MODE_NONE;
    rasterization.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    rasterization.lineWidth = 1.0f;

    VkPipelineMultisampleStateCreateInfo multisample{};
    multisample.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineDepthStencilStateCreateInfo depthStencil{};
    depthStencil.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
    depthStencil.depthTestEnable = VK_TRUE;
    depthStencil.depthWriteEnable = VK_TRUE;
    depthStencil.depthCompareOp = VK_COMPARE_OP_LESS;

    VkPipelineColorBlendAttachmentState blendAttachment{};
    blendAttachment.blendEnable = VK_FALSE;
    blendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                                     | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;

    VkPipelineColorBlendStateCreateInfo colorBlend{};
    colorBlend.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    colorBlend.attachmentCount = 1;
    colorBlend.pAttachments = &blendAttachment;

    VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamicState{};
    dynamicState.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
    dynamicState.dynamicStateCount = 2;
    dynamicState.pDynamicStates = dynamicStates;

    VkPushConstantRange pushRange{};
    pushRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    pushRange.offset = 0;
    pushRange.size = sizeof(Mat4);

    VkPipelineLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.pushConstantRangeCount = 1;
    layoutInfo.pPushConstantRanges = &pushRange;
    VK_CHECK(vkCreatePipelineLayout(g.device, &layoutInfo, nullptr, &g.pipelineLayout));

    VkGraphicsPipelineCreateInfo pipelineInfo{};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pipelineInfo.stageCount = 2;
    pipelineInfo.pStages = stages;
    pipelineInfo.pVertexInputState = &vertexInput;
    pipelineInfo.pInputAssemblyState = &inputAssembly;
    pipelineInfo.pViewportState = &viewportState;
    pipelineInfo.pRasterizationState = &rasterization;
    pipelineInfo.pMultisampleState = &multisample;
    pipelineInfo.pDepthStencilState = &depthStencil;
    pipelineInfo.pColorBlendState = &colorBlend;
    pipelineInfo.pDynamicState = &dynamicState;
    pipelineInfo.layout = g.pipelineLayout;
    pipelineInfo.renderPass = g.renderPass;
    pipelineInfo.subpass = 0;

    VkResult result = vkCreateGraphicsPipelines(g.device, VK_NULL_HANDLE, 1,
                                                &pipelineInfo, nullptr, &g.pipeline);
    vkDestroyShaderModule(g.device, vertexModule, nullptr);
    vkDestroyShaderModule(g.device, fragmentModule, nullptr);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateGraphicsPipelines failed: %d", static_cast<int>(result));
        return false;
    }
    LOGI("graphics pipeline created (SPIR-V, depth %d)", static_cast<int>(g.depthFormat));
    return true;
}

void destroy_pipeline() {
    if (g.pipeline != VK_NULL_HANDLE) {
        vkDestroyPipeline(g.device, g.pipeline, nullptr);
        g.pipeline = VK_NULL_HANDLE;
    }
    if (g.pipelineLayout != VK_NULL_HANDLE) {
        vkDestroyPipelineLayout(g.device, g.pipelineLayout, nullptr);
        g.pipelineLayout = VK_NULL_HANDLE;
    }
}

Mat4 compute_mvp() {
    const float yaw = 0.7853982f;
    const float pitch = 0.6108652f;
    const float distance = g.radius * 2.4f + 64.0f;
    const float eyeX = g.centerX + distance * std::cos(pitch) * std::cos(yaw);
    const float eyeY = g.centerY + distance * std::cos(pitch) * std::sin(yaw);
    const float eyeZ = g.centerZ + distance * std::sin(pitch);

    Mat4 view = mat_look_at(eyeX, eyeY, eyeZ, g.centerX, g.centerY, g.centerZ, 0.0f, 0.0f, 1.0f);

    const float aspect = static_cast<float>(g.extent.width)
                         / static_cast<float>(g.extent.height == 0 ? 1 : g.extent.height);
    const float nearPlane = g.radius * 0.02f + 4.0f;
    const float farPlane = g.radius * 8.0f + 4096.0f;
    Mat4 projection = mat_perspective(1.0471976f, aspect, nearPlane, farPlane);
    return mat_mul(projection, view);
}

bool create_instance() {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "GoldSrcMaker";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    appInfo.pEngineName = "GoldSrcMakerVk";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 1, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    const char* extensions[] = {
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
    };

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;

    VK_CHECK(vkCreateInstance(&createInfo, nullptr, &g.instance));

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = g.window;
    VK_CHECK(vkCreateAndroidSurfaceKHR(g.instance, &surfaceInfo, nullptr, &g.surface));

    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(g.instance, &deviceCount, nullptr);
    if (deviceCount == 0) {
        LOGE("no Vulkan physical devices");
        return false;
    }
    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(g.instance, &deviceCount, devices.data());

    for (VkPhysicalDevice candidate : devices) {
        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, families.data());

        for (uint32_t index = 0; index < familyCount; index++) {
            VkBool32 presentSupport = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(candidate, index, g.surface, &presentSupport);
            if ((families[index].queueFlags & VK_QUEUE_GRAPHICS_BIT) && presentSupport) {
                g.physical = candidate;
                g.queueFamily = index;
                break;
            }
        }
        if (g.physical != VK_NULL_HANDLE) {
            break;
        }
    }

    if (g.physical == VK_NULL_HANDLE) {
        LOGE("no Vulkan device with graphics+present queue");
        return false;
    }

    VkPhysicalDeviceProperties properties{};
    vkGetPhysicalDeviceProperties(g.physical, &properties);
    strncpy(g.deviceName, properties.deviceName, sizeof(g.deviceName) - 1);
    LOGI("selected GPU: %s (Vulkan %u.%u.%u)", g.deviceName,
         VK_VERSION_MAJOR(properties.apiVersion),
         VK_VERSION_MINOR(properties.apiVersion),
         VK_VERSION_PATCH(properties.apiVersion));

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = g.queueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;

    const char* deviceExtensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};

    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = 1;
    deviceInfo.ppEnabledExtensionNames = deviceExtensions;

    VK_CHECK(vkCreateDevice(g.physical, &deviceInfo, nullptr, &g.device));
    vkGetDeviceQueue(g.device, g.queueFamily, 0, &g.queue);
    return true;
}

bool create_swapchain(uint32_t width, uint32_t height) {
    VkSurfaceCapabilitiesKHR caps{};
    VK_CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(g.physical, g.surface, &caps));

    uint32_t formatCount = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(g.physical, g.surface, &formatCount, nullptr);
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    if (formatCount > 0) {
        vkGetPhysicalDeviceSurfaceFormatsKHR(g.physical, g.surface, &formatCount, formats.data());
    }

    VkSurfaceFormatKHR chosen{};
    chosen.format = VK_FORMAT_R8G8B8A8_UNORM;
    chosen.colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    for (const VkSurfaceFormatKHR& candidate : formats) {
        if (candidate.format == VK_FORMAT_R8G8B8A8_UNORM
            && candidate.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            chosen = candidate;
            break;
        }
    }
    g.format = chosen.format;

    uint32_t imageCount = caps.minImageCount + 1;
    if (caps.maxImageCount > 0 && imageCount > caps.maxImageCount) {
        imageCount = caps.maxImageCount;
    }

    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX || extent.height == UINT32_MAX) {
        extent.width = clamp_extent(width, caps.minImageExtent.width, caps.maxImageExtent.width);
        extent.height = clamp_extent(height, caps.minImageExtent.height, caps.maxImageExtent.height);
    }
    if (extent.width == 0 || extent.height == 0) {
        extent.width = 1080;
        extent.height = 1920;
    }
    g.extent = extent;

    VkSwapchainCreateInfoKHR swapInfo{};
    swapInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapInfo.surface = g.surface;
    swapInfo.minImageCount = imageCount;
    swapInfo.imageFormat = g.format;
    swapInfo.imageColorSpace = chosen.colorSpace;
    swapInfo.imageExtent = extent;
    swapInfo.imageArrayLayers = 1;
    swapInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    swapInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapInfo.preTransform = caps.currentTransform;
    swapInfo.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    swapInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    swapInfo.clipped = VK_TRUE;

    VK_CHECK(vkCreateSwapchainKHR(g.device, &swapInfo, nullptr, &g.swapchain));

    vkGetSwapchainImagesKHR(g.device, g.swapchain, &imageCount, nullptr);
    g.images.resize(imageCount);
    vkGetSwapchainImagesKHR(g.device, g.swapchain, &imageCount, g.images.data());

    g.imageViews.resize(imageCount);
    for (size_t index = 0; index < g.images.size(); index++) {
        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = g.images[index];
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = g.format;
        viewInfo.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;
        VK_CHECK(vkCreateImageView(g.device, &viewInfo, nullptr, &g.imageViews[index]));
    }
    return true;
}

bool create_render_pass() {
    VkAttachmentDescription colorAttachment{};
    colorAttachment.format = g.format;
    colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference colorReference{};
    colorReference.attachment = 0;
    colorReference.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkAttachmentDescription depthAttachment{};
    depthAttachment.format = g.depthFormat;
    depthAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    depthAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    depthAttachment.storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    depthAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    depthAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    depthAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    depthAttachment.finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

    VkAttachmentReference depthReference{};
    depthReference.attachment = 1;
    depthReference.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorReference;
    subpass.pDepthStencilAttachment = &depthReference;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                              | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    dependency.srcAccessMask = 0;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                              | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                               | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;

    VkAttachmentDescription attachments[] = {colorAttachment, depthAttachment};

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 2;
    renderPassInfo.pAttachments = attachments;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;

    VK_CHECK(vkCreateRenderPass(g.device, &renderPassInfo, nullptr, &g.renderPass));
    return true;
}

bool create_framebuffers() {
    g.framebuffers.resize(g.imageViews.size());
    for (size_t index = 0; index < g.imageViews.size(); index++) {
        VkImageView attachments[] = {g.imageViews[index], g.depthView};

        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = g.renderPass;
        framebufferInfo.attachmentCount = 2;
        framebufferInfo.pAttachments = attachments;
        framebufferInfo.width = g.extent.width;
        framebufferInfo.height = g.extent.height;
        framebufferInfo.layers = 1;
        VK_CHECK(vkCreateFramebuffer(g.device, &framebufferInfo, nullptr, &g.framebuffers[index]));
    }
    return true;
}

bool allocate_command_buffers() {
    g.commandBuffers.resize(g.framebuffers.size());
    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = g.commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = static_cast<uint32_t>(g.commandBuffers.size());
    VK_CHECK(vkAllocateCommandBuffers(g.device, &allocInfo, g.commandBuffers.data()));
    return true;
}

bool create_commands() {
    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = g.queueFamily;
    VK_CHECK(vkCreateCommandPool(g.device, &poolInfo, nullptr, &g.commandPool));

    if (!allocate_command_buffers()) {
        return false;
    }

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    VK_CHECK(vkCreateSemaphore(g.device, &semaphoreInfo, nullptr, &g.imageAvailable));
    VK_CHECK(vkCreateSemaphore(g.device, &semaphoreInfo, nullptr, &g.renderFinished));

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    VK_CHECK(vkCreateFence(g.device, &fenceInfo, nullptr, &g.inFlight));
    return true;
}

void destroy_swapchain() {
    if (g.device == VK_NULL_HANDLE) {
        return;
    }
    for (VkFramebuffer framebuffer : g.framebuffers) {
        vkDestroyFramebuffer(g.device, framebuffer, nullptr);
    }
    g.framebuffers.clear();
    for (VkImageView view : g.imageViews) {
        vkDestroyImageView(g.device, view, nullptr);
    }
    g.imageViews.clear();
    g.images.clear();
    if (g.swapchain != VK_NULL_HANDLE) {
        vkDestroySwapchainKHR(g.device, g.swapchain, nullptr);
        g.swapchain = VK_NULL_HANDLE;
    }
}

}  // namespace

bool vkrenderer_init(ANativeWindow* window) {
    if (window == nullptr) {
        return false;
    }
    g.window = window;

    if (!create_instance()) {
        return false;
    }
    if (!create_swapchain(static_cast<uint32_t>(ANativeWindow_getWidth(window)),
                          static_cast<uint32_t>(ANativeWindow_getHeight(window)))) {
        return false;
    }
    if (!create_depth_resources()) {
        return false;
    }
    if (!create_render_pass()) {
        return false;
    }
    if (!create_framebuffers()) {
        return false;
    }
    if (!create_pipeline()) {
        return false;
    }
    if (!create_commands()) {
        return false;
    }

    g.ready = true;
    LOGI("Vulkan renderer initialized (extent %ux%u)", g.extent.width, g.extent.height);
    return true;
}

void vkrenderer_resize(int width, int height) {
    if (!g.ready) {
        return;
    }
    vkDeviceWaitIdle(g.device);

    vkFreeCommandBuffers(g.device, g.commandPool,
                         static_cast<uint32_t>(g.commandBuffers.size()),
                         g.commandBuffers.data());
    g.commandBuffers.clear();

    destroy_swapchain();
    destroy_depth_resources();

    if (!create_swapchain(static_cast<uint32_t>(width), static_cast<uint32_t>(height))) {
        g.ready = false;
        return;
    }
    if (!create_depth_resources()) {
        g.ready = false;
        return;
    }
    if (!create_framebuffers()) {
        g.ready = false;
        return;
    }
    if (!allocate_command_buffers()) {
        g.ready = false;
    }
}

void vkrenderer_draw_frame() {
    if (!g.ready) {
        return;
    }

    vkWaitForFences(g.device, 1, &g.inFlight, VK_TRUE, UINT64_MAX);

    uint32_t imageIndex = 0;
    VkResult acquireResult = vkAcquireNextImageKHR(
            g.device, g.swapchain, UINT64_MAX, g.imageAvailable, VK_NULL_HANDLE, &imageIndex);
    if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
        return;
    }
    if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
        return;
    }

    vkResetFences(g.device, 1, &g.inFlight);

    VkCommandBuffer commandBuffer = g.commandBuffers[imageIndex];
    vkResetCommandBuffer(commandBuffer, 0);

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    if (vkBeginCommandBuffer(commandBuffer, &beginInfo) != VK_SUCCESS) {
        return;
    }

    VkClearValue clearValues[2]{};
    clearValues[0].color.float32[0] = 0.05f;
    clearValues[0].color.float32[1] = 0.05f;
    clearValues[0].color.float32[2] = 0.08f;
    clearValues[0].color.float32[3] = 1.0f;
    clearValues[1].depthStencil.depth = 1.0f;
    clearValues[1].depthStencil.stencil = 0;

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = g.renderPass;
    renderPassInfo.framebuffer = g.framebuffers[imageIndex];
    renderPassInfo.renderArea.offset.x = 0;
    renderPassInfo.renderArea.offset.y = 0;
    renderPassInfo.renderArea.extent = g.extent;
    renderPassInfo.clearValueCount = 2;
    renderPassInfo.pClearValues = clearValues;

    vkCmdBeginRenderPass(commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

    if (g.pipeline != VK_NULL_HANDLE && g.vertexCount > 0) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, g.pipeline);

        VkViewport viewport{};
        viewport.x = 0.0f;
        viewport.y = 0.0f;
        viewport.width = static_cast<float>(g.extent.width);
        viewport.height = static_cast<float>(g.extent.height);
        viewport.minDepth = 0.0f;
        viewport.maxDepth = 1.0f;
        vkCmdSetViewport(commandBuffer, 0, 1, &viewport);

        VkRect2D scissor{};
        scissor.offset.x = 0;
        scissor.offset.y = 0;
        scissor.extent = g.extent;
        vkCmdSetScissor(commandBuffer, 0, 1, &scissor);

        Mat4 mvp = compute_mvp();
        vkCmdPushConstants(commandBuffer, g.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT,
                           0, sizeof(Mat4), mvp.m);

        VkDeviceSize offset = 0;
        vkCmdBindVertexBuffers(commandBuffer, 0, 1, &g.vertexBuffer, &offset);
        vkCmdDraw(commandBuffer, g.vertexCount, 1, 0, 0);
    }

    vkCmdEndRenderPass(commandBuffer);

    if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
        return;
    }

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = &g.imageAvailable;
    submitInfo.pWaitDstStageMask = &waitStage;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffer;
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = &g.renderFinished;
    if (vkQueueSubmit(g.queue, 1, &submitInfo, g.inFlight) != VK_SUCCESS) {
        return;
    }

    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = &g.renderFinished;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &g.swapchain;
    presentInfo.pImageIndices = &imageIndex;
    vkQueuePresentKHR(g.queue, &presentInfo);
}

void vkrenderer_set_geometry(const float* data, uint32_t floatCount) {
    if (!g.ready || g.device == VK_NULL_HANDLE) {
        return;
    }
    vkDeviceWaitIdle(g.device);

    if (g.vertexBuffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(g.device, g.vertexBuffer, nullptr);
        g.vertexBuffer = VK_NULL_HANDLE;
    }
    if (g.vertexMemory != VK_NULL_HANDLE) {
        vkFreeMemory(g.device, g.vertexMemory, nullptr);
        g.vertexMemory = VK_NULL_HANDLE;
    }
    g.vertexCount = 0;

    if (data == nullptr || floatCount < 6) {
        return;
    }
    const uint32_t count = floatCount / 6;
    const VkDeviceSize size = static_cast<VkDeviceSize>(count) * 6 * sizeof(float);

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(g.device, &bufferInfo, nullptr, &g.vertexBuffer) != VK_SUCCESS) {
        LOGE("vkCreateBuffer(vertex) failed");
        g.vertexBuffer = VK_NULL_HANDLE;
        return;
    }

    VkMemoryRequirements memoryRequirements{};
    vkGetBufferMemoryRequirements(g.device, g.vertexBuffer, &memoryRequirements);

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memoryRequirements.size;
    allocInfo.memoryTypeIndex = find_memory_type(
            memoryRequirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (allocInfo.memoryTypeIndex == UINT32_MAX) {
        LOGE("no host-visible memory type for vertex buffer");
        vkDestroyBuffer(g.device, g.vertexBuffer, nullptr);
        g.vertexBuffer = VK_NULL_HANDLE;
        return;
    }
    if (vkAllocateMemory(g.device, &allocInfo, nullptr, &g.vertexMemory) != VK_SUCCESS) {
        LOGE("vkAllocateMemory(vertex) failed");
        vkDestroyBuffer(g.device, g.vertexBuffer, nullptr);
        g.vertexBuffer = VK_NULL_HANDLE;
        return;
    }
    if (vkBindBufferMemory(g.device, g.vertexBuffer, g.vertexMemory, 0) != VK_SUCCESS) {
        LOGE("vkBindBufferMemory(vertex) failed");
        vkDestroyBuffer(g.device, g.vertexBuffer, nullptr);
        g.vertexBuffer = VK_NULL_HANDLE;
        vkFreeMemory(g.device, g.vertexMemory, nullptr);
        g.vertexMemory = VK_NULL_HANDLE;
        return;
    }

    void* mapped = nullptr;
    if (vkMapMemory(g.device, g.vertexMemory, 0, size, 0, &mapped) == VK_SUCCESS && mapped != nullptr) {
        std::memcpy(mapped, data, size);
        vkUnmapMemory(g.device, g.vertexMemory);
    }

    float minX = data[0];
    float minY = data[1];
    float minZ = data[2];
    float maxX = data[0];
    float maxY = data[1];
    float maxZ = data[2];
    for (uint32_t vertex = 1; vertex < count; vertex++) {
        const float* position = data + vertex * 6;
        minX = std::fmin(minX, position[0]);
        maxX = std::fmax(maxX, position[0]);
        minY = std::fmin(minY, position[1]);
        maxY = std::fmax(maxY, position[1]);
        minZ = std::fmin(minZ, position[2]);
        maxZ = std::fmax(maxZ, position[2]);
    }
    g.centerX = (minX + maxX) / 2.0f;
    g.centerY = (minY + maxY) / 2.0f;
    g.centerZ = (minZ + maxZ) / 2.0f;
    const float spanX = maxX - minX;
    const float spanY = maxY - minY;
    const float spanZ = maxZ - minZ;
    g.radius = std::fmax(std::sqrt(spanX * spanX + spanY * spanY + spanZ * spanZ) / 2.0f, 32.0f);
    g.vertexCount = count;
    LOGI("geometry uploaded: %u vertices, center (%.0f %.0f %.0f) radius %.0f",
         count, g.centerX, g.centerY, g.centerZ, g.radius);
}

void vkrenderer_shutdown() {
    if (g.device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(g.device);

        destroy_pipeline();

        if (g.vertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(g.device, g.vertexBuffer, nullptr);
            g.vertexBuffer = VK_NULL_HANDLE;
        }
        if (g.vertexMemory != VK_NULL_HANDLE) {
            vkFreeMemory(g.device, g.vertexMemory, nullptr);
            g.vertexMemory = VK_NULL_HANDLE;
        }
        g.vertexCount = 0;

        if (g.imageAvailable != VK_NULL_HANDLE) {
            vkDestroySemaphore(g.device, g.imageAvailable, nullptr);
        }
        if (g.renderFinished != VK_NULL_HANDLE) {
            vkDestroySemaphore(g.device, g.renderFinished, nullptr);
        }
        if (g.inFlight != VK_NULL_HANDLE) {
            vkDestroyFence(g.device, g.inFlight, nullptr);
        }
        if (g.commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(g.device, g.commandPool, nullptr);
        }
        g.commandBuffers.clear();
        if (g.renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(g.device, g.renderPass, nullptr);
        }
    }

    destroy_swapchain();
    if (g.device != VK_NULL_HANDLE) {
        destroy_depth_resources();
    }

    if (g.device != VK_NULL_HANDLE) {
        vkDestroyDevice(g.device, nullptr);
    }
    if (g.surface != VK_NULL_HANDLE && g.instance != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(g.instance, g.surface, nullptr);
    }
    if (g.instance != VK_NULL_HANDLE) {
        vkDestroyInstance(g.instance, nullptr);
    }

    ANativeWindow* window = g.window;
    g = VkState{};
    if (window != nullptr) {
        ANativeWindow_release(window);
    }
}

const char* vkrenderer_device_name() {
    return g.deviceName;
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeSurfaceCreated(JNIEnv* env,
                                                                     jobject,
                                                                     jobject surface) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        LOGE("ANativeWindow_fromSurface returned null");
        return;
    }
    if (!vkrenderer_init(window)) {
        LOGE("vkrenderer_init failed");
        vkrenderer_shutdown();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeSurfaceChanged(JNIEnv*,
                                                                     jobject,
                                                                     jint width,
                                                                     jint height) {
    vkrenderer_resize(static_cast<int>(width), static_cast<int>(height));
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeDrawFrame(JNIEnv*, jobject) {
    vkrenderer_draw_frame();
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeSetGeometry(JNIEnv* env,
                                                                  jobject,
                                                                  jfloatArray data) {
    if (data == nullptr) {
        vkrenderer_set_geometry(nullptr, 0);
        return;
    }
    const jsize length = env->GetArrayLength(data);
    jfloat* elements = env->GetFloatArrayElements(data, nullptr);
    if (elements == nullptr) {
        vkrenderer_set_geometry(nullptr, 0);
        return;
    }
    vkrenderer_set_geometry(elements, static_cast<uint32_t>(length));
    env->ReleaseFloatArrayElements(data, elements, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeSurfaceDestroyed(JNIEnv*, jobject) {
    vkrenderer_shutdown();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeRendererInfo(JNIEnv* env, jobject) {
    return env->NewStringUTF(vkrenderer_device_name());
}
