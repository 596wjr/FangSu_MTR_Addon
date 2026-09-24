package com.fangsu.modular;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 一个"容器槽"。一个 ModularBlock 可挂多个命名的容器槽，每个槽承载一条竖直的积木子序列
 * （用于实现 Scratch 的 C 形包裹结构，如 "当 ... / 重复 ..." 的内层）。槽内每块仍可有自己的子槽，递归嵌套。
 */
public final class ContainerObject {
    private final String name;
    private final List<ModularBlock> blockList;

    public ContainerObject(String name) {
        this.name = name;
        this.blockList = new ArrayList<>();
    }

    public ContainerObject(String name, List<ModularBlock> blockList) {
        this.name = name;
        this.blockList = new ArrayList<>(blockList);
    }

    public boolean add(ModularBlock block) {
        return blockList.add(block);
    }

    public void add(int index, ModularBlock block) {
        blockList.add(index, block);
    }

    public ModularBlock remove(int index) {
        return blockList.remove(index);
    }

    public boolean remove(ModularBlock block) {
        return blockList.remove(block);
    }

    public ModularBlock get(int index) {
        return blockList.get(index);
    }

    public int indexOf(ModularBlock block) {
        return blockList.indexOf(block);
    }

    public int size() {
        return blockList.size();
    }

    public boolean isEmpty() {
        return blockList.isEmpty();
    }

    public String getName() {
        return name;
    }

    public List<ModularBlock> getBlockList() {
        return Collections.unmodifiableList(blockList);
    }

    /** 可变内部列表，仅供编辑器/代码编解码器在受控场合使用 */
    public List<ModularBlock> mutableList() {
        return blockList;
    }

    public ContainerObject copy() {
        ContainerObject c = new ContainerObject(name);
        for (ModularBlock b : blockList) {
            c.blockList.add(b.copy());
        }
        return c;
    }
}
