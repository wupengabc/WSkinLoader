package com.wupeng.wskinloader.client.render;

import net.minecraft.network.chat.Component;

/**
 * 报告当前 GUI 渲染后端。
 *
 * <p>1.16.5 只有 OpenGL 后端，没有 Vulkan。
 */
public final class RenderBackendInfo {
    private RenderBackendInfo() {
    }

    public enum Api {
        OPENGL("wskinloader.config.render_api.opengl");

        private final String translationKey;

        Api(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component displayName() {
            return Component.translatable(translationKey);
        }
    }

    public static Api currentApi() {
        return Api.OPENGL;
    }

    public static Component currentApiName() {
        return currentApi().displayName();
    }
}
