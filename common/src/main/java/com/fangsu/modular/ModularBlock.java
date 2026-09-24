package com.fangsu.modular;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一块积木。既有“命令块”（进入脚本/子栈列表：HAT/STACK/CAP/CONTAINER），
 * 也有“数据/表达式块”（REPORTER/PREDICATE，只能作为某块值槽的嵌套 expr 出现）。
 * <ul>
 *   <li>{@code type} 类型键；</li>
 *   <li>{@code color} 主色（按 family 通常一致）；</li>
 *   <li>{@code families} 归属的积木组（可多个）；</li>
 *   <li>{@code shape} 形状（决定卡扣/外形与可放置位置）；</li>
 *   <li>{@code componentList} 展示单元（标签文字 + 值槽），值槽可内嵌表达式块；</li>
 *   <li>{@code containers} 命名的子容器槽（仅 CONTAINER 使用），每条承载子脚本。</li>
 * </ul>
 * <p>由 {@link ModularBlockFactory} 以“类型 → 模板实例”注册，编辑器复制模板得到新块。
 * 序列化统一由 {@link ModularCodec} 负责。
 */
public class ModularBlock {

    private final String type;
    private final Color color;
    private final List<String> families;
    private final BlockShape shape;
    private final boolean terminatesStack;
    private final List<ModularComponent> componentList;
    private final List<ContainerObject> containers;

    public ModularBlock(String type, Color color, List<String> families, BlockShape shape,
                        List<ModularComponent> componentList, List<ContainerObject> containers) {
        this(type, color, families, shape, false, componentList, containers);
    }

    public ModularBlock(String type, Color color, List<String> families, BlockShape shape,
                        boolean terminatesStack,
                        List<ModularComponent> componentList, List<ContainerObject> containers) {
        this.type = type;
        this.color = color;
        this.families = new ArrayList<>(families);
        this.shape = shape == null ? BlockShape.STACK : shape;
        this.terminatesStack = terminatesStack;
        this.componentList = new ArrayList<>();
        if (componentList != null) {
            for (ModularComponent c : componentList) this.componentList.add(c.copy());
        }
        this.containers = new ArrayList<>();
        if (containers != null) {
            for (ContainerObject co : containers) this.containers.add(co.copy());
        }
    }

    public String getType() {
        return type;
    }

    public Color getColor() {
        return color;
    }

    public List<String> getFamilies() {
        return Collections.unmodifiableList(families);
    }

    public BlockShape getShape() {
        return shape;
    }

    public List<ModularComponent> getComponentList() {
        return Collections.unmodifiableList(componentList);
    }

    public List<ContainerObject> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    public boolean hasContainers() {
        return !containers.isEmpty();
    }

    /** 是否为可上下拼接的命令类（可作为脚本/子栈元素）。 */
    public boolean isStackCommand() {
        return shape.isStackCommand();
    }

    /** 是否为数据/表达式块（只可作某值槽的 expr）。 */
    public boolean isExpression() {
        return shape.isExpression();
    }

    public boolean isBegin() {
        return shape == BlockShape.HAT;
    }

    public boolean isEnd() {
        return shape == BlockShape.CAP;
    }

    /** 是否为栈终止块（底部无卡扣、其后不能再接命令块，如 forever 循环）。 */
    public boolean isStackTerminator() {
        return terminatesStack;
    }

    public ModularBlock copy() {
        return new ModularBlock(type, color, families, shape, terminatesStack, componentList, containers);
    }
}
