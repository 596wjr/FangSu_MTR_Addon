package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.utils.GraphicContext;
import com.fangsu.utils.ResourceUtil;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * 资源文件浏览器 Screen，类似 Windows 资源管理器的风格。
 * <p>
 * 按命名空间 + 路径前缀浏览资源包中的文件，支持：
 * <ul>
 *   <li>文件夹树状导航（点击文件夹进入、[..] 返回上级）</li>
 *   <li>按文件扩展名筛选（构造时指定 suffixes 列表）</li>
 *   <li>多选</li>
 *   <li>路径面包屑显示</li>
 * </ul>
 */
public class ResourceSelectionScreen extends Screen {

    /* ===================== 常量 ===================== */

    private static final int PATH_BAR_HEIGHT = 18;
    private static final int ITEM_HEIGHT = 16;
    private static final int ITEM_PADDING = 4;
    private static final int BOTTOM_BAR_HEIGHT = 38;
    private static final int SCROLLBAR_WIDTH = 5;

    /* ===================== 构造参数 ===================== */

    private final String namespace;
    private final List<String> suffixes;
    private final int maxSelect;
    private final Consumer<List<ResourceLocation>> callback;
    private final Screen parent;

    /* ===================== 运行时状态 ===================== */

    /** 当前选中的命名空间，null 表示尚在选择命名空间的层级 */
    private String selectedNamespace;

    /** 当前所在目录路径（相对于选中命名空间的根），空字符串表示根目录 */
    private String currentPath = "";

    /** 当前目录下的子文件夹名列表 */
    private List<String> currentSubDirs = Collections.emptyList();

    /** 当前目录下的所有可显示条目（合并 dirs + files，用于渲染索引） */
    private List<String> displayEntries = Collections.emptyList();

    /** 显示名 → 完整资源ID（"namespace:path"）的映射（仅文件条目） */
    private final Map<String, String> entryToResourceId = new HashMap<>();

    /** 已选中的完整资源ID集合（格式 "namespace:path"） */
    private final Set<String> selectedResourceIds = new HashSet<>();

    /** 滚动偏移量（像素） */
    private int scrollOffset = 0;

    /** 滚动条是否正在被拖拽 */
    private boolean scrollbarDragging = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartOffset = 0;

    /* ===================== 预览状态 ===================== */

    private boolean previewMode = false;
    private String previewResourceId = null;
    private GraphicsTexture previewTexture = null;

    /* ===================== 构造 ===================== */

    /**
     * @param title     界面标题
     * @param namespace 命名空间过滤（如 "fangsu"），null 表示所有命名空间
     * @param suffixes 文件扩展名筛选列表（如 [".json", ".png"]），null 或空列表表示所有文件
     * @param callback  确认时回调，传入选中的 ResourceLocation 列表
     * @param maxSelect 最大可选文件数（≤0 表示不限制）
     * @param parent    返回的上一级 Screen
     */
    public ResourceSelectionScreen(Component title, String namespace, List<String> suffixes,
                                    Consumer<List<ResourceLocation>> callback, int maxSelect, Screen parent) {
        super(title);
        this.namespace = namespace;
        this.selectedNamespace = namespace; // 非 null 时直接进入该 namespace
        this.suffixes = suffixes;
        this.callback = callback;
        this.maxSelect = maxSelect;
        this.parent = parent;
    }

    /* ===================== 初始化 ===================== */

    @Override
    protected void init() {
        super.init();
        refreshCurrentDirectory();
    }

    /* ===================== 目录导航 ===================== */

    /** 进入指定相对路径的目录 */
    private void navigateTo(String path) {
        currentPath = path;
        selectedResourceIds.clear();
        scrollOffset = 0;
        closePreview();
        refreshCurrentDirectory();
    }

    /** 回到上级目录 */
    private void navigateUp() {
        if (currentPath.isEmpty()) {
            // 根目录再向上 → 回到命名空间选择层
            if (selectedNamespace != null) {
                selectedNamespace = null;
                selectedResourceIds.clear();
                scrollOffset = 0;
                closePreview();
                refreshCurrentDirectory();
            }
            return;
        }
        int lastSlash = currentPath.lastIndexOf('/');
        navigateTo(lastSlash >= 0 ? currentPath.substring(0, lastSlash) : "");
    }

