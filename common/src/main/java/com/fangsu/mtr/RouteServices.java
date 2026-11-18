package com.fangsu.mtr;

import mtr.data.Route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import java.util.function.BiPredicate;

public final class RouteServices {
    private RouteServices() {}

    public static ServiceStop relation(Route route) {
        for (final var stop : route.platformIds) {
            final var metadata = ServiceStop.get(stop);
            if (metadata != null && metadata.parentStop() != null) {
                return metadata;
            }
        }
        return null;
    }

    public static long platformId(Route.RoutePlatform stop) {
        return stop.platformId;
    }

    public static boolean hasRunningSection(Route parent, Route child) {
        final var relation = relation(child);
        if (relation == null) {
            return true;
        }
        if (parent == null || parent.platformIds.size() < 2) {
            return false;
        }
        if (relation.express()) {
            return true;
        }

        final var inherited = new HashSet<UUID>();
        for (final var stop : child.platformIds) {
            final var metadata = ServiceStop.get(stop);
            if (metadata != null && metadata.parentStop() != null) {
                inherited.add(metadata.parentStop());
            }
        }
        int matches = 0;
        for (final var stop : parent.platformIds) {
            final var metadata = ServiceStop.get(stop);
            if (metadata != null && inherited.remove(metadata.id()) && ++matches == 2) {
                return true;
            }
        }
        return false;
    }

    /** 旧线路必须是同方向的站序子序列；重复经过同一站且无法定位时不自动导入。 */
    public static int[] matchImport(Route parent, Route child, LongUnaryOperator stationForPlatform) {
        final var parentStops = parent.platformIds;
        final var childStops = child.platformIds;
        if (parent == child || relation(parent) != null || childStops.size() < 2 || childStops.size() > parentStops.size()) {
            return null;
        }
        final int[] forward = matchImportStops(parentStops, childStops, stationForPlatform, 1);
        final int[] backward = matchImportStops(parentStops, childStops, stationForPlatform, -1);
        return forward != null && java.util.Arrays.equals(forward, backward) ? forward : null;
    }

    private static int[] matchImportStops(List<Route.RoutePlatform> parent, List<Route.RoutePlatform> child,
                                          LongUnaryOperator stationForPlatform, int step) {
        final int[] result = new int[child.size()];
        int parentIndex = step > 0 ? 0 : parent.size() - 1;
        for (int i = step > 0 ? 0 : child.size() - 1; i >= 0 && i < child.size(); i += step) {
            final long id = platformId(child.get(i));
            final long station = stationForPlatform.applyAsLong(id);
            if (id == 0) return null;
            while (parentIndex >= 0 && parentIndex < parent.size()) {
                final long candidate = platformId(parent.get(parentIndex));
                if (candidate == id || (station != 0 && station == stationForPlatform.applyAsLong(candidate))) break;
                parentIndex += step;
            }
            if (parentIndex < 0 || parentIndex >= parent.size()) return null;
            result[i] = parentIndex;
            parentIndex += step;
        }
        return result;
    }

    public static boolean importService(Route parent, Route child, boolean express, LongUnaryOperator stationForPlatform) {
        if (relation(child) != null) return false;
        final int[] match = matchImport(parent, child, stationForPlatform);
        if (match == null) return false;
        final var parentStops = parent.platformIds;
        final var childStops = child.platformIds;
        final int first = express ? 0 : match[0];
        final int last = express ? parentStops.size() - 1 : match[match.length - 1];
        final var imported = new ArrayList<Route.RoutePlatform>();
        int childIndex = 0;
        for (int index = first; index <= last; index++) {
            final var source = parentStops.get(index);
            final var old = childIndex < match.length && index == match[childIndex] ? childStops.get(childIndex++) : null;
            final long platform = platformId(old == null ? source : old);
            final var entry = new Route.RoutePlatform(platform);
            if (old != null) entry.customDestination = old.customDestination;
            final var oldStatus = old == null ? RouteStopStatus.DISABLED : RouteStopStatus.of(old);
            final var oldMetadata = old == null ? null : ServiceStop.get(old);
            final var bypass = express && oldStatus == RouteStopStatus.SKIPPED && oldMetadata != null ? oldMetadata.bypass() : null;
            final var status = bypass == null && oldStatus == RouteStopStatus.SKIPPED ? RouteStopStatus.DISABLED : oldStatus;
            final var link = ServiceStop.ensure(source).child(parent.id, express);
            final var identity = new ServiceStop(oldMetadata == null ? link.id() : oldMetadata.id(),
                    link.parentRoute(), link.parentStop(), express, platform != platformId(source),
                    status == RouteStopStatus.SKIPPED ? RouteStopStatus.DISABLED : status, bypass);
            ServiceStop.set(entry, identity);
            RouteStopStatus.set(entry, status);
            imported.add(entry);
        }
        childStops.clear();
        childStops.addAll(imported);
        return true;
    }

