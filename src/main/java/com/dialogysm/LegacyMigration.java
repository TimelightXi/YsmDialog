package com.dialogysm;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 旧名迁移：模组 1.0.7 起把 modId / 配置目录 / 存档数据从 ysmdialog 改为 dialogysm。
 * 只做一次性拷贝，不删除旧目录，出问题可以直接回退。
 */
public final class LegacyMigration {

    private LegacyMigration() {}

    private static final Logger LOG = DialogYSMMod.LOG;

    /** 服务端启动时调用一次：迁移配置目录与世界存档数据。 */
    public static void run(MinecraftServer server) {
        try {
            Path cfg = FMLPaths.CONFIGDIR.get();
            copyIfAbsent(cfg.resolve("ysmdialog"), cfg.resolve("dialogysm"), "配置目录");
        } catch (Exception e) {
            LOG.warn("[DialogYSM] 配置目录迁移失败: {}", e.toString());
        }
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            copyIfAbsent(root.resolve("ysmdialog"), root.resolve("dialogysm"), "世界数据");
        } catch (Exception e) {
            LOG.warn("[DialogYSM] 世界数据迁移失败: {}", e.toString());
        }
    }

    /** 目标不存在且源存在时，整棵目录复制过去。 */
    private static void copyIfAbsent(Path from, Path to, String what) throws IOException {
        if (!Files.isDirectory(from) || Files.exists(to)) return;
        try (var walk = Files.walk(from)) {
            walk.forEach(src -> {
                try {
                    Path dst = to.resolve(from.relativize(src).toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        LOG.info("[DialogYSM] 已把旧{} {} 复制到 {}", what, from.getFileName(), to.getFileName());
    }
}
