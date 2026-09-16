package com.wupeng.wskinloader.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Client commands for quickly changing the local player's own skin/cape.
 *
 * <pre>
 *   /wskin skin &lt;player&gt;   map the local player's skin to another player
 *   /wskin cape &lt;player&gt;   map the local player's cape to another player
 *   /wskin reset skin      remove the skin mapping
 *   /wskin reset cape      remove the cape mapping
 * </pre>
 *
 * <p>Mapping is stored in the local player's player-override entry: an existing
 * entry is only updated, otherwise the entry is created automatically. Unrelated
 * fields of an existing entry are preserved.
 *
 * <p>Both {@code /wskin} and the alias {@code /skin} are registered.
 */
public final class WSkinCommand {

    private WSkinCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            registerRoot(dispatcher, "wskin");
            registerRoot(dispatcher, "skin");
        });
    }

    private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher, String root) {
        // attended() keeps these from being triggered by a server-sent text
        // component; they are only run when the user types them.
        LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommands.literal(root)
                .requires(FabricClientCommandSource::attended)
                .executes(WSkinCommand::showUsage);

        node.then(ClientCommands.literal("skin")
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(context -> setMapping(context, true))));

        node.then(ClientCommands.literal("cape")
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(context -> setMapping(context, false))));

        node.then(ClientCommands.literal("reset")
                .then(ClientCommands.literal("skin").executes(context -> resetMapping(context, true)))
                .then(ClientCommands.literal("cape").executes(context -> resetMapping(context, false))));

        dispatcher.register(node);
    }

    private static int showUsage(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        source.sendFeedback(Component.translatable("wskinloader.command.usage.title"));
        source.sendFeedback(Component.translatable("wskinloader.command.usage.skin"));
        source.sendFeedback(Component.translatable("wskinloader.command.usage.cape"));
        source.sendFeedback(Component.translatable("wskinloader.command.usage.reset_skin"));
        source.sendFeedback(Component.translatable("wskinloader.command.usage.reset_cape"));
        // A bare /wskin is informational only.
        return 0;
    }

    private static int setMapping(CommandContext<FabricClientCommandSource> context, boolean skin) throws CommandSyntaxException {
        FabricClientCommandSource source = context.getSource();
        LocalPlayer player = source.getPlayer();
        if (player == null) {
            source.sendError(Component.translatable("wskinloader.command.error.no_player"));
            return 0;
        }

        String sourceName = StringArgumentType.getString(context, "player").trim();
        if (sourceName.isEmpty()) {
            source.sendError(Component.translatable("wskinloader.command.error.empty_name"));
            return 0;
        }

        String ownerName = player.getGameProfile().name();
        if (ownerName == null || ownerName.isEmpty()) {
            source.sendError(Component.translatable("wskinloader.command.error.no_player"));
            return 0;
        }

        if (sourceName.equalsIgnoreCase(ownerName)) {
            // Mapping a player onto themselves is the same as resetting the mapping.
            return resetMapping(context, skin);
        }

        ModConfig.PlayerOverride override = getOrCreateOverride(ownerName);
        if (skin) {
            override.skinSourcePlayer = sourceName;
        } else {
            override.capeSourcePlayer = sourceName;
        }
        ModConfig.getInstance().save();

        applyToLocalPlayer(player);

        source.sendFeedback(Component.translatable(skin
                        ? "wskinloader.command.success.skin"
                        : "wskinloader.command.success.cape",
                sourceName, ownerName));
        return 1;
    }

    private static int resetMapping(CommandContext<FabricClientCommandSource> context, boolean skin) throws CommandSyntaxException {
        FabricClientCommandSource source = context.getSource();
        LocalPlayer player = source.getPlayer();
        if (player == null) {
            source.sendError(Component.translatable("wskinloader.command.error.no_player"));
            return 0;
        }

        String ownerName = player.getGameProfile().name();
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

        source.sendFeedback(Component.translatable(skin
                ? "wskinloader.command.success.reset_skin"
                : "wskinloader.command.success.reset_cape"));
        return 1;
    }

    /**
     * Returns the existing entry unchanged, or creates a new one whose rules match
     * the defaults: premium first (custom API disabled), so a mapping shows the
     * source player's premium texture and only falls back to the custom APIs when
     * that account has none.
     */
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

    /**
     * Clears the cached textures of the local player and starts loading again, so
     * the new mapping is visible without relogging.
     */
    private static void applyToLocalPlayer(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        // Pass the name explicitly: reload(name) does not depend on the name cache
        // being populated yet, which is not guaranteed when the command runs.
        SkinLoader.reload(player.getUUID(), player.getGameProfile().name());
    }
}
