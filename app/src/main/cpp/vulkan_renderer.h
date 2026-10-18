#ifndef GOLDSRC_VULKAN_RENDERER_H
#define GOLDSRC_VULKAN_RENDERER_H

#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR
#endif

#include <android/native_window.h>
#include <vulkan/vulkan.h>

bool vkrenderer_init(ANativeWindow* window);
void vkrenderer_resize(int width, int height);
void vkrenderer_draw_frame();
void vkrenderer_set_geometry(const float* data, uint32_t floatCount);
void vkrenderer_shutdown();
const char* vkrenderer_device_name();

#endif