    /** 母线条目决定顺序，子线各自保留目标站台与停靠覆盖。 */
    public static boolean synchronize(Route parent, Route child, LongUnaryOperator stationForPlatform,
                                      BiPredicate<Long, ServiceStop.Bypass> validBypass) {
        final var relation = relation(child);
        final var parentStops = parent.platformIds;
        final var childStops = child.platformIds;
        final var previous = new HashMap<UUID, Route.RoutePlatform>();
        for (final var stop : childStops) {
            final var metadata = ServiceStop.get(stop);
            if (metadata != null && metadata.parentStop() != null) {
                previous.put(metadata.parentStop(), stop);
            }
        }

        int first = 0;
        int last = parentStops.size() - 1;
        if (!relation.express()) {
            first = 0;
            while (first < parentStops.size() && !previous.containsKey(ServiceStop.ensure(parentStops.get(first)).id())) {
                first++;
            }
            while (last >= first && !previous.containsKey(ServiceStop.ensure(parentStops.get(last)).id())) {
                last--;
            }
            if (last <= first) {
                return invalidateChild(childStops);
            }
        }
        if (parentStops.size() < 2) {
            return invalidateChild(childStops);
        }

        final List<Route.RoutePlatform> updated = new ArrayList<>();
        for (int index = first; index <= last; index++) {
            final var parentStop = parentStops.get(index);
            final var parentMetadata = ServiceStop.ensure(parentStop);
            final var old = previous.get(parentMetadata.id());
            final var metadata = old == null ? parentMetadata.child(relation.parentRoute(), relation.express()) : ServiceStop.get(old);
            final long nativePlatform = platformId(parentStop);
            final long stationId = stationForPlatform.applyAsLong(nativePlatform);
            final boolean keepPlatform = old != null && metadata.platformOverride()
                    && stationId != 0 && stationId == stationForPlatform.applyAsLong(platformId(old));
            final boolean sameStation = old != null && stationId == stationForPlatform.applyAsLong(platformId(old));
            final long platform = keepPlatform ? platformId(old) : nativePlatform;
            final var bypass = sameStation && relation.express() ? metadata.bypass() : null;
            final var acceptedBypass = bypass != null && validBypass.test(nativePlatform, bypass) ? bypass : null;
            final var status = acceptedBypass != null ? RouteStopStatus.SKIPPED : metadata.statusOverride() == null
                    ? RouteStopStatus.of(parentStop) : metadata.statusOverride();
            final String destination = old == null ? parentStop.customDestination : old.customDestination;
            final var next = metadata.override(keepPlatform, metadata.statusOverride(), acceptedBypass);
            if (old != null && platformId(old) == platform && status == RouteStopStatus.of(old)
                    && next.equals(ServiceStop.get(old)) && destination.equals(old.customDestination)) {
                updated.add(old);
            } else {
                final var entry = new Route.RoutePlatform(platform);
                entry.customDestination = destination;

                ServiceStop.set(entry, next);
                RouteStopStatus.set(entry, status);
                updated.add(entry);
            }
        }
        if (childStops.equals(updated)) {
            return false;
        }
        childStops.clear();
        childStops.addAll(updated);
        return true;
    }

    private static boolean invalidateChild(List<Route.RoutePlatform> childStops) {
        // 保留原区间身份，仪表盘仍可打开并重新选段。
        boolean changed = false;
        for (final var stop : childStops) {
            if (RouteStopStatus.of(stop) != RouteStopStatus.DISABLED) {
                RouteStopStatus.set(stop, RouteStopStatus.DISABLED);
                changed = true;
            }
        }
        return changed;
    }
}
