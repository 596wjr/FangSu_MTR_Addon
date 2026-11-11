package com.fangsu.announcements.mtradapter;

import com.fangsu.train.TrainStatus;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import java.util.*;

/** MTR3 适配：站序来自现有 TrainStatus；仅在喇叭范围内监听时调用。 */
public final class MtrAnnouncementAdapter {
    public static List<ApproachingTrain> approaching(Set<Long> platforms) {
        List<ApproachingTrain> result = new ArrayList<>();
        for (TrainClient train : new ArrayList<>(ClientData.TRAINS)) {
            if (!train.isOnRoute() || train.getDoorValue() > .01) continue;
            TrainStatus status = new TrainStatus(train); status.reset();
            var stops = status.getAllPlatforms(); int index = status.getAllPlatformsNextIndex();
            if (index < 0 || index >= stops.size()) continue;
            var stop = stops.get(index);
            double remaining = stop.distance - train.getRailProgress();
            if(!Double.isFinite(remaining)||remaining<=.5||stop.platform==null||stop.route==null||!platforms.contains(stop.platform.id))continue;
            result.add(new ApproachingTrain(train.id,stop.route.id,stop.platform.id,index,train.getRailProgress(),remaining,Double.NaN,Long.MIN_VALUE,stop.destinationName));
        }
        return result;
    }
    public static RidingAnnouncementState riding(){return riding(-1);}
    public static List<RidingAnnouncementState> forecast(){
        List<RidingAnnouncementState> result=new ArrayList<>();for(int n=0;n<2;n++){var state=riding(n);if(state!=null)result.add(state);}return result;
    }
    private static RidingAnnouncementState riding(int forecast) {
        var player = net.minecraft.client.Minecraft.getInstance().player; if (player == null) return null;
        for (TrainClient train : new ArrayList<>(ClientData.TRAINS)) {
            if (!train.isPlayerRiding(player)) continue;
            TrainStatus status = new TrainStatus(train); status.updateRoute();
            var stops = status.getAllPlatforms(); int index = status.getAllPlatformsNextIndex();
            if (train.getDoorValue() > .01) {
                long platform = com.fangsu.train.DoorSideObserverMtr3.currentPlatformId(train);
                for (int n = 0; n < stops.size(); n++) if (stops.get(n).platform != null && stops.get(n).platform.id == platform) { index = n; break; }
            }
            if(forecast>=0)index+=(train.getDoorValue()>.01?1:0)+forecast;
            if (index < 0 || index >= stops.size()) return null;
            var stop = stops.get(index); if (stop.platform == null || stop.route == null) return null;
            var namedStation = new com.fangsu.announcements.recordings.RecordingFamily.Named(stop.station == null ? 0 : stop.station.id, stop.station == null ? "" : stop.station.name);
            var destination = new com.fangsu.announcements.recordings.RecordingFamily.Named(stop.destinationStation == null ? 0 : stop.destinationStation.id, stop.destinationName == null ? "" : stop.destinationName);
            List<com.fangsu.announcements.recordings.RecordingFamily.Named> transfers = new ArrayList<>();
            Set<Long> stationIds = new HashSet<>(); stationIds.add(namedStation.id());
            if (stop.station != null) for (var connected : ClientData.DATA_CACHE.stationIdToConnectingStations.getOrDefault(stop.station, Set.of())) stationIds.add(connected.id);
            for (var route : ClientData.DATA_CACHE.routeIdMap.values()) if (route.id != stop.route.id && route.platformIds.stream().anyMatch(p -> {
                var station = ClientData.DATA_CACHE.platformIdToStation.get(p.platformId); return station != null && stationIds.contains(station.id);
            })) transfers.add(new com.fangsu.announcements.recordings.RecordingFamily.Named(route.id, route.name));
            boolean left = false, right = false;
            for (int car = 0; car < status.doorLeftOpen.length; car++) {
                var side = status.getDoorOpenSide(car);
                left |= side == com.fangsu.train.DoorOpenRecord.DoorOpenSide.LEFT || side == com.fangsu.train.DoorOpenRecord.DoorOpenSide.BOTH;
                right |= side == com.fangsu.train.DoorOpenRecord.DoorOpenSide.RIGHT || side == com.fangsu.train.DoorOpenRecord.DoorOpenSide.BOTH;
            }
            if(forecast>0||forecast>=0&&train.getDoorValue()>.01){left=false;right=false;}
            int localIndex = 0; for (int n = 0; n < stop.route.platformIds.size(); n++) if (stop.route.platformIds.get(n).platformId == stop.platform.id) { localIndex = n; break; }
            var context = new com.fangsu.announcements.recordings.RecordingFamily.Context(namedStation, destination,
                    new com.fangsu.announcements.recordings.RecordingFamily.Named(stop.route.id, stop.route.name), List.copyOf(transfers), localIndex <= 1,
                    localIndex == stop.route.platformIds.size() - 1, left, right);
            return new RidingAnnouncementState(train.id, train.trainId, stop.route.id, stop.platform.id, index, train.getRailProgress(), stop.distance - train.getRailProgress(), train.getDoorValue() > .01, train.isOnRoute(), context);
        }
        return null;
    }
}
