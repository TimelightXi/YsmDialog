package com.dialogysm.net;

import com.dialogysm.DialogYSMMod;
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

    private static final String VERSION = "1.2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DialogYSMMod.MODID, "main"),
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
        CHANNEL.registerMessage(id++, CloseDialogS2C.class,
                CloseDialogS2C::encode, CloseDialogS2C::decode, CloseDialogS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TextureS2C.class,
                TextureS2C::encode, TextureS2C::decode, TextureS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        DialogYSMMod.LOG.info("[DialogYSM] 网络通道注册完成（{} 条包）", id);
    }

    /** 单块大小：32KB，避免超过 Forge 默认包长 */
    private static final int TEXTURE_CHUNK = 32_000;

    /** 把一张配置贴图分块推给客户端（必须在发 OpenDialogS2C 之前调用） */
    public static void sendTexture(ServerPlayer player, String rl, byte[] data) {
        int count = Math.max(1, (data.length + TEXTURE_CHUNK - 1) / TEXTURE_CHUNK);
        for (int i = 0; i < count; i++) {
            int from = i * TEXTURE_CHUNK;
            int to = Math.min(data.length, from + TEXTURE_CHUNK);
            byte[] chunk = java.util.Arrays.copyOfRange(data, from, to);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TextureS2C(rl, i, count, chunk));
        }
    }

    public static void open(ServerPlayer player, OpenDialogS2C msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** 服务端主动关掉玩家当前的对话 */
    public static void close(ServerPlayer player, String scriptId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CloseDialogS2C(scriptId));
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
