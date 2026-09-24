package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.modular.*;
import com.fangsu.utils.ColorUtil;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 图形化积木编辑器（Scratch 仿）——全屏主界面。
 * <p>
 * 布局：最左积木组列 → 其右该组积木列 → 中间编程画布（{@link ModularDocument} 多根位置化脚本）。
 * <p>
 * 统一递归模型：本编辑器把"命令块文字段"与"表达式块内容"都当作一个由"标签 + 值槽"组成的水平 run，
 * 值槽既可以放字面值，也可以再嵌一个表达式块（报告器/布尔）——从而支持任意深度嵌套
 * （例如 显示文本[ 连接[ 随机数[1][10] ]["b"] ] ）。渲染时递归画槽，并把所有深度的槽都登记为
 * 命中/拖放目标。
 * <p>
 * 交互：
 * <ul>
 *   <li>点击 vs 拖拽：按阈值区分。点击空值槽 → 数字/文本内联编辑、布尔点按取反、下拉循环；</li>
 *   <li>拖拽：命令块上下拼接/移动/进出容器；数据块（报告器/布尔）拖到任何兼容槽可嵌套/替换；
 *       拖回左侧积木区=删除；实时吸附提示；</li>
 *   <li>起始块(HAT)只能作脚本首元素或新建脚本，不能塞到别的命令块下方；</li>
 *   <li>中键拖动画布平移，滚轮竖向滚动；保存/Esc 退出。</li>
 * </ul>
 */
public class ModularEditingScreen extends Screen {

    /** 打开前的屏幕（通常是配置屏），保存/关闭时返回它，保证配置屏的 onClose 持久化逻辑能执行。 */
    private final Screen parent;

    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final ModularBlockFactory factory;
    /** 白名单：仅显示/创建这些积木 type（null = 全部）。 */
    private final java.util.Set<String> allowedTypes;

    private final ModularDocument doc = new ModularDocument();
    private boolean dirty;
    private String selectedFamilyId;

    private int canvasX, canvasY, canvasW, canvasH;
    private int scrollX, scrollY;
    /** 左侧积木组/积木面板的纵向滚动偏移。 */
    private int paletteScroll;

    /* 渲染期几何收集 */
    private final List<RowHit> rows = new ArrayList<>();    // 命令块命中（脚本/容器/索引）
    private final List<SocketHit> sockets = new ArrayList<>(); // 所有深度的值槽命中
    private final List<ContainerRect> containerRects = new ArrayList<>(); // C 形容器体命中

    /* 交互 */
    private ModularBlock grab;              // 正在拖：命令块 或 数据块
    private boolean grabFromPalette;        // true=新块
    private ModularScript grabScript;       // 命令块来源脚本
    private ContainerObject grabContainer;  // 命令块来源容器
    private ModularComponent grabSocket;    // 若拖的是"从某槽中取出的数据块"，记录原槽
    private boolean dragging;
    private double pressX, pressY;
    private boolean panning;
    private double panX, panY;
    private static final double DRAG_THRESHOLD = 5;

    /* 点击待编辑 */
    private ModularComponent clickEdit;

    /* 内联编辑 */
    private ModularComponent editComp;
    private StringBuilder editBuf = new StringBuilder();
    private int editX, editY, editW;
    private boolean editing;

    private static final int BLOCK_H = 30;
    private static final int TOP_NOTCH = 5;
    private static final int BOT_TAB = 5;
    private static final int SOCKET_H = 16;
    private static final int EXPR_H = 20;   // 表达式块高度

    public ModularEditingScreen(Supplier<String> getter, Consumer<String> setter) {
        this(getter, setter, null);
    }

