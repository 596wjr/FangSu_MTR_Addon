package com.fangsu.mtr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 按唯一的车站顺序将两个方向并到一张图，支线保留独立站序。 */
public final class RouteOverview {
    private RouteOverview() {}

    public record Stop(long stationId, String name, RouteStopStatus status, java.util.UUID id, java.util.UUID parentStop) {}
    public record Line(long id, long parentId, boolean express, String name, int color, List<Stop> stops) {}
    public record Row(Stop up, Stop down) {}
    public record Diagram(Line up, Line down, Line upExpress, Line downExpress, List<Row> rows) {
        public static boolean stops(Line express, Stop parentStop) {
            return express != null && parentStop != null && parentStop.id() != null
                    && express.stops().stream().anyMatch(stop -> parentStop.id().equals(stop.parentStop())
                    && stop.status() == RouteStopStatus.NORMAL);
        }
    }

    public static List<Diagram> build(List<Line> lines) {
        final var remaining = new ArrayList<>(lines.stream().filter(line -> !line.express()).toList());
        final var expresses = new java.util.HashMap<Long, Line>();
        lines.stream().filter(Line::express).forEach(line -> expresses.put(line.parentId(), line));
        final var diagrams = new ArrayList<Diagram>();
        while (!remaining.isEmpty()) {
            final Line first = remaining.remove(0);
            boolean paired = false;
            for (int index = 0; index < remaining.size(); index++) {
                final Line other = remaining.get(index);
                final int firstDirection = direction(first.name());
                final int secondDirection = direction(other.name());
                if (firstDirection == 0 || firstDirection != -secondDirection) {
                    continue;
                }
                final Line up = firstDirection > 0 ? first : other;
                final Line down = firstDirection < 0 ? first : other;
                final var reversed = new ArrayList<>(down.stops());
                Collections.reverse(reversed);
                final var rows = align(up.stops(), reversed);
                if (rows != null) {
                    diagrams.add(new Diagram(up, down, expresses.get(up.id()), expresses.get(down.id()), rows));
                    remaining.remove(index);
                    paired = true;
                    break;
                }
            }
            if (!paired) {
                diagrams.add(new Diagram(first, null, expresses.get(first.id()), null,
                        first.stops().stream().map(stop -> new Row(stop, null)).toList()));
            }
        }
        return diagrams;
    }

    private static int direction(String name) {
        int direction = 0;
        for (final String part : name.split("\\|")) {
            final int next = switch (part.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "UP", "上行" -> 1;
                case "DOWN", "下行" -> -1;
                default -> 0;
            };
            if (next != 0) {
                if (direction == -next) {
                    return 0;
                }
                direction = next;
            }
        }
        return direction;
    }

    private static List<Row> align(List<Stop> up, List<Stop> down) {
        final boolean upIsLonger = up.size() >= down.size();
        final List<Stop> longer = upIsLonger ? up : down;
        final List<Stop> shorter = upIsLonger ? down : up;
        final var earliest = match(longer, shorter, false);
        if (earliest == null || !earliest.equals(match(longer, shorter, true))) {
            return null;
        }
        final var rows = new ArrayList<Row>();
        int shortIndex = 0;
        for (int index = 0; index < longer.size(); index++) {
            final Stop primary = longer.get(index);
            final Stop secondary = shortIndex < shorter.size() && earliest.get(shortIndex) == index
                    ? shorter.get(shortIndex++) : null;
            rows.add(upIsLonger ? new Row(primary, secondary) : new Row(secondary, primary));
        }
        return rows;
    }

    private static List<Integer> match(List<Stop> longer, List<Stop> shorter, boolean backwards) {
        final int step = backwards ? -1 : 1;
        final var matches = new ArrayList<Integer>();
        int index = backwards ? longer.size() - 1 : 0;
        for (int stepIndex = 0; stepIndex < shorter.size(); stepIndex++) {
            final long station = shorter.get(backwards ? shorter.size() - 1 - stepIndex : stepIndex).stationId();
            if (station == 0) {
                return null;
            }
            while (index >= 0 && index < longer.size() && longer.get(index).stationId() != station) {
                index += step;
            }
            if (index < 0 || index >= longer.size()) {
                return null;
            }
            matches.add(index);
            index += step;
        }
        if (backwards) {
            Collections.reverse(matches);
        }
        return matches;
    }
}
