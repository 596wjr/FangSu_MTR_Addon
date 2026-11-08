package com.fangsu.drawing.sis;

import com.fangsu.blockEntities.BlockEntitySis;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.TextUtil;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;

public final class MtrSisDrawing extends BaseSisDrawing {
    public static final String KEY = "fangsu:sis/mtr_station_info";
    private static final Color NAVY = new Color(0x10263D);
    private static final Color DEFAULT_ACCENT = new Color(0xA71930);

    @Override
    public void draw(GraphicsTexture gt, Map<String, Object> drawState,
                     int arrowDirection, int texW, int texH, BlockEntitySis.SISDrawInfo drawInfo) {
        Graphics2D g = (Graphics2D) gt.graphics.create();
        try {
            g.scale(texW / 384.0, texH / 640.0);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Font font = ResourceUtil.loadFont(new ResourceLocation("fangsu:fonts/source-han-sans-bold.otf"));
            boolean hasStation = drawInfo.station.getRaw() != null && !"?".equals(drawInfo.station.name);
            String name = hasStation ? drawInfo.station.name : "請選擇車站|Select a station";
            String cjk = TextUtil.getCjkParts(name);
            String other = TextUtil.getNonCjkParts(name);

            g.setColor(new Color(0xF4F5F2));
            g.fillRect(0, 0, 384, 640);
            g.setColor(NAVY);
            g.fillRect(0, 0, 384, 144);
            g.fillRect(0, 596, 384, 44);

            g.setColor(Color.WHITE);
            drawLabel(g, font, "車站資訊", 65, 37);
            drawLabel(g, font, "Station information", 112, 22);

            g.setColor(NAVY);
            if (cjk.isEmpty() || other.isEmpty()) {
                drawLabel(g, font, cjk.isEmpty() ? other : cjk, 388, cjk.isEmpty() ? 52 : 76);
            } else {
                drawLabel(g, font, cjk, 353, 76);
                drawLabel(g, font, other, 425, 38);
            }

            g.setColor(hasStation ? new Color(drawInfo.station.color) : DEFAULT_ACCENT);
            g.fillRect(28, 548, 328, 16);
        } finally {
            g.dispose();
        }
        gt.upload();
    }

    private static void drawLabel(Graphics2D g, Font baseFont, String text, int baseline, int size) {
        Font font = baseFont.deriveFont((float) size);
        int width = g.getFontMetrics(font).stringWidth(text);
        if (width > 328) {
            font = font.deriveFont(size * (328f / width));
        }
        g.setFont(font);
        g.drawString(text, 28, baseline);
    }
}
