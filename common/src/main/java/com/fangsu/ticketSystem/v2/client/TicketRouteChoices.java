package com.fangsu.ticketSystem.v2.client;
import java.util.*;
/** 只用客户端线路组织原界面，车站可售范围及价格始终来自服务端。 */
public final class TicketRouteChoices {
    public record RouteChoice(String name,int color,Set<Long> stations) { }
    public static List<RouteChoice> load(){
        Map<String, RouteChoice> grouped = new LinkedHashMap<>();
        for(var route:mtr.client.ClientData.ROUTES){
            Set<Long> stations=new LinkedHashSet<>();for(var platform:route.platformIds){
                var station=com.fangsu.utils.MtrUtil.getStationByPlatform(com.fangsu.utils.MtrUtil.getPlatformById(platform.platformId));
                if(station!=null)stations.add(station.id);
            }
            merge(grouped, route.name, route.color, stations);
        }
        return grouped.values().stream()
                .sorted(Comparator.comparing(RouteChoice::name).thenComparingInt(RouteChoice::color)).toList();
    }
    private static void merge(Map<String, RouteChoice> grouped, String name, int color, Set<Long> stations) {
        String key = com.fangsu.scripting.TextUtil.getNonExtraParts(name);
        if (key.isBlank()) return;
        grouped.computeIfAbsent(key, ignored -> new RouteChoice(key, color, new LinkedHashSet<>()))
                .stations().addAll(stations);
    }
    private TicketRouteChoices(){ }
}
