package com.wupeng.wskinloader.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TranslatableComponent;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

/**
 * Client commands for quickly changing the local player's own skin/cape.
 *
 * <pre>
 *   /wskin skin &lt;player&gt;   map the local player's skin to another player
 *   /wskin cape &lt;player&gt;   map the local player's cape to another player
 *   /wskin reset skin      remove the skin mapping
 *   /wskin reset cape      remove the cape mapping
 * </pre>
 */
public final class WSkinCommand {

    private WSkinCommand() {
    }

    public static void register() {
        ClientCommandManager.DISPATCHER.register(
                literal("wskin")
                        .executes(context -> showUsage(context.getSource()))
                        .then(literal("skin")
                                .then(argument("player", StringArgumentType.word())
                                        .executes(context -> setMapping(context.getSource(),
                                                StringArgumentType.getString(context, "player"), true))))
                        .then(literal("cape")
                                .then(argument("player", StringArgumentType.word())
                                        .executes(context -> setMapping(context.getSource(),
                                                StringArgumentType.getString(context, "player"), false))))
                        .then(literal("reset")
                                .then(literal("skin").executes(context -> resetMapping(context.getSource(), true)))
                                .then(literal("cape").executes(context -> resetMapping(context.getSource(), false)))));
    }

    private static int showUsage(FabricClientCommandSource source) {
        source.sendFeedback(new TranslatableComponent("wskinloader.command.usage.title"));
        source.sendFeedback(new TranslatableComponent("wskinloader.command.usage.skin"));
        source.sendFeedback(new TranslatableComponent("wskinloader.command.usage.cape"));
        source.sendFeedback(new TranslatableComponent("wskinloader.command.usage.reset_skin"));
        source.sendFeedback(new TranslatableComponent("wskinloader.command.usage.reset_cape"));
        return 0;
    }

    private static int setMapping(FabricClientCommandSource source, String rawSourceName, boolean skin) {
        LocalPlayer player = source.getPlayer();
        if (player == null) {
            source.sendError(new TranslatableComponent("wskinloader.command.error.no_player"));
            return 0;
        }

        String sourceName = rawSourceName == null ? "" : rawSourceName.trim();
        if (sourceName.isEmpty()) {
            source.sendError(new TranslatableComponent("wskinloader.command.error.empty_name"));
            return 0;
        }

        String ownerName = player.getGameProfile().getName();
        if (ownerName == null || ownerName.isEmpty()) {
            source.sendError(new TranslatableComponent("wskinloader.command.error.no_player"));
            return 0;
        }

        if (sourceName.equalsIgnoreCase(ownerName)) {
            return resetMapping(source, skin);
        }

        ModConfig.PlayerOverride override = getOrCreateOverride(ownerName);
        if (skin) {
            override.skinSourcePlayer = sourceName;
        } else {
            override.capeSourcePlayer = sourceName;
        }
        ModConfig.getInstance().save();

        applyToLocalPlayer(player);

        source.sendFeedback(new TranslatableComponent(skin
                        ? "wskinloader.command.success.skin"
                        : "wskinloader.command.success.cape",
                sourceName, ownerName));
        return 1;
    }

    private static int resetMapping(FabricClientCommandSource source, boolean skin) {
        LocalPlayer player = source.getPlayer();
        if (player == null) {
            source.sendError(new TranslatableComponent("wskinloader.command.error.no_player"));
            return 0;
        }

        String ownerName = player.getGameProfile().getName();
        ModConfig.PlayerOverride override = ownerName == null ? null : ModConfig.getInstance().getPlayerOverride(ownerName);

        if (override != null) {
            if (skin) {
                override.skinSourcePlayer = "";
            } else {
                override.capeSourcePlayer = "";
            }
            ModConfig.getInstance().save();
        }

        applyToLocalPlayer(player);

        source.sendFeedback(new TranslatableComponent(skin
                ? "wskinloader.command.success.reset_skin"
                : "wskinloader.command.success.reset_cape"));
        return 1;
    }

    private static ModConfig.PlayerOverride getOrCreateOverride(String playerName) {
        ModConfig config = ModConfig.getInstance();
        ModConfig.PlayerOverride override = config.getPlayerOverride(playerName);
        if (override == null) {
            override = new ModConfig.PlayerOverride();
            override.skin.useCustomApi = false;
            override.cape.useCustomApi = false;
            config.playerOverrides.put(playerName, override);
        }
        return override;
    }

    private static void applyToLocalPlayer(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        SkinLoader.reload(player.getUUID(), player.getGameProfile().getName());
    }
}
