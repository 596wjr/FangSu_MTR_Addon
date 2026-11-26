package com.fangsu.train;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** 与 MTR3 车辆部件和 MTR4 旧车转换共用相同的筛选优先级。 */
final class LcdCarFilter {
    private final List<Rule> whitelist;
    private final List<Rule> blacklist;
    private final int whitelistDefault;
    private final int blacklistDefault;

    LcdCarFilter(JsonObject slot) {
        final String[] white = read(slot, "whitelisted_cars");
        final String[] black = read(slot, "blacklisted_cars");
        whitelist = parse(white);
        blacklist = parse(black);
        whitelistDefault = white.length == 0 ? 1 : 0;
        blacklistDefault = black.length == 0 ? 1 : 0;
    }

    boolean matches(int car, int totalCars) {
        // 具体车号优先于周期表达式，匹配强度相同则显示。
        return strength(whitelist, whitelistDefault, car, totalCars)
                >= strength(blacklist, blacklistDefault, car, totalCars);
    }

    private static String[] read(JsonObject slot, String key) {
        final JsonElement value = slot.get(key);
        return (value != null && value.isJsonPrimitive() ? value.getAsString() : "").split(",");
    }

    private static List<Rule> parse(String[] expressions) {
        final List<Rule> rules = new ArrayList<>();
        for (String expression : expressions) {
            try {
                if (expression.contains("%")) {
                    final String[] pieces = expression.split("\\+");
                    final int period = Integer.parseInt(pieces[0].replace("%", ""));
                    final int offset = pieces.length == 1 ? 0 : Integer.parseInt(pieces[1]);
                    if (period != 0) rules.add(new Rule(0, period, offset));
                } else if (!expression.isEmpty()) {
                    rules.add(new Rule(Integer.parseInt(expression), 0, 0));
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                // MTR 忽略无法解析的表达式。
            }
        }
        return List.copyOf(rules);
    }

    private static int strength(List<Rule> rules, int fallback, int car, int totalCars) {
        int strength = fallback;
        for (Rule rule : rules) {
            if (rule.period() == 0) {
                if (rule.car() == car + 1 || rule.car() == car - totalCars) return 3;
            } else if ((car + 1 + rule.offset()) % rule.period() == 0) {
                strength = 2;
            }
        }
        return strength;
    }

    private record Rule(int car, int period, int offset) {
    }
}
