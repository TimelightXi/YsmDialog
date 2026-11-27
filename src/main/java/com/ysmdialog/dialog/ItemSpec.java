package com.ysmdialog.dialog;

/** 对话中展示的物品（任务奖励预览 / 交付物等） */
public class ItemSpec {

    /** 物品 id，如 "minecraft:diamond_sword" */
    public String item = "minecraft:air";

    /** 数量，默认 1 */
    public int count = 1;

    /** 可选 NBT 字符串 */
    public String nbt;
}
