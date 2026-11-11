package com.fangsu.platform;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** 每块吊板的内容设置；未单独指定时沿用已保存的门组默认值。 */
public record PlatformDoorBoardContent(String drawing, String routes) {
    public static final String DEFAULT_DRAWING = "fangsu:diaoban/mtr_diaoban_station.js";

    public PlatformDoorBoardContent {
        if (!validDrawing(drawing)) throw new IllegalArgumentException("invalid_board_drawing");
        countRoutes(routes);
    }

    public static PlatformDoorBoardContent resolve(Map<String, String> options, String drawing, String routes) {
        String selectedDrawing = options.getOrDefault("drawScript", drawing);
        String selectedRoutes = options.getOrDefault("routes", routes);
        if (!validDrawing(selectedDrawing)) selectedDrawing = DEFAULT_DRAWING;
        try {
            return new PlatformDoorBoardContent(selectedDrawing, selectedRoutes);
        } catch (IllegalArgumentException e) {
            return new PlatformDoorBoardContent(selectedDrawing, "[]");
        }
    }

    public static Map<String, String> withDrawing(Map<String, String> options, String drawing) {
        var next = new LinkedHashMap<>(options);
        next.put("drawScript", drawing);
        return PlatformDoorSettings.checkOptions(next);
    }

    public static Map<String, String> withRoutes(Map<String, String> options, String routes) {
        var next = new LinkedHashMap<>(options);
        next.put("routes", routes);
        return PlatformDoorSettings.checkOptions(next);
    }

    public int routeCount() { return countRoutes(routes); }

    private static boolean validDrawing(String drawing) {
        return drawing != null && drawing.length() <= 256 && !drawing.contains("..") && drawing.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static int countRoutes(String routes) {
        if (routes == null || routes.length() > 4096) throw new IllegalArgumentException("invalid_board_routes");
        try {
            var rows = JsonParser.parseString(routes).getAsJsonArray();
            if (rows.size() > 16) throw new IllegalArgumentException("too_many_board_routes");
            for (var row : rows) {
                var pair = row.getAsJsonArray();
                if (pair.size() != 2) throw new IllegalArgumentException("invalid_board_route_pair");
                for (var value : pair) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid_board_route_id");
                    value.getAsBigDecimal().longValueExact();
                }
            }
            return rows.size();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid_board_routes", e);
        }
    }
}
