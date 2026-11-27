package com.ysmdialog.net;

import com.ysmdialog.YsmDialogMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 网络通道：只两条包
 * - S2C 打开对话（把整个脚本 JSON 下发，客户端本地推进）
 * - C2S 交互回报（执行某条命令 / 对话结束）
 */
@SuppressWarnings("removal")
public final class Net {

    private static final String VERSION = "1.0";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(YsmDialogMod.MODID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

    private static boolean initialized = false;

    private Net() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        int id = 0;
        CHANNEL.registerMessage(id++, OpenDialogS2C.class,
                OpenDialogS2C::encode, OpenDialogS2C::decode, OpenDialogS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DialogActionC2S.class,
                DialogActionC2S::encode, DialogActionC2S::decode, DialogActionC2S::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        YsmDialogMod.LOG.info("[YsmDialog] 网络通道注册完成（{} 条包）", id);
    }

    public static void open(ServerPlayer player, OpenDialogS2C msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendCommand(String scriptId, String command) {
        CHANNEL.sendToServer(new DialogActionC2S(scriptId, DialogActionC2S.ACTION_COMMAND, command));
    }

    /** 插件命令（Bukkit 侧派发，菜单类插件用这条） */
    public static void sendPluginCommand(String scriptId, String command) {
        CHANNEL.sendToServer(new DialogActionC2S(scriptId, DialogActionC2S.ACTION_PLUGIN, command));
    }

    public static void sendEnd(String scriptId) {
        CHANNEL.sendToServer(new DialogActionC2S(scriptId, DialogActionC2S.ACTION_END, ""));
    }
}
