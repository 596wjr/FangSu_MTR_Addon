package com.fangsu.announcements.mtradapter;

import com.fangsu.utils.MtrUtil.PidsArrivalInfo;
import java.util.*;

/** 与 PIDS 共用客户端时间戳，保留真实列车的停站身份；不再以距离 / 速度估算到站时间。 */
public final class PidsAnnouncementTimes {
    private record Key(long route,long platform) { }
    public static List<ApproachingTrain> match(List<ApproachingTrain> trains,List<PidsArrivalInfo> predictions,long now){
        Map<Key,List<PidsArrivalInfo>> upcoming=new HashMap<>();
        for(var prediction:predictions)if(prediction.arrivalMillis>now)
            upcoming.computeIfAbsent(new Key(prediction.routeId,prediction.platformId),key->new ArrayList<>()).add(prediction);
        upcoming.values().forEach(list->list.sort(Comparator.comparingLong(PidsArrivalInfo::arrivalMillis)));
        List<ApproachingTrain> sorted=new ArrayList<>(trains),result=new ArrayList<>();
        sorted.sort(Comparator.comparingDouble(ApproachingTrain::remainingDistance));
        for(var train:sorted){
            var options=upcoming.get(new Key(train.routeId(),train.platformId()));PidsArrivalInfo selected=null;
            if(options!=null)for(var iterator=options.iterator();iterator.hasNext();){var candidate=iterator.next();
                // MTR4 的 departureIndex 精确匹配；MTR3 原协议无班次 ID，按同线路同站台的前后顺序对应。
                if(train.departureIndex()!=Long.MIN_VALUE&&train.departureIndex()!=candidate.departureIndex)continue;
                selected=candidate;iterator.remove();break;
            }
            double seconds=selected==null?Double.NaN:(selected.arrivalMillis-now)/1000d;
            String destination=selected==null?train.destination():selected.customDestination!=null&&!selected.customDestination.isBlank()?selected.customDestination:selected.destination;
            result.add(new ApproachingTrain(train.vehicleId(),train.routeId(),train.platformId(),train.stopIndex(),train.progress(),train.remainingDistance(),seconds,train.departureIndex(),destination));
        }
        return List.copyOf(result);
    }
}
