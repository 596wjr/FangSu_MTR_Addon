package com.fangsu.scripting;

import java.awt.*;

public class G2dTextHelper {
    public static int getMultiLinesWidth(Graphics2D g, Font font, int h, String... lines) {
        int width = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * 0.9 / (lines.length + 2) * (i == 0 ? 3 : 1));
            g.setFont(font.deriveFont(Font.PLAIN, fontSize));
            width = Math.max(width, g.getFontMetrics().stringWidth(line));
        }
        return width;
    }

    public static int drawStrMultiLines(Graphics2D g, Font font, int x, int y, int h, int align, String... lines) {
        int width = getMultiLinesWidth(g, font, h, lines);
        int currentY = (int) (y - h * 0.095);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * 0.9 / (lines.length + 2) * (i == 0 ? 3 : 1));
            currentY += fontSize;
            g.setFont(font.deriveFont(Font.PLAIN, fontSize));
            int lineWidth = g.getFontMetrics().stringWidth(line);
            int baseX = align == 0 ? x :
                    align == 1 ? x + width / 2 - lineWidth / 2 :
                            x + width - lineWidth;
            g.drawString(line, baseX, currentY);
        }
        return width;
    }
}
