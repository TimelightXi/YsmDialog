package com.dialogysm.net;

import com.dialogysm.client.DynamicTextures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：一张配置目录里的贴图（分块传，单包 ≤ 32KB）。
 * 客户端收齐后注册成 TextureManager 里的动态纹理，路径即 dialogysm:custom/xxx。
 */
public class TextureS2C {

    public final String rl;
    public final int index;
    public final int count;
    public final byte[] data;

    public TextureS2C(String rl, int index, int count, byte[] data) {
        this.rl = rl;
        this.index = index;
        this.count = count;
        this.data = data;
    }

    public static void encode(TextureS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.rl, 256);
        buf.writeVarInt(msg.index);
        buf.writeVarInt(msg.count);
        buf.writeByteArray(msg.data);
    }

    public static TextureS2C decode(FriendlyByteBuf buf) {
        String rl = buf.readUtf(256);
        int index = buf.readVarInt();
        int count = buf.readVarInt();
        byte[] data = buf.readByteArray();
        return new TextureS2C(rl, index, count, data);
    }

    public static void handle(TextureS2C msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> DynamicTextures.accept(msg.rl, msg.index, msg.count, msg.data)));
        ctx.setPacketHandled(true);
    }
}
