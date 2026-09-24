package com.fangsu.ui;

import com.fangsu.blockEntities.FunctionalObjBlockEntity;
import com.fangsu.data.hybrid.HybridPlacementTask;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
//#if MC_VERSION >= 11903
import com.mojang.math.Axis;
//#endif
import net.minecraft.client.Minecraft;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 「放置组合」画布编辑器：中央网格画布（列=垂直轨道法向 dn、行=竖直 du），右侧物品栏式方块选择器。
 * 左键放置当前方块到格子、中键取色、右键编辑该格方块（在世界放临时模板方块并打开扳手配置屏，
 * 关闭时捕获其 NBT 回写预设并移除临时块）。组合沿轨道逐站重复放置（每站独立定向跟随轨道）。
 */
public class HybridPlacementTaskScreen extends Screen {

    private static final int LENGTH = 18;

    private HybridPlacementTask task;
    private final String key;
    private final Screen parent;

    /** 画布逻辑尺寸：width = dn 列数、height = du 行数（默认 5×5，中心列/行 = 轨道中心） */
    private int width;
    private int height;

    /** 当前笔刷方块状态（null = 清空格） */
    private BlockState nowState = null;
    /** 笔刷的方块实体预设（从世界方块捕获或从画布格读回） */
    private CompoundTag nowBeNbt = null;

    private int scissorX, scissorY, scissorW, scissorH;
    private final List<Cell> cells = new ArrayList<>();
    private Cell mouseOver = null;
    private final Inventory inventory = new Inventory();

