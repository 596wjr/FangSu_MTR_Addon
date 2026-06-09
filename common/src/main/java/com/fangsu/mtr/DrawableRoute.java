package com.fangsu.mtr;

import com.fangsu.utils.MtrUtil;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DrawableRoute {
    public final String routeName;
    public final Color routeColor;
    private final List<DrawableRouteStationInfo> stations;
    public final LocalRoute.CircularState circularState;

    // 运行区间在最终列表中的索引范围（包含两端）
    public final int beginIndexInclusive;
    private final int endIndexInclusive;

    private static final Map<Long, DrawableRoute> cacheRequestLongestRoute = Collections.synchronizedMap(new HashMap<>());

    /**
     * 清除缓存。在重进存档/世界重载时应调用此方法。
     */
    public static void clearCache() {
        cacheRequestLongestRoute.clear();
    }

    public DrawableRoute(String routeName, Color routeColor, List<DrawableRouteStationInfo> stations,
                         LocalRoute.CircularState circularState, int beginIndexInclusive, int endIndexInclusive) {
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.stations = stations;
        this.circularState = circularState;
        this.beginIndexInclusive = beginIndexInclusive;
        this.endIndexInclusive = endIndexInclusive;
    }

    /**
     * 根据列车当前位置（下一站的索引）动态生成带状态码的站点列表。
     *
     * @param currentIndex 列车当前所在的站点索引（即“下一站”在列表中的位置）
     * @return 用于绘制的站点列表，包含正确的 passingStatus
     */
    public List<DrawableRouteStation> getStations(int currentIndex) {
        List<DrawableRouteStation> result = new ArrayList<>(stations.size());
        for (int i = 0; i < stations.size(); i++) {
            DrawableRouteStationInfo info = stations.get(i);
            int status;
            if (i < beginIndexInclusive) {
                status = 0; // 区间外之前
            } else if (i > endIndexInclusive) {
                status = 4; // 区间外之后
            } else {
                // 运行区间内
                if (i < currentIndex) {
                    status = 1; // 已通过
                } else if (i == currentIndex) {
                    status = 2; // 下一站
                } else {
                    status = 3; // 未到达
                }
            }
            result.add(new DrawableRouteStation(info, status));
        }
        return result;
    }

    // ==================== 静态工厂方法 ====================

    public static DrawableRoute getDrawableRoute(LocalRoute route) {
        int lastIdx = Math.max(0, route.platformIds.size() - 1);
        return getDrawableRoute(route.asRouteDetail(), false, 0, lastIdx);
    }

    public static DrawableRoute getDrawableRoute(LocalRoute route, boolean isReversed) {
        int lastIdx = Math.max(0, route.platformIds.size() - 1);
        return getDrawableRoute(route.asRouteDetail(), isReversed, 0, lastIdx);
    }

    public static DrawableRoute getDrawableRoute(LocalRouteDetail routeDetail) {
        int lastIdx = Math.max(0, routeDetail.stationDetails.size() - 1);
        return getDrawableRoute(routeDetail, false, 0, lastIdx);
    }

    public static DrawableRoute getDrawableRoute(LocalRouteDetail routeDetail, boolean isReversed) {
        int lastIdx = Math.max(0, routeDetail.stationDetails.size() - 1);
        return getDrawableRoute(routeDetail, isReversed, 0, lastIdx);
    }

    /**
     * 核心构造方法：根据原始详情、方向、原始起止索引构建 DrawableRoute。
     *
     * @param routeDetail 路线详情
     * @param isReversed  是否反向
     * @param rawBegin    原始传入区间起点索引（正向列表中的位置）
     * @param rawEnd      原始传入区间终点索引（正向列表中的位置）
     * @return 可绘制路线对象
     */
    public static DrawableRoute getDrawableRoute(LocalRouteDetail routeDetail, boolean isReversed,
                                                 int rawBegin, int rawEnd) {
        List<LocalRouteDetail.StationDetails> source = routeDetail.stationDetails;
        int size = source.size();

        // 计算区间在正向列表中的实际起止（已排序）
        int forwardBegin = Math.min(rawBegin, rawEnd);
        int forwardEnd = Math.max(rawBegin, rawEnd);

        List<DrawableRouteStationInfo> stations = new ArrayList<>(size);

        // 根据方向确定遍历顺序
        int start = isReversed ? size - 1 : 0;
        int step = isReversed ? -1 : 1;

        // 用于记录最终列表中区间边界的索引
        int finalBegin = -1;
        int finalEnd = -1;

        for (int i = 0; i < size; i++) {
            int srcIdx = start + i * step;
            LocalRouteDetail.StationDetails detail = source.get(srcIdx);
            stations.add(new DrawableRouteStationInfo(detail.stationName, detail.transInfo));

            // 判断该站在正向列表中的索引是否处于 [forwardBegin, forwardEnd] 区间内
            int forwardIdx = srcIdx; // 正向索引即原始列表索引
            if (forwardIdx >= forwardBegin && forwardIdx <= forwardEnd) {
                if (finalBegin == -1) {
                    finalBegin = i; // 第一个进入区间的站点在最终列表中的位置
                }
                finalEnd = i; // 持续更新最后一个区间站点位置
            }
        }

        // 若未找到区间边界（理论上不会发生，因为传入区间必定有效），则退化为整个列表
        if (finalBegin == -1) {
            finalBegin = 0;
            finalEnd = size - 1;
        }

        return new DrawableRoute(routeDetail.routeName, routeDetail.routeColor, stations,
                routeDetail.circularState, finalBegin, finalEnd);
    }

    public static DrawableRoute requestLongestRoute(Long routeId) {
        if (cacheRequestLongestRoute.containsKey(routeId)) return cacheRequestLongestRoute.get(routeId);
        LocalRoute route = MtrUtil.getRouteById(routeId);
        if (route == null) return null;
        return requestLongestRoute(route);
    }

    public static DrawableRoute requestLongestRoute(LocalRoute route) {
        if (cacheRequestLongestRoute.containsKey(route.id)) {
            return cacheRequestLongestRoute.get(route.id);
        }

        List<LocalRoute> candidates = MtrUtil.getRouteByName(route.name);
        if (candidates == null) {
            candidates = Collections.emptyList();
        }

        LocalRoute longestRoute = route;
        boolean isReversed = false;
        int beginIdx = 0;
        int endIdx = route.platformIds.size() - 1;

        RouteLoop:
        for (LocalRoute r : candidates) {
            if (longestRoute.equals(r)) continue;
            if (r.platformIds.size() < longestRoute.platformIds.size()) continue;

            int prevStnIdx = -1;
            boolean reversedCandidate = false;

            for (int i = 0; i < longestRoute.platformIds.size(); i++) {
                LocalRoute.RoutePlatform pid = longestRoute.platformIds.get(i);
                int curIdx = r.platformIds.indexOf(pid);
                if (curIdx < 0) continue RouteLoop;

                if (i == 1) {
                    reversedCandidate = curIdx < prevStnIdx;
                } else if (i > 1) {
                    if (reversedCandidate) {
                        if (curIdx >= prevStnIdx) continue RouteLoop;
                    } else {
                        if (curIdx <= prevStnIdx) continue RouteLoop;
                    }
                }
                prevStnIdx = curIdx;
            }

            // 通过校验
            boolean longer = r.platformIds.size() > longestRoute.platformIds.size();
            boolean sameLengthPreferSameDir = r.platformIds.size() == longestRoute.platformIds.size()
                    && !reversedCandidate && isReversed;

            if (longer || sameLengthPreferSameDir) {
                // 更长 → 直接替换；等长但当前为反向候选为同向 → 优选同向
                longestRoute = r;
                isReversed = reversedCandidate;
                beginIdx = r.platformIds.indexOf(route.platformIds.get(0));
                endIdx = r.platformIds.indexOf(route.platformIds.get(route.platformIds.size() - 1));
            }
        }

        DrawableRoute result = getDrawableRoute(longestRoute.asRouteDetail(), isReversed, beginIdx, endIdx);
        cacheRequestLongestRoute.put(route.id, result);
        return result;
    }

    // ==================== 内部数据类 ====================

    public record DrawableRouteStationInfo(String stationName, List<ColorNameTuple> transInfo) {
    }

    public static class DrawableRouteStation {
        public final String stationName;
        public final List<ColorNameTuple> transInfo;
        /**
         * 0 = 运行区间之前
         * 1 = 已通过
         * 2 = 下一站/本站
         * 3 = 未到达
         * 4 = 运行区间之后
         */
        public final int passingStatus;

        public DrawableRouteStation(String stationName, List<ColorNameTuple> transInfo, int passingStatus) {
            this.stationName = stationName;
            this.transInfo = transInfo;
            this.passingStatus = passingStatus;
        }

        public DrawableRouteStation(DrawableRouteStationInfo info, int passingStatus) {
            this(info.stationName, info.transInfo, passingStatus);
        }
    }
}