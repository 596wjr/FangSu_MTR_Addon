package com.fangsu.mtr;

import com.fangsu.scripting.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public final class DashboardRouteGroups {
    private DashboardRouteGroups() {
    }

    public record Route<T>(T value, long id, String name, int color) {
        public String groupName() {
            return TextUtil.getNonExtraParts(name).trim();
        }

        public String branchName() {
            return TextUtil.getExtraParts(name);
        }

        public String label() {
            final String branch = branchName();
            if (branch.isEmpty()) {
                return groupName();
            }
            final int separator = branch.indexOf("||");
            return separator < 0 ? branch : branch.substring(separator + 2);
        }

        public long parentId() {
            final var relation = RouteServices.relation((mtr.data.Route) value);
            return relation == null ? 0 : relation.parentRoute();
        }
    }

    public record Row<T>(String label, int color, int depth, int groupIndex, List<Route<T>> routes, Route<T> individual) {}

    public static <T> List<Row<T>> rows(List<Route<T>> routes, String search) {
        final var groups = new TreeMap<String, List<Route<T>>>();
        final var byId = new HashMap<Long, Route<T>>();
        for (final var route : routes) byId.put(route.id(), route);
        for (final var route : routes) {
            final var parent = byId.get(route.parentId());
            final String group = parent == null ? route.groupName() : parent.groupName();
            groups.computeIfAbsent(group, name -> new ArrayList<>()).add(route);
        }
        final String query = search.toLowerCase(Locale.ROOT).trim();
        final var result = new ArrayList<Row<T>>();
        int groupIndex = 0;
        for (final var entry : groups.entrySet()) {
            final var members = entry.getValue();
            members.sort(Comparator.comparing((Route<T> route) -> route.label().toLowerCase(Locale.ROOT)).thenComparingLong(Route::id));
            final boolean matchesMain = entry.getKey().toLowerCase(Locale.ROOT).contains(query);
            final var matched = members.stream().filter(route -> matchesMain || route.name().toLowerCase(Locale.ROOT).contains(query)).toList();
            if (matched.isEmpty()) {
                continue;
            }

            final int visibleGroup = groupIndex++;
            final var header = members.stream().filter(route -> route.parentId() == 0).findFirst().orElse(members.get(0));
            if (members.size() == 1 && header.branchName().isEmpty()) {
                result.add(new Row<>(header.label(), header.color(), 0, visibleGroup, List.of(header), header));
                continue;
            }
            final var groupMembers = List.copyOf(members);
            result.add(new Row<>(entry.getKey(), header.color(), 0, visibleGroup, groupMembers, null));
            final var parents = members.stream().filter(route -> route.parentId() == 0
                    || members.stream().noneMatch(parent -> parent.id() == route.parentId())).toList();
            final var visible = parents.stream().filter(parent -> matched.contains(parent)
                    || matched.stream().anyMatch(child -> child.parentId() == parent.id())).toList();
            for (int index = 0; index < visible.size(); index++) {
                final var route = visible.get(index);
                result.add(new Row<>(route.label(), route.color(), 1, visibleGroup, groupMembers, route));
                final var children = members.stream().filter(child -> child.parentId() == route.id()
                        && (matched.contains(route) || matched.contains(child))).toList();
                for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                    final var child = children.get(childIndex);
                    result.add(new Row<>(child.label(), child.color(), 2, visibleGroup, groupMembers, child));
                }
            }
        }
        return result;
    }
}
