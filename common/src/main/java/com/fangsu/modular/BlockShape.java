package com.fangsu.modular;

/**
 * 积木形状（决定画布上如何绘制卡扣/外形，以及能与哪些槽位拼接）。
 * <ul>
 *   <li>{@link #HAT} 起始/帽子块：顶部为拱形，只能作为脚本/子栈的栈顶（如"当绿旗被点击"）；</li>
 *   <li>{@link #STACK} 普通命令块：顶部有卡槽、底部有卡扣，可上下拼接（可置于其它命令块下方、也可接后续块）；</li>
 *   <li>{@link #CAP} 收尾/帽尾块：顶部有卡槽、底部圆弧闭合（如"停止"），其后不能再接块；</li>
 *   <li>{@link #CONTAINER} C 形命令块：如 if/repeat/forever，包裹一段子脚本（见 {@link ContainerObject}），仍属命令可上下拼接；</li>
 *   <li>{@link #REPORTER} 数值/文本报告器（圆角）：返回一个值，可拖入兼容的值槽（矩形/圆形输入框）并递归嵌套；</li>
 *   <li>{@link #PREDICATE} 布尔/条件块（六边形）：返回 true/false，可拖入布尔槽（菱形）。</li>
 * </ul>
 */
public enum BlockShape {
    HAT, STACK, CAP, CONTAINER, REPORTER, PREDICATE;

    /** 是否为可上下拼接的命令类积木（可进入脚本/子栈列表）。 */
    public boolean isStackCommand() {
        return this == HAT || this == STACK || this == CAP || this == CONTAINER;
    }

    /** 是否为可拖入槽位的数据/表达式类积木。 */
    public boolean isExpression() {
        return this == REPORTER || this == PREDICATE;
    }
}