    /** 带白名单构造：allowedTypes 非空时仅显示/创建这些积木；否则全部。 */
    public ModularEditingScreen(Supplier<String> getter, Consumer<String> setter, java.util.Set<String> allowedTypes) {
        super(ComponentHelper.translatable("modular.fangsu.title"));
        this.parent = Minecraft.getInstance().screen;
        this.getter = getter;
        this.setter = setter;
        this.factory = ModularBlockFactory.getInstance();
        this.allowedTypes = allowedTypes;
        List<ModularBlockFactory.FamilyInfo> fams = factory.getFamilies();
        this.selectedFamilyId = fams.isEmpty() ? null : fams.get(0).getId();
        try {
            String s = getter.get();
            if (s != null && !s.isEmpty()) {
                ModularDocument d = ModularCodec.documentFromJson(s);
                for (ModularScript sc : d.getScripts()) doc.addScript(sc);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        editing = false;
        canvasX = width / 3;
        canvasY = 22;
        canvasW = width - canvasX;
        canvasH = height - canvasY - 6;
        scrollX = 0;
        scrollY = 0;
        addRenderableWidget(ComponentHelper.button(width - 110, 2, 100, 18,
                ComponentHelper.translatable("modular.fangsu.save"), b -> saveAndClose()));
    }

    /* ===================== 坐标变换 ===================== */

    private int worldX(double mouseX) {
        return (int) mouseX - canvasX + scrollX;
    }

    private int worldY(double mouseY) {
        return (int) mouseY - canvasY + scrollY;
    }

    /* ===================== 渲染 ===================== */

    @Override
    //#if MC_VERSION >= 12000
    public void render(GuiGraphics g0, int mouseX, int mouseY, float partialTick) {
        //#else
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //#endif
        //#if MC_VERSION >= 12000
        super.render(g0, mouseX, mouseY, partialTick);
        GraphicContext g = GraphicContext.of(g0);
        //#else
        //$$ super.render(poseStack, mouseX, mouseY, partialTick);
        //$$ GraphicContext g = GraphicContext.of(poseStack);
        //#endif
        renderPalette(g, mouseX, mouseY);
        renderCanvas(g, mouseX, mouseY);
        g.drawString(font, ComponentHelper.translatable("modular.fangsu.title").getString(), 6, 4, 0xffffffff, false);
        if (dirty) {
            g.drawString(font, ComponentHelper.translatable("modular.fangsu.unsaved").getString(),
                    canvasX + 6, 5, 0xffffaa44, false);
        }
        drawEditOverlay(g);
    }

    private void renderCanvas(GraphicContext g, int mouseX, int mouseY) {
        g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF141414);
        g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        g.pose().pushPose();
        g.pose().translate(canvasX - scrollX, canvasY - scrollY, 0);

        rows.clear();
        sockets.clear();
        containerRects.clear();
        for (ModularScript script : doc.getScripts()) {
            int y = (int) script.getY();
            for (int i = 0; i < script.getBlocks().size(); i++) {
                ModularBlock b = script.getBlocks().get(i);
                y += drawCommand(g, script, (int) script.getX(), y, b, i, null);
            }
        }

        if (rows.isEmpty() && sockets.isEmpty() && grab == null && !dragging) {
            g.drawString(font, ComponentHelper.translatable("modular.fangsu.canvas_hint").getString(),
                    16, 16, 0x55ffffff, false);
        }

        // 拖拽提示 + 跟手
        if (grab != null && dragging) {
            ModularBlock moving = grab;
            int mx = worldX(mouseX);
            int my = worldY(mouseY);
            if (moving.isExpression()) {
                SocketHit target = findEmptyAcceptingSocket(mx, my, moving);
                if (target != null) {
                    g.fill(target.x - 1, target.y - 2, target.x + target.w + 1, target.y + SOCKET_H + 3, 0xffffd23e);
                } else {
                    g.fill(mx - 1, my - 1, mx + exprWidth(moving) + 1, my + EXPR_H + 1, 0xffd04a4a);
                }
            } else {
                DropHint hint = computeCommandDrop(mx, my, moving);
                if (hint != null) {
                    if (hint.intoContainer) {
                        // 高亮“容器内部”区域：亮黄描边的 C 框，清楚提示会放“里头”
                        int left = hint.containerBodyLeft;
                        int top = hint.containerHeadBottom;
                        int right = hint.containerBodyRight;
                        int bottom = hint.containerBodyBottom;
                        g.fill(left - 3, top, right + 3, top + 2, 0xffffd23e);
                        g.fill(left - 3, bottom, right + 3, bottom + 2, 0xffffd23e);
                        g.fill(left - 3, top, left - 1, bottom, 0xffffd23e);
                        g.fill(right + 1, top, right + 3, bottom, 0xffffd23e);
                    } else {
                        int indW = Math.max(measureRun(moving), 90);
                        g.fill(hint.indX, hint.indY, hint.indX + indW, hint.indY + 3, 0xff4ad04a);
                    }
                }
            }
            int gw = moving.isExpression() ? exprWidth(moving) : measureRun(moving);
            int color = ColorUtil.awtColorToMinecraft(moving.getColor());
            if (moving.isExpression()) {
                g.fill(mx, my, mx + gw, my + EXPR_H, (color & 0x00ffffff) | 0xb0000000);
                drawComponents(g, moving, mx + 4, my + (EXPR_H - font.lineHeight) / 2, false);
            } else {
                g.fill(mx, my, mx + gw, my + BLOCK_H, (color & 0x00ffffff) | 0xb0000000);
                drawComponents(g, moving, mx + 8, my + (BLOCK_H - font.lineHeight) / 2, false);
            }
        }
        g.pose().popPose();
        g.disableScissor();
    }

    /* ---------- 命令块递归布局 ---------- */

    private int drawCommand(GraphicContext g, ModularScript script, int x, int y,
                            ModularBlock b, int index, ContainerObject parent) {
        int w = measureRun(b);
        int bodyY = y + TOP_NOTCH;
        int bodyH = BLOCK_H - TOP_NOTCH - BOT_TAB;
        drawBlockShape(g, x, y, w, b);
        // 内容基线：块正文与所有内嵌芯片共享同一竖直中线，保证输入框对齐
        int contentY = bodyY + (bodyH - font.lineHeight) / 2;
        drawComponents(g, b, x + 8, contentY, true);

        int bottom;
        if (b.getShape() != BlockShape.CONTAINER) {
            bottom = y + BLOCK_H;
        } else {
            // C 形：用父块色带做“左臂 + 底带”包裹子脚本，子脚本明显缩进，一看便知“在里头”。
            int cy = y + BLOCK_H - BOT_TAB;
            List<ContainerObject> cos = b.getContainers();
            if (cos.isEmpty()) {
                bottom = y + BLOCK_H;
            } else {
                ContainerObject co = cos.get(0);
                int armW = 26;
                int innerGap = 6;
                int boxX = x + 8 + armW + innerGap;
                int armCol = ColorUtil.awtColorToMinecraft(b.getColor());
                int edge = ColorUtil.awtColorToMinecraft(b.getColor().darker());
                int bodyTop = cy;
                int bodyEnd;
                if (co.getBlockList().isEmpty()) {
                    bodyEnd = bodyTop + 18;
                    g.fill(x + 8, bodyTop, x + 8 + armW, bodyEnd, armCol);
                    g.fill(x + 8, bodyEnd - 4, x + 8 + armW + 150, bodyEnd, armCol);
                    g.fill(x + 8 + armW, bodyTop + 2, x + 8 + armW + 148, bodyEnd - 2, 0x22000000);
                    g.fill(x + 8, bodyTop, x + 8 + armW, bodyTop + 2, edge);
                    g.fill(x + 8, bodyEnd - 4, x + 8 + armW + 150, bodyEnd - 2, edge);
                    containerRects.add(new ContainerRect(b, x, x + 8 + armW, bodyTop, x + 8 + armW + 150, bodyEnd));
                } else {
                    int by = bodyTop + 4;
                    for (int i = 0; i < co.getBlockList().size(); i++) {
                        ModularBlock child = co.getBlockList().get(i);
                        by += drawCommand(g, script, boxX, by, child, i, co);
                    }
                    bodyEnd = Math.max(bodyTop + 12, by);
                    int maxRight = 0;
                    for (int i = 0; i < co.getBlockList().size(); i++) {
                        maxRight = Math.max(maxRight, measureRun(co.getBlockList().get(i)));
                    }
                    int rightX = boxX + maxRight + 6;
                    g.fill(x + 8, bodyTop, x + 8 + armW, bodyEnd, armCol);
                    g.fill(x + 8, bodyTop, x + 8 + armW, bodyTop + 2, edge);
                    g.fill(x + 8, bodyEnd - 2, x + 8 + armW, bodyEnd, edge);
                    g.fill(x + 8, bodyEnd - 4, rightX + 6, bodyEnd, armCol);
                    containerRects.add(new ContainerRect(b, x, x + 8 + armW, bodyTop, rightX + 6, bodyEnd));
                }
                bottom = bodyEnd;
            }
        }
        // 命中高度：命令块含整个 C 体，保证“接在其后”的绿线画在 C 底部而非头块下方。
        rows.add(new RowHit(script, parent, index, b, x, bodyY, w, bottom - bodyY));
        return bottom - y;
    }

    private void drawBlockShape(GraphicContext g, int x, int y, int w, ModularBlock b) {
        int color = ColorUtil.awtColorToMinecraft(b.getColor());
        int edge = ColorUtil.awtColorToMinecraft(b.getColor().darker());
        int dark = shade(color, 0.7f);
        int bodyY = y + TOP_NOTCH;
        int bodyH = BLOCK_H - TOP_NOTCH - BOT_TAB;
        int x2 = x + w;

        g.fill(x, bodyY, x2, bodyY + bodyH, color);
        switch (b.getShape()) {
            case HAT:
                g.fill(x + 6, y, x2 - 6, bodyY, color);
                g.fill(x + 12, y, x2 -12, y + 2, color);
                g.fill(x, bodyY, x2, bodyY + 2, dark);
                drawBottomTab(g, x, bodyY + bodyH, w, color);
                break;
            case CAP:
                drawTopNotch(g, x, y, w, color, dark);
                g.fill(x + 8, y + BLOCK_H - BOT_TAB, x2 - 8, y + BLOCK_H, color);
                break;
            case CONTAINER:
                drawTopNotch(g, x, y, w, color, dark);
                if (b.isStackTerminator()) {
                    // forever 等栈终止块：底部闭合（无卡扣），其后不能再接块
                    g.fill(x + 8, y + BLOCK_H - BOT_TAB, x2 - 8, y + BLOCK_H, color);
                } else {
                    drawBottomTab(g, x, bodyY + bodyH, w, color);
                }
                break;
            default:
                drawTopNotch(g, x, y, w, color, dark);
                drawBottomTab(g, x, bodyY + bodyH, w, color);
                break;
        }
        // 深色描边（最外层命令块外周）
        strokeRect(g, x - 1, y - 1, w + 2, BLOCK_H + 2, edge);
    }

    /** 画 1px 矩形描边（用于块/芯片加深色边框）。 */
    private void strokeRect(GraphicContext g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 顶部凹槽：固定在块左缘的一个固定偏移处（Scratch 同款，不随块宽居中），保证叠放时对齐。 */
    private void drawTopNotch(GraphicContext g, int x, int y, int w, int color, int dark) {
        int x2 = x + w;
        int gap = 12;
        int center = x + 16;              // 固定偏移（约头部内容起始处）
        g.fill(x, y, center - gap / 2, y + TOP_NOTCH, color);
        g.fill(center + gap / 2, y, x2, y + TOP_NOTCH, color);
        g.fill(center - gap / 2, y + TOP_NOTCH - 2, center + gap / 2, y + TOP_NOTCH, dark);
    }

    /** 底部卡扣：与顶部凹槽同一固定偏移，上下卡扣/凹槽对齐。 */
    private void drawBottomTab(GraphicContext g, int x, int tabTopY, int w, int color) {
        int x2 = x + w;
        int tabW = 14;
        int center = x + 16;              // 与 drawTopNotch 一致
        g.fill(center - tabW / 2, tabTopY, center + tabW / 2, tabTopY + BOT_TAB, color);
        g.fill(x, tabTopY, center - tabW / 2, tabTopY, shade(color, 0.9f));
        g.fill(center + tabW / 2, tabTopY, x2, tabTopY, shade(color, 0.9f));
    }

    private int shade(int mc, float f) {
        int r = (int) (((mc >> 16) & 0xff) * f);
        int gg = (int) (((mc >> 8) & 0xff) * f);
        int b = (int) ((mc & 0xff) * f);
        return 0xff000000 | (r << 16) | (gg << 8) | b;
    }

    /* ---------- 递归 run：渲染 + 登记所有深度槽 ---------- */

    /**
     * 把一个块的水平 run 画出来：标签文字 + 值槽；值槽若嵌表达式则递归（实现任意深度嵌套）。
     * collect 为 true（画布命令/表达式块）时，把遇到的每个值槽登记进 {@code sockets} 供命中/拖放。
     * 返回 run 占用宽度（不含左右留白，视调用而定）。
     */
    private int drawComponents(GraphicContext g, ModularBlock block, int x, int contentY, boolean collect) {
        return drawComponents(g, block, x, contentY, collect, 0);
    }

    /**
     * depth：当前块所在嵌套深度（0=命令块内容行，其内嵌报告器为 1，再嵌为 2…）。
     * 用来让外层芯片更高、内层更矮，形成“阶梯”以便区分内外层。
     */
    private int drawComponents(GraphicContext g, ModularBlock block, int x, int contentY, boolean collect, int depth) {
        int cx = x;
        for (ModularComponent c : block.getComponentList()) {
            if (c.isLabel()) {
                String t = ComponentHelper.translatable(c.getName()).getString();
                g.drawString(font, t, cx, contentY, 0xffffffff, true);
                cx += font.width(t) + 4;
                continue;
            }
            if (c.hasExpr()) {
                ModularBlock e = c.getExpr();
                int ew = exprWidth(e);
                int ec = ColorUtil.awtColorToMinecraft(e.getColor());
                // 阶梯高度：越深越矮（外层最高）。芯片围绕“整块文字行基线”上下对称扩展，
                // 内层内容仍用同一 contentY，保证所有输入框排在一条水平线上。
                int h = Math.max(SOCKET_H, EXPR_H + 6 - depth * 4);
                int centerY = contentY + (font.lineHeight - SOCKET_H) / 2 + SOCKET_H / 2; // 输入框带中线
                int ey = centerY - h / 2;
                strokeRect(g, cx - 1, ey - 1, ew + 2, h + 2, ColorUtil.awtColorToMinecraft(e.getColor().darker()));
                g.fill(cx, ey, cx + ew, ey + h, ec);
                // 内层内容仍用同一 contentY（同一基线）
                drawComponents(g, e, cx + 4, contentY, collect, depth + 1);
                if (collect) sockets.add(new SocketHit(block, c, e, cx, ey, ew, h));
                cx += ew + 4;
            } else {
                int cw = socketWidth(c);
                int boxTop = contentY + (font.lineHeight - SOCKET_H) / 2;
                drawSocketBox(g, cx, boxTop, cw, c.getKind());
                String disp = socketText(c);
                int ty = boxTop + (SOCKET_H - font.lineHeight) / 2;
                g.drawString(font, disp, cx + 4, ty, 0xff333333, false);
                if (c.getKind() == ModularComponent.Kind.CHOICE) {
                    g.drawString(font, "▾", cx + cw - 11, ty, 0xff777777, false);
                }
                if (collect) sockets.add(new SocketHit(block, c, null, cx, boxTop, cw, SOCKET_H));
                cx += cw + 4;
            }
        }
        return cx - x;
    }

    private void drawSocketBox(GraphicContext g, int x, int y, int w, ModularComponent.Kind kind) {
        if (kind == ModularComponent.Kind.BOOLEAN) {
            g.fill(x + 2, y, x + w - 2, y + SOCKET_H, 0xffffffff);
            g.fill(x, y + 3, x + 4, y + SOCKET_H - 3, 0xffffffff);
            g.fill(x + w - 4, y + 3, x + w, y + SOCKET_H - 3, 0xffffffff);
        } else {
            g.fill(x, y, x + w, y + SOCKET_H, 0xffffffff);
        }
    }

    /* ---------- 测量 ---------- */

    private int measureRun(ModularBlock b) {
        int w = 12;
        for (ModularComponent c : b.getComponentList()) {
            if (c.isLabel()) w += font.width(ComponentHelper.translatable(c.getName()).getString()) + 4;
            else if (c.hasExpr()) w += exprWidth(c.getExpr()) + 4;
            else w += socketWidth(c) + 4;
        }
        return Math.max(w, 60);
    }

    private int exprWidth(ModularBlock e) {
        int w = 8;
        for (ModularComponent c : e.getComponentList()) {
            if (c.isLabel()) w += font.width(ComponentHelper.translatable(c.getName()).getString()) + 4;
            else if (c.hasExpr()) w += exprWidth(c.getExpr()) + 4;
            else w += socketWidth(c) + 4;
        }
        return Math.max(w, 40);
    }

    private String socketText(ModularComponent c) {
        switch (c.getKind()) {
            case NUMBER:
                return String.valueOf(c.asNumber());
            case TEXT:
                return c.asString().isEmpty() ? " " : c.asString();
            case BOOLEAN:
                return String.valueOf(c.asBoolean());
            case CHOICE:
                return ComponentHelper.translatable(c.asString()).getString();
            default:
                return "";
        }
    }

    private int socketWidth(ModularComponent c) {
        int base = font.width(socketText(c)) + 12;
        return Math.max(30, base);
    }

    /* ===================== 左侧调色板 ===================== */

    /** 左侧面板最大滚动量：按家族列与积木列中较高者扣除面板可视高度。 */
    private int paletteMaxScroll() {
        int famContent = 0;
        for (ModularBlockFactory.FamilyInfo fi : factory.getFamilies()) {
            famContent += 26;
        }
        int blockContent = 0;
        if (selectedFamilyId != null) {
            for (String type : factory.getBlockTypesOf(selectedFamilyId)) {
                ModularBlock fb = factory.getDefaultBlock(type);
                if (fb == null || !isAllowed(fb)) continue;
                blockContent += 32;
            }
        }
        int content = 44 + Math.max(famContent, blockContent);
        int visible = height - 20;
        return Math.max(0, content - visible);
    }

    private void renderPalette(GraphicContext g, int mouseX, int mouseY) {
        int famW = width / 6;
        int blockW = width / 6;
        g.fill(0, 20, famW + blockW, height, 0x44111111);

        // 裁切到左侧面板区，防超出项绘制到屏幕外
        g.enableScissor(0, 20, famW + blockW, height);

        g.drawString(font, ComponentHelper.translatable("modular.fangsu.family_title").getString(), 6, 24, 0xffffffff, false);
        int y = 44 - paletteScroll;
        for (ModularBlockFactory.FamilyInfo fi : factory.getFamilies()) {
            boolean sel = fi.getId().equals(selectedFamilyId);
            boolean hover = isIn(mouseX, mouseY, 0, y, famW, 24);
            g.fill(2, y, famW - 2, y + 24, sel ? 0x55ffffff : (hover ? 0x33ffffff : 0x00000000));
            g.fill(4, y + 7, 8, y + 17, ColorUtil.awtColorToMinecraft(fi.getColor()));
            g.drawString(font, ComponentHelper.translatable(fi.getNameKey()).getString(), 13, y + 8, 0xffffffff, false);
            y += 26;
        }

        g.drawString(font, ComponentHelper.translatable("modular.fangsu.block_title").getString(), famW + 6, 24, 0xffffffff, false);
        int by = 44 - paletteScroll;
        if (selectedFamilyId != null) {
            for (String type : factory.getBlockTypesOf(selectedFamilyId)) {
                ModularBlock fb = factory.getDefaultBlock(type);
                if (fb == null || !isAllowed(fb)) continue;
                boolean hover = isIn(mouseX, mouseY, famW, by, blockW, 26);
                g.fill(famW + 2, by, famW + blockW - 2, by + 26, hover ? 0x33ffffff : 0x11000000);
                int w = fb.isExpression() ? Math.min(exprWidth(fb), blockW - 12) : Math.min(measureRun(fb), blockW - 12);
                int color = ColorUtil.awtColorToMinecraft(fb.getColor());
                int hgt = fb.isExpression() ? 22 : 24;
                g.fill(famW + 6, by, famW + 6 + w, by + hgt, color);
                if (fb.isExpression()) {
                    drawComponents(g, fb, famW + 10, by + (hgt - font.lineHeight) / 2, false);
                } else {
                    drawComponents(g, fb, famW + 10, by + (hgt - font.lineHeight) / 2, false);
                }
                by += 32;
            }
        }

        g.disableScissor();
    }

    /* ===================== 鼠标 ===================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editing) commitEdit();

        int famW = width / 6;
        int blockW = width / 6;
        boolean overPalette = mouseX <= famW + blockW;

        if (button == 0) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            pressX = mouseX;
            pressY = mouseY;
            dragging = false;
            clickEdit = null;

            if (mouseX <= famW && mouseY >= 44) { // 分组
                int y = 44 - paletteScroll;
                for (ModularBlockFactory.FamilyInfo fi : factory.getFamilies()) {
                    if (mouseY >= y && mouseY <= y + 24) {
                        selectedFamilyId = fi.getId();
                        return true;
                    }
                    y += 26;
                }
                return true;
            }
            if (overPalette && mouseX > famW && mouseY >= 44) { // 抓新块
                int y = 44 - paletteScroll;
                if (selectedFamilyId != null) {
                    for (String type : factory.getBlockTypesOf(selectedFamilyId)) {
                        ModularBlock dflt = factory.getDefaultBlock(type);
                        if (dflt == null || !isAllowed(dflt)) continue;
                        if (mouseY >= y && mouseY <= y + 26) {
                            ModularBlock t = factory.createBlock(type);
                            if (t != null) startGrabNew(t);
                            return true;
                        }
                        y += 32;
                    }
                }
                return true;
            }
            if (isIn((int) mouseX, (int) mouseY, canvasX, canvasY, canvasW, canvasH)) {
                int px = worldX(mouseX);
                int py = worldY(mouseY);
                // 最内层（最深）槽优先：用于抓取其中嵌套数据块
                SocketHit s = deepestSocket(px, py);
                if (s != null && s.expr != null) {
                    startGrabExpr(s);
                } else if (s != null) {
                    // 空槽点击候选
                    clickEdit = s.comp;
                } else {
                    RowHit hit = findCommand(px, py);
                    if (hit != null) startGrabCommand(hit);
                }
                return true;
            }
            return true;
        }
        if (button == 2) {
            panning = true;
            panX = mouseX;
            panY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 2) {
            if (panning) {
                scrollX -= (int) (mouseX - panX);
                scrollY -= (int) (mouseY - panY);
                panX = mouseX;
                panY = mouseY;
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (grab == null) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        double dist = Math.hypot(mouseX - pressX, mouseY - pressY);
        if (!dragging && dist > DRAG_THRESHOLD) dragging = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) {
            panning = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }
        if (button != 0) return super.mouseReleased(mouseX, mouseY, button);

        if (!dragging) {
            // 点击（未拖）：若命中某个空值槽则编辑/切换
            ModularComponent ce = clickEdit;
            clearGrab();
            if (ce != null && isIn((int) mouseX, (int) mouseY, canvasX, canvasY, canvasW, canvasH)) {
                clickEditSocket(ce);
            }
            return true;
        }

        ModularBlock moving = grab;
        boolean fromPalette = grabFromPalette;
        ModularComponent originSocket = grabSocket;
        ModularScript moveFromScript = grabScript;
        ContainerObject moveFromContainer = grabContainer;
        clearGrab();

        boolean toPalette = mouseX <= (width / 3);
        if (toPalette) {
            if (moving.isExpression()) {
                if (originSocket != null) {
                    originSocket.clearExpr();
                    dirty = true;
                }
            } else if (!fromPalette) {
                removeCommand(moving, moveFromScript, moveFromContainer);
                dirty = true;
            }
            return true;
        }
        if (!isIn((int) mouseX, (int) mouseY, canvasX, canvasY, canvasW, canvasH)) {
            if (moving.isExpression() && originSocket != null) originSocket.setExpr(moving);
            return true;
        }
        int px = worldX(mouseX);
        int py = worldY(mouseY);

        if (moving.isExpression()) {
            dropExpr(moving, originSocket, px, py);
        } else if (fromPalette) {
            placeNew(moving, px, py);
        } else {
            moveCommand(moving, moveFromScript, moveFromContainer, px, py);
        }
        return true;
    }

    /* ---------- 抓取 ---------- */

    private void startGrabNew(ModularBlock t) {
        grab = t;
        grabFromPalette = true;
        grabScript = null;
        grabContainer = null;
        grabSocket = null;
        dragging = false;
    }

    private void startGrabCommand(RowHit hit) {
        grab = hit.block;
        grabFromPalette = false;
        grabScript = hit.script;
        grabContainer = hit.parent;
        grabSocket = null;
    }

    private void startGrabExpr(SocketHit s) {
        grab = s.expr;
        grabFromPalette = false;
        grabSocket = s.comp;
        grabScript = null;
        grabContainer = null;
        // 注意：此处不从原槽移除，仅记录来源；真正移除/删除发生在松开时。
    }

    private void clearGrab() {
        grab = null;
        grabFromPalette = false;
        grabScript = null;
        grabContainer = null;
        grabSocket = null;
        dragging = false;
    }

    /* ---------- 数据块拖放（任意深度） ---------- */

    private void dropExpr(ModularBlock expr, ModularComponent originSocket, int px, int py) {
        SocketHit target = deepestSocket(px, py);
        boolean moved = target != null && target.comp != originSocket && target.comp.acceptsShape(expr.getShape());
        if (moved) {
            // 从原槽取出，放入目标槽（若目标已有内容，被覆盖丢失）
            if (originSocket != null) originSocket.clearExpr();
            target.comp.setExpr(expr);
            dirty = true;
        } else {
            // 未命中合适目标（或回到原槽）：保持原位置（origin 仍持有）
        }
    }

    /* ---------- 命令块放置/移动 ---------- */

    private DropHint computeCommandDrop(int px, int py, ModularBlock moving) {
        boolean isHat = moving.getShape() == BlockShape.HAT;
        // 1) 命中某个容器体内部 → 放“里头”。判定：竖直落在容器体 [top,bottom] 内，
        //    且水平已越过左臂(left)。右边界放宽到很大，避免被子块自身宽度顶出。
        ContainerRect targetBody = null;
        for (ContainerRect cr : containerRects) {
            if (px >= cr.left - 4 && py >= cr.top - 4 && py <= cr.bottom + 8) {
                targetBody = cr;
            }
        }
        if (targetBody != null) {
            if (isHat) return null; // HAT 不能进容器
            ContainerObject co = targetBody.host.getContainers().get(0);
            // 依据拖放 Y 确定容器内插入锚点：第一个 y>=py 的子块（插到它之前），否则追加尾部。
            // 排除正在拖动的块自身（同容器重排时）。
            ModularBlock anchor = containerDropAnchor(co, moving, py);
            return new DropHint(null, co, anchor, true,
                    targetBody.left + 4, targetBody.top + 4,
                    targetBody.left, targetBody.top,
                    Math.max(targetBody.right, targetBody.left + 400), targetBody.bottom);
        }
        // 2) 命中某命令块 → “外头”，接在其后（若是容器块，接在容器体之后/下方）
        RowHit hit = findCommand(px, py);
        if (hit != null) {
            if (isHat) return null; // HAT 只能建新脚本
            // 栈终止块（forever 等）底部闭合、其后不能再接命令块
            if (hit.block.isStackTerminator()) return null;
            return new DropHint(hit.script, hit.parent, hit.block, false, hit.x, hit.y + hit.h);
        }
        // 3) 空区：就近脚本末尾或新建（但脚本以栈终止块收尾时不应再接块）
        ModularScript best = null;
        double dmin = Double.MAX_VALUE;
        for (ModularScript s : doc.getScripts()) {
            if (s.getBlocks().isEmpty()) continue;
            ModularBlock last = s.getBlocks().get(s.getBlocks().size() - 1);
            if (last.isStackTerminator()) continue; // 栈终止块之后不能再接
            double d = Math.abs(s.getX() - px);
            if (d < dmin) {
                dmin = d;
                best = s;
            }
        }
        if (best != null && dmin < 40) {
            int indY = (int) best.getY();
            for (ModularBlock bb : best.getBlocks()) indY += BLOCK_H;
            return new DropHint(best, null, null, false, (int) best.getX(), indY);
        }
        return new DropHint(null, null, null, false, px, py);
    }

    private void placeNew(ModularBlock nb, int px, int py) {
        if (nb.isExpression()) return;
        DropHint h = computeCommandDrop(px, py, nb);
        if (h == null) return;
        if (nb.getShape() == BlockShape.HAT && !h.intoContainer) {
            // HAT：只能新建脚本（不进容器，不接其它块后）
            ModularScript s = new ModularScript(px, py);
            s.add(nb);
            doc.addScript(s);
            dirty = true;
            return;
        }
        if (h.intoContainer) {
            insertIntoContainer(h.container, h.after, nb);
        } else if (h.script == null) {
            ModularScript s = new ModularScript(h.indX, h.indY);
            s.add(nb);
            doc.addScript(s);
        } else {
            List<ModularBlock> list = listOf(h.script, h.container);
            int idx = (h.after != null ? list.indexOf(h.after) + 1 : list.size());
            list.add(Math.max(0, idx), nb);
        }
        dirty = true;
    }

    private void moveCommand(ModularBlock b, ModularScript fromScript, ContainerObject fromContainer,
                             int px, int py) {
        DropHint h = computeCommandDrop(px, py, b);
        if (h != null && h.after == b) return;
        removeCommand(b, fromScript, fromContainer);
        if (h == null) {
            ModularScript s = new ModularScript(px, py);
            s.add(b);
            doc.addScript(s);
        } else if (h.intoContainer) {
            insertIntoContainer(h.container, h.after, b);
        } else if (h.script == null) {
            ModularScript s = new ModularScript(h.indX, h.indY);
            s.add(b);
            doc.addScript(s);
        } else {
            List<ModularBlock> list = listOf(h.script, h.container);
            int idx = (h.after != null ? list.indexOf(h.after) + 1 : list.size());
            list.add(Math.max(0, idx), b);
        }
        dirty = true;
    }

    /** 往容器内插入：锚点(anchor)非空时插到它之前，否则追加尾部。锚点若正好是被插块自身则追加。 */
    private void insertIntoContainer(ContainerObject co, ModularBlock anchor, ModularBlock block) {
        List<ModularBlock> list = co.mutableList();
        int idx = list.size();
        if (anchor != null) {
            int ai = list.indexOf(anchor);
            if (ai >= 0) idx = ai;
        }
        list.add(Math.max(0, Math.min(idx, list.size())), block);
    }

    private void removeCommand(ModularBlock b, ModularScript script, ContainerObject container) {
        if (container != null) container.mutableList().remove(b);
        else if (script != null) script.remove(b);
    }

    private List<ModularBlock> listOf(ModularScript script, ContainerObject container) {
        return container != null ? container.mutableList() : script.mutableBlocks();
    }

    /* ---------- 命中 ---------- */

    /**
     * 计算容器内该把被拖块插入到哪个子块之前。
     * 遍历本容器（parent==co）的子块 RowHit，找到第一个 y &gt;= py 的子块作为锚点（插到它前面）；
     * 全都不满足（拖到最底）返回 null = 追加到容器末尾。排除被拖块自身，避免同容器重排时自我锚定。
     */
    private ModularBlock containerDropAnchor(ContainerObject co, ModularBlock moving, int py) {
        ModularBlock anchor = null;
        for (RowHit r : rows) {
            if (r.parent != co) continue;
            if (r.block == moving) continue;
            if (r.y >= py) {
                anchor = r.block;
                break;
            }
        }
        return anchor;
    }

    private RowHit findCommand(int px, int py) {
        RowHit best = null;
        for (RowHit r : rows) {
            if (px >= r.x && px <= r.x + r.w && py >= r.y - 2 && py <= r.y + r.h + 2) best = r;
        }
        return best;
    }

    /** 最内层（最深）槽：面积最小者。若多个同面积取最后。 */
    private SocketHit deepestSocket(int px, int py) {
        SocketHit best = null;
        long bestArea = Long.MAX_VALUE;
        for (SocketHit s : sockets) {
            if (px >= s.x && px <= s.x + s.w && py >= s.y - 1 && py <= s.y + s.h + 1) {
                long area = (long) s.w * s.h;
                if (area < bestArea) {
                    bestArea = area;
                    best = s;
                }
            }
        }
        return best;
    }

    private SocketHit findEmptyAcceptingSocket(int px, int py, ModularBlock expr) {
        SocketHit deep = deepestSocket(px, py);
        if (deep != null && deep.comp.acceptsShape(expr.getShape())) return deep;
        // 回退：找同区域里兼容的空槽
        SocketHit fallback = null;
        for (SocketHit s : sockets) {
            if (px >= s.x && px <= s.x + s.w && py >= s.y - 1 && py <= s.y + s.h + 1
                    && s.comp.acceptsShape(expr.getShape()) && s.expr == null) {
                fallback = s;
            }
        }
        return fallback;
    }

    /* ---------- 点击值槽编辑 ---------- */

    private void clickEditSocket(ModularComponent c) {
        if (c.isLabel()) return;
        if (c.hasExpr()) return;
        switch (c.getKind()) {
            case BOOLEAN:
                c.setBoolean(!c.asBoolean());
                dirty = true;
                break;
            case CHOICE:
                cycleChoice(c);
                dirty = true;
                break;
            case NUMBER:
            case TEXT:
                beginInlineEdit(c);
                break;
            default:
                break;
        }
    }

    private void beginInlineEdit(ModularComponent c) {
        editing = true;
        editComp = c;
        editBuf = new StringBuilder(c.getKind() == ModularComponent.Kind.NUMBER
                ? String.valueOf(c.asNumber()) : c.asString());
        // 编辑框先放画布左上，提交后自动更新显示（简化定位）
        editX = canvasX + 8;
        editY = canvasY + 6;
        editW = 90;
    }

    /* ===================== 键盘 / 内联编辑 ===================== */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing) {
            if (keyCode == 256) {
                editing = false;
                editComp = null;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                commitEdit();
                return true;
            }
            if (keyCode == 259 && editBuf.length() > 0) {
                editBuf.deleteCharAt(editBuf.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editing) {
            if (!Character.isISOControl(codePoint)) editBuf.append(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void drawEditOverlay(GraphicContext g) {
        if (!editing || editComp == null) return;
        g.fill(editX, editY, editX + editW, editY + SOCKET_H + 2, 0xff000000);
        g.fill(editX + 1, editY + 1, editX + editW - 1, editY + SOCKET_H + 1, 0xffffffff);
        g.drawString(font, editBuf.toString(), editX + 3, editY + 2, 0xff222222, false);
    }

    private void commitEdit() {
        if (editing && editComp != null) {
            String t = editBuf.toString();
            if (editComp.getKind() == ModularComponent.Kind.NUMBER) {
                try {
                    editComp.setNumber(Double.parseDouble(t));
                    dirty = true;
                } catch (NumberFormatException ignored) {
                }
            } else if (editComp.getKind() == ModularComponent.Kind.TEXT) {
                editComp.setString(t);
                dirty = true;
            }
        }
        editing = false;
        editComp = null;
    }

    private void cycleChoice(ModularComponent c) {
        List<String> items = c.getItems();
        if (items.isEmpty()) return;
        int idx = items.indexOf(c.asString());
        c.setString(items.get((idx + 1) % items.size()));
    }

    /* ===================== 滚轮 ===================== */

    //#if MC_VERSION < 12003
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    //#else
    //$$ @Override
    //$$ public boolean mouseScrolled(double mouseX, double mouseY, double delta, double horizontalAmount) {
    //#endif
        if (isIn((int) mouseX, (int) mouseY, canvasX, canvasY, canvasW, canvasH)) {
            scrollY -= (int) (delta * 14);
            if (scrollY < 0) scrollY = 0;
            return true;
        }
        // 左侧面板滚动
        if (mouseX <= width / 3 && mouseY >= 20) {
            paletteScroll -= (int) (delta * 14);
            int maxScroll = paletteMaxScroll();
            if (paletteScroll < 0) paletteScroll = 0;
            if (paletteScroll > maxScroll) paletteScroll = maxScroll;
            return true;
        }
        //#if MC_VERSION < 12003
        return super.mouseScrolled(mouseX, mouseY, delta);
        //#else
        //$$ return super.mouseScrolled(mouseX, mouseY, delta, horizontalAmount);
        //#endif
    }

    /* ===================== 保存 ===================== */

    public void saveAndClose() {
        try {
            String json = ModularCodec.documentToJson(doc);
            setter.accept(json);
            com.fangsu.Main.LOGGER.info("[ModularEditing] saved program ({}) length {}", json.length(), json.length());
        } catch (Exception e) {
            com.fangsu.Main.LOGGER.error("[ModularEditing] save failed", e);
        }
        // 返回打开前的屏幕（配置屏），让其 onClose 把配置写入并同步到服务端
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** 某个积木 type/块是否允许显示（白名单过滤）。 */
    private boolean isAllowed(ModularBlock block) {
        if (allowedTypes == null || allowedTypes.isEmpty()) return true;
        return block != null && allowedTypes.contains(block.getType());
    }

    /* ===================== 内部类型 ===================== */

    /** C 形容器体矩形（世界坐标），用于“放里头”的命中/高亮。 */
    private static final class ContainerRect {
        final ModularBlock host;
        final int hostX;    // 宿主命令块左缘（头块）
        final int left;     // 内部区左（越过左臂后）
        final int top;
        final int right;
        final int bottom;

        ContainerRect(ModularBlock host, int hostX, int left, int top, int right, int bottom) {
            this.host = host;
            this.hostX = hostX;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static final class RowHit {
        final ModularScript script;
        final ContainerObject parent;
        final int index;
        final ModularBlock block;
        final int x, y, w, h;

        RowHit(ModularScript script, ContainerObject parent, int index, ModularBlock block,
               int x, int y, int w, int h) {
            this.script = script;
            this.parent = parent;
            this.index = index;
            this.block = block;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class SocketHit {
        final ModularBlock owner;         // 槽所在块（命令或表达式）
        final ModularComponent comp;      // 值槽
        final ModularBlock expr;          // 槽内数据块（空槽为 null）
        final int x, y, w, h;

        SocketHit(ModularBlock owner, ModularComponent comp, ModularBlock expr, int x, int y, int w, int h) {
            this.owner = owner;
            this.comp = comp;
            this.expr = expr;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class DropHint {
        final ModularScript script;
        final ContainerObject container;
        final ModularBlock after;
        final boolean intoContainer;
        final int indX, indY;
        // 容器内部高亮几何（仅 intoContainer 时使用）
        final int containerBodyLeft;
        final int containerHeadBottom;
        final int containerBodyRight;
        final int containerBodyBottom;

        DropHint(ModularScript script, ContainerObject container, ModularBlock after,
                 boolean intoContainer, int indX, int indY) {
            this(script, container, after, intoContainer, indX, indY, 0, 0, 0, 0);
        }

        DropHint(ModularScript script, ContainerObject container, ModularBlock after,
                 boolean intoContainer, int indX, int indY,
                 int containerBodyLeft, int containerHeadBottom, int containerBodyRight, int containerBodyBottom) {
            this.script = script;
            this.container = container;
            this.after = after;
            this.intoContainer = intoContainer;
            this.indX = indX;
            this.indY = indY;
            this.containerBodyLeft = containerBodyLeft;
            this.containerHeadBottom = containerHeadBottom;
            this.containerBodyRight = containerBodyRight;
            this.containerBodyBottom = containerBodyBottom;
        }
    }
}
