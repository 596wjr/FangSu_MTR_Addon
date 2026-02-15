package com.fangsu.utils;

import com.fangsu.Main;
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
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class MtrUtil {
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
                        return new Vector3f((Vector3fc) checkPos);
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
    public static Route getRouteById(long routeId) {
        for (Route r : ClientData.ROUTES) {
            if (r.id == routeId) return r;
        }
        return null;
    }

    /**
     * 获取站台所属车站。
     */
    public static Station getStationByPlatform(Platform platform) {
        try {
            var posCentral = new Vector3f(platform.getMidPos().getCenter().toVector3f());
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
    public static List<Route> getRouteByPlatform(Platform platform) {
        if (platform == null) return null;
        List<Route> routes = new ArrayList<>();
        for (Route r : ClientData.ROUTES) {
            for (Route.RoutePlatform rp : r.platformIds) {
                Platform p = getPlatformById(rp.platformId);
                if (p == null) continue;
                if (p.equals(platform)) routes.add(r);
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
     * 根据站台获取所有终点站名称（去重且按字典序拼接）。
     */
    public static String getDestinationByPlatform(Platform platform) {
        if (platform == null) return "undefined";
        try {
            List<Route> routes = getRouteByPlatform(platform);
            if (routes == null) return "";
            List<String> destinations = new ArrayList<>();
            for (Route route : routes) {
                destinations.add(getDestinationByRoute(route));
            }
            destinations.sort(String::compareTo);
            return String.join("/", destinations);
        } catch (Exception ignored) {
            return "undefined";
        }
    }

    /**
     * 坐标转换工具，统一向下取整。
     */
    private static BlockPos toBlockPos(Vector3f pos) {
        return new BlockPos(new Vec3i((int) pos.x, (int) pos.y, (int) pos.z));
    }
}