    /** 刷新当前目录下的文件/文件夹列表 */
    private void refreshCurrentDirectory() {
        // 命名空间选择层级：列出所有可用的命名空间
        if (selectedNamespace == null) {
            final List<ResourceLocation> allNS = ResourceUtil.listResources(null, null, suffixes);
            final Set<String> nsSet = new TreeSet<>();
            for (final ResourceLocation loc : allNS) {
                if (namespace == null || loc.getNamespace().equals(namespace)) {
                    nsSet.add(loc.getNamespace());
                }
            }
            currentSubDirs = new ArrayList<>(nsSet);
            entryToResourceId.clear();
            displayEntries = new ArrayList<>(currentSubDirs);
            return;
        }

        // 已选定命名空间：正常浏览目录
        // 在根目录时，不使用路径前缀过滤，确保根目录下的文件（不含子目录路径）能被获取到。
        // Minecraft 1.19+ 的 resourceManager.listResources 在传入空字符串前缀时，
        // 某些实现可能不返回根目录下的直接文件（如 "a.png" 不含 "/" 的路径）。
        // 因此这里统一使用 "" 作为前缀，而非 null 转 ""，保证遍历所有路径。
        final List<ResourceLocation> allResources = ResourceUtil.listResources(null, currentPath.isEmpty() ? "" : currentPath, suffixes);

        final Set<String> dirSet = new TreeSet<>();
        final Set<String> fileSet = new TreeSet<>();
        final String prefix = currentPath.isEmpty() ? "" : currentPath + "/";

        entryToResourceId.clear();

        for (final ResourceLocation loc : allResources) {
            if (!loc.getNamespace().equals(selectedNamespace)) continue;

            final String path = loc.getPath();
            if (!path.startsWith(prefix)) continue;

            final String relative = path.substring(prefix.length());
            if (relative.isEmpty()) continue;

            final int slashIdx = relative.indexOf('/');
            if (slashIdx >= 0) {
                dirSet.add(relative.substring(0, slashIdx));
            } else {
                fileSet.add(relative);
                entryToResourceId.put(relative, loc.toString());
            }
        }

        currentSubDirs = new ArrayList<>(dirSet);

        displayEntries = new ArrayList<>();
        if (!currentPath.isEmpty()) {
            displayEntries.add("..");
        }
        displayEntries.addAll(currentSubDirs);
        displayEntries.addAll(fileSet);
    }

    /* ===================== 条目工具方法 ===================== */

    private boolean isDirectoryEntry(String entry) {
        return "..".equals(entry) || currentSubDirs.contains(entry);
    }

    private boolean isNamespaceLevel() {
        return selectedNamespace == null;
    }

    /** 获取条目的显示文本 */
    private String getEntryDisplayText(String entry) {
        if ("..".equals(entry)) {
            return "[..]";
        }
        if (currentSubDirs.contains(entry)) {
            return "[" + entry + "]";
        }
        return entry;
    }

    /* ===================== 预览 ===================== */

    private boolean isImageFile(String resourceId) {
        final String lower = resourceId.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
    }

