package com.fangsu.mtr;

import java.awt.*;
import java.util.Objects;

public class ColorNameTuple {

    public final Color color;
    public final String name;
    public final Color routeColor;
    public final String routeName;

    public ColorNameTuple(int color, String name) {
        this.color = new Color(color);
        this.name = name;
        this.routeColor = this.color;
        this.routeName = name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ColorNameTuple o1) {
            return o1.color == color && o1.name.equals(name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, color);
    }
}