package com.fangsu.mtr;

import com.fangsu.render.sowcer.math.Vector3f;
import net.minecraft.core.BlockPos;

public abstract class LocalAreaBase {
    public final long id;
    public String name;
    public int color;

    public int x1, x2, z1, z2;

    public LocalAreaBase(long id, String name, int color, int x1, int x2, int z1, int z2) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.x1 = x1;
        this.x2 = x2;
        this.z1 = z1;
        this.z2 = z2;
    }

    public boolean inArea(int x, int z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    public BlockPos getCenter() {
        return new BlockPos((x1 + x2) / 2, 0, (z1 + z2) / 2);
    }

    public Vector3f getCenterVector() {
        return new Vector3f((x1 + x2) / 2f, 0, (z1 + z2) / 2f);
    }
}
