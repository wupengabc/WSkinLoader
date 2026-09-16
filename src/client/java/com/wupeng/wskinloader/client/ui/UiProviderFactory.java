package com.wupeng.wskinloader.client.ui;

import com.wupeng.wskinloader.client.config.screen.ConfigScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * UI provider using the original full-featured config screen.
 */
public class UiProviderFactory {

    private static final UiProvider INSTANCE = new DefaultUiProvider();

    public static UiProvider get() {
        return INSTANCE;
    }

    private static class DefaultUiProvider implements UiProvider {
        @Override
        public Screen openConfigScreen(Screen parent) {
            return new ConfigScreen(parent);
        }
    }
}
