package com.fangsu.train;

import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;
import mtr.client.ClientData;
import mtr.data.*;
import mtr.path.PathData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TrainStatus {
    private final TrainClient train;

    public final boolean[] doorLeftOpen;
    public final boolean[] doorRightOpen;

    public final Matrix4f[] lastWorldPose;
    public final Vector3f[] lastCarPosition;
    public final Vector3f[] lastCarRotation;

    public boolean shouldRender;
    public boolean isInDetailDistance;

    public LocalRoute currentRoute;
    public DrawableRoute drawableRoute;
    public boolean isOnRoute;
    public boolean isReverse;
    /**
     * 0 = no route
     * 1 = waiting
     * 2 = leaving
     * 3 = on_route
     * 4 = arrived
     * 5 = changing
     * 6 = returning
     */
    public int trainStatus;

    private PlatformLookupMap trainPlatforms;
    private List<PathData> trainPlatformsValidPath;

    private int cacheIndex;

    public TrainStatus(TrainClient train) {
        this.train = train;
        int trainCars = train.trainCars;
        doorLeftOpen = new boolean[trainCars];
        doorRightOpen = new boolean[trainCars];
        lastWorldPose = new Matrix4f[trainCars];
        lastCarPosition = new Vector3f[trainCars];
        lastCarRotation = new Vector3f[trainCars];
        shouldRender = true;
        isInDetailDistance = false;
    }

    public void reset() {
        if (trainPlatformsValidPath == null || !trainPlatformsValidPath.equals(train.path) || trainPlatforms.platforms.isEmpty()) {
            trainPlatformsValidPath = new ArrayList<>(train.path);
            if (!train.getRouteIds().isEmpty()) {
                trainPlatforms = getTrainPlatforms();
            } else {
                trainPlatforms = new PlatformLookupMap();
            }
        }
    }

    public void updateRoute() {
        reset();
        int nextIndex = getAllPlatformsNextIndex();
        if (nextIndex < trainPlatforms.platforms.size()) {
            var nextPlatformInfo = trainPlatforms.platforms.get(nextIndex);
            this.currentRoute = new LocalRoute(nextPlatformInfo.route);
        } else if (!train.getRouteIds().isEmpty()) {
            // 尚未到达第一个站台，但已有路线信息 → 使用第一条路线
            Route route = ClientData.DATA_CACHE.routeIdMap.get(train.getRouteIds().get(0));
            this.currentRoute = route != null ? new LocalRoute(route) : null;
        } else {
            this.currentRoute = null;
        }

        if (currentRoute != null) {
            this.drawableRoute = DrawableRoute.requestLongestRoute(currentRoute);
        } else {
            this.drawableRoute = null;
        }

        this.isOnRoute = train.isOnRoute();
        this.isReverse = train.isReversed();
        this.trainStatus = geTrainStatus();
    }

    public void update(int carIndex, boolean doorLeftOpen, boolean doorRightOpen, Matrix4f carPose) {
        updateRoute();
        this.doorLeftOpen[carIndex] = doorLeftOpen;
        this.doorRightOpen[carIndex] = doorRightOpen;
        this.lastWorldPose[carIndex] = carPose.copy();
        this.lastCarPosition[carIndex] = carPose.getTranslationPart();
//        this.lastCarRotation[carIndex] = carPose.getEulerAnglesXYZ();

    }

    private PlatformLookupMap getTrainPlatforms() {
        List<Long> routeIds = train.getRouteIds();
        DataCache dataCache = ClientData.DATA_CACHE;
        PlatformLookupMap result = new PlatformLookupMap();
        result.siding = dataCache.sidingIdMap.get(train.sidingId);
        if (routeIds.isEmpty()) return result;

        int routeIndex = 0;
        List<PlatformInfo> currentRoutePlatforms = new ArrayList<>();
        for (int pathIndex = 0; pathIndex < train.path.size(); pathIndex++) {
            if (train.path.get(pathIndex).dwellTime <= 0) continue;
            if (train.path.get(pathIndex).rail.railType != RailType.PLATFORM) continue;

            if (routeIndex >= routeIds.size()) break;
            Route thisRoute = dataCache.routeIdMap.get(routeIds.get(routeIndex));
            Route nextRoute = routeIndex < routeIds.size() - 1 ? dataCache.routeIdMap.get(routeIds.get(routeIndex + 1)) : null;
            boolean reverseAtPlatform = !thisRoute.platformIds.isEmpty() && nextRoute != null && !nextRoute.platformIds.isEmpty()
                    && thisRoute.getLastPlatformId() == nextRoute.getFirstPlatformId();

            int routeStationIndex = currentRoutePlatforms.size();
            Station thisStation = dataCache.platformIdToStation.get((thisRoute.platformIds.get(routeStationIndex)).platformId);
            Platform thisPlatform = dataCache.platformIdMap.get((thisRoute.platformIds.get(routeStationIndex)).platformId);
            String customDestination = thisRoute.getDestination(routeStationIndex);
//            double distance = ((TrainAccessor)train).getDistances().get(pathIndex);
            boolean reverseAtThisPlatform = (currentRoutePlatforms.size() + 1 >= thisRoute.platformIds.size() && reverseAtPlatform);
            Station lastStation = ClientData.DATA_CACHE.platformIdToStation.get(thisRoute.getLastPlatformId());
            PlatformInfo platformInfo = new PlatformInfo(thisRoute, thisStation, thisPlatform, lastStation,
                    customDestination != null ? customDestination : (lastStation != null ? lastStation.name : ""),
                    0, reverseAtThisPlatform);

            result.pathToPlatformIndex.put(pathIndex, result.platforms.size());
            result.platforms.add(platformInfo);
            result.pathToRoutePlatformIndex.put(pathIndex, currentRoutePlatforms.size());
            currentRoutePlatforms.add(platformInfo);

            if (currentRoutePlatforms.size() >= thisRoute.platformIds.size()) {
                result.pathToRoutePlatforms.put(pathIndex, currentRoutePlatforms);
                currentRoutePlatforms = new ArrayList<>();
                routeIndex++;
                if (reverseAtPlatform) {
                    currentRoutePlatforms.add(platformInfo);
                }
            }
        }

        return result;
    }

    public List<PlatformInfo> getAllPlatforms() {
        return trainPlatforms.platforms;
    }

    private int geTrainStatus() {
        if (currentRoute == null) return 0;
        if (!train.isOnRoute()) {
            // 有路线但不在正线上：有速度→出库中，无速度→等待中
            return train.getSpeed() > 0.01f ? 2 : 1;
        }
        int nextIndex = getAllPlatformsNextIndex();
        // 平台数据为空或已过最后一个平台：说明路线数据尚未就绪或列车已到终点
        int platformCount = trainPlatforms.platforms.size();
        if (platformCount == 0) {
            // 无平台数据（刚出库尚未到达首个站台）：当作运行中
            return 3;
        }
        if (nextIndex >= platformCount) return 6;
        if (onPlatformRail()) return 4;
        return 3;
    }

    public List<PlatformInfo> getThisRoutePlatforms() {
        int headIndex = train.getIndex(0, train.spacing, true);
        Map.Entry<Integer, List<PlatformInfo>> ceilEntry = trainPlatforms.pathToRoutePlatforms.ceilingEntry(headIndex);
        if (ceilEntry == null) return List.of();
        return ceilEntry.getValue();
    }

    public int getThisRoutePlatformsNextIndex() {
        int headIndex = train.getIndex(0, train.spacing, true);
        Map.Entry<Integer, Integer> ceilEntry = trainPlatforms.pathToRoutePlatformIndex.ceilingEntry(headIndex);
        if (ceilEntry == null) return getThisRoutePlatforms().size();
        return ceilEntry.getValue();
    }

    /**
     * 获取下一站在整个长交路 DrawableRoute 中的全局索引。
     * 用于传递给 DrawableRoute.getStations() 以保证索引匹配。
     */
    public int getThisRoutePlatformsNextIndexGlobal() {
        int localIndex = getThisRoutePlatformsNextIndex();
        if (drawableRoute == null) return localIndex;
        return drawableRoute.beginIndexInclusive + localIndex;
    }

    public int getAllPlatformsNextIndex() {
        int headIndex = train.getIndex(0, train.spacing, true);
        Map.Entry<Integer, Integer> ceilEntry = trainPlatforms.pathToPlatformIndex.ceilingEntry(headIndex);
        if (ceilEntry == null) return trainPlatforms.platforms.size();
        return ceilEntry.getValue();
    }

    private boolean onPlatformRail() {
        int nextIndex = getAllPlatformsNextIndex();
        if (nextIndex >= trainPlatforms.platforms.size()) return false;

        int pathSize = train.path.size();
        if (pathSize == 0) return false;

        int idx1 = Math.max(0, Math.min(train.getIndex(getRailProgress(0), false), pathSize - 1));
        int idx2 = Math.max(0, Math.min(train.getIndex(getRailProgress(train.trainCars - 1), true), pathSize - 1));
        var path1 = train.path.get(idx1); // 车头所在轨道
        var path2 = train.path.get(idx2); // 车尾所在轨道
        var nextPlatformId = trainPlatforms.platforms.get(nextIndex).platform.id;
        return (path1.dwellTime != 0 && path1.savedRailBaseId == nextPlatformId) || (path2.dwellTime != 0 && path2.savedRailBaseId == nextPlatformId);
    }

    private static class PlatformLookupMap {
        public Siding siding;
        public final List<PlatformInfo> platforms = new ArrayList<>();
        public final TreeMap<Integer, Integer> pathToPlatformIndex = new TreeMap<>();
        public final TreeMap<Integer, List<PlatformInfo>> pathToRoutePlatforms = new TreeMap<>();
        public final TreeMap<Integer, Integer> pathToRoutePlatformIndex = new TreeMap<>();
    }

    public static class PlatformInfo {

        public Route route;
        public Station station;
        public Platform platform;
        public Station destinationStation;
        public String destinationName;
        public double distance;
        public boolean reverseAtPlatform;

        public PlatformInfo(Route route, Station station, Platform platform,
                            Station destinationStation, String destinationName, double distance,
                            boolean reverseAtPlatform) {
            this.route = route;
            this.station = station;
            this.platform = platform;
            this.destinationStation = destinationStation;
            this.destinationName = destinationName;
            this.distance = distance;
            this.reverseAtPlatform = reverseAtPlatform;
        }
    }

    @SuppressWarnings("unused")
    public Train mtrTrain() {
        return train;
    }

    @SuppressWarnings("unused")
    public long id() {
        return train.id;
    }

    @SuppressWarnings("unused")
    public Siding siding() {
        return trainPlatforms.siding;
    }

    @SuppressWarnings("unused")
    public String trainTypeId() {
        return train.trainId;
    }

    @SuppressWarnings("unused")
    public String baseTrainType() {
        return train.baseTrainType;
    }

    @SuppressWarnings("unused")
    public TransportMode transportMode() {
        return train.transportMode;
    }

    @SuppressWarnings("unused")
    public int spacing() {
        return train.spacing;
    }

    @SuppressWarnings("unused")
    public int width() {
        return train.width;
    }

    @SuppressWarnings("unused")
    public int trainCars() {
        return train.trainCars;
    }

    @SuppressWarnings("unused")
    public float accelerationConstant() {
        return train.accelerationConstant;
    }

    @SuppressWarnings("unused")
    public boolean manualAllowed() {
        return train.isManualAllowed;
    }

    @SuppressWarnings("unused")
    public int maxManualSpeed() {
        return train.maxManualSpeed;
    }

    @SuppressWarnings("unused")
    public int manualToAutomaticTime() {
        return train.manualToAutomaticTime;
    }

    @SuppressWarnings("unused")
    public List<PathData> path() {
        return train.path;
    }

    @SuppressWarnings("unused")
    public double railProgress() {
        return train.getRailProgress();
    }

    @SuppressWarnings("unused")
    public double getRailProgress(int car) {
        return train.getRailProgress() - car * train.spacing;
    }

    @SuppressWarnings("unused")
    public int getRailIndex(double railProgress, boolean roundDown) {
        return train.getIndex(railProgress, roundDown);
    }

    @SuppressWarnings("unused")
    public float getRailSpeed(int railIndex) {
        return train.getRailSpeed(railIndex);
    }

    @SuppressWarnings("unused")
    public float speed() {
        return train.getSpeed();
    }

    @SuppressWarnings("unused")
    public float doorValue() {
        return train.getDoorValue();
    }

    @SuppressWarnings("unused")
    public boolean isCurrentlyManual() {
        return train.isCurrentlyManual();
    }

    @SuppressWarnings("unused")
    public boolean isReversed() {
        return train.isReversed();
    }

    @SuppressWarnings("unused")
    public boolean isOnRoute() {
        return train.isOnRoute();
    }

    @SuppressWarnings("unused")
    public boolean justOpening() {
        return train.justOpening();
    }

    @SuppressWarnings("unused")
    public boolean justClosing(float doorCloseTime) {
        return train.justClosing(doorCloseTime);
    }

    @SuppressWarnings("unused")
    public final boolean isDoorOpening() {
        return train.isDoorOpening();
    }

    @SuppressWarnings("unused")
    public boolean doorTarget() {
        return train.isDoorOpening();
    }
}
