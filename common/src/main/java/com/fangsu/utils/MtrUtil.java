package com.fangsu.utils;

import com.fangsu.mtr.LocalRoute;
import com.fangsu.scripting.TextUtil;
import mtr.block.BlockNode;
import mtr.client.ClientCache;
import mtr.data.*;
import mtr.client.ClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
//#if MC_VERSION >= 11903
import org.joml.Vector3f;
import org.joml.Vector3fc;
//#else
//$$import com.mojang.math.Vector3f;
//#endif

import java.util.*;

public class MtrUtil {
    private Map<Long, Route> requestIdToRoute = new HashMap<>();

    /**
     * 根据坐标与搜索范围获取最近的站台。
     */
    public static Platform getPlatformAt(Vector3f pos, int radius, int lower, int upper) {
        BlockPos blockPos = toBlockPos(pos);
        Station station = RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, blockPos);
        Map<Long, Platform> platformPositions = ClientData.DATA_CACHE.requestStationIdToPlatforms(station.id);
        long id = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, blockPos, radius, lower, upper);
        return platformPositions.get(id);
    }

    /**
     * 获取当前位置对应的车站。
     */
    public static Station getStationAt(Vector3f pos) {
        return RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, toBlockPos(pos));
    }

    /**
     * 获取附近的轨道节点位置。
     */
    public static Vector3f getNodeAt(Vector3f vPos, Float fFacing) {
        BlockPos pos = toBlockPos(vPos);
        Direction facing = Direction.fromYRot(fFacing);
        BlockGetter world = Minecraft.getInstance().level;
        final int[] checkDistance = {0, 1, -1, 2, -2, 3, -3, 4, -4};
        for (final int z : checkDistance) {
            for (final int x : checkDistance) {
                for (int y = -5; y <= 0; y++) {
                    final BlockPos checkPos = pos.above(y).relative(facing.getClockWise(), x).relative(facing, z);
                    final BlockState checkState = world.getBlockState(checkPos);
                    if (checkState.getBlock() instanceof BlockNode) {
                        //#if MC_VERSION >= 11903
                        return new Vector3f((Vector3fc) checkPos);
                        //#else
                        //$$ return new Vector3f((float) checkPos.getX(), (float) checkPos.getY(), (float) checkPos.getZ());
                        //#endif
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取站台对应的路线详情。
     */
    public static List<ClientCache.PlatformRouteDetails> getRouteByPlatform(long platformId) {
        return ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId);
    }

    /**
     * 根据站台 ID 查找站台。
     */
    public static Platform getPlatformById(long platformId) {
        for (Platform p : ClientData.PLATFORMS) {
            if (p.id == platformId) return p;
        }
        return null;
    }

    /**
     * 根据车站 ID 查找车站。
     */
    public static Station getStationById(long stationId) {
        for (Station s : ClientData.STATIONS) {
            if (s.id == stationId) return s;
        }
        return null;
    }

    /**
     * 根据路线 ID 查找路线。
     */
    @Deprecated
    public static Route getRouteByIdMtr(long routeId) {
        for (Route r : ClientData.ROUTES) {
            if (r.id == routeId) return r;
        }
        return null;
    }

    /**
     * 根据路线 ID 查找路线。
     */
    public static LocalRoute getRouteById(long routeId) {
        for (Route r : ClientData.ROUTES) {
            if (r.id == routeId) return new LocalRoute(r);
        }
        return null;
    }

    public static List<LocalRoute> getRouteByName(String routeName) {
        String compareName = TextUtil.getNonExtraParts(routeName);
        List<LocalRoute> routes = new ArrayList<>();
        for (Route r : ClientData.ROUTES) {
            String currentRouteName = TextUtil.getNonExtraParts(r.name);
            if (compareName.equals(currentRouteName)) {
                routes.add(new LocalRoute(r));
            }
        }
        return routes;
    }

    /**
     * 获取站台所属车站。
     */
    public static Station getStationByPlatform(Platform platform) {
        try {
            var posCentral = getCenterVector3f(platform.getMidPos());
            return getStationAt(posCentral);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 获取车站的所有站台。
     */
    public static List<Platform> getPlatformByStation(Station station) {
        try {
            var platforms = ClientData.DATA_CACHE.requestStationIdToPlatforms((station.id));
            return new ArrayList<>(platforms.values());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 获取站台对应的所有路线。
     */
    public static List<LocalRoute> getRouteByPlatform(Platform platform) {
        if (platform == null) return null;
        List<LocalRoute> routes = new ArrayList<>();
        for (Route r : ClientData.ROUTES) {
            for (Route.RoutePlatform rp : r.platformIds) {
                Platform p = getPlatformById(rp.platformId);
                if (p == null) continue;
                if (p.equals(platform)) routes.add(new LocalRoute(r));
            }
        }
        return routes;
    }

    /**
     * 获取路线的终点站名称。
     */
    public static String getDestinationByRoute(Route route) {
        if (route == null) return "undefined";
        try {
            Route.RoutePlatform destinationRoutePlatform = route.platformIds.get(route.platformIds.size() - 1);
            Platform destinationPlatform = getPlatformById(destinationRoutePlatform.platformId);
            Station station = getStationByPlatform(destinationPlatform);
            if (station != null) return station.name;
            else return "未命名|Undefined";
        } catch (Exception ignored) {
            return "undefined";
        }
    }

    /**
     * 获取路线的终点站名称。
     */
    public static String getDestinationByRoute(LocalRoute route) {
        if (route == null) return "undefined";
        try {
            LocalRoute.RoutePlatform destinationRoutePlatform = route.platformIds.get(route.platformIds.size() - 1);
            Platform destinationPlatform = getPlatformById(destinationRoutePlatform.platformId);
            Station station = getStationByPlatform(destinationPlatform);
            if (station != null) return station.name;
            else return "未命名|Undefined";
        } catch (Exception ignored) {
            return "undefined";
        }
    }

    public static boolean isAllDestination(Platform platform) {
        if (platform == null) return true;
        List<LocalRoute> routes = getRouteByPlatform(platform);
        if (routes == null) return true;
        boolean result = true;
        for (LocalRoute route : routes) {
            result &= isDestination(route, platform);
        }
        return result;
    }

    public static boolean isDestination(LocalRoute route, Platform platform) {
        if (route == null) return false;
        if (platform == null) return false;
        return route.platformIds.get(route.platformIds.size() - 1).platformId == platform.id;
    }

    /**
     * 根据站台获取所有终点站名称（去重且按字典序拼接）。
     */
    public static String getDestinationByPlatform(Platform platform) {
        if (platform == null) return "undefined";
        try {
            List<LocalRoute> routes = getRouteByPlatform(platform);
            if (routes == null) return "";
            List<String> destinations = new ArrayList<>();
            for (LocalRoute route : routes) {
                destinations.add(getDestinationByRoute(route));
            }
            destinations.sort(String::compareTo);
            return String.join("/", destinations);
        } catch (Exception ignored) {
            return "undefined";
        }
    }

    public static List<PidsArrivalInfo> getPidsArrivalInfoList(List<Long> platformIds) {
        List<PidsArrivalInfo> arrivalInfoList = new ArrayList<>();
        if (platformIds == null || platformIds.isEmpty()) return arrivalInfoList;

        Map<Long, Set<ScheduleEntry>> scheduleMap = ClientData.SCHEDULES_FOR_PLATFORM;
        for (Long platformId : platformIds) {
            if (platformId == null) continue;
            Set<ScheduleEntry> schedules = scheduleMap.get(platformId);
            if (schedules == null || schedules.isEmpty()) continue;

            for (ScheduleEntry entry : schedules) {
                if (entry.routeId == 0) continue;
                Route route = getRouteByIdMtr(entry.routeId);
                if (route == null || route.platformIds == null || route.platformIds.isEmpty()) continue;

                List<String> stationNames = new ArrayList<>();
                String currentPlatformName = null;

                for (Route.RoutePlatform routePlatform : route.platformIds) {
                    Platform routePlatformObj = getPlatformById(routePlatform.platformId);
                    Station station = getStationByPlatform(routePlatformObj);
                    if (station != null) stationNames.add(station.name);
                    if (routePlatform.platformId == platformId && routePlatformObj != null) {
                        currentPlatformName = routePlatformObj.name;
                    }
                }

                String destination = getDestinationByRoute(route);
                String customDestination = route.getDestination(entry.currentStationIndex);

                arrivalInfoList.add(new PidsArrivalInfo(
                        entry.arrivalMillis,
                        entry.trainCars,
                        entry.routeId,
                        entry.currentStationIndex,
                        destination,
                        customDestination,
                        stationNames,
                        currentPlatformName
                ));
            }
        }

        arrivalInfoList.sort(Comparator.comparingLong(PidsArrivalInfo::arrivalMillis));
        return arrivalInfoList;
    }

    public static final class PidsArrivalInfo {
        public final long arrivalMillis;
        public final int trainCars;
        public final long routeId;
        public final int currentStationIndex;
        public final String destination;
        public final String customDestination;
        public final List<String> stationNames;
        public final String currentPlatformName;

        public PidsArrivalInfo(
                long arrivalMillis,
                int trainCars,
                long routeId,
                int currentStationIndex,
                String destination,
                String customDestination,
                List<String> stationNames,
                String currentPlatformName
        ) {
            this.arrivalMillis = arrivalMillis;
            this.trainCars = trainCars;
            this.routeId = routeId;
            this.currentStationIndex = currentStationIndex;
            this.destination = destination;
            this.customDestination = customDestination;
            this.stationNames = stationNames;
            this.currentPlatformName = currentPlatformName;
        }

        public long arrivalMillis() {
            return arrivalMillis;
        }

        public int trainCars() {
            return trainCars;
        }

        public long routeId() {
            return routeId;
        }

        public int currentStationIndex() {
            return currentStationIndex;
        }

        public String destination() {
            return destination;
        }

        public String customDestination() {
            return customDestination;
        }

        public List<String> stationNames() {
            return stationNames;
        }

        public String currentPlatformName() {
            return currentPlatformName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (PidsArrivalInfo) obj;
            return this.arrivalMillis == that.arrivalMillis &&
                    this.trainCars == that.trainCars &&
                    this.routeId == that.routeId &&
                    this.currentStationIndex == that.currentStationIndex &&
                    Objects.equals(this.destination, that.destination) &&
                    Objects.equals(this.customDestination, that.customDestination) &&
                    Objects.equals(this.stationNames, that.stationNames) &&
                    Objects.equals(this.currentPlatformName, that.currentPlatformName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(arrivalMillis, trainCars, routeId, currentStationIndex, destination, customDestination, stationNames, currentPlatformName);
        }

        @Override
        public String toString() {
            return "PidsArrivalInfo[" +
                    "arrivalMillis=" + arrivalMillis + ", " +
                    "trainCars=" + trainCars + ", " +
                    "routeId=" + routeId + ", " +
                    "currentStationIndex=" + currentStationIndex + ", " +
                    "destination=" + destination + ", " +
                    "customDestination=" + customDestination + ", " +
                    "stationNames=" + stationNames + ", " +
                    "currentPlatformName=" + currentPlatformName + ']';
        }
    }

    /**
     * 坐标转换工具，统一向下取整。
     */
    /**
     * 获取 BlockPos 的中心点 Vector3f，兼容 1.18.2（无 getCenter()）。
     */
    public static Vector3f getCenterVector3f(BlockPos pos) {
        //#if MC_VERSION >= 11903
        return new Vector3f(pos.getCenter().toVector3f());
        //#else
        //$$ return new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
        //#endif
    }

    private static BlockPos toBlockPos(Vector3f pos) {
        //#if MC_VERSION >= 11903
        return new BlockPos(new Vec3i((int) pos.x, (int) pos.y, (int) pos.z));
        //#else
        //$$ return new BlockPos(new Vec3i((int) pos.x(), (int) pos.y(), (int) pos.z()));
        //#endif
    }
}
