package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridCreatorJsonIO;
import com.fangsu.data.hybrid.HybridSliceTask;
import com.fangsu.items.ModItems;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.HybridCreatorPackets;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 混合构建器任务列表屏幕（照 ANTE CompoundCreatorScreen 删减：仅切片任务类型）。
 * <p>
 * 任务以 NBT 存于主手物品（键 {@value #TAG_TASKS}），任何修改通过 {@link #updateTag} 同步到服务端，
 * 服务端 onConnect 构建时读取。
 */
public class HybridCreatorScreen extends Screen {

    public static final String TAG_TASKS = "tasks";
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/block/white_concrete_powder.png");

    public static Screen createScreen(Screen parent) {
        final HybridCreatorScreen screen = new HybridCreatorScreen(parent);
        return screen.load() ? screen : parent;
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private Entry selectedEntry = null;
    private int scroll = 0;
    private int scissorX, scissorY, scissorW, scissorH;

    // 按钮创建统一走 ComponentHelper.button（内部处理 builder/构造器版本差异），位置由 updateWidgetPosition 设置
    private final Button btnAdd = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("+"), button -> addEntry());
    private final Button btnRemove = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("-"), button -> removeEntry());
    private final Button btnCopy = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.copy"), button -> copyEntry());
    private final Button btnClear = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.clear"), button -> clearEntries());
    private final Button btnExport = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.export"), button -> exportJson());
    private final Button btnImport = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import"), button -> importJson());
    private final Button btnExportPreset = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset"), button -> exportPresetJson());
    private final Button btnImportPreset = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import_preset"), button -> importPresetJson());
    private final Button btnClose = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("X"), button -> onClose());

    private HybridCreatorScreen(Screen parent) {
        super(ComponentHelper.literal(""));
        this.parent = parent;
    }

    /* ===================== NBT 读写（静态，供所有屏幕使用） ===================== */

    public static CompoundTag getTag() {
        if (Minecraft.getInstance().player == null) return null;
        final ItemStack item = Minecraft.getInstance().player.getMainHandItem();
        if (!item.is(ModItems.ITEM_HYBRID_CREATOR.get())) return null;
        return item.getOrCreateTag();
    }

    public static void updateTag(Consumer<CompoundTag> modifier) {
        if (Minecraft.getInstance().player == null) return;
        final ItemStack item = Minecraft.getInstance().player.getMainHandItem();
        if (!item.is(ModItems.ITEM_HYBRID_CREATOR.get())) return;
        final CompoundTag tag = item.getOrCreateTag();
        modifier.accept(tag);
        HybridCreatorPackets.sendUpdateHoldingItemC2S(tag);
    }

    /* ===================== 数据 ===================== */

    public boolean load() {
        final CompoundTag tag = getTag();
        if (tag == null) return false;
        if (!tag.contains(TAG_TASKS)) return true;
        final CompoundTag tasksTag = tag.getCompound(TAG_TASKS);
        for (String key : tasksTag.getAllKeys()) {
            entries.add(new Entry(new HybridSliceTask(tasksTag.getCompound(key)), key));
        }
        entries.sort(Comparator.comparingInt(entry -> entry.task.order));
        selectedEntry = entries.isEmpty() ? null : entries.get(0);
        return true;
    }

    /** 把全部条目按顺序写回 NBT（key = 列表下标）并同步服务端 */
    private void update() {
        updateTag(tag -> {
            final List<Entry> copy = new ArrayList<>(entries);
            final CompoundTag tasksTag = new CompoundTag();
            for (int i = 0; i < copy.size(); i++) {
                copy.get(i).task.order = i;
                copy.get(i).key = String.valueOf(i);
                tasksTag.put(copy.get(i).key, copy.get(i).task.toCompoundTag());
            }
            tag.put(TAG_TASKS, tasksTag);
        });
    }

    private void addEntry() {
        final HybridSliceTask task = new HybridSliceTask();
        final Entry entry = new Entry(task, String.valueOf(entries.size()));
        entries.add(entry);
        selectedEntry = entry;
        update();
    }

    private void removeEntry() {
        if (selectedEntry == null) return;
        entries.remove(selectedEntry);
        selectedEntry = entries.isEmpty() ? null : entries.get(0);
        update();
    }

    private void copyEntry() {
        if (selectedEntry == null) return;
        final Entry entry = new Entry(new HybridSliceTask(selectedEntry.task), String.valueOf(entries.size()));
        entries.add(entry);
        selectedEntry = entry;
        update();
    }

    private void clearEntries() {
        entries.clear();
        selectedEntry = null;
        update();
    }

    /* ===================== 导出/导入 JSON（分享配置） ===================== */

    private void exportJson() {
        final CompoundTag tag = getTag();
        if (tag == null || !tag.contains(TAG_TASKS)) {
            showMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.export_fail"));
            return;
        }
        try {
            final String path = HybridCreatorJsonIO.write(tag.getCompound(TAG_TASKS));
            // 自动复制到剪贴板，方便直接分享配置（消息不显示路径，太长屏幕看不全）
            minecraft.keyboardHandler.setClipboard(Files.readString(Path.of(path), StandardCharsets.UTF_8));
            showMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.export_success"));
        } catch (IOException e) {
            com.fangsu.Main.LOGGER.error("[HybridCreator] 导出任务失败", e);
            showMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.io_error"));
        }
    }

    /** 导入：打开输入框粘贴 JSON 文本 */
    private void importJson() {
        minecraft.setScreen(new HybridImportScreen(this, this::applyImported));
    }

    /** 导入预设：从游戏目录 hybrid_creator/ 文件夹选择一个 JSON 文件 */
    private void importPresetJson() {
        minecraft.setScreen(new HybridPresetImportScreen(this, this::applyImported));
    }

    /** 导出预设：输入预设名称，导出到游戏目录 hybrid_creator/ 文件夹 */
    private void exportPresetJson() {
        final CompoundTag tag = getTag();
        if (tag == null || !tag.contains(TAG_TASKS)) {
            showMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.export_fail"));
            return;
        }
        minecraft.setScreen(new HybridPresetExportScreen(this, tag.getCompound(TAG_TASKS)));
    }

    /** 导入结果统一落地：写 NBT、刷新条目列表（供粘贴导入与预设导入共用） */
    private void applyImported(CompoundTag tasksTag) {
        entries.clear();
        for (String key : tasksTag.getAllKeys()) {
            entries.add(new Entry(new HybridSliceTask(tasksTag.getCompound(key)), key));
        }
        entries.sort(Comparator.comparingInt(entry -> entry.task.order));
        selectedEntry = entries.isEmpty() ? null : entries.get(0);
        update();
        showMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.import_success", entries.size()));
    }

    /** 屏幕内提示消息（客户端本地显示） */
    private void showMessage(Component message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    /* ===================== 渲染 ===================== */

    @Override
    protected void init() {
        // 按钮手动管理（照 ANTE）：渲染顺序与滚动条/条目相对位置可控
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
        g.fill(0, 38, width, height - 38, 0x90000000);

        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.title").getString(), width / 2, 18, 0xFFFFFFFF);

        placeButton(btnAdd, width - 50, 60, 40);
        placeButton(btnRemove, width - 50, 90, 40);
        placeButton(btnCopy, width - 50, 120, 40);
        placeButton(btnClear, width - 50, 150, 40);
        placeButton(btnExport, width - 50, 180, 40);
        placeButton(btnImport, width - 50, 210, 40);
        placeButton(btnExportPreset, width - 99, 180, 44);
        placeButton(btnImportPreset, width - 99, 210, 44);
        placeButton(btnClose, 10, 10, 20);
        btnAdd.render(graphics, mouseX, mouseY, partialTick);
        btnRemove.render(graphics, mouseX, mouseY, partialTick);
        btnCopy.render(graphics, mouseX, mouseY, partialTick);
        btnClear.render(graphics, mouseX, mouseY, partialTick);
        btnExport.render(graphics, mouseX, mouseY, partialTick);
        btnImport.render(graphics, mouseX, mouseY, partialTick);
        btnExportPreset.render(graphics, mouseX, mouseY, partialTick);
        btnImportPreset.render(graphics, mouseX, mouseY, partialTick);
        btnClose.render(graphics, mouseX, mouseY, partialTick);

        scissorX = 0;
        scissorY = 40;
        scissorW = width - 55;
        scissorH = height - 80;
        checkAndScroll(scroll);
        g.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);
        int y = 40 + scroll;
        for (Entry entry : entries) {
            entry.render(graphics, mouseX, mouseY, y, partialTick);
            y += Entry.HEIGHT;
        }
        g.disableScissor();

        if (canScroll()) {
            final int[] pas = getSliderPositionAndSize();
            g.blit(WHITE, pas[0], pas[1], pas[2], pas[3], 0, 0, 1, 1, 1, 1);
        }
    }

    private static void placeButton(Button button, int x, int y, int width) {
        //#if MC_VERSION >= 11903
        button.setPosition(x, y);
        button.setWidth(width);
        //#else
        //$$ button.x = x; // 1.19.2 及以下无 setPosition/setY，x/y 为 public 字段
        //$$ button.y = y;
        //$$ button.setWidth(width);
        //#endif
    }

    /* ===================== 滚动 ===================== */

    private void setScroll(int mouseY) {
        final int[] pas = getSliderPositionAndSize();
        final int maxd = scissorH - pas[3];
        final int dy = mouseY - pas[3] / 2 - scissorY;
        final int maxScroll = -entries.size() * Entry.HEIGHT + scissorH;
        checkAndScroll((int) (dy / (float) maxd * maxScroll));
    }

    private void checkAndScroll(int temp) {
        if (!canScroll()) {
            scroll = 0;
            return;
        }
        if (temp > 0) temp = 0;
        final int min = -entries.size() * Entry.HEIGHT + scissorH;
        if (temp < min) temp = min;
        scroll = temp;
    }

    //#if MC_VERSION < 12003
    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!canScroll()) return super.mouseScrolled(x, y, amount);
        if (scissorX <= x && x <= scissorX + scissorW && scissorY <= y && y <= scissorY + scissorH) {
            checkAndScroll(scroll + 10 * (int) amount);
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseScrolled(double x, double y, double amount, double horizontalAmount) {
    //$$     if (!canScroll()) return super.mouseScrolled(x, y, amount, horizontalAmount);
    //$$     if (scissorX <= x && x <= scissorX + scissorW && scissorY <= y && y <= scissorY + scissorH) {
    //$$         checkAndScroll(scroll + 10 * (int) amount);
    //$$         return true;
    //$$     }
    //$$     return super.mouseScrolled(x, y, amount, horizontalAmount);
    //$$ }
    //#endif

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!canScroll()) return super.mouseClicked(mouseX, mouseY, button);
        final int[] pas = getSliderPositionAndSize();
        if (isMouseOverSlider(mouseX, mouseY)) {
            setScroll((int) mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double sx, double sy, int button, double dx, double dy) {
        if (isMouseOverSlider(sx, sy)) {
            setScroll((int) (sy + dy));
            return true;
        }
        return super.mouseDragged(sx, sy, button, dx, dy);
    }

    private boolean isMouseOverSlider(double mouseX, double mouseY) {
        if (!canScroll()) return false;
        final int[] pas = getSliderPositionAndSize();
        return pas[0] <= mouseX && mouseX <= pas[0] + pas[2] && pas[1] <= mouseY && mouseY <= pas[1] + pas[3];
    }

    private int[] getSliderPositionAndSize() {
        final float ah = (float) entries.size() * Entry.HEIGHT;
        final float th = (float) scissorH;
        final int h = (int) (th / ah * th);
        final int py = scissorY + (int) (-1F * scroll / ah * th);
        return new int[]{scissorW + 1, py, (int) (width * 0.01F), h};
    }

    private boolean canScroll() {
        return entries.size() * Entry.HEIGHT > scissorH;
    }

    /* ===================== 输入分发 ===================== */

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        final List<GuiEventListener> result = new ArrayList<>();
        result.addAll(super.children());
        for (Entry entry : entries) {
            result.addAll(entry.children());
        }
        result.add(btnAdd);
        result.add(btnRemove);
        result.add(btnCopy);
        result.add(btnClear);
        result.add(btnExport);
        result.add(btnImport);
        result.add(btnExportPreset);
        result.add(btnImportPreset);
        result.add(btnClose);
        return result;
    }

    @Override
    public void onClose() {
        update();
        minecraft.setScreen(parent);
    }

    /* ===================== 任务条目 ===================== */

    public class Entry implements GuiEventListener {
        public static final int HEIGHT = 24;

        public HybridSliceTask task;
        /** 任务在 tasksTag 中的键（列表屏 update 后 = 列表下标） */
        public String key;
        private int y;

        /** 懒创建：minecraft 在 init() 后才非 null，而 Entry 可能在 load()（屏幕未 init）时创建 */
        private EditBox nameField;
        private final Button enter = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.translatable("ui.fangsu.hybrid_creator.enter"), button -> enter());
        private final Button up = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("▲"), button -> moveUp());
        private final Button down = ComponentHelper.button(0, 0, 20, 20, ComponentHelper.literal("▼"), button -> moveDown());

        public Entry(HybridSliceTask task, String key) {
            this.task = task;
            this.key = key;
            // nameField 由 nameField() 懒创建（不在此处 new EditBox）
        }

        private EditBox nameField() {
            if (nameField == null) {
                nameField = new EditBox(minecraft.font, 0, 0, 0, 16, ComponentHelper.literal(""));
                nameField.setValue(task.name);
                //#if MC_VERSION >= 12003
                //$$ nameField.moveCursorToStart(true);
                //#else
                nameField.moveCursorToStart();
                //#endif
                nameField.setResponder(this::updateName);
            }
            return nameField;
        }

        public List<? extends GuiEventListener> children() {
            final List<GuiEventListener> result = new ArrayList<>();
            result.add(nameField());
            result.add(enter);
            result.add(up);
            result.add(down);
            result.add(this);
            return result;
        }

        public void enter() {
            minecraft.setScreen(new HybridSliceTaskScreen(task, key, HybridCreatorScreen.this));
        }

        public void updateName(String name) {
            task.name = name;
            selectedEntry = this;
            update();
        }

        public void moveUp() {
            final int index = entries.indexOf(this);
            if (index > 0) {
                entries.set(index, entries.get(index - 1));
                entries.set(index - 1, this);
                selectedEntry = this;
                update();
            }
        }

        public void moveDown() {
            final int index = entries.indexOf(this);
            if (index < entries.size() - 1) {
                entries.set(index, entries.get(index + 1));
                entries.set(index + 1, this);
                selectedEntry = this;
                update();
            }
        }

        //#if MC_VERSION >= 12000
        public void render(GuiGraphics graphics, int mouseX, int mouseY, int y, float partialTick) {
            renderImpl(GraphicContext.of(graphics), mouseX, mouseY, y, partialTick);
        }
        //#else
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, int y, float partialTick) {
        //$$     renderImpl(GraphicContext.of(poseStack), mouseX, mouseY, y, partialTick);
        //$$ }
        //#endif

        private void renderImpl(GraphicContext g, int mouseX, int mouseY, int y, float partialTick) {
            //#if MC_VERSION >= 12000
            final GuiGraphics graphics = g.asMinecraft();
            //#else
            //$$ final com.mojang.blaze3d.vertex.PoseStack graphics = g.asMinecraft();
            //#endif
            this.y = y;
            if (isSelected()) {
                g.fill(0, y(), width - 55, y() + HEIGHT, 0xa0eeeeee);
            } else if (isMouseOver(mouseX, mouseY)) {
                g.fill(0, y(), width - 55, y() + HEIGHT, 0x40eeeeee);
            }
            y += 2;
            final int count = width() / 20;
            int x = 20;
            //#if MC_VERSION >= 11903
            nameField().setPosition(x, y);
            //#else
            //$$ nameField().x = x; // 1.19.2 及以下无 setPosition，x/y 为 public 字段
            //$$ nameField().y = y;
            //#endif
            nameField().setWidth(count * 7);
            x += count * 8;
            //#if MC_VERSION >= 11903
            enter.setPosition(x, y);
            //#else
            //$$ enter.x = x;
            //$$ enter.y = y;
            //#endif
            enter.setWidth(count * 6);
            x += count * 7;
            //#if MC_VERSION >= 11903
            up.setPosition(x, y);
            //#else
            //$$ up.x = x;
            //$$ up.y = y;
            //#endif
            up.setWidth(count * 2);
            x += count * 3;
            //#if MC_VERSION >= 11903
            down.setPosition(x, y);
            //#else
            //$$ down.x = x;
            //$$ down.y = y;
            //#endif
            down.setWidth(count * 2);
            nameField().render(graphics, mouseX, mouseY, partialTick);
            enter.render(graphics, mouseX, mouseY, partialTick);
            up.render(graphics, mouseX, mouseY, partialTick);
            down.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return 20 <= mouseX && mouseX <= width - 55 && y() <= mouseY && mouseY <= y() + HEIGHT;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width - 80;
        }

        public boolean isSelected() {
            return selectedEntry == this;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOver(mouseX, mouseY)) {
                selectedEntry = this;
                return true;
            }
            return false;
        }

        //#if MC_VERSION >= 11903
        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
        }
        //#else
        //$$ public boolean isFocused() {
        //$$     return false;
        //$$ }
        //$$
        //$$ public void setFocused(boolean focused) {
        //$$ }
        //#endif
    }
}
