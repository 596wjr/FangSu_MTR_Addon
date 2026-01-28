package com.fangsu.utils;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class MtrUtil {
    public static Platform getPlatformAt(Vector3f pos, int radius, int lower, int upper) {
        Station station = RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, new BlockPos(new Vec3i((int) pos.x, (int) pos.y, (int) pos.z)));
        Map<Long, Platform> platformPositions = ClientData.DATA_CACHE.requestStationIdToPlatforms(station.id);
        Long id = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, new BlockPos(new Vec3i((int) pos.x, (int) pos.y, (int) pos.z)), radius, lower, upper);
        Platform platform = platformPositions.get(id);
        return platform;
    }
    public static Station getStationAt(Vector3f pos) {
        return RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, new BlockPos(new Vec3i((int) pos.x, (int) pos.y, (int) pos.z)));
    }
    public static Vector3f getNodeAt(Vector3f vPos, Float fFacing) {
        BlockPos pos = new BlockPos(new Vec3i((int) vPos.x, (int) vPos.y, (int) vPos.z));
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
    public static List<ClientCache.PlatformRouteDetails> getRouteByPlatform(long platformId){
        return ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId);
    }
    public static Platform getPlatformById(long platformId) {
        for(Platform p:ClientData.PLATFORMS){
            if(p.id==platformId) return p;
        }
        return null;
    }
    public static Station getStationById(long stationId) {
        for(Station s:ClientData.STATIONS){
            if(s.id==stationId) return s;
        }
        return null;
    }
    public static Route getRouteById(long routeId) {
        for(Route r:ClientData.ROUTES){
            if(r.id==routeId) return r;
        }
        return null;
    }
    public static Station getStationByPlatform(Platform platform) {
        try {
            var posCentral = new Vector3f((Vector3fc) platform.getMidPos());
            return getStationAt(posCentral);
        } catch (Exception ignored) {
            return null;
        }
    }
    public static List<Platform> getPlatformByStation(Station station) {
        try{
            var platforms = ClientData.DATA_CACHE.requestStationIdToPlatforms((station.id));
            return (List<Platform>) platforms.values();
        }
        catch (Exception ignored) {
            return null;
        }
    }
    public static List<Route> getRouteByPlatform(Platform platform) {
        if(platform==null) return null;
        List<Route> routes = new ArrayList<>();
        for(Route r:ClientData.ROUTES){
            for(Route.RoutePlatform rp:r.platformIds){
                Platform p=getPlatformById(rp.platformId);
                if(p==null) continue;
                if(p.equals(platform)) routes.add(r);
            }
        }
        return routes;
    }
    public static String getDestinationByRoute(Route route) {
        if (route == null) return "undefined";
        try {
            Route.RoutePlatform destinationRoutePlatform = route.platformIds.get(route.platformIds.size() - 1);
            Platform destinationPlatform = getPlatformById((destinationRoutePlatform.platformId));
            Station station = getStationByPlatform(destinationPlatform);
            if (station!=null) return station.name;
            else return "未命名|Undefined";
        }
        catch (Exception ignored) {
            return "undefined";
        }
    }
    public static String getDestinationByPlatform(Platform platform) {
        if(platform==null) return "undefined";
        try{
            List<Route> routes = getRouteByPlatform(platform);
            if(routes==null) return "";
            List<String> destinations = new ArrayList<>();
            for(Route route:routes){
                destinations.add(getDestinationByRoute(route));
            }
            destinations.sort(String::compareTo);
            StringBuilder destination = new StringBuilder();
            for (int i = 0; i < destinations.size(); i++) {
                String destinationStr = destinations.get(i);
                if(i!=0) destination.append("/");
                destination.append(destinationStr);
            }
            return destination.toString();
        }
        catch (Exception ignored) {
            return "undefined";
        }
    }
}
