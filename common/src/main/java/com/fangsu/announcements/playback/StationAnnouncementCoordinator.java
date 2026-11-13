package com.fangsu.announcements.playback;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.definition.AnnouncementRegistry;
import com.fangsu.announcements.cache.AudioCacheStore;
import com.fangsu.announcements.cache.AudioSequence;
import com.fangsu.announcements.definition.AnnouncementDefinitions.ResolvedUtterance;
import com.fangsu.announcements.definition.AnnouncementDefinitions.Template;
import com.fangsu.announcements.resolve.StationNamePronunciation;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.utils.MtrUtil;
import com.fangsu.utils.MtrUtil.PidsArrivalInfo;
import com.fangsu.announcements.resolve.AnnouncementResolver;
import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 同方向同站台共用事件和合成，保留每个喇叭空间声源；按真实时长串行播出。 */
public final class StationAnnouncementCoordinator {
    private static final Map<BlockPos, WeakReference<BlockEntityAnnouncementSpeaker>> SPEAKERS = new HashMap<>();
    private static final Map<String, List<Arrival>> SEEN = new HashMap<>();
    private static final Map<String, Long> PLAYING_UNTIL = new HashMap<>();
    private static final Map<String,List<net.minecraft.client.resources.sounds.SoundInstance>> PLAYING=new HashMap<>();
    private static final Map<String,Long> HARD_DEADLINE=new HashMap<>();
    private static void stopSpeakers(){CachedAudioPlayback.stopSpeakers();PLAYING.clear();PLAYING_UNTIL.clear();HARD_DEADLINE.clear();}
    private static final Map<String, ArrayDeque<Pending>> QUEUES = new HashMap<>();
    private static Object world;
    private static int ticks;
    private record Arrival(long departureIndex, long arrivalMillis) {
        boolean matches(PidsArrivalInfo row) {
            long tolerance = departureIndex == Long.MIN_VALUE ? 5000 : 30000;
            return departureIndex == row.departureIndex && Math.abs(arrivalMillis - row.arrivalMillis) <= tolerance;
        }
    }
    private static final class StationCue {
        final CompletableFuture<AudioCacheStore.Artifact> audio;
        boolean started;
        StationCue(Template template) { audio = AnnouncementClient.stationChime(template); }
    }
    private record Pending(long deadline, CompletableFuture<AudioSequence> audio, StationCue cue) { }
    public static void onReload() { QUEUES.clear(); PLAYING_UNTIL.clear(); stopSpeakers(); }
    public static void track(BlockEntityAnnouncementSpeaker speaker) {
        if (world != speaker.getLevel()) reset(speaker.getLevel());
        SPEAKERS.put(speaker.getBlockPos(), new WeakReference<>(speaker));
    }
    private static void reset(Object newWorld) {
        world = newWorld; SPEAKERS.clear(); SEEN.clear(); PLAYING_UNTIL.clear(); QUEUES.clear(); stopSpeakers();
    }
    public static void tick(Minecraft client) {
        if (world != client.level) reset(client.level);
        if (++ticks % 10 != 0 || client.player == null) return;
        CachedAudioPlayback.prune();
        long now = System.currentTimeMillis();
        SEEN.values().forEach(arrivals -> arrivals.removeIf(arrival -> now - arrival.arrivalMillis() > 120000));
        SEEN.values().removeIf(List::isEmpty);
        Map<String,List<BlockPos>> activeGroups = new HashMap<>();
        List<BlockEntityAnnouncementSpeaker> audible = new ArrayList<>(),loaded=new ArrayList<>();
        SPEAKERS.entrySet().removeIf(entry -> entry.getValue().get() == null || entry.getValue().get().isRemoved());
        for (var ref : SPEAKERS.values()) {
            var speaker = ref.get();
            if(speaker!=null)loaded.add(speaker);
            if (speaker != null && client.player.distanceToSqr(speaker.getBlockPos().getX() + .5, speaker.getBlockPos().getY() + .5, speaker.getBlockPos().getZ() + .5) <= 32 * 32) audible.add(speaker);
        }
        try {
            Set<Long> platforms=new HashSet<>();
            for(var speaker:AnnouncementClient.settings().preSynthesize&&AnnouncementClient.settings().enabled?loaded:audible)for(var binding:speaker.bindings())platforms.add(binding.platformId());
            if(platforms.isEmpty()){QUEUES.clear();stopSpeakers();return;}
            var predictions=com.fangsu.utils.MtrUtil.getPidsArrivalInfoList(new ArrayList<>(platforms));
            if(AnnouncementClient.settings().preSynthesize&&AnnouncementClient.settings().enabled)prewarm(loaded,predictions,now);
            if(audible.isEmpty()){QUEUES.clear();stopSpeakers();return;}
            for (var speaker : audible) for (var binding : speaker.bindings()) {
                String key = groupKey(speaker, binding.routeId(), binding.platformId());
                activeGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(speaker.getBlockPos().immutable());
            }
            for (var row : predictions) {
                long remaining = row.arrivalMillis - now;
                if (remaining <= 0 || remaining > BlockEntityAnnouncementSpeaker.MAX_LEAD_SECONDS * 1000L) continue;
                Set<String> checked = new HashSet<>();
                for (var speaker : audible) {
                    if (remaining > speaker.leadSeconds() * 1000L || speaker.bindings().stream().noneMatch(
                            binding -> binding.routeId() == row.routeId && binding.platformId() == row.platformId)) continue;
                    String key = groupKey(speaker, row.routeId, row.platformId);
                    if (!checked.add(key)) continue;
                    List<Arrival> seen = SEEN.computeIfAbsent(key, ignored -> new ArrayList<>());
                    int match = -1;
                    for (int i = 0; i < seen.size(); i++) if (seen.get(i).matches(row)) { match = i; break; }
                    if (match >= 0) {
                        seen.set(match, new Arrival(row.departureIndex, row.arrivalMillis));
                        continue;
                    }
                    ArrayDeque<Pending> queue = QUEUES.computeIfAbsent(key, ignored -> new ArrayDeque<>());
                    StationCue cue = null;
                    String destination = destination(row);
                    String fullDestination = fullServiceDestination(row, destination);
                    for (String locale : AnnouncementClient.settings().languages) {
                        if (queue.size() >= 4) break;
                        var resolved = resolve(speaker, locale, destination, "");
                        if (resolved.isEmpty()) continue;
                        if (cue == null) cue = new StationCue(AnnouncementRegistry.INSTANCE.snapshot().templates().get(speaker.template()));
                        long deadline = Math.max(now + 20000, row.arrivalMillis + 5000);
                        var normal = resolved.get();
                        var audio = resolve(speaker, locale, destination, fullDestination)
                                .filter(extended -> !extended.parts().equals(normal.parts()))
                                .map(extended -> AnnouncementClient.generate(extended, false)
                                        .completeOnTimeout(null, 1500, TimeUnit.MILLISECONDS)
                                        .handle((sequence, error) -> error == null ? sequence : null)
                                        .thenCompose(sequence -> sequence == null ? AnnouncementClient.generate(normal, false) : CompletableFuture.completedFuture(sequence)))
                                .orElseGet(() -> AnnouncementClient.generate(normal, false));
                        queue.add(new Pending(deadline, audio, cue));
                    }
                    if (cue != null) seen.add(new Arrival(row.departureIndex, row.arrivalMillis));
                }
            }
        } catch (RuntimeException ignored) { /* MTR 数据快照暂不可用时跳过，不猜测邻线或目的地。 */ }
        QUEUES.keySet().removeIf(key -> !activeGroups.containsKey(key));
        PLAYING.entrySet().removeIf(entry -> {
            if (activeGroups.containsKey(entry.getKey())) return false;
            entry.getValue().forEach(CachedAudioPlayback::stopSound);
            HARD_DEADLINE.remove(entry.getKey());
            PLAYING_UNTIL.remove(entry.getKey());
            return true;
        });
        for (var group : QUEUES.entrySet()) {
            var voices=PLAYING.get(group.getKey());
            if(voices!=null){
                if(now>HARD_DEADLINE.getOrDefault(group.getKey(),0L)){voices.forEach(CachedAudioPlayback::stopSound);PLAYING.remove(group.getKey());HARD_DEADLINE.remove(group.getKey());PLAYING_UNTIL.remove(group.getKey());}
                else if(voices.stream().anyMatch(CachedAudioPlayback::isPlayingOrPending))continue;
                else{PLAYING.remove(group.getKey());HARD_DEADLINE.remove(group.getKey());PLAYING_UNTIL.put(group.getKey(),now+300);}
            }
            if (PLAYING_UNTIL.getOrDefault(group.getKey(), 0L) > now) continue;
            ArrayDeque<Pending> queue = group.getValue();
            while (!queue.isEmpty()) {
                Pending pending = queue.peek();
                if (pending.deadline() < now || pending.audio().isCompletedExceptionally() || pending.audio().isCancelled()) {
                    queue.remove();
                    continue;
                }
                if (!pending.audio().isDone() || !pending.cue().audio.isDone()) break;
                var sequence = pending.audio().getNow(null);
                if (sequence == null) { queue.remove(); continue; }
                if (!pending.cue().started) sequence = sequence.withChime(pending.cue().audio.getNow(null));
                var started = CachedAudioPlayback.playSpeakers(sequence, activeGroups.get(group.getKey()));
                if (started.isEmpty()) break;
                queue.remove();
                pending.cue().started = true;
                PLAYING.put(group.getKey(), started);
                PLAYING_UNTIL.remove(group.getKey());
                HARD_DEADLINE.put(group.getKey(), now + CachedAudioPlayback.SEQUENCE_TIMEOUT_MILLIS);
                break;
            }
        }
        QUEUES.entrySet().removeIf(e -> e.getValue().isEmpty() && !PLAYING.containsKey(e.getKey()) && PLAYING_UNTIL.getOrDefault(e.getKey(), 0L) < now);
        PLAYING_UNTIL.entrySet().removeIf(e -> e.getValue() < now);
    }
    private static String groupKey(BlockEntityAnnouncementSpeaker speaker,long route,long platform){
        return route + "/" + platform + "/" + speaker.template() + "/" + speaker.variant() + "/" + speaker.leadSeconds() + "/" + speaker.allStops();
    }
    private static String destination(PidsArrivalInfo row){
        return row.customDestination == null || row.customDestination.isBlank() ? Objects.toString(row.destination, "") : row.customDestination;
    }
    private static Optional<ResolvedUtterance> resolve(BlockEntityAnnouncementSpeaker speaker,String locale,String destination,String fullDestination){
        Map<String,String> variables=new HashMap<>();
        Set<String> conditions=new HashSet<>(Set.of(speaker.allStops()?"allStops":"limitedService"));
        if(!destination.isBlank())variables.put("tripDestination",destination);
        if(!fullDestination.isBlank()){
            variables.put("fullServiceDestination",fullDestination);
            conditions.add("shortTurn");
        }
        return AnnouncementClient.resolve(AnnouncementRegistry.INSTANCE.snapshot(),speaker.template(),speaker.variant(),locale,variables,conditions);
    }
    private static String fullServiceDestination(PidsArrivalInfo row,String destination){
        if(destination.isBlank())return "";
        try{
            var service=MtrUtil.getRouteById(row.routeId);
            if(service==null||service.circularState!=LocalRoute.CircularState.NONE||service.platformIds.size()<2)return "";
            int stop=service.getPlatformIdIndex(row.platformId);
            if(stop<0||stop>=service.platformIds.size()-1||!sameStation(destination,MtrUtil.getDestinationByRoute(service)))return "";
            var route=DrawableRoute.requestLongestRoute(service);
            if(route==null||route.circularState!=LocalRoute.CircularState.NONE)return "";
            var stations=route.getStations(-1);
            if(stations.isEmpty())return "";
            var terminal=stations.get(stations.size()-1);
            if(terminal.passingStatus!=4||sameStation(destination,terminal.stationName)||!knownStation(terminal.stationName))return "";
            return terminal.stationName;
        }catch(RuntimeException e){
            return ""; // 站序未同步时沿用 PIDS 实际终点，只播常规内容。
        }
    }
    private static boolean sameStation(String first,String second){
        if(!knownStation(first)||!knownStation(second))return false;
        for(String locale:List.of("zh-CN","en-US")){
            String a=StationNamePronunciation.select(first,locale).display();
            String b=StationNamePronunciation.select(second,locale).display();
            if(!a.isBlank()&&a.equalsIgnoreCase(b))return true;
        }
        return false;
    }
    private static boolean knownStation(String name){
        return name!=null&&!name.isBlank()&&!name.equalsIgnoreCase("undefined")&&!name.equals("未命名|Undefined");
    }
    private static void prewarm(List<BlockEntityAnnouncementSpeaker> speakers,List<PidsArrivalInfo> predictions,long now){
        Set<String> groups=new HashSet<>();
        for(var speaker:speakers){
            String key=speaker.bindings()+"/"+speaker.template()+"/"+speaker.variant()+"/"+speaker.allStops();if(!groups.add(key))continue;
            int count=0;
            for(var row:predictions){
                if(row.arrivalMillis<=now||speaker.bindings().stream().noneMatch(b->b.routeId()==row.routeId&&b.platformId()==row.platformId))continue;
                String destination=destination(row);
                String fullDestination=fullServiceDestination(row,destination);
                for(String locale:AnnouncementClient.settings().languages)
                    resolve(speaker,locale,destination,fullDestination).ifPresent(AnnouncementClient::preSynthesize);
                if(++count>=2)break;
            }
        }
    }
}
