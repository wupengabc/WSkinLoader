package com.wupeng.wskinloader.client;

import com.wupeng.wskinloader.client.command.WSkinCommand;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WskinloaderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("WSkinLoader");

    @Override
    public void onInitializeClient() {
        LOGGER.info("WSkinLoader 初始化中...");
        
        // 加载配置
        ModConfig.getInstance();

        // 客户端快捷命令：/wskin skin|cape|reset
        WSkinCommand.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SkinLoader.shutdown());

        LOGGER.info("WSkinLoader 初始化完成！");
    }
}
