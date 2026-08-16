package com.fangsu.ui;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 混合方案条目方块选择器：搜索框 + 12 列网格（CELL=18），点击方块回调选中的 BlockState。
 * 单元格不建 widget（方块数量大），mouseClicked 直接做命中测试；滚动逻辑照抄
 * HybridSliceTaskScreen.Inventory。renderBlockState 复制自 HybridSliceTaskScreen
 * （版本门控一致，改动需同步两处）。
 */
public class HybridBlockPickerScreen extends Screen {

    public static final int CELL = 18;
    public static final int COL = 12;

    private final Screen parent;
    private final Consumer<BlockState> onPick;
    private final List<Block> blocksList = new ArrayList<>();
    private final List<Block> searchedList = new ArrayList<>();
    private EditBox searchField;
    private int scroll = 0;
    private boolean draggingSlider = false;

    public HybridBlockPickerScreen(Screen parent, Consumer<BlockState> onPick) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.picker.title"));
        this.parent = parent;
        this.onPick = onPick;
        //#if MC_VERSION >= 11903
        for (Block block : BuiltInRegistries.BLOCK) {
            //#else
            //$$ for (Block block : net.minecraft.core.Registry.BLOCK) {
            //#endif
            blocksList.add(block);
        }
        searchedList.addAll(blocksList);
    }

    @Override
    protected void init() {
        // 网格区：x = 屏幕中央 12 列宽，y=48 起（标题下方），高度 = 底部留 24
        searchField = new EditBox(minecraft.font, width / 2 - COL * CELL / 2, 20, COL * CELL, 15, ComponentHelper.literal(""));
        //#if MC_VERSION >= 12003
        //$$ searchField.moveCursorToStart(true);
        //#else
        searchField.moveCursorToStart();
        //#endif
        searchField.setResponder(this::search);
        addRenderableWidget(searchField);
    }

    public void search(String str) {
        if (str.isEmpty()) {
            searchedList.clear();
            searchedList.addAll(blocksList);
            return;
        }
        str = str.toLowerCase();
        final List<Block> list = new ArrayList<>();
        for (Block block : blocksList) {
            final String name = block.getName().getString().toLowerCase();
            final String di = block.getDescriptionId().toLowerCase();
            if (name.contains(str) || di.contains(str)) {
                list.add(block);
            }
        }
        searchedList.clear();
        searchedList.addAll(list);
    }

    /* ===================== 渲染 ===================== */

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
        final GuiGraphics matrices = g.asMinecraft();
        //#if MC_VERSION < 12003
        renderBackground(matrices);
        //#else
        //$$ renderBackground(matrices, mouseX, mouseY, partialTick);
        //#endif
        //#else
        //$$ final PoseStack matrices = g.asMinecraft();
        //$$ renderBackground(matrices);
        //#endif
        super.render(matrices, mouseX, mouseY, partialTick); // 渲染搜索框
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.picker.title").getString(), width / 2, 6, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.picker.hint").getString(), width / 2, 36, 0xFFAAAAAA);

        // 网格视口（12 列宽，y=48 起）
        final int gx = width / 2 - COL * CELL / 2;
        final int gy = 48;
        final int gh = height - 48 - 24;
        checkAndScroll(scroll);
        g.enableScissor(gx, gy, gx + COL * CELL, gy + gh);
        for (int i = 0; i < searchedList.size(); i++) {
            renderBlockState(matrices, gx + i % COL * CELL, gy + scroll + i / COL * CELL, partialTick, searchedList.get(i).defaultBlockState());
        }
        g.disableScissor();
        if (canScroll()) {
            final int[] pas = getSliderPositionAndSize(gx, gy, gh);
            g.fill(pas[0], pas[1], pas[0] + pas[2], pas[1] + pas[3], 0xffb0b0b0);
        }
        // 悬停方块名（网格下方一行）
        final int idx = hoveredIndex(mouseX, mouseY);
        if (idx >= 0) {
            g.drawCenteredString(minecraft.font, searchedList.get(idx).getName().getString(), width / 2, height - 18, 0xFFFFFFFF);
        }
    }

    /** 鼠标位置对应的方块索引（含滚动静止偏移）；不在网格区返回 -1 */
    private int hoveredIndex(double mouseX, double mouseY) {
        final int gx = width / 2 - COL * CELL / 2;
        final int gy = 48;
        final int gh = height - 48 - 24;
        if (mouseX < gx || mouseX >= gx + COL * CELL || mouseY < gy || mouseY >= gy + gh) return -1;
        final int col = (int) ((mouseX - gx) / CELL);
        final int row = (int) ((mouseY - gy - scroll) / CELL);
        final int idx = row * COL + col;
        return idx >= 0 && idx < searchedList.size() ? idx : -1;
    }

    /* ===================== 方块渲染（复制自 HybridSliceTaskScreen，改此需同步） ===================== */

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

    /* ===================== 输入与滚动（照 HybridSliceTaskScreen.Inventory） ===================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            final int idx = hoveredIndex(mouseX, mouseY);
            if (idx >= 0) {
                onPick.accept(searchedList.get(idx).defaultBlockState());
                minecraft.setScreen(parent);
                return true;
            }
            final int gx = width / 2 - COL * CELL / 2;
            if (canScroll() && isMouseOverSlider(mouseX, mouseY, gx, 48, height - 48 - 24)) {
                setScroll((int) mouseY, gx);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double sx, double sy, int button, double dx, double dy) {
        if (button == 0 && (isMouseOverSlider(sx, sy, width / 2 - COL * CELL / 2, 48, height - 48 - 24) || draggingSlider)) {
            setScroll((int) (sy + dy), width / 2 - COL * CELL / 2);
            draggingSlider = true;
            return true;
        }
        return super.mouseDragged(sx, sy, button, dx, dy);
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        final int gx = width / 2 - COL * CELL / 2;
        return gx <= mouseX && mouseX <= gx + COL * CELL && 48 <= mouseY && mouseY <= height - 24;
    }

    private boolean isMouseOverSlider(double mouseX, double mouseY, int ssx, int ssy, int ssh) {
        if (!canScroll()) return false;
        final int[] pas = getSliderPositionAndSize(ssx, ssy, ssh);
        return pas[0] <= mouseX && mouseX <= pas[0] + pas[2] && pas[1] <= mouseY && mouseY <= pas[1] + pas[3];
    }

    private int[] getSliderPositionAndSize(int ssx, int ssy, int ssh) {
        final float ah = ah();
        final float th = ssh;
        int h = (int) (th / ah * th);
        if (h < 5) h = 5;
        final int py = ssy + (int) (-1F * scroll / ah * th);
        // 滑块在网格右缘外 4px（贴网格右侧，避免压住方块图标）
        return new int[]{ssx + COL * CELL + 4, py, 5, h};
    }

    private boolean canScroll() {
        return ah() > height - 48 - 24;
    }

    private int ah() {
        return (searchedList.size() / COL) * CELL;
    }

    private void setScroll(int mouseY, int ssx) {
        final int[] pas = getSliderPositionAndSize(ssx, 48, height - 48 - 24);
        final int maxd = (height - 48 - 24) - pas[3];
        final int dy = mouseY - pas[3] / 2 - 48;
        checkAndScroll((int) (dy / (float) maxd * (-ah() + (height - 48 - 24))));
    }

    private void checkAndScroll(int temp) {
        if (!canScroll()) {
            scroll = 0;
            return;
        }
        if (temp > 0) temp = 0;
        final int min = -ah() + (height - 48 - 24);
        if (temp < min) temp = min;
        scroll = temp;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc 返回（onClose 已处理回退，这里拦截避免默认行为覆盖）
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        // super.children() = renderables（addRenderableWidget 的 searchField 已在内），无需重复添加
        return super.children();
    }
}