    private boolean isFontFile(String resourceId) {
        final String lower = resourceId.toLowerCase();
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    private void startPreview(String resourceId) {
        previewResourceId = resourceId;
        previewTexture = null;
        previewMode = true;
    }

    private void closePreview() {
        if (previewTexture != null) {
            try { previewTexture.close(); } catch (Exception ignored) { }
            previewTexture = null;
        }
        previewMode = false;
        previewResourceId = null;
    }

    private void buildPreviewTexture() {
        if (previewResourceId == null || previewTexture != null) return;
        try {
            final ResourceLocation loc = new ResourceLocation(previewResourceId);
            if (isImageFile(previewResourceId)) {
                final BufferedImage img = ResourceUtil.loadImage(loc);
                if (img == null) return;
                int maxW = width - 40, maxH = height - 40;
                int pw = img.getWidth(), ph = img.getHeight();
                if (pw > maxW || ph > maxH) {
                    double scale = Math.min((double) maxW / pw, (double) maxH / ph);
                    pw = (int) (pw * scale); ph = (int) (ph * scale);
                }
                previewTexture = new GraphicsTexture(pw, ph);
                previewTexture.graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                previewTexture.graphics.drawImage(img, 0, 0, pw, ph, null);
            } else if (isFontFile(previewResourceId)) {
                final Font font = ResourceUtil.loadFont(loc);
                if (font == null) return;
                int pw = width - 80, ph = height - 80;
                previewTexture = new GraphicsTexture(pw, ph);
                var g2d = previewTexture.graphics;
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect(0, 0, pw, ph);
                String sampleText = "Hello 你好 こんにちは\nFangSu 方速 Mod\nABCDEFGHIJKLMN\n0123456789";
                float fontSize = Math.max(12, Math.min(48, pw / 12f));
                Font drawFont = font.deriveFont(fontSize);
                g2d.setFont(drawFont);
                g2d.setColor(Color.WHITE);
                FontMetrics fm = g2d.getFontMetrics(drawFont);
                int lineY = fm.getAscent() + 16;
                for (String line : sampleText.split("\n")) {
                    int lineW = fm.stringWidth(line);
                    int lineX = Math.max(4, (pw - lineW) / 2);
                    if (lineX + lineW > pw - 4) lineX = 4;
                    g2d.drawString(line, lineX, lineY);
                    lineY += fm.getHeight() + 8;
                }
            } else { return; }
            if (previewTexture != null) previewTexture.upload();
        } catch (Exception e) { previewTexture = null; }
    }

    private void renderPreview(GraphicContext g) {
        if (!previewMode) return;
        g.fill(0, 0, width, height, 0xCC000000);
        if (previewTexture == null) return;
        int ptW = previewTexture.width, ptH = previewTexture.height;
        int px = (width - ptW) / 2, py = (height - ptH) / 2;
        g.fill(px - 2, py - 2, px + ptW + 2, py + ptH + 2, 0xFFFFFFFF);
        g.blit(previewTexture.identifier, px, py, 0, 0, ptW, ptH, ptW, ptH);
        final Component hint = ComponentHelper.translatable("ui.fangsu.common.preview_close_hint");
        g.drawString(font, hint, (width - font.width(hint.getString())) / 2, height - 16, 0xAAAAAA, false);
    }

    /* ===================== 渲染 ===================== */

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        final GraphicContext g = GraphicContext.of(guiGraphics);

        if (previewMode) {
            buildPreviewTexture();
            renderBackground(guiGraphics);
            renderPreview(g);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        renderBackground(guiGraphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     final GraphicContext g = GraphicContext.of(poseStack);
        //$$     if (previewMode) {
        //$$         buildPreviewTexture();
        //$$         renderBackground(poseStack);
        //$$         renderPreview(g);
        //$$         super.render(poseStack, mouseX, mouseY, partialTick);
        //$$         return;
        //$$     }
        //$$     renderBackground(poseStack);
        //#endif

        // 整体背景
        g.fill(0, 0, width, height, 0xFF1E1E1E);

        // 标题栏
        renderTitleBar(g);

        // 路径面包屑
        renderPathBar(g);

        // 文件列表区
        final int listTop = PATH_BAR_HEIGHT + PATH_BAR_HEIGHT;
        final int listBottom = height - BOTTOM_BAR_HEIGHT;
        if (listBottom > listTop) {
            renderFileList(g, listTop, listBottom, mouseX, mouseY);
        }

        // 底部信息栏 + 按钮
        renderBottomBar(g);

        //#if MC_VERSION >= 12000
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        //#else
        //$$ super.render(poseStack, mouseX, mouseY, partialTick);
        //#endif
    }

    private void renderTitleBar(GraphicContext g) {
        g.fill(0, 0, width, PATH_BAR_HEIGHT, 0xFF2D2D2D);
        g.drawString(font, title, 8, (PATH_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF, false);
    }

    private void renderPathBar(GraphicContext g) {
        final int y = PATH_BAR_HEIGHT;
        g.fill(0, y, width, y + PATH_BAR_HEIGHT, 0xFF333333);

        final String displayPath;
        if (selectedNamespace == null) {
            displayPath = "/";
        } else {
            displayPath = selectedNamespace + ":" + (currentPath.isEmpty() ? "" : currentPath);
        }
        g.drawString(font, displayPath,
                8, y + (PATH_BAR_HEIGHT - font.lineHeight) / 2, 0xFF8BC34A, false);
    }

    private void renderFileList(GraphicContext g, int listTop, int listBottom, int mouseX, int mouseY) {
        final int listHeight = listBottom - listTop;
        final int contentHeight = displayEntries.size() * ITEM_HEIGHT;
        final int maxScroll = Math.max(0, contentHeight - listHeight);

        // 列表背景
        g.fill(0, listTop, width, listBottom, 0xFF252525);

        // 空状态
        if (displayEntries.isEmpty()) {
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.common.empty"), width / 2 - 20, listTop + listHeight / 2 - 4, 0xFF666666, false);
            return;
        }

        // 滚动裁剪
        g.enableScissor(0, listTop, width, listBottom);

        int y = listTop - scrollOffset;
        for (int i = 0; i < displayEntries.size(); i++) {
            final String entry = displayEntries.get(i);
            final boolean isDir = isDirectoryEntry(entry);
            final boolean isSelected = !isDir && selectedResourceIds.contains(entryToResourceId.get(entry));
            final boolean hovered = mouseY >= y && mouseY < y + ITEM_HEIGHT && mouseX >= 0 && mouseX < width - SCROLLBAR_WIDTH;

            renderFileItem(g, entry, i, 0, y, width - SCROLLBAR_WIDTH, ITEM_HEIGHT, isDir, isSelected, hovered);

            y += ITEM_HEIGHT;
        }

        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            final int scrollBarHeight = Math.max(20, listHeight * listHeight / contentHeight);
            final int scrollBarY = listTop + (listHeight - scrollBarHeight) * scrollOffset / maxScroll;
            g.fill(width - SCROLLBAR_WIDTH, listTop, width, listBottom, 0xFF3A3A3A);
            g.fill(width - SCROLLBAR_WIDTH, scrollBarY, width, scrollBarY + scrollBarHeight, 0xFF666666);
        }
    }

    private void renderFileItem(GraphicContext g, String entry, int index, int x, int y, int width, int height,
                                 boolean isDir, boolean selected, boolean hovered) {
        // 背景
        if (selected) {
            g.fill(x, y, x + width, y + height, 0xFF264F78);
        } else if (hovered) {
            g.fill(x, y, x + width, y + height, 0xFF2A2D2E);
        } else if (index % 2 == 1) {
            // 交替行条纹
            g.fill(x, y, x + width, y + height, 0x08FFFFFF);
        }

        // 图标 + 文件名（超出宽度自动截断）
        final String displayText = getEntryDisplayText(entry);
        final int maxTextWidth = width - ITEM_PADDING * 2;
        final String clippedText = font.width(displayText) > maxTextWidth
                ? font.plainSubstrByWidth(displayText, maxTextWidth - 6) + "..."
                : displayText;
        final int textColor;
        if (isDir) {
            textColor = 0xFF8BC34A;
        } else if (selected) {
            textColor = 0xFFFFFFFF;
        } else {
            textColor = 0xFFCCCCCC;
        }

        g.drawString(font, clippedText, x + ITEM_PADDING, y + (height - font.lineHeight) / 2, textColor, false);
    }

    private void renderBottomBar(GraphicContext g) {
        final int y = height - BOTTOM_BAR_HEIGHT;
        g.fill(0, y, width, height, 0xFF2D2D2D);

        // 选中文件信息
        final String infoText = selectedResourceIds.size() + " / " +
                (maxSelect > 0 ? String.valueOf(maxSelect) : "∞") + " selected";
        g.drawString(font, infoText, 8, y + 4, 0xFFAAAAAA, false);

        // 空格预览提示
        if (selectedResourceIds.size() == 1) {
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.common.preview_hint"), 8, y + 14, 0xFF8BC34A, false);
        }

        // 确认按钮
        final int btnW = 60;
        final int btnH = 16;
        final int btnY = y + (BOTTOM_BAR_HEIGHT - btnH) / 2;

        final int confirmX = width - btnW * 2 - 12;
        final int cancelX = width - btnW - 6;

        drawButton(g, confirmX, btnY, btnW, btnH, ComponentHelper.translatable("ui.fangsu.block.confirm").getString(), 0xFF4CAF50, !selectedResourceIds.isEmpty());
        drawButton(g, cancelX, btnY, btnW, btnH, ComponentHelper.translatable("ui.fangsu.block.cancel").getString(), 0xFF757575, true);
    }

    private void drawButton(GraphicContext g, int x, int y, int w, int h, String text, int color, boolean enabled) {
        g.fill(x, y, x + w, y + h, enabled ? color : 0xFF555555);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF, false);
    }

    /* ===================== 键盘事件 ===================== */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) {
            if (previewMode) {
                closePreview();
                return true;
            }
            if (selectedResourceIds.size() == 1) {
                String resourceId = selectedResourceIds.iterator().next();
                if (isImageFile(resourceId) || isFontFile(resourceId)) {
                    startPreview(resourceId);
                    return true;
                }
            }
        }
        if (previewMode && keyCode == 256) {
            closePreview();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /* ===================== 鼠标事件 ===================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (previewMode) {
            closePreview();
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        final int listTop = PATH_BAR_HEIGHT + PATH_BAR_HEIGHT;
        final int listBottom = height - BOTTOM_BAR_HEIGHT;

        // 点击路径栏 — 返回上级（根目录时回到命名空间选择）
        if (mouseY >= PATH_BAR_HEIGHT && mouseY < PATH_BAR_HEIGHT + PATH_BAR_HEIGHT) {
            navigateUp();
            return true;
        }

        // 滚轮条点击拖拽
        if (mouseY >= listTop && mouseY < listBottom) {
            if (mouseX >= width - SCROLLBAR_WIDTH) {
                final int listHeight = listBottom - listTop;
                final int contentHeight = displayEntries.size() * ITEM_HEIGHT;
                final int maxScroll = Math.max(0, contentHeight - listHeight);
                if (maxScroll > 0) {
                    final int scrollBarHeight = Math.max(20, listHeight * listHeight / contentHeight);
                    final int scrollBarY = listTop + (listHeight - scrollBarHeight) * scrollOffset / maxScroll;
                    if (mouseY >= scrollBarY && mouseY < scrollBarY + scrollBarHeight) {
                        scrollbarDragging = true;
                        scrollbarDragStartY = (int) mouseY;
                        scrollbarDragStartOffset = scrollOffset;
                    } else {
                        double ratio = (mouseY - listTop - scrollBarHeight / 2.0) / (listHeight - scrollBarHeight);
                        scrollOffset = (int) Math.max(0, Math.min(maxScroll, ratio * maxScroll));
                    }
                }
                return true;
            }

            final int clickIndex = ((int) mouseY - listTop + scrollOffset) / ITEM_HEIGHT;
            if (clickIndex >= 0 && clickIndex < displayEntries.size()) {
                handleEntryClick(clickIndex);
            }
            return true;
        }

        // 底部按钮
        if (mouseY >= height - BOTTOM_BAR_HEIGHT) {
            final int btnW = 60;
            final int btnH = 16;
            final int btnY = height - BOTTOM_BAR_HEIGHT + (BOTTOM_BAR_HEIGHT - btnH) / 2;

            final int confirmX = width - btnW * 2 - 12;
            final int cancelX = width - btnW - 6;

            if (mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                onConfirm();
                return true;
            }
            if (mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                onCancel();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleEntryClick(int index) {
        final String entry = displayEntries.get(index);

        if ("..".equals(entry)) {
            navigateUp();
            return;
        }

        // 命名空间选择层：点击选中命名空间
        if (selectedNamespace == null) {
            selectedNamespace = entry;
            selectedResourceIds.clear();
            scrollOffset = 0;
            refreshCurrentDirectory();
            return;
        }

        if (currentSubDirs.contains(entry)) {
            // 进入子文件夹
            final String newPath = currentPath.isEmpty() ? entry : currentPath + "/" + entry;
            navigateTo(newPath);
            return;
        }

        // 文件 — 通过完整资源ID切换选中状态
        final String resourceId = entryToResourceId.get(entry);
        if (resourceId == null) return;

        if (selectedResourceIds.contains(resourceId)) {
            selectedResourceIds.remove(resourceId);
        } else {
            // maxSelect=1 时自动切换（清除旧选中，选中新文件）
            if (maxSelect == 1) {
                selectedResourceIds.clear();
            }
            if (maxSelect <= 0 || selectedResourceIds.size() < maxSelect) {
                selectedResourceIds.add(resourceId);
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (scrollbarDragging) {
            final int listTop = PATH_BAR_HEIGHT + PATH_BAR_HEIGHT;
            final int listBottom = height - BOTTOM_BAR_HEIGHT;
            final int listHeight = listBottom - listTop;
            final int contentHeight = displayEntries.size() * ITEM_HEIGHT;
            final int maxScroll = Math.max(0, contentHeight - listHeight);
            if (maxScroll > 0) {
                final int scrollBarHeight = Math.max(20, listHeight * listHeight / contentHeight);
                double deltaY = mouseY - scrollbarDragStartY;
                double ratio = deltaY / (listHeight - scrollBarHeight);
                scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollbarDragStartOffset + ratio * maxScroll));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final int listTop = PATH_BAR_HEIGHT + PATH_BAR_HEIGHT;
        final int listBottom = height - BOTTOM_BAR_HEIGHT;
        final int listHeight = listBottom - listTop;
        final int contentHeight = displayEntries.size() * ITEM_HEIGHT;
        final int maxScroll = Math.max(0, contentHeight - listHeight);

        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * ITEM_HEIGHT));
        return true;
    }

    /* ===================== 按钮操作 ===================== */

    private void onConfirm() {
        final List<ResourceLocation> result = new ArrayList<>();
        for (final String resourceId : selectedResourceIds) {
            result.add(new ResourceLocation(resourceId));
        }
        callback.accept(result);
        Minecraft.getInstance().setScreen(parent);
    }

    private void onCancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        closePreview();
        Minecraft.getInstance().setScreen(parent);
    }
}
