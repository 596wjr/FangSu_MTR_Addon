package com.fangsu.modular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 积木的展示单元：标签文字或一个值槽（socket）。
 * <p>
 * 值槽按“输入类型”区分可接受的数据与外形：
 * <ul>
 *   <li>{@link Kind#LABEL} 普通文字标签（不可编辑）；</li>
 *   <li>{@link Kind#NUMBER} 数字槽（O 形/圆角输入），接受数字或圆角报告器；</li>
 *   <li>{@link Kind#TEXT} 文本槽（矩形输入框），接受字符串/任意报告器（数字自动转字符串）；</li>
 *   <li>{@link Kind#BOOLEAN} 布尔槽（菱形/条件），接受布尔（predicate）块；</li>
 *   <li>{@link Kind#CHOICE} 下拉选择（PULL），不接受嵌套块。</li>
 * </ul>
 * 值槽要么持有“字面值”（literal），要么持有一个嵌套的表达式块 {@code expr}
 * （数据块：REPORTER / PREDICATE，可在画布上直接拖入，并可递归再嵌套）。字面值仅在 expr 为空时生效。
 */
public final class ModularComponent {

    public enum Kind {
        LABEL, NUMBER, TEXT, BOOLEAN, CHOICE
    }

    private final Kind kind;
    private final String name;         // 槽名（序列化键）；LABEL 时 name 即文案翻译键
    /** 数字/文本字面值；NUMBER 存 Double，TEXT 存 String，BOOLEAN 存 Boolean，CHOICE 存所选 String。 */
    private Object literal;
    private final List<String> items;  // 仅 CHOICE
    private ModularBlock expr;         // 嵌套表达式块（可空）

    private ModularComponent(Kind kind, String name, Object literal, List<String> items) {
        this.kind = kind;
        this.name = name;
        this.literal = literal;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public static ModularComponent label(String textKey) {
        return new ModularComponent(Kind.LABEL, textKey, null, null);
    }

    public static ModularComponent number(String name) {
        return number(name, 0d);
    }

    public static ModularComponent number(String name, double val) {
        return new ModularComponent(Kind.NUMBER, name, val, null);
    }

    public static ModularComponent text(String name) {
        return text(name, "");
    }

    public static ModularComponent text(String name, String val) {
        return new ModularComponent(Kind.TEXT, name, val, null);
    }

    public static ModularComponent bool(String name) {
        return bool(name, false);
    }

    public static ModularComponent bool(String name, boolean val) {
        return new ModularComponent(Kind.BOOLEAN, name, val, null);
    }

    public static ModularComponent choice(String name, List<String> items) {
        return choice(name, items.isEmpty() ? "" : items.get(0), items);
    }

    public static ModularComponent choice(String name, String val, List<String> items) {
        List<String> list = new ArrayList<>(items);
        String v = list.contains(val) ? val : (list.isEmpty() ? "" : list.get(0));
        return new ModularComponent(Kind.CHOICE, name, v, list);
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public boolean isLabel() {
        return kind == Kind.LABEL;
    }

    /* ---------------- 字面值 ---------------- */

    public double asNumber() {
        if (kind != Kind.NUMBER) return 0;
        return literal instanceof Number ? ((Number) literal).doubleValue() : 0;
    }

    public void setNumber(double v) {
        literal = v;
        clearExpr();
    }

    public String asString() {
        if (kind == Kind.TEXT) return literal == null ? "" : String.valueOf(literal);
        if (kind == Kind.CHOICE) return literal == null ? "" : String.valueOf(literal);
        // 兼容：数字/其它统一转字符串显示
        return literal == null ? "" : String.valueOf(literal);
    }

    public void setString(String v) {
        literal = v == null ? "" : v;
        clearExpr();
    }

    public boolean asBoolean() {
        return kind == Kind.BOOLEAN && Boolean.TRUE.equals(literal);
    }

    public void setBoolean(boolean v) {
        literal = v;
        clearExpr();
    }

    public List<String> getItems() {
        return Collections.unmodifiableList(items);
    }

    /* ---------------- 嵌套表达式块 ---------------- */

    public boolean hasExpr() {
        return expr != null;
    }

    public ModularBlock getExpr() {
        return expr;
    }

    public void setExpr(ModularBlock block) {
        this.expr = block;
    }

    public void clearExpr() {
        this.expr = null;
    }

    /** 该槽是否允许放入嵌套数据块。 */
    public boolean acceptsNested() {
        return kind == Kind.NUMBER || kind == Kind.TEXT || kind == Kind.BOOLEAN;
    }

    /** 数据块形状与该槽是否兼容（含自动类型转换规则）。 */
    public boolean acceptsShape(BlockShape shape) {
        if (shape == null) return false;
        switch (kind) {
            case BOOLEAN:
                return shape == BlockShape.PREDICATE;
            case NUMBER:
                // 数字槽收数字报告器；文本报告器数字也接受（自动转数值，尽力而为）
                return shape == BlockShape.REPORTER;
            case TEXT:
                // 文本槽收文本/任意圆角报告器；布尔除外
                return shape == BlockShape.REPORTER;
            default:
                return false;
        }
    }

    public ModularComponent copy() {
        ModularComponent c = new ModularComponent(kind, name, literal, items);
        if (expr != null) c.expr = expr.copy();
        return c;
    }
}
