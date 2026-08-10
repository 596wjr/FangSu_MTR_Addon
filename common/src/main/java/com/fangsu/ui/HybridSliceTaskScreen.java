package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridSliceTask;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
//#if MC_VERSION >= 11903
import com.mojang.math.Axis;
//#endif
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
//#if MC_VERSION >= 11903
import net.minecraft.core.registries.BuiltInRegistries;
//#endif
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 切片任务画布屏幕（照 ANTE SliceTaskScreen 移植）：
 * <ul>
 *   <li>中央画布：宽（列）= 垂直于轨道（横截面），高（行）= 向上；左键放置当前方块、右键编辑属性、中键取色</li>
 *   <li>右侧方块选择器：全方块列表 + 本地化名搜索（ANTE 的拼音搜索省略）</li>
 *   <li>尺寸 ±/平移/居中/参数配置（start/step/interval）</li>
 * </ul>
 * 任何修改通过 {@link HybridCreatorScreen#updateTag} 写回物品 NBT 并同步服务端。
 */
public class HybridSliceTaskScreen extends Screen {

    private final HybridSliceTask task;
    /** 任务在 tasksTag 中的键 */
    private final String key;
    private final Screen parent;
    protected int tx = 0;
    protected int ty = 0;

    // 按钮创建统一走 ComponentHelper.button（内部处理 builder/构造器版本差异），位置由 placeButton 设置
    private final Button btnReturn = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("X"), button -> onClose());
    private final Button btnEnterConfig = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.title"), button -> setConfigScreen());
    /** 懒创建：Screen.minecraft 在 init() 后才非 null，字段初始化器里用会 NPE */
    private EditBox nameField;
    private final Button btnAddWidth;
    private final Button btnSubWidth;
    private final Button btnAddHeight;
    private final Button btnSubHeight;
    private final Button btnSubTX = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("◁"), button -> setTX(tx + 4 * Square.LENGTH));
    private final Button btnAddTX = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("▷"), button -> setTX(tx - 4 * Square.LENGTH));
    private final Button btnSubTY = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("▲"), button -> setTY(ty + 4 * Square.LENGTH));
    private final Button btnAddTY = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("▼"), button -> setTY(ty - 4 * Square.LENGTH));
    private final Button btnCenter = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("▣"), button -> { tx = 0; ty = 0; });

    private final List<Square> canvas = new ArrayList<>();
    private final Inventory inventory = new Inventory();
    private Square mouseOver = null;
    private final Square now = new Square(0, 0, null, square -> {}, square -> true, square -> true, true);

    private int scissorX, scissorY, scissorW, scissorH;

    public HybridSliceTaskScreen(HybridSliceTask task, String key, Screen parent) {
        super(ComponentHelper.literal(""));
        this.task = task;
        this.key = key;
        this.parent = parent;
        // 按钮 lambda 引用 task 字段，必须在 task 赋值后初始化（字段初始化器顺序在前会报「可能尚未初始化」）
        btnAddWidth = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("+"), button -> setWidthAndHeight(task.width + 2, task.height));
        btnSubWidth = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("-"), button -> setWidthAndHeight(task.width - 2, task.height));
        btnAddHeight = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("+"), button -> setWidthAndHeight(task.width, task.height + 2));
        btnSubHeight = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("-"), button -> setWidthAndHeight(task.width, task.height - 2));
        reload();
    }

    /* ===================== 数据 ===================== */

    private void setWidthAndHeight(int width, int height) {
        if (!task.setWidthAndHeight(width, height)) return;
        // 顺序重要：先 reload() 让画布从「搬运后的新尺寸 lumps」重建，
        // 再 updateTask() 写回（无损）并同步 NBT；若先 updateTask 会用旧尺寸画布
        // 按索引覆盖新 lumps，导致已有布局混乱
        reload();
        updateTask();
    }

    /** 画布写回任务 lumps 并同步物品 NBT */
    private void updateTask() {
        for (int i = 0; i < canvas.size() && i < task.lumps.size(); i++) {
            final Square sq = canvas.get(i);
            task.lumps.get(i).blockState = sq.state;
            task.lumps.get(i).replacement = sq.replacement;
        }
        HybridCreatorScreen.updateTag(tag -> {
            if (tag.contains(HybridCreatorScreen.TAG_TASKS)) {
                tag.getCompound(HybridCreatorScreen.TAG_TASKS).put(key, task.toCompoundTag());
            }
        });
    }

    private void reload() {
        canvas.clear();
        for (int i = 0; i < task.lumps.size(); i++) {
            final HybridSliceTask.HybridCreatorLump lump = task.lumps.get(i);
            canvas.add(new Square(0, 0, lump.blockState, square -> {
                square.state = now.state;
                square.replacement = now.replacement;
                updateTask();
            }, square -> true, square -> isInScissor(square), lump.replacement));
        }
    }

    private boolean isInScissor(Square sq) {
        final int l = Square.LENGTH;
        return sq.x + l >= scissorX && sq.x <= scissorX + scissorW && sq.y + l >= scissorY && sq.y <= scissorY + scissorH;
    }

    /** 属性屏保存回调：画布格 → 写回 NBT；方块选择器格 → 同步到当前方块 */
    public void onPropertySaved(Square square) {
        if (canvas.contains(square)) {
            updateTask();
        } else {
            now.state = square.state;
            now.replacement = square.replacement;
        }
    }

    /* ===================== 渲染 ===================== */

    @Override
    protected void init() {
        // minecraft 在 init(Minecraft, int, int) 里才被赋值，所有依赖字体的 widget 必须在这里创建
        nameField = new EditBox(minecraft.font, 0, 0, 0, 16, ComponentHelper.literal(""));
        nameField.setValue(task.name);
        //#if MC_VERSION >= 12003
        //$$ nameField.moveCursorToStart(true);
        //#else
        nameField.moveCursorToStart();
        //#endif
        nameField.setResponder(str -> {
            task.name = str;
            updateTask();
        });
        inventory.initSearchField();
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderImpl(GraphicContext.of(graphics), mouseX, mouseY, partialTick);
    }
    //#else
    //$$ @Override
    //$$ public void render(@NotNull com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     renderImpl(GraphicContext.of(poseStack), mouseX, mouseY, partialTick);
    //$$ }
    //#endif

    /** 渲染主体：graphics 预处理后为 GuiGraphics（1.20+）/ PoseStack（旧版），随版本分支 */
    private void renderImpl(GraphicContext g, int mouseX, int mouseY, float partialTick) {
        //#if MC_VERSION >= 12000
        final GuiGraphics graphics = g.asMinecraft();
        //#if MC_VERSION < 12003
        renderBackground(graphics);
        //#else
        //$$ renderBackground(graphics, mouseX, mouseY, partialTick);
        //#endif
        //#else
        //$$ final com.mojang.blaze3d.vertex.PoseStack graphics = g.asMinecraft();
        //$$ renderBackground(graphics);
        //#endif
        super.render(graphics, mouseX, mouseY, partialTick);
        setTY(ty);
        setTX(tx);
        mouseOver = null;
        updateWidgetPosition();
        // 按钮手动渲染（不依赖 addRenderableWidget；此前遗漏 render 导致全部按钮不可见）
        btnReturn.render(graphics, mouseX, mouseY, partialTick);
        btnEnterConfig.render(graphics, mouseX, mouseY, partialTick);
        btnAddWidth.render(graphics, mouseX, mouseY, partialTick);
        btnSubWidth.render(graphics, mouseX, mouseY, partialTick);
        btnAddHeight.render(graphics, mouseX, mouseY, partialTick);
        btnSubHeight.render(graphics, mouseX, mouseY, partialTick);
        btnSubTX.render(graphics, mouseX, mouseY, partialTick);
        btnAddTX.render(graphics, mouseX, mouseY, partialTick);
        btnSubTY.render(graphics, mouseX, mouseY, partialTick);
        btnAddTY.render(graphics, mouseX, mouseY, partialTick);
        btnCenter.render(graphics, mouseX, mouseY, partialTick);

        final int fullX = 40;
        final int fullY = 40;
        final int fullW = width - 40 - 10 - Inventory.WIDTH;
        final int fullH = height - 40 - 40;
        scissorX = 62;
        scissorY = 52;
        scissorW = width - 62 - 10 - Inventory.WIDTH;
        scissorH = height - 52 - 40;

        final float midX = tx + scissorX + scissorW / 2.0F;
        final float midY = ty + scissorY + scissorH / 2.0F;
        final int x = (int) (midX - task.width / 2.0F * Square.LENGTH);
        final int y = (int) (midY - task.height / 2.0F * Square.LENGTH);

        // 画布外框与内部
        g.fill(fullX - 1, fullY - 1, fullX + fullW + 1, fullY + fullH + 1, contains(fullX, fullY, fullW, fullH, mouseX, mouseY) ? 0xfff0eacc : 0xff0c0d0b);
        g.fill(fullX, fullY, fullX + fullW, fullY + fullH, 0xff424242);
        g.fill(scissorX - 1, scissorY - 1, scissorX + scissorW + 1, scissorY + scissorH + 1, contains(scissorX, scissorY, scissorW, scissorH, mouseX, mouseY) ? 0xffd1b2b2 : 0xff403636);
        g.fill(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH, 0xff8f5d5d);

        // 顶部 X 轴刻度
        g.enableScissor(scissorX, fullY, scissorX + scissorW, fullY + 12);
        final int a = task.width / 2;
        final int in = 3;
        final int ay = 42;
        g.drawCenteredString(minecraft.font, "0", (int) midX, ay, 0xFFFFFFFF);
        if (a >= in) {
            for (int i = in; i <= a; i += in) {
                g.drawCenteredString(minecraft.font, "+" + i, (int) midX + i * Square.LENGTH, ay, 0xFFFFFFFF);
                g.drawCenteredString(minecraft.font, "-" + i, (int) midX - i * Square.LENGTH, ay, 0xFFFFFFFF);
            }
        } else if (a >= 1) {
            g.drawCenteredString(minecraft.font, "+" + a, (int) midX + a * Square.LENGTH, ay, 0xFFFFFFFF);
            g.drawCenteredString(minecraft.font, "-" + a, (int) midX - a * Square.LENGTH, ay, 0xFFFFFFFF);
        }
        g.disableScissor();

        // 左侧 Y 轴刻度（向上为正）
        g.enableScissor(fullX, scissorY, fullX + 22, scissorY + scissorH);
        final int b = task.height / 2;
        final int ax = 50;
        g.drawCenteredString(minecraft.font, "0", ax, (int) midY - 5, 0xFFFFFFFF);
        if (b >= in) {
            for (int i = in; i <= b; i += in) {
                g.drawCenteredString(minecraft.font, "-" + i, ax, (int) midY - 5 + i * Square.LENGTH, 0xFFFFFFFF);
                g.drawCenteredString(minecraft.font, "+" + i, ax, (int) midY - 5 - i * Square.LENGTH, 0xFFFFFFFF);
            }
        } else if (b >= 1) {
            g.drawCenteredString(minecraft.font, "+" + b, ax, (int) midY - 5 - b * Square.LENGTH, 0xFFFFFFFF);
            g.drawCenteredString(minecraft.font, "-" + b, ax, (int) midY - 5 + b * Square.LENGTH, 0xFFFFFFFF);
        }
        g.disableScissor();

        // 格子
        g.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);
        for (int i = 0; i < canvas.size(); i++) {
            canvas.get(i).render(graphics, mouseX, mouseY, x + i % task.width * Square.LENGTH, y + i / task.width * Square.LENGTH, partialTick);
        }
        // 中心行/列标记（轨道线位置）
        final int py = (int) midY - 18 / 2 - 1;
        g.fill(x, py, x + task.width * Square.LENGTH, py + 2, 0x7FFF0000);
        g.fill(x, py + 18, x + task.width * Square.LENGTH, py + 20, 0x7FFF0000);
        final int px = (int) midX - 18 / 2 - 1;
        g.fill(px, y, px + 2, y + task.height * Square.LENGTH, 0x7F00FF00);
        g.fill(px + 18, y, px + 20, y + task.height * Square.LENGTH, 0x7F00FF00);
        g.disableScissor();

        // 当前方块与选择器
        now.render(graphics, mouseX, mouseY, 191, 11, partialTick);
        inventory.render(graphics, mouseX, mouseY, partialTick);

        if (mouseOver != null) {
            mouseOver.renderTooltip(graphics, mouseX, mouseY);
        }

        // ── 右上角小罗盘（5 行竖排、0.75 缩放字号，顶部条内不重叠画布主体）──
        // 左=西(-X) 右=东(+X) 向里=北(-Z)；W<->E 为左右方向箭头；
        // ×（叉）= 垂直屏幕向内、·（点）= 垂直屏幕向外。内容整体居中对齐。
        // N 绿色、× 橙、· 蓝
        final float scale = 0.75F;
        final int refX = width - 170;
        final int refY = 4;
        g.fill(refX - 3, refY - 3, refX + 26, refY + 30, 0x90000000);
        //#if MC_VERSION >= 12000
        final PoseStack pose = graphics.pose();
        //#else
        //$$ final PoseStack pose = graphics;
        //#endif
        pose.pushPose();
        pose.translate(refX, refY, 0);
        pose.scale(scale, scale, 1);
        // 内容中心在缩放系 x=15（"W<->E" 宽 30 居中），行高 9
        g.drawCenteredString(minecraft.font, "N", 15, 0, 0xFF00FF00);
        g.drawCenteredString(minecraft.font, "×", 15, 9, 0xFFFFC500);
        g.drawCenteredString(minecraft.font, "W", 3, 18, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, "<", 9, 18, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, "-", 15, 18, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, ">", 21, 18, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, "E", 27, 18, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, "·", 15, 27, 0xFF9FBFFF);
        g.drawCenteredString(minecraft.font, "S", 15, 36, 0xFFFFFFFF);
        pose.popPose();
    }

    private static boolean contains(int x, int y, int w, int h, double mouseX, double mouseY) {
        return x <= mouseX && mouseX <= x + w && y <= mouseY && mouseY <= y + h;
    }

    private void updateWidgetPosition() {
        placeButton(btnReturn, 10, 10, 20);
        //#if MC_VERSION >= 11903
        nameField.setPosition(40, 10);
        //#else
        //$$ nameField.x = 40; nameField.y = 10; // 1.19.2 及以下 AbstractWidget 无 setPosition，x/y 为 public 字段
        //#endif
        nameField.setWidth(90);
        placeButton(btnEnterConfig, 140, 10, 40);

        placeButton(btnAddWidth, 40, height - 30, 20);
        placeButton(btnSubWidth, 70, height - 30, 20);
        placeButton(btnSubTX, 100, height - 30, 20);
        placeButton(btnAddTX, 130, height - 30, 20);

        placeButton(btnAddHeight, 10, 40, 20);
        placeButton(btnSubHeight, 10, 70, 20);
        placeButton(btnSubTY, 10, 100, 20);
        placeButton(btnAddTY, 10, 130, 20);

        placeButton(btnCenter, 10, height - 30, 20);
    }

    private static void placeButton(Button button, int x, int y, int width) {
        //#if MC_VERSION >= 11903
        button.setPosition(x, y);
        //#else
        //$$ button.x = x; button.y = y; // 1.19.2 及以下 AbstractWidget 无 setPosition，x/y 为 public 字段
        //#endif
        button.setWidth(width);
    }

    private void setTX(int tx) {
        final int w = task.width * Square.LENGTH;
        if (w <= scissorW) {
            this.tx = 0;
            return;
        }
        final int full = w - scissorW;
        this.tx = Math.max(-full / 2, Math.min(full / 2, tx));
    }

    private void setTY(int ty) {
        final int h = task.height * Square.LENGTH;
        if (h <= scissorH) {
            this.ty = 0;
            return;
        }
        final int full = h - scissorH;
        this.ty = Math.max(-full / 2, Math.min(full / 2, ty));
    }

    private void setConfigScreen() {
        minecraft.setScreen(new HybridSliceConfigScreen(task, key, this));
    }

    /* ===================== 中键拖动平移画布 ===================== */

    private double pressX = 0;
    private double pressY = 0;
    private boolean middleDown = false;

    /**
     * 中键：按下只记录位置（吃掉事件避免方块立刻取色）；
     * 拖动 → 平移画布（tx/ty 跟随鼠标）；松开时若无位移（< 5px）→ 视为点击，对按住的方块取色。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2) {
            pressX = mouseX;
            pressY = mouseY;
            middleDown = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double sx, double sy, int button, double dx, double dy) {
        if (button == 2 && middleDown) {
            setTX(tx + (int) dx);
            setTY(ty + (int) dy);
            return true;
        }
        return super.mouseDragged(sx, sy, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2 && middleDown) {
            middleDown = false;
            // 无拖动的点击：取色（优先画布格子，其次方块选择器格子）
            if (Math.abs(mouseX - pressX) < 5 && Math.abs(mouseY - pressY) < 5) {
                Square picked = null;
                for (Square square : canvas) {
                    if (square.isMouseOver(pressX, pressY) && square.state != null) {
                        picked = square;
                        break;
                    }
                }
                if (picked == null) {
                    picked = inventory.pick(pressX, pressY);
                }
                if (picked != null) {
                    now.state = picked.state;
                    now.replacement = picked.replacement;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /* ===================== 输入分发 ===================== */

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        final List<GuiEventListener> result = new ArrayList<>();
        result.addAll(super.children());
        result.addAll(canvas);
        result.addAll(inventory.children());
        result.add(nameField);
        result.add(btnReturn);
        result.add(btnEnterConfig);
        result.add(btnAddWidth);
        result.add(btnSubWidth);
        result.add(btnAddHeight);
        result.add(btnSubHeight);
        result.add(btnSubTX);
        result.add(btnAddTX);
        result.add(btnSubTY);
        result.add(btnAddTY);
        result.add(btnCenter);
        result.add(now);
        return result;
    }

    @Override
    public void onClose() {
        updateTask();
        minecraft.setScreen(parent);
    }

    /* ===================== 方块渲染 ===================== */

    //#if MC_VERSION >= 12000
    private void renderBlockState(GuiGraphics matrices, int x, int y, float partialTick, BlockState state) {
        final BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        final PoseStack poseStack = matrices.pose();
        poseStack.pushPose();
        poseStack.translate(x, y + 16, 0);
        poseStack.scale(15.5F, -15.5F, 15.5F);
        poseStack.mulPose(Axis.XP.rotation((float) (3 * Math.PI / 180)));

        final MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        blockRenderer.renderSingleBlock(state, poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        buffer.endBatch();
        poseStack.popPose();
    }
    //#else
    //$$ private void renderBlockState(com.mojang.blaze3d.vertex.PoseStack poseStack, int x, int y, float partialTick, BlockState state) {
    //$$     final BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
    //$$     poseStack.pushPose();
    //$$     poseStack.translate(x, y + 16, 0);
    //$$     poseStack.scale(15.5F, -15.5F, 15.5F);
    //$$     // 1.19.3 起用 com.mojang.math.Axis（Vector3f 在 1.19.4 已移除），旧版用 Vector3f；
    //$$     // 注意：//$$ 块内嵌套 #if 指令行不能带 //$$ 前缀，否则指令不生效（两分支都会输出）
    //#if MC_VERSION >= 11903
    //$$     poseStack.mulPose(Axis.XP.rotationDegrees(3));
    //#else
    //$$     poseStack.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(3));
    //#endif
    //$$
    //$$     final MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
    //$$     RenderSystem.enableDepthTest();
    //$$     RenderSystem.setShader(GameRenderer::getPositionTexShader);
    //$$     RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
    //$$
    //$$     blockRenderer.renderSingleBlock(state, poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    //$$
    //$$     buffer.endBatch();
    //$$     poseStack.popPose();
    //$$ }
    //#endif

    /* ===================== 画布格子 ===================== */

    public class Square implements GuiEventListener {
        public static final int LENGTH = 18;

        public static final ResourceLocation PURPLE_CIRCLE = new ResourceLocation("fangsu", "textures/gui/hybrid_creator/purple_circle.png");
        public static final ResourceLocation BLUE_CIRCLE = new ResourceLocation("fangsu", "textures/gui/hybrid_creator/blue_circle.png");

        public int x;
        public int y;
        public BlockState state;
        public boolean replacement;
        private final Consumer<Square> consumer;
        private final Function<Square, Boolean> highlight;
        private final Function<Square, Boolean> visible;

        public Square(int x, int y, BlockState state, Consumer<Square> consumer, Function<Square, Boolean> highlight, Function<Square, Boolean> visible, boolean replacement) {
            this.x = x;
            this.y = y;
            this.state = state;
            this.consumer = consumer;
            if (highlight != null) this.highlight = highlight;
            else this.highlight = square -> false;
            if (visible != null) this.visible = visible;
            else this.visible = square -> true;
            this.replacement = replacement;
        }

        //#if MC_VERSION >= 12000
        public void render(GuiGraphics matrices, int mouseX, int mouseY, int tx, int ty, float partialTick) {
            renderImpl(GraphicContext.of(matrices), mouseX, mouseY, tx, ty, partialTick);
        }
        //#else
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, int tx, int ty, float partialTick) {
        //$$     renderImpl(GraphicContext.of(poseStack), mouseX, mouseY, tx, ty, partialTick);
        //$$ }
        //#endif

        private void renderImpl(GraphicContext g, int mouseX, int mouseY, int tx, int ty, float partialTick) {
            //#if MC_VERSION >= 12000
            final GuiGraphics matrices = g.asMinecraft();
            //#else
            //$$ final com.mojang.blaze3d.vertex.PoseStack matrices = g.asMinecraft();
            //#endif
            x = tx;
            y = ty;
            if (!isVisible()) return;
            g.fill(x, y, x + LENGTH, y + LENGTH, isMouseOver(mouseX, mouseY) ? 0xfffafff2 : 0xff9b9e96);
            g.fill(x + 1, y + 1, x + LENGTH - 1, y + LENGTH - 1, 0xff919191);
            if (state != null) {
                renderBlockState(matrices, x + 1, y + 1, partialTick, state);
                g.fill(x + 1, y + 1, x + LENGTH - 1, y + LENGTH - 1, highlight.apply(this) ? 0x2ff5f5f5 : 0x1fdda9df);
                g.blit(replacement ? PURPLE_CIRCLE : BLUE_CIRCLE, x + 1, y + 1, 16, 16, 0, 0, 1, 1, 1, 1);
            } else if (x >= width - Inventory.WIDTH && highlight.apply(this)) {
                // 选择器「空」项：选中时画圆环标记（画布空格被 scissor 裁剪不会进入此区域）
                g.blit(PURPLE_CIRCLE, x + 1, y + 1, 16, 16, 0, 0, 1, 1, 1, 1);
            }
            if (isMouseOver(mouseX, mouseY)) {
                mouseOver = this;
            }
        }

        //#if MC_VERSION >= 12000
        public void renderTooltip(GuiGraphics matrices, int mouseX, int mouseY) {
            // 1.20.1 的 renderTooltip 只收 List<FormattedCharSequence>，逐条转
            final List<FormattedCharSequence> lines = new ArrayList<>();
            if (state != null) {
                lines.add(ComponentHelper.literal("Replacement: " + replacement).getVisualOrderText());
                lines.add(state.getBlock().getName().getVisualOrderText());
                for (var property : state.getBlock().getStateDefinition().getProperties()) {
                    lines.add(ComponentHelper.literal(property.getName() + ": " + state.getValue(property)).getVisualOrderText());
                }
            } else {
                lines.add(ComponentHelper.translatable("ui.fangsu.hybrid_creator.empty").getVisualOrderText());
            }
            matrices.renderTooltip(minecraft.font, lines, mouseX, mouseY);
        }
        //#else
        //$$ public void renderTooltip(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY) {
        //$$     // 旧版渲染接口只收 List<FormattedCharSequence>，逐条转
        //$$     final List<FormattedCharSequence> lines = new ArrayList<>();
        //$$     if (state != null) {
        //$$         lines.add(ComponentHelper.literal("Replacement: " + replacement).getVisualOrderText());
        //$$         lines.add(state.getBlock().getName().getVisualOrderText());
        //$$         for (var property : state.getBlock().getStateDefinition().getProperties()) {
        //$$             lines.add(ComponentHelper.literal(property.getName() + ": " + state.getValue(property)).getVisualOrderText());
        //$$         }
        //$$     } else {
        //$$         lines.add(ComponentHelper.translatable("ui.fangsu.hybrid_creator.empty").getVisualOrderText());
        //$$     }
        //$$     // 内部类不继承外层 Screen 方法，且自身同名方法会遮蔽外层；
        //$$     // 必须显式引用外层 HybridSliceTaskScreen.this.renderTooltip(PoseStack, List, int, int)
        //$$     HybridSliceTaskScreen.this.renderTooltip(poseStack, lines, mouseX, mouseY);
        //$$ }
        //#endif

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return isVisible() && x <= mouseX && mouseX <= x + LENGTH && y <= mouseY && mouseY <= y + LENGTH;
        }

        private boolean isVisible() {
            return x >= -LENGTH && x <= width && y >= -LENGTH && y <= height && visible.apply(this);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            if (button == 0) {
                consumer.accept(this);
            } else if (button == 1) {
                if (state != null) {
                    minecraft.setScreen(new HybridCreatorPropertyScreen(HybridSliceTaskScreen.this, this));
                }
            } else if (button == 2) {
                now.state = this.state;
                now.replacement = this.replacement;
            }
            return true;
        }

        //#if MC_VERSION >= 11903
        @Override
        //#endif
        public boolean isFocused() {
            return false;
        }

        //#if MC_VERSION >= 11903
        @Override
        //#endif
        public void setFocused(boolean focused) {
        }
    }

    /* ===================== 方块选择器 ===================== */

    public class Inventory implements GuiEventListener {
        public static final int WIDTH = 100;
        public static final int COL = 5;

        private int scroll = 0;
        private boolean draggingSlider = false;
        private final List<Square> blocksList = new ArrayList<>();
        private final List<Square> searchedList = new ArrayList<>();
        /** 懒创建（Screen.minecraft 在 init() 后才非 null），由外层 Screen.init() 调 {@link #initSearchField()} */
        private EditBox searchField;

        public Inventory() {
            // 「空」作为第一个可选方块：选中后画布放置即清空该格（blockState=null）
            blocksList.add(new Square(0, 0, null, square -> {
                now.state = square.state;
                now.replacement = square.replacement;
            }, square -> now.state == null, square -> true, true));
            //#if MC_VERSION >= 11903
            for (Block block : BuiltInRegistries.BLOCK) {
            //#else
            //$$ for (Block block : net.minecraft.core.Registry.BLOCK) {
            //#endif
                blocksList.add(new Square(0, 0, block.defaultBlockState(), square -> {
                    now.state = square.state;
                    now.replacement = square.replacement;
                }, square -> square.state == now.state, square -> true, true));
            }
            searchedList.addAll(blocksList);
        }

        /** 中键取色用：返回鼠标位置处的方块格子（含「空」项），无则 null */
        public Square pick(double mouseX, double mouseY) {
            for (Square square : searchedList) {
                if (square.isMouseOver(mouseX, mouseY)) return square;
            }
            return null;
        }

        public void initSearchField() {
            searchField = new EditBox(minecraft.font, 0, 1, WIDTH - 3, 15, ComponentHelper.literal(""));
            //#if MC_VERSION >= 12003
            //$$ searchField.moveCursorToStart(true);
            //#else
            searchField.moveCursorToStart();
            //#endif
            searchField.setResponder(this::search);
        }

        public void search(String str) {
            if (str.isEmpty()) {
                searchedList.clear();
                searchedList.addAll(blocksList);
                return;
            }
            str = str.toLowerCase();
            final List<Square> list = new ArrayList<>();
            for (Square square : blocksList) {
                if (square.state == null) continue; // 「空」项无方块状态，仅出现在空搜索词列表
                final String blockName = square.state.getBlock().getName().getString().toLowerCase();
                final String di = square.state.getBlock().getDescriptionId().toLowerCase();
                if (blockName.contains(str) || di.contains(str)) {
                    list.add(square);
                }
            }
            searchedList.clear();
            searchedList.addAll(list);
        }

        //#if MC_VERSION >= 12000
        public void render(GuiGraphics matrices, int mouseX, int mouseY, float partialTick) {
            renderImpl(GraphicContext.of(matrices), mouseX, mouseY, partialTick);
        }
        //#else
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderImpl(GraphicContext.of(poseStack), mouseX, mouseY, partialTick);
        //$$ }
        //#endif

        private void renderImpl(GraphicContext g, int mouseX, int mouseY, float partialTick) {
            //#if MC_VERSION >= 12000
            final GuiGraphics matrices = g.asMinecraft();
            //#else
            //$$ final com.mojang.blaze3d.vertex.PoseStack matrices = g.asMinecraft();
            //#endif
            final int sx = width - WIDTH;
            g.fill(sx, 0, sx + WIDTH, height, 0xff212121);
            g.fill(sx, 0, sx + 1, height, mouseX >= sx ? 0xfff2f7eb : 0xffafb3aa);
            //#if MC_VERSION >= 11903
            searchField.setPosition(sx + 3, 1);
            //#else
            //$$ searchField.x = sx + 3; searchField.y = 1; // 1.19.2 及以下 AbstractWidget 无 setPosition，x/y 为 public 字段
            //#endif
            searchField.setWidth(WIDTH - 3);
            searchField.render(matrices, mouseX, mouseY, partialTick);

            final int ssx = sx;
            final int ssy = 18;
            final int ssw = WIDTH;
            final int ssh = height - 18;
            checkAndScroll(scroll);
            final int x = ssx + 3;
            g.enableScissor(ssx, ssy, ssx + ssw, ssy + ssh);
            for (int i = 0; i < searchedList.size(); i++) {
                searchedList.get(i).render(matrices, mouseX, mouseY, x + i % COL * Square.LENGTH, ssy + scroll + i / COL * Square.LENGTH, partialTick);
            }
            g.disableScissor();
            if (canScroll()) {
                final int[] pas = getSliderPositionAndSize(ssx, ssy, ssh);
                g.fill(pas[0], pas[1], pas[0] + pas[2], pas[1] + pas[3], 0xffb0b0b0);
            }
        }

        public List<? extends GuiEventListener> children() {
            final List<GuiEventListener> result = new ArrayList<>();
            result.add(this);
            result.add(searchField);
            result.addAll(searchedList);
            return result;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!canScroll()) return false;
            final int[] pas = getSliderPositionAndSize(width - WIDTH, 18, height - 18);
            if (isMouseOverSlider(mouseX, mouseY)) {
                setScroll((int) mouseY);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double sx, double sy, int button, double dx, double dy) {
            if (isMouseOverSlider(sx, sy) || draggingSlider) {
                setScroll((int) (sy + dy));
                draggingSlider = true;
                return true;
            }
            return false;
        }

        //#if MC_VERSION < 12003
        @Override
        public boolean mouseScrolled(double x, double y, double amount) {
            if (canScroll() && isMouseOver(x, y)) {
                checkAndScroll(scroll + 20 * (int) amount);
                return true;
            }
            return false;
        }
        //#else
        //$$ @Override
        //$$ public boolean mouseScrolled(double x, double y, double amount, double horizontalAmount) {
        //$$     if (canScroll() && isMouseOver(x, y)) {
        //$$         checkAndScroll(scroll + 20 * (int) amount);
        //$$         return true;
        //$$     }
        //$$     return false;
        //$$ }
        //#endif

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (draggingSlider) {
                draggingSlider = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            final int sx = width - WIDTH;
            return sx <= mouseX && mouseX <= sx + WIDTH && 18 <= mouseY && mouseY <= height;
        }

        private boolean isMouseOverSlider(double mouseX, double mouseY) {
            if (!canScroll()) return false;
            final int[] pas = getSliderPositionAndSize(width - WIDTH, 18, height - 18);
            return pas[0] <= mouseX && mouseX <= pas[0] + pas[2] && pas[1] <= mouseY && mouseY <= pas[1] + pas[3];
        }

        private int[] getSliderPositionAndSize(int ssx, int ssy, int ssh) {
            final float ah = ah();
            final float th = ssh;
            int h = (int) (th / ah * th);
            if (h < 5) h = 5;
            final int py = ssy + (int) (-1F * scroll / ah * th);
            return new int[]{ssx + WIDTH - 5, py, 5, h};
        }

        private boolean canScroll() {
            return ah() > height - 18;
        }

        private int ah() {
            return (searchedList.size() / COL) * Square.LENGTH;
        }

        private void setScroll(int mouseY) {
            final int[] pas = getSliderPositionAndSize(width - WIDTH, 18, height - 18);
            final int maxd = (height - 18) - pas[3];
            final int dy = mouseY - pas[3] / 2 - 18;
            checkAndScroll((int) (dy / (float) maxd * (-ah() + (height - 18))));
        }

        private void checkAndScroll(int temp) {
            if (!canScroll()) {
                scroll = 0;
                return;
            }
            if (temp > 0) temp = 0;
            final int min = -ah() + (height - 18);
            if (temp < min) temp = min;
            scroll = temp;
        }

        //#if MC_VERSION >= 11903
        @Override
        //#endif
        public boolean isFocused() {
            return false;
        }

        //#if MC_VERSION >= 11903
        @Override
        //#endif
        public void setFocused(boolean focused) {
        }
    }
}
