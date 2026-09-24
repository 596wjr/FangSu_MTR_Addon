package com.fangsu.modular;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ModularBlock 流式构造器：便于以可读方式注册命令块/容器块/数据块。
 */
public final class ModularBlockBuilder {

    private String type;
    private Color color = new Color(0x5b8bd4);
    private final List<String> families = new ArrayList<>();
    private BlockShape shape = BlockShape.STACK;
    private boolean terminatesStack = false;
    private final List<ModularComponent> components = new ArrayList<>();
    private final List<ContainerObject> containers = new ArrayList<>();

    private ModularBlockBuilder() {
    }

    public static ModularBlockBuilder of(String type) {
        ModularBlockBuilder b = new ModularBlockBuilder();
        b.type = type;
        return b;
    }

    public ModularBlockBuilder color(Color c) {
        this.color = c;
        return this;
    }

    public ModularBlockBuilder family(String f) {
        this.families.add(f);
        return this;
    }

    /** 起始/帽子块（如"当绿旗被点击"）。 */
    public ModularBlockBuilder hat() {
        this.shape = BlockShape.HAT;
        return this;
    }

    /** 普通命令块。 */
    public ModularBlockBuilder stack() {
        this.shape = BlockShape.STACK;
        return this;
    }

    /** 收尾块（cap）。 */
    public ModularBlockBuilder cap() {
        this.shape = BlockShape.CAP;
        return this;
    }

    /** C 形命令块（带容器槽）。 */
    public ModularBlockBuilder containerBlock() {
        this.shape = BlockShape.CONTAINER;
        return this;
    }

    /** 圆角数据报告器（数字/文本）。 */
    public ModularBlockBuilder reporter() {
        this.shape = BlockShape.REPORTER;
        return this;
    }

    /** 六边形/布尔条件块。 */
    public ModularBlockBuilder predicate() {
        this.shape = BlockShape.PREDICATE;
        return this;
    }

    public ModularBlockBuilder shape(BlockShape s) {
        this.shape = s == null ? BlockShape.STACK : s;
        return this;
    }

    /** 标记为栈终止块（底部无卡扣、其后不能再接命令块，如 forever 循环）。 */
    public ModularBlockBuilder terminatesStack() {
        this.terminatesStack = true;
        return this;
    }

    /* ---------------- 展示单元 ---------------- */

    public ModularBlockBuilder text(String textKey) {
        this.components.add(ModularComponent.label(textKey));
        return this;
    }

    public ModularBlockBuilder num(String name) {
        this.components.add(ModularComponent.number(name));
        return this;
    }

    public ModularBlockBuilder num(String name, double val) {
        this.components.add(ModularComponent.number(name, val));
        return this;
    }

    public ModularBlockBuilder str(String name) {
        this.components.add(ModularComponent.text(name));
        return this;
    }

    public ModularBlockBuilder str(String name, String val) {
        this.components.add(ModularComponent.text(name, val));
        return this;
    }

    public ModularBlockBuilder bool(String name) {
        this.components.add(ModularComponent.bool(name));
        return this;
    }

    public ModularBlockBuilder bool(String name, boolean val) {
        this.components.add(ModularComponent.bool(name, val));
        return this;
    }

    public ModularBlockBuilder choice(String name, List<String> items) {
        this.components.add(ModularComponent.choice(name, items));
        return this;
    }

    public ModularBlockBuilder choice(String name, String val, List<String> items) {
        this.components.add(ModularComponent.choice(name, val, items));
        return this;
    }

    /** 追加一个命名容器槽（仅 CONTAINER 使用），可链式多次。 */
    public ModularBlockBuilder container(String slotName) {
        this.containers.add(new ContainerObject(slotName));
        return this;
    }

    public ModularBlock build() {
        return new ModularBlock(type, color, families, shape, terminatesStack, components, containers);
    }
}
