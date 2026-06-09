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
            return color.equals(o1.color) && name.equals(o1.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, name);
    }

    @Override
    public String toString() {
        return "ColorNameTuple["
                + "color=" + color + ","
                + "name=" + name + "]";
    }
}