package com.fangsu.announcements.playback;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.definition.AnnouncementRegistry;
import com.fangsu.announcements.mtradapter.*;
import com.fangsu.announcements.resolve.AnnouncementResolver;
import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.lang.ref.WeakReference;
import java.util.*;

/** 同方向同站台共用事件和合成，保留每个喇叭空间声源；按真实时长串行播出。 */
public final class StationAnnouncementCoordinator {
    private static final Map<BlockPos, WeakReference<BlockEntityAnnouncementSpeaker>> SPEAKERS = new HashMap<>();
    private static final Map<String, Long> SEEN = new HashMap<>();
    private static final Map<Long, Double> PROGRESS = new HashMap<>();
    private static final Map<Long, Integer> RUNS = new HashMap<>();
    private static final Map<String, Long> PLAYING_UNTIL = new HashMap<>();
    private static final Map<String,List<net.minecraft.client.resources.sounds.SoundInstance>> PLAYING=new HashMap<>();
    private static final Map<String,Long> HARD_DEADLINE=new HashMap<>();
    private static void stopSpeakers(){CachedAudioPlayback.stopSpeakers();PLAYING.clear();PLAYING_UNTIL.clear();HARD_DEADLINE.clear();}
    private static final Map<String, ArrayDeque<Pending>> QUEUES = new HashMap<>();
    private static Object world;
    private static int ticks;
    private record Pending(String event, long deadline, java.util.concurrent.CompletableFuture<com.fangsu.announcements.cache.AudioSequence> audio, List<BlockPos> positions) { }
    public static void onReload() { QUEUES.clear(); PLAYING_UNTIL.clear(); stopSpeakers(); }
    public static void track(BlockEntityAnnouncementSpeaker speaker) {
        if (world != speaker.getLevel()) reset(speaker.getLevel());
        SPEAKERS.put(speaker.getBlockPos(), new WeakReference<>(speaker));
    }
    private static void reset(Object newWorld) {
        world = newWorld; SPEAKERS.clear(); SEEN.clear(); PROGRESS.clear(); RUNS.clear(); PLAYING_UNTIL.clear(); QUEUES.clear(); stopSpeakers();
    }
    public static void tick(Minecraft client) {
        if (world != client.level) reset(client.level);
        if (++ticks % 10 != 0 || client.player == null) return;
        CachedAudioPlayback.prune();
        long now = System.currentTimeMillis();
        SEEN.entrySet().removeIf(entry -> now - entry.getValue() > 300000);
        Set<String> validEvents = new HashSet<>();
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
            for (ApproachingTrain train : PidsAnnouncementTimes.match(MtrAnnouncementAdapter.approaching(platforms),predictions,now)) {
                Double previous = PROGRESS.put(train.vehicleId(), train.progress());
                if (previous != null && train.progress() + 10 < previous) RUNS.merge(train.vehicleId(), 1, Integer::sum);
                Map<String, List<BlockEntityAnnouncementSpeaker>> groups = new LinkedHashMap<>();
                for (var speaker : audible) if (speaker.bindings().stream().anyMatch(b -> b.routeId() == train.routeId() && b.platformId() == train.platformId()))
                    groups.computeIfAbsent(train.routeId() + "/" + train.platformId() + "/" + speaker.template() + "/" + speaker.variant()+"/"+speaker.leadSeconds()+"/"+speaker.allStops(), k -> new ArrayList<>()).add(speaker);
                for (var group : groups.entrySet()) {
                    String event = train.vehicleId() + "/" + RUNS.getOrDefault(train.vehicleId(), 0) + "/" + train.stopIndex() + "/" + group.getKey();
                    validEvents.add(event);
                    if (SEEN.containsKey(event)) continue;
                    var speaker = group.getValue().get(0);
                    if(!Double.isFinite(train.secondsUntilArrival())||train.secondsUntilArrival()>speaker.leadSeconds())continue;
                    SEEN.put(event, now);
                    List<BlockPos> positions = group.getValue().stream().map(s -> s.getBlockPos().immutable()).toList();
                    ArrayDeque<Pending> queue = QUEUES.computeIfAbsent(group.getKey(), k -> new ArrayDeque<>());
                    // 广播语言独立于客户端界面；当前默认中英顺序，缺语言文案就跳过。
                    for (String locale : AnnouncementClient.settings().languages) {
                        if (queue.size() >= 4) break;
                        String destination = train.destination()==null?"":train.destination();
                        Set<String> conditions = Set.of(speaker.allStops() ? "allStops" : "limitedService");
                        var resolved = AnnouncementClient.resolve(AnnouncementRegistry.INSTANCE.snapshot(), speaker.template(), speaker.variant(), locale,
                                destination.isBlank() ? Map.of() : Map.of("tripDestination", destination), conditions);
                        if (resolved.isPresent()) queue.add(new Pending(event, now + Math.max(20000,speaker.leadSeconds()*1000L+5000), AnnouncementClient.generate(resolved.get(), false), positions));
                    }
                }
            }
        } catch (RuntimeException ignored) { /* MTR 数据快照暂不可用时跳过，不猜测邻线或目的地。 */ }
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
                if (!validEvents.contains(pending.event()) || pending.deadline() < now || pending.audio().isCompletedExceptionally() || pending.audio().isCancelled()) { queue.remove(); continue; }
                if (!pending.audio().isDone()) break;
                queue.remove(); var artifact = pending.audio().getNow(null); if (artifact == null) continue;
                List<BlockPos> positions = pending.positions().stream().filter(pos -> SPEAKERS.containsKey(pos)
                        && client.player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 1024).toList();
                if (!positions.isEmpty()) {
                    var started=CachedAudioPlayback.playSpeakers(artifact,positions);
                    if(started.isEmpty()){queue.addFirst(pending);break;}
                    PLAYING.put(group.getKey(),started);PLAYING_UNTIL.remove(group.getKey());
                    HARD_DEADLINE.put(group.getKey(),now+CachedAudioPlayback.SEQUENCE_TIMEOUT_MILLIS);
                }
                break;
            }
        }
        QUEUES.entrySet().removeIf(e -> e.getValue().isEmpty() && !PLAYING.containsKey(e.getKey()) && PLAYING_UNTIL.getOrDefault(e.getKey(), 0L) < now);
        PLAYING_UNTIL.entrySet().removeIf(e -> e.getValue() < now);
        if (PROGRESS.size() > 1024) { PROGRESS.clear(); RUNS.clear(); }
    }
    private static void prewarm(List<BlockEntityAnnouncementSpeaker> speakers,List<com.fangsu.utils.MtrUtil.PidsArrivalInfo> predictions,long now){
        Set<String> groups=new HashSet<>();
        for(var speaker:speakers){
            String key=speaker.bindings()+"/"+speaker.template()+"/"+speaker.variant()+"/"+speaker.allStops();if(!groups.add(key))continue;
            int count=0;
            for(var row:predictions){
                if(row.arrivalMillis<=now||speaker.bindings().stream().noneMatch(b->b.routeId()==row.routeId&&b.platformId()==row.platformId))continue;
                String destination=row.customDestination!=null&&!row.customDestination.isBlank()?row.customDestination:row.destination;
                if(destination==null||destination.isBlank())continue;
                for(String locale:AnnouncementClient.settings().languages)AnnouncementClient.resolve(AnnouncementRegistry.INSTANCE.snapshot(),speaker.template(),speaker.variant(),locale,
                        Map.of("tripDestination",destination),Set.of(speaker.allStops()?"allStops":"limitedService")).ifPresent(AnnouncementClient::preSynthesize);
                if(++count>=2)break;
            }
        }
    }
}
