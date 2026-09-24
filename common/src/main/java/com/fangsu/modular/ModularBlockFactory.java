package com.fangsu.modular;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 图形化积木注册中心。每种积木类型注册一个"模板"实例（含默认参数与空容器），
 * 编辑器复制模板以生成新积木；序列化统一由 {@link ModularCodec} 负责（注册方无需手写 JSON 解析）。
 * <p>
 * 另维护积木"分组目录"（family）：左侧最外层选择列展示的是有序的积木组，每组有名称与主色。
 */
public final class ModularBlockFactory {

    private static final ModularBlockFactory INSTANCE = new ModularBlockFactory();

    public static ModularBlockFactory getInstance() {
        return INSTANCE;
    }

    /**
     * family 的注册顺序与元信息
     */
    public static final class FamilyInfo {
        private final String id;
        private final String nameKey;
        private final Color color;
        private final List<String> blockTypes = new ArrayList<>();

        public FamilyInfo(String id, String nameKey, Color color) {
            this.id = id;
            this.nameKey = nameKey;
            this.color = color;
        }

        public String getId() {
            return id;
        }

        public String getNameKey() {
            return nameKey;
        }

        public Color getColor() {
            return color;
        }

        public List<String> getBlockTypes() {
            return Collections.unmodifiableList(blockTypes);
        }

        void addBlockType(String type) {
            if (!blockTypes.contains(type)) blockTypes.add(type);
        }
    }

    private final LinkedHashMap<String, FamilyInfo> familyMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, ModularBlock> defaultBlockMap = new LinkedHashMap<>();

    private ModularBlockFactory() {
    }

    // ---------------- 分组目录 ----------------

    /**
     * 注册积木组（左侧列）。nameKey 为文案翻译键。
     */
    public FamilyInfo registerFamily(String id, String nameKey, Color color) {
        if (!familyMap.containsKey(id)) {
            FamilyInfo info = new FamilyInfo(id, nameKey, color);
            familyMap.put(id, info);
            return info;
        }
        return familyMap.get(id);
    }

    /**
     * 按注册顺序返回分组目录（用于左侧最外层选择列）
     */
    public List<FamilyInfo> getFamilies() {
        return new ArrayList<>(familyMap.values());
    }

    public FamilyInfo getFamily(String id) {
        return familyMap.get(id);
    }

    // ---------------- 积木注册 ----------------

    /**
     * 注册积木模板。block 的 families 应引用已注册的分组 id；
     * 模板即默认实例（新积木由 template.copy() 生成）。
     */
    public void register(ModularBlock block) {
        String type = block.getType();
        defaultBlockMap.put(type, block);
        for (String f : block.getFamilies()) {
            FamilyInfo info = familyMap.get(f);
            if (info != null) info.addBlockType(type);
        }
    }

    /**
     * 注册积木模板并显式放入某分组（block.families 为空时也能归组）
     */
    public void register(String familyId, ModularBlock block) {
        register(block);
        FamilyInfo info = familyMap.get(familyId);
        if (info != null) info.addBlockType(block.getType());
    }

    public Set<String> getKeys() {
        return defaultBlockMap.keySet();
    }

    public boolean contains(String type) {
        return defaultBlockMap.containsKey(type);
    }

    /**
     * 某分组的积木类型集合
     */
    public List<String> getBlockTypesOf(String familyId) {
        FamilyInfo info = familyMap.get(familyId);
        return info == null ? List.of() : info.getBlockTypes();
    }

    public ModularBlock getDefaultBlock(String type) {
        return defaultBlockMap.get(type);
    }

    /**
     * 生成一块新的同类型积木（复制模板）
     */
    public ModularBlock createBlock(String type) {
        ModularBlock t = defaultBlockMap.get(type);
        return t == null ? null : t.copy();
    }
}