    private final Button btnReturn = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("X"), button -> onClose());
    private final Button btnAddW = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("+"), button -> setSize(width + 2, height));
    private final Button btnSubW = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("-"), button -> setSize(Math.max(3, width - 2), height));
    private final Button btnAddH = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("+"), button -> setSize(width, height + 2));
    private final Button btnSubH = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("-"), button -> setSize(width, Math.max(3, height - 2)));
    private final Button btnVertical = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("竖直"), button -> {
        task.verticalMode = task.verticalMode == HybridPlacementTask.VerticalMode.GROUND
                ? HybridPlacementTask.VerticalMode.TRACK : HybridPlacementTask.VerticalMode.GROUND;
        updateTask();
    });

    private EditBox nameField;

    public HybridPlacementTaskScreen(HybridPlacementTask task, String key, Screen parent) {
        super(ComponentHelper.literal(""));
        this.task = task;
        this.key = key;
        this.parent = parent;
        this.width = 5;
        this.height = 5;
    }

    /* ===================== 数据 ===================== */

    private void rebuild() {
        cells.clear();
        final int midX = width / 2;
        final int midY = height / 2;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                final int dn = c - midX;
                final int du = midY - r;
                final HybridPlacementTask.PlacementObject obj = findObject(dn, du);
                cells.add(new Cell(c, r, obj));
            }
        }
    }

    private HybridPlacementTask.PlacementObject findObject(int dn, int du) {
        for (HybridPlacementTask.PlacementObject obj : task.objects) {
            if (Math.round(obj.dn) == dn && Math.round(obj.du) == du) return obj;
        }
        return null;
    }

    private void setSize(int w, int h) {
        width = Math.max(3, w);
        height = Math.max(3, h);
        rebuild();
        updateTask();
    }

    /** 在格子 (c,r) 放置当前笔刷（替换该 dn/du 处的对象） */
    private void placeAt(Cell cell) {
        final int dn = cell.dn();
        final int du = cell.du();
        final HybridPlacementTask.PlacementObject existing = findObject(dn, du);
        if (nowState == null) {
            // 清空格：删除该处对象
            if (existing != null) {
                task.objects.remove(existing);
                updateTask();
                rebuild();
            }
            return;
        }
        final Block block = nowState.getBlock();
        if (existing != null) {
            existing.blockName = blockKey(block);
            existing.beNbt = nowBeNbt;
            existing.dn = dn;
            existing.du = du;
            existing.dt = 0;
        } else {
            final HybridPlacementTask.PlacementObject obj = new HybridPlacementTask.PlacementObject();
            obj.blockName = blockKey(block);
            obj.dn = dn;
            obj.du = du;
            obj.dt = 0;
            obj.beNbt = nowBeNbt;
            task.objects.add(obj);
        }
        updateTask();
        rebuild();
    }

    /** 编辑某格对象：在世界放临时模板方块并打开扳手配置屏，关闭时捕获 NBT 回写 */
    private void editObject(Cell cell) {
        final HybridPlacementTask.PlacementObject obj = cell.object;
        if (obj == null || obj.blockName == null || obj.blockName.isEmpty()) return;
        final Block block = HybridSliceTaskByName.getBlockByName(obj.blockName);
        if (block == null) return;
        if (minecraft.player == null || minecraft.level == null) return;

        // 找一个远离视线的空气位置放临时模板方块
        final BlockPos playerPos = minecraft.player.blockPosition();
        BlockPos tempPos = null;
        for (int dy = 2; dy <= 6; dy++) {
            final BlockPos p = playerPos.above(dy);
            if (minecraft.level.isEmptyBlock(p)) { tempPos = p; break; }
        }
        if (tempPos == null) return;
        minecraft.level.setBlockAndUpdate(tempPos, block.defaultBlockState());
        final BlockEntity be = minecraft.level.getBlockEntity(tempPos);
        if (!(be instanceof FunctionalObjBlockEntity functional)) {
            minecraft.level.setBlockAndUpdate(tempPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            return;
        }
        // 注入当前预设
        if (obj.beNbt != null) {
            final CompoundTag data = obj.beNbt.copy();
            data.remove("x"); data.remove("y"); data.remove("z");
            functional.load(data);
        }
        // 打开扳手配置屏；关闭时捕获回写并移除临时块
        minecraft.setScreen(new TemplateEditScreen(functional, tempPos, obj, this));
    }

    /* ===================== 渲染 ===================== */

    @Override
    protected void init() {
        nameField = new EditBox(minecraft.font, 0, 0, 0, 16, ComponentHelper.literal(""));
        nameField.setValue(task.name);
        nameField.setResponder(s -> {
            task.name = s;
            updateTask();
        });
        inventory.initSearchField();
        rebuild();
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

    private void renderImpl(GraphicContext g, int mouseX, int mouseY, float partialTick) {
        //#if MC_VERSION >= 12000
        final GuiGraphics graphics = g.asMinecraft();
        renderBackground(graphics);
        //#else
        //$$ final com.mojang.blaze3d.vertex.PoseStack graphics = g.asMinecraft();
        //$$ renderBackground(graphics);
        //#endif
        super.render(graphics, mouseX, mouseY, partialTick);
        mouseOver = null;
        updateWidgetPosition();

        btnReturn.render(graphics, mouseX, mouseY, partialTick);
        btnAddW.render(graphics, mouseX, mouseY, partialTick);
        btnSubW.render(graphics, mouseX, mouseY, partialTick);
        btnAddH.render(graphics, mouseX, mouseY, partialTick);
        btnSubH.render(graphics, mouseX, mouseY, partialTick);
        btnVertical.render(graphics, mouseX, mouseY, partialTick);
        nameField.render(graphics, mouseX, mouseY, partialTick);
        inventory.render(graphics, mouseX, mouseY, partialTick);

        scissorX = 40;
        scissorY = 40;
        scissorW = width - 50 - Inventory.WIDTH;
        scissorH = height - 40 - 40;
        final int sx = scissorX + scissorW / 2;
        final int sy = scissorY + scissorH / 2;

        g.fill(scissorX - 1, scissorY - 1, scissorX + scissorW + 1, scissorY + scissorH + 1, 0xff403636);
        g.fill(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH, 0xff8f5d5d);

        g.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);
        for (Cell cell : cells) {
            cell.render(graphics, mouseX, mouseY, sx + (cell.c - width / 2) * LENGTH, sy + (height / 2 - cell.r) * LENGTH, partialTick);
        }
        // 轨道中心标记
        final int py = sy - LENGTH / 2 - 1;
        g.fill(scissorX, py, scissorX + scissorW, py + 2, 0x7FFF0000);
        final int px = sx - LENGTH / 2 - 1;
        g.fill(px, scissorY, px + 2, scissorY + scissorH, 0x7F00FF00);
        g.disableScissor();

        // 当前笔刷预览 + 竖直模式 + 提示
        if (nowState != null) {
            renderBlockState(graphics, 10, 10, partialTick, nowState);
        }
        g.drawString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.placement.vertical", task.verticalMode == HybridPlacementTask.VerticalMode.GROUND ? "垂直地面" : "垂直轨道").getString(), 40, 12, 0xFFFFFF, false);
        g.drawString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.placement.canvas_hint").getString(), 120, 12, 0xAAAAAA, false);

        if (mouseOver != null && mouseOver.object != null) {
            // 显示对象信息
            final HybridPlacementTask.PlacementObject o = mouseOver.object;
            g.drawString(minecraft.font, (o.blockName == null || o.blockName.isEmpty() ? "?" : o.blockName), mouseX + 4, mouseY + 4, 0xFFFFFF, false);
        }
    }

    private void updateWidgetPosition() {
        placeButton(btnReturn, 10, 10, 20);
        //#if MC_VERSION >= 11903
        nameField.setPosition(34, 10);
        //#else
        //$$ nameField.x = 34; nameField.y = 10;
        //#endif
        nameField.setWidth(80);
        placeButton(btnSubW, 40, 28, 20);
        placeButton(btnAddW, 70, 28, 20);
        placeButton(btnSubH, 100, 28, 20);
        placeButton(btnAddH, 130, 28, 20);
        placeButton(btnVertical, 160, 28, 60);
    }

    private static void placeButton(Button button, int x, int y, int width) {
        //#if MC_VERSION >= 11903
        button.setPosition(x, y);
        //#else
        //$$ button.x = x; button.y = y;
        //#endif
        button.setWidth(width);
    }

    private void updateTask() {
        HybridCreatorScreen.updateTag(tag -> {
            if (tag.contains(HybridCreatorScreen.TAG_TASKS)) {
                tag.getCompound(HybridCreatorScreen.TAG_TASKS).put(key, task.toCompoundTag());
            }
        });
    }

    /** 对象屏/模板编辑关闭后回调 */
    public void markDirty() {
        updateTask();
        rebuild();
    }

    @Override
    public void onClose() {
        updateTask();
        minecraft.setScreen(parent);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        final List<GuiEventListener> result = new ArrayList<>();
        result.addAll(super.children());
        result.add(nameField);
        result.add(btnReturn);
        result.add(btnAddW);
        result.add(btnSubW);
        result.add(btnAddH);
        result.add(btnSubH);
        result.add(btnVertical);
        result.addAll(inventory.children());
        result.addAll(cells);
        return result;
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
    //#if MC_VERSION >= 11903
    //$$     poseStack.mulPose(Axis.XP.rotationDegrees(3));
    //#else
    //$$     poseStack.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(3));
    //#endif
    //$$     final MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
    //$$     RenderSystem.enableDepthTest();
    //$$     RenderSystem.setShader(GameRenderer::getPositionTexShader);
    //$$     RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
    //$$     blockRenderer.renderSingleBlock(state, poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    //$$     buffer.endBatch();
    //$$     poseStack.popPose();
    //$$ }
    //#endif

    /* ===================== 画布格子 ===================== */

    public class Cell implements GuiEventListener {
        public int c;
        public int r;
        public HybridPlacementTask.PlacementObject object;
        public int x;
        public int y;

        public Cell(int c, int r, HybridPlacementTask.PlacementObject object) {
            this.c = c;
            this.r = r;
            this.object = object;
        }

        public int dn() {
            return c - width / 2;
        }

        public int du() {
            return height / 2 - r;
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
            final boolean hover = x <= mouseX && mouseX <= x + LENGTH && y <= mouseY && mouseY <= y + LENGTH;
            g.fill(x, y, x + LENGTH, y + LENGTH, hover ? 0xfffafff2 : 0xff9b9e96);
            g.fill(x + 1, y + 1, x + LENGTH - 1, y + LENGTH - 1, 0xff919191);
            if (object != null && !object.blockName.isEmpty()) {
                final Block block = HybridSliceTaskByName.getBlockByName(object.blockName);
                if (block != null) {
                    renderBlockState(matrices, x + 1, y + 1, partialTick, block.defaultBlockState());
                }
                g.fill(x + 1, y + 1, x + LENGTH - 1, y + LENGTH - 1, 0x2fdda9df);
            }
            if (hover) {
                mouseOver = this;
            }
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return x <= mouseX && mouseX <= x + LENGTH && y <= mouseY && mouseY <= y + LENGTH;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            if (button == 0) {
                placeAt(this);
            } else if (button == 1) {
                editObject(this);
            } else if (button == 2) {
                // 取色：把该格对象设为笔刷（含其 BE 预设）
                if (object != null && !object.blockName.isEmpty()) {
                    final Block block = HybridSliceTaskByName.getBlockByName(object.blockName);
                    if (block != null) {
                        nowState = block.defaultBlockState();
                        nowBeNbt = object.beNbt;
                    }
                }
            }
            return true;
        }

        //#if MC_VERSION >= 11904
        @Override
        public boolean isFocused() { return false; }
        @Override
        public void setFocused(boolean focused) { }
        //#endif
    }

    /* ===================== 物品栏式方块选择器 ===================== */

    public class Inventory implements GuiEventListener {
        public static final int WIDTH = 100;
        public static final int COL = 5;
        private int scroll = 0;
        private final List<BlockEntry> all = new ArrayList<>();
        private final List<BlockEntry> shown = new ArrayList<>();
        private EditBox searchField;

        public Inventory() {
            for (Block block : allBlocks()) {
                all.add(new BlockEntry(block));
            }
            shown.addAll(all);
        }

        private List<Block> allBlocks() {
            final List<Block> list = new ArrayList<>();
            //#if MC_VERSION >= 11903
            for (Block block : BuiltInRegistries.BLOCK) { list.add(block); }
            //#else
            //$$ for (Block block : net.minecraft.core.Registry.BLOCK) { list.add(block); }
            //#endif
            return list;
        }

        public void initSearchField() {
            searchField = new EditBox(minecraft.font, 0, 1, WIDTH - 3, 15, ComponentHelper.literal(""));
            searchField.setResponder(s -> {
                shown.clear();
                if (s.isEmpty()) {
                    shown.addAll(all);
                } else {
                    final String q = s.toLowerCase();
                    for (BlockEntry e : all) {
                        if (e.block.getName().getString().toLowerCase().contains(q) || e.block.getDescriptionId().toLowerCase().contains(q)) {
                            shown.add(e);
                        }
                    }
                }
                scroll = 0;
            });
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
            //#if MC_VERSION >= 11903
            searchField.setPosition(sx + 3, 1);
            //#else
            //$$ searchField.x = sx + 3; searchField.y = 1;
            //#endif
            searchField.setWidth(WIDTH - 3);
            searchField.render(matrices, mouseX, mouseY, partialTick);
            final int ssy = 18;
            final int ssh = height - 18;
            if (shown.size() / COL * LENGTH > ssh) {
                if (scroll > 0) scroll = 0;
            }
            g.enableScissor(sx, ssy, sx + WIDTH, ssy + ssh);
            for (int i = 0; i < shown.size(); i++) {
                final int cx = sx + 3 + i % COL * LENGTH;
                final int cy = ssy + scroll + i / COL * LENGTH;
                final BlockEntry e = shown.get(i);
                final boolean hover = cx <= mouseX && mouseX <= cx + LENGTH && cy <= mouseY && mouseY <= cy + LENGTH;
                g.fill(cx, cy, cx + LENGTH, cy + LENGTH, hover ? 0xfffafff2 : 0xff9b9e96);
                g.fill(cx + 1, cy + 1, cx + LENGTH - 1, cy + LENGTH - 1, 0xff919191);
                renderBlockState(matrices, cx + 1, cy + 1, partialTick, e.block.defaultBlockState());
                if (hover) {
                    g.drawString(minecraft.font, e.block.getName().getString(), mouseX + 4, mouseY + 4, 0xFFFFFF, false);
                }
            }
            g.disableScissor();
        }

        public List<? extends GuiEventListener> children() {
            return List.of(this, searchField);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            final int sx = width - WIDTH;
            if (mouseX < sx || mouseX > sx + WIDTH || mouseY < 18) return false;
            final int col = (int) ((mouseX - sx - 3) / LENGTH);
            final int row = (int) ((mouseY - 18 - scroll) / LENGTH);
            final int idx = row * COL + col;
            if (idx >= 0 && idx < shown.size()) {
                final BlockEntry e = shown.get(idx);
                nowState = e.block.defaultBlockState();
                nowBeNbt = null;
            }
            return true;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            final int sx = width - WIDTH;
            return sx <= mouseX && mouseX <= sx + WIDTH && 18 <= mouseY && mouseY <= height;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            scroll += (int) delta * 20;
            return true;
        }

        //#if MC_VERSION >= 11904
        @Override
        public boolean isFocused() { return false; }
        @Override
        public void setFocused(boolean focused) { }
        //#endif
    }

    private static class BlockEntry {
        final Block block;
        BlockEntry(Block block) { this.block = block; }
    }

    /* ===================== 临时模板方块 + 扳手屏 ===================== */

    /**
     * 包装 {@link ObjBlockConfigScreen}：打开在临时模板方块上，关闭时捕获其 NBT 回写对象预设并移除临时块。
     */
    private static class TemplateEditScreen extends ObjBlockConfigScreen {
        private final FunctionalObjBlockEntity be;
        private final BlockPos tempPos;
        private final HybridPlacementTask.PlacementObject obj;
        private final HybridPlacementTaskScreen canvas;

        TemplateEditScreen(FunctionalObjBlockEntity be, BlockPos tempPos, HybridPlacementTask.PlacementObject obj, HybridPlacementTaskScreen canvas) {
            super(be);
            this.be = be;
            this.tempPos = tempPos;
            this.obj = obj;
            this.canvas = canvas;
        }

        @Override
        public void onClose() {
            super.onClose();
            // 捕获回写预设
            final CompoundTag tag = be.saveWithFullMetadata();
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
            obj.beNbt = tag;
            obj.blockName = HybridSliceTaskByName.getBlockKey(be.getBlockState().getBlock());
            canvas.markDirty();
            // 移除临时模板方块
            final Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.level.setBlockAndUpdate(tempPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            mc.setScreen(canvas);
        }
    }

    /** 用完整方块名查询方块（跨版本注册表，仅数据层用） */
    private static String blockKey(Block block) {
        return HybridSliceTaskByName.getBlockKey(block);
    }

    private static class HybridSliceTaskByName {
        static Block getBlockByName(String name) {
            try {
                //#if MC_VERSION >= 11903
                return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(name));
                //#else
                //$$ return net.minecraft.core.Registry.BLOCK.get(new net.minecraft.resources.ResourceLocation(name));
                //#endif
            } catch (Exception e) {
                return null;
            }
        }

        static String getBlockKey(Block block) {
            //#if MC_VERSION >= 11903
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
            //#else
            //$$ return net.minecraft.core.Registry.BLOCK.getKey(block).toString();
            //#endif
        }
    }
}
