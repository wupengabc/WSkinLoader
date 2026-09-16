package com.wupeng.wskinloader.client.render;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import net.minecraft.network.chat.Component;

/**
 * Reports the active Minecraft rendering backend used by GUI rendering.
 */
public final class RenderBackendInfo {
    private RenderBackendInfo() {
    }

    public enum Api {
        OPENGL("wskinloader.config.render_api.opengl"),
        VULKAN("wskinloader.config.render_api.vulkan"),
        UNKNOWN("wskinloader.config.render_api.unknown");

        private final String translationKey;

        Api(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component displayName() {
            return Component.translatable(translationKey);
        }
    }

    public static Api currentApi() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return Api.UNKNOWN;
        }

        GpuDeviceBackend backend = device.backend;
        if (backend instanceof GlDevice) {
            return Api.OPENGL;
        }
        if (backend instanceof VulkanDevice) {
            return Api.VULKAN;
        }
        return Api.UNKNOWN;
    }

    public static Component currentApiName() {
        return currentApi().displayName();
    }
}
