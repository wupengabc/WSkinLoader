package com.wupeng.wskinloader.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.wupeng.wskinloader.client.ui.UiProviderFactory;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> UiProviderFactory.get().openConfigScreen(parent);
    }
}
