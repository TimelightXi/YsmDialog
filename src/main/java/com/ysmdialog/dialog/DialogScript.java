package com.ysmdialog.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.ysmdialog.YsmDialogMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一整份对话脚本（对应一个 JSON 文件）。
 * 结构参考 VNDialog：id / title / start / entries[]，节点之间用 next 或 options.target 串起来。
 */
public class DialogScript {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** 脚本 id（= 文件名去掉 .json） */
    public String id;

    public String title;

    public String description;

    /** 起始节点 id；不写则从 entries[0] 开始 */
    @SerializedName("start")
    public String startId;

    /** 是否允许 ESC 关掉对话（任务对话建议 false，强制走完流程） */
    @SerializedName("allowClose")
    public Boolean allowClose;

    public List<DialogNode> entries = new ArrayList<>();

    /** 运行时索引，不参与序列化 */
    private transient Map<String, DialogNode> index;

    public static DialogScript parse(String json) {
        DialogScript s = GSON.fromJson(json, DialogScript.class);
        if (s == null) {
            return null;
        }
        if (s.entries == null) {
            s.entries = new ArrayList<>();
        }
        return s;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public boolean closeAllowed() {
        return allowClose != null && allowClose;
    }

    public DialogNode firstNode() {
        if (entries.isEmpty()) {
            return null;
        }
        if (startId != null && !startId.isBlank()) {
            DialogNode n = find(startId);
            if (n != null) {
                return n;
            }
        }
        // 找第一个没有 visibility 标记的节点作为入口
        return entries.get(0);
    }

    public DialogNode find(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || entries.isEmpty()) {
            return null;
        }
        if (index == null) {
            Map<String, DialogNode> m = new HashMap<>();
            for (DialogNode n : entries) {
                if (n != null && n.id != null) {
                    m.put(n.id, n);
                }
            }
            index = m;
        }
        return index.get(nodeId);
    }

    /** 取下一个节点：优先 next，其次数组顺序 */
    public DialogNode next(DialogNode current) {
        if (current == null) {
            return null;
        }
        if (current.next != null && !current.next.isBlank()) {
            return find(current.next);
        }
        for (int i = 0; i < entries.size() - 1; i++) {
            if (entries.get(i) == current) {
                return entries.get(i + 1);
            }
        }
        return null;
    }

    /** 兜底：把所有节点文本拼一下用于调试日志 */
    public void debugSummary() {
        YsmDialogMod.LOG.info("[YsmDialog] 脚本 {} 共 {} 个节点，起始节点={}", id, entries.size(),
                firstNode() == null ? "null" : firstNode().id);
    }

    /** 节点级可见性过滤（服务端调用），会就地删除不可见节点 */
    public void filterNodes(java.util.function.Predicate<String> conditionTest) {
        List<DialogNode> keep = new ArrayList<>();
        for (DialogNode n : entries) {
            if (n.visibilityCommand == null || conditionTest.test(n.visibilityCommand)) {
                keep.add(n);
            }
        }
        entries = keep;
        index = null;
    }

    /** 未使用的占位方法，保持 API 干净 */
    public JsonElement raw() {
        return GSON.toJsonTree(this);
    }
}
