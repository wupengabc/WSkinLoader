package com.wupeng.wskinloader.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
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

        String backendName = device.getDeviceInfo().backendName();
        if ("OpenGL".equalsIgnoreCase(backendName)) {
            return Api.OPENGL;
        }
        if ("Vulkan".equalsIgnoreCase(backendName)) {
            return Api.VULKAN;
        }
        return Api.UNKNOWN;
    }

    public static Component currentApiName() {
        return currentApi().displayName();
    }
}
