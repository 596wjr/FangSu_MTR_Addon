package com.fangsu.train;

import com.fangsu.mtr.LocalRoute;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;
import mtr.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TrainStatus {
    private final TrainClient train;

    public boolean[] doorLeftOpen;
    public boolean[] doorRightOpen;

    public Matrix4f[] lastWorldPose;
    public Vector3f[] lastCarPosition;
    public Vector3f[] lastCarRotation;

    public boolean shouldRender;
    public boolean isInDetailDistance;

    public LocalRoute currentRoute;

    private PlatformLookupMap trainPlatforms;
//    private List<PathData> trainPlatformsValidPath;

    public TrainStatus(TrainClient train) {
        this.train = train;
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
}
