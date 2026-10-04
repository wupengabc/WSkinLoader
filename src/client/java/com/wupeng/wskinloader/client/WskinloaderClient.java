package com.wupeng.wskinloader.client;

import com.wupeng.wskinloader.client.command.WSkinCommand;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WskinloaderClient implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("WSkinLoader");

    private static int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("WSkinLoader 初始化中...");

        // 加载配置
        ModConfig.getInstance();

        // 客户端快捷命令：/wskin skin|cape|reset
        WSkinCommand.register();

        // 进入世界后周期性地为已加载的玩家应用覆盖配置，避免必须手动
        // 执行 /wskin 才会加载（本地玩家的皮肤 getter 不一定被触发）。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter % 40 != 0) {
                return;
            }
            ClientLevel level = client.level;
            if (level == null) {
                return;
            }
            for (AbstractClientPlayer player : level.players()) {
                String name = player.getGameProfile().getName();
                if (name != null && !name.isEmpty()) {
                    SkinLoader.loadSkinForProfile(player.getGameProfile());
                }
            }
        });

        LOGGER.info("WSkinLoader 初始化完成！");
    }
}
