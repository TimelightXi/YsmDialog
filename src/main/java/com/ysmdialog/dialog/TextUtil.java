package com.ysmdialog.dialog;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ysmdialog.YsmDialogMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文本工具：把脚本里"字符串 / 文本组件 JSON / 组件数组"三种写法统一成 Component，
 * 并支持 @i（玩家名）占位符替换。
 */
public final class TextUtil {

    private TextUtil() {
    }

    public static Component fromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return Component.empty();
        }
        try {
            if (el.isJsonPrimitive()) {
                // 纯字符串
                return Component.literal(el.getAsString());
            }
            if (el.isJsonArray()) {
                MutableComponent out = Component.empty();
                for (JsonElement e : el.getAsJsonArray()) {
                    out.append(fromJson(e));
                }
                return out;
            }
            return Component.Serializer.fromJson(el);
        } catch (Exception ex) {
            YsmDialogMod.LOG.warn("[YsmDialog] 文本组件解析失败: {}", el, ex);
            return Component.literal(el.toString());
        }
    }

    public static Component fromJsonString(String json) {
        if (json == null || json.isBlank()) {
            return Component.empty();
        }
        try {
            return fromJson(JsonParser.parseString(json));
        } catch (Exception ex) {
            return Component.literal(json);
        }
    }

    /** 把 command 字段统一成字符串列表（支持 "cmd" 与 ["cmd1","cmd2"] 两种写法） */
    public static List<String> stringList(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null || el.isJsonNull()) {
            return out;
        }
        if (el.isJsonPrimitive()) {
            String s = el.getAsString();
            if (!s.isBlank()) {
                out.add(s);
            }
            return out;
        }
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                if (e != null && e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (!s.isBlank()) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    /** 遍历整个组件树做字符串替换（保留颜色/样式） */
    public static Component replace(Component component, String placeholder, String replacement) {
        if (component == null) {
            return Component.empty();
        }
        if (placeholder == null || placeholder.isEmpty()) {
            return component;
        }
        MutableComponent out = Component.empty();
        out.setStyle(component.getStyle());
        component.visit((style, text) -> {
            out.append(Component.literal(text.replace(placeholder, replacement)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
