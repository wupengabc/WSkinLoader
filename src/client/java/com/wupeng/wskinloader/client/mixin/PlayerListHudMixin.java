package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.util.ComponentFaceDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 Tab 列表中的玩家头像始终显示，并在服务端已自带头像时整帧回收头像槽位。
 *
 * <p>原版逻辑：只有在本地服务器或加密连接时才显示玩家头像。修改后可通过
 * {@code enableTabListHeads} 开关控制。
 *
 * <p>头像重复：部分服务端把玩家头像作为文字组件写进 Tab 显示名，原版仍会在
 * 名称左侧再画一个 8×8 头像，导致同一玩家出现两个头像。
 *
 * <p>槽位占位：{@code showHead} 在列宽公式里给每个槽位预留 {@code 9px}。
 * 只跳过绘制会留下一条空白竖条，因此当 Tab 列表显示的所有玩家（listed）
 * 显示名都带皮肤头像字形时（粘性锁存，见 {@link #wskinloader$facesLatched}），
 * 让 {@code showHead} 整帧为 {@code false}，宽度、位移、延迟图标位置全部
 * 随之收紧，空白槽位彻底消失。
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"
        )
    )
    private boolean wskinloader$alwaysShowPlayerHeads(ClientPacketListener instance) {
        ModConfig config = ModConfig.getInstance();
        if (!config.enableTabListHeads) {
            return instance.onlineMode();
        }
        if (config.skipDuplicateTabHead && wskinloader$allNamesHaveServerFaces()) {
            return false;
        }
        return true;
    }

    /**
     * 「全员显示名自带皮肤头像」的粘性锁存。
     *
     * <p>一旦确认就保持到断开连接或玩家列表清空。否则新玩家加入的头几帧里，
     * 其显示名/队伍前缀（带头像字形）尚未从服务端同步到本地，全量检测会
     * 短暂翻转为 false，导致原版头像槽位宽度和绘制闪现一下又消失。
     * 锁存后本帧检测完全跳过，也无后续逐帧开销。
     */
    private static boolean wskinloader$facesLatched;

    /**
     * 判断是否所有 Tab 列表实际显示的玩家（listed）渲染名都含有皮肤头像字形。
     *
     * <p>使用与原版 {@code getPlayerInfos()} 相同的 {@code getListedOnlinePlayers()}
     * 来源，避免未列出玩家影响判定。
     */
    private static boolean wskinloader$allNamesHaveServerFaces() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            wskinloader$facesLatched = false;
            return false;
        }
        java.util.Collection<PlayerInfo> players = connection.getListedOnlinePlayers();
        if (players.isEmpty()) {
            wskinloader$facesLatched = false;
            return false;
        }
        if (!wskinloader$facesLatched) {
            boolean all = true;
            for (PlayerInfo info : players) {
                if (!ComponentFaceDetector.containsPlayerFace(ComponentFaceDetector.renderableTabName(info))) {
                    all = false;
                    break;
                }
            }
            wskinloader$facesLatched = all;
        }
        return wskinloader$facesLatched;
    }
}
