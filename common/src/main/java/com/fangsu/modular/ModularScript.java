package com.fangsu.modular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一个独立脚本（Scratch 画布上的一根可自由摆放的栈）。
 * 脚本内元素必须是命令类积木（HAT/STACK/CAP/CONTAINER）；数据块只作某块值槽的嵌套 expr。
 */
public final class ModularScript {

    private double x;
    private double y;
    private final List<ModularBlock> blocks = new ArrayList<>();

    public ModularScript() {
        this(0, 0);
    }

    public ModularScript(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public List<ModularBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    /** 可变内部列表（供编辑器/编解码器在受控场合使用）。 */
    public List<ModularBlock> mutableBlocks() {
        return blocks;
    }

    public void add(ModularBlock b) {
        blocks.add(b);
    }

    public void add(int i, ModularBlock b) {
        blocks.add(Math.max(0, Math.min(i, blocks.size())), b);
    }

    public ModularBlock remove(int i) {
        return blocks.remove(i);
    }

    public boolean remove(ModularBlock b) {
        return blocks.remove(b);
    }

    public int size() {
        return blocks.size();
    }

    public ModularScript copy() {
        ModularScript s = new ModularScript(x, y);
        for (ModularBlock b : blocks) s.blocks.add(b.copy());
        return s;
    }
}
