package com.ysmdialog.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.ysmdialog.YsmDialogMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端：把服务端下发的配置贴图注册进 TextureManager。
 * 注册后资源路径就是服务端给定的 ysmdialog:custom/xxx，DialogScreen 直接 blit 即可。
 */
public final class DynamicTextures {

    /** 分块缓存：rl → 已收的字节 */
    private static final Map<String, ByteArrayOutputStream> PARTIAL = new HashMap<>();

    private DynamicTextures() {
    }

    public static void accept(String rlString, int index, int count, byte[] data) {
        if (rlString == null || rlString.isBlank() || data == null) {
            return;
        }
        ByteArrayOutputStream buffer;
        if (index == 0) {
            buffer = new ByteArrayOutputStream(Math.max(16, data.length));
            PARTIAL.put(rlString, buffer);
        } else {
            buffer = PARTIAL.get(rlString);
            if (buffer == null) {
                YsmDialogMod.LOG.warn("[YsmDialog] 贴图分块乱序，丢弃 {} (块 {}/{})", rlString, index + 1, count);
                return;
            }
        }
        buffer.write(data, 0, data.length);
        if (index + 1 >= count) {
            PARTIAL.remove(rlString);
            register(rlString, buffer.toByteArray());
        }
    }

    private static void register(String rlString, byte[] png) {
        try {
            ResourceLocation rl = new ResourceLocation(rlString);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            var textureManager = Minecraft.getInstance().getTextureManager();
            // 同一 id 重复下发时先关掉旧纹理，避免显存泄漏
            if (textureManager.getTexture(rl) instanceof DynamicTexture old) {
                old.close();
            }
            textureManager.register(rl, texture);
            DialogScreen.putTextureSize(rl, image.getWidth(), image.getHeight());
            YsmDialogMod.LOG.info("[YsmDialog] 已接收配置贴图 {}（{}×{}，{}B）",
                    rlString, image.getWidth(), image.getHeight(), png.length);
        } catch (Exception e) {
            YsmDialogMod.LOG.error("[YsmDialog] 配置贴图注册失败 {}", rlString, e);
        }
    }

    /** 断开连接时清空半包缓存 */
    public static void clear() {
        PARTIAL.clear();
    }
}
