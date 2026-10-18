#include "vulkan_renderer.h"

#include <android/log.h>
#include <android/native_window_jni.h>

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

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorReference;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.srcAccessMask = 0;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 1;
    renderPassInfo.pAttachments = &colorAttachment;
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
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = g.renderPass;
        framebufferInfo.attachmentCount = 1;
        framebufferInfo.pAttachments = &g.imageViews[index];
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
    if (!create_render_pass()) {
        return false;
    }
    if (!create_framebuffers()) {
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

    if (!create_swapchain(static_cast<uint32_t>(width), static_cast<uint32_t>(height))) {
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

    VkClearValue clearValue{};
    clearValue.color.float32[0] = 0.05f;
    clearValue.color.float32[1] = 0.05f;
    clearValue.color.float32[2] = 0.08f;
    clearValue.color.float32[3] = 1.0f;

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = g.renderPass;
    renderPassInfo.framebuffer = g.framebuffers[imageIndex];
    renderPassInfo.renderArea.offset.x = 0;
    renderPassInfo.renderArea.offset.y = 0;
    renderPassInfo.renderArea.extent = g.extent;
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clearValue;

    vkCmdBeginRenderPass(commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
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

void vkrenderer_shutdown() {
    if (g.device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(g.device);

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
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeSurfaceDestroyed(JNIEnv*, jobject) {
    vkrenderer_shutdown();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_goldsrc_maker_render_VulkanSurfaceView_nativeRendererInfo(JNIEnv* env, jobject) {
    return env->NewStringUTF(vkrenderer_device_name());
}
