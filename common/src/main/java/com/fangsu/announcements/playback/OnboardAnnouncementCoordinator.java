package com.fangsu.announcements.playback;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.cache.AudioSequence;
import com.fangsu.announcements.definition.AnnouncementRegistry;
import com.fangsu.announcements.definition.TrainAnnouncementRegistry;
import com.fangsu.announcements.mtradapter.*;
import com.fangsu.announcements.recordings.*;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 模组本体车内播报：乘车会话、阶段锁存、逐语言字幕、录音/云端排队统一管理。 */
public final class OnboardAnnouncementCoordinator {
    private record Pending(String event, String phase, String locale, String text, RecordingFamily.Clip recording,
                           CompletableFuture<AudioSequence> cloud, long queuedAt, List<RecordingFamily.Clip> fallback) { }
    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static final Set<String> SUBTITLES = new HashSet<>();
    private static final Set<String> SEEN = new LinkedHashSet<>();
    private static Object world;
    private static long vehicle = Long.MIN_VALUE, arrivedPlatform;
    private static double progress = Double.NaN, arrivalProgress;
    private static int run, ticks, startedTick;
    private static long deadline;
    private static SoundInstance playing;
    private static String selection = "", playingPhase = "", subtitleEvent = "";
    private static boolean ownsSession;
    public static boolean ownsSession() { return ownsSession; }
    public static void reset() {
        stopCurrent(); QUEUE.clear(); SEEN.clear(); SUBTITLES.clear(); vehicle = Long.MIN_VALUE; progress = Double.NaN;
        arrivedPlatform = 0; run = 0; ownsSession = false; subtitleEvent = "";
    }
    private static void stopCurrent() {
        if (playing != null) CachedAudioPlayback.stopSound(playing);
        playing = null;
        playingPhase = "";
    }
    public static void tick(Minecraft client) {
        ticks++;
        String mode = AnnouncementClient.settings().onboardMode;
        if (world != client.level) { reset(); world = client.level; }
        if (client.player == null || mode.equals("off")) { if (ownsSession) reset(); return; }
        if (ticks % 2 != 0) return;
        RidingAnnouncementState state;
        try { state = MtrAnnouncementAdapter.riding(); }
        catch (RuntimeException e) { return; } // 暂无完整站序时不猜目的地。
        if (state == null) { if (ownsSession) reset(); return; }
        var profile = TrainAnnouncementRegistry.match(state.vehicleType());
        if (profile == null) { if (ownsSession) reset(); return; }
        String selected = profile.id() + "/" + profile.source() + "/" + profile.variant() + "/" + profile.mode();
        if (vehicle != state.vehicleId() || !selection.equals(selected)) { reset(); vehicle = state.vehicleId(); selection = selected; }
        if (!ownsSession) LegacyRecordingAudio.stop();
        ownsSession = true;
        if(ticks%40==0&&state.onRoute()&&profile.mode().equals("cloud")&&AnnouncementClient.settings().preSynthesize&&AnnouncementClient.settings().enabled){
            for(var upcoming:MtrAnnouncementAdapter.forecast())if(upcoming.vehicleId()==state.vehicleId())
                for(String stage:List.of("next","arriving","arrive"))for(String language:profile.languages())resolveCloud(upcoming,stage,profile,language,true).ifPresent(AnnouncementClient::preSynthesize);
        }
        if (Double.isFinite(progress) && state.progress() + 10 < progress) { run++; SEEN.clear(); arrivedPlatform = 0; QUEUE.clear(); stopCurrent(); }
        progress = state.progress();
        String phase = null;
        if (state.doorsOpen() && state.onRoute()) { phase = "arrive"; arrivedPlatform = state.platformId(); arrivalProgress = state.progress(); }
        else if (arrivedPlatform != 0 && Math.abs(state.progress() - arrivalProgress) < 8) {
            // 停稳后关闭车门仍属同一次到站，不能回到即将到站。
        } else if (state.onRoute()) {
            if (state.remaining() >= -1 && state.remaining() <= profile.approachingDistance() && arrivedPlatform != state.platformId()) phase = "arriving";
            else if (state.remaining() > profile.approachingDistance()) { phase = "next"; arrivedPlatform = 0; }
        }
        if (phase != null) {
            String key = run + "/" + state.routeId() + "/" + state.stopIndex() + "/" + state.platformId() + "/" + phase;
            if (SEEN.add(key)) {
                if (SEEN.size() > 256) SEEN.remove(SEEN.iterator().next());
                enqueue(state, phase, key, profile);
            }
        }
        long now = System.currentTimeMillis();
        QUEUE.removeIf(item -> now - item.queuedAt() > 300000);
        if (playing != null) {
            if (ticks - startedTick < 3) return;
            if (CachedAudioPlayback.isPlayingOrPending(playing) && now < deadline) return;
            stopCurrent();
        }
        while (!QUEUE.isEmpty()) {
            Pending item = QUEUE.peek();
            showSubtitles(client, item);
            if (item.cloud() != null && !item.cloud().isDone()) {
                long waiting = now - item.queuedAt();
                if (!item.fallback().isEmpty() && waiting >= 250) { QUEUE.remove(); fallback(item); continue; }
                if (waiting >= 20000) { QUEUE.remove(); continue; }
                return;
            }
            QUEUE.remove();
            if (item.cloud() != null && (item.cloud().isCompletedExceptionally() || item.cloud().isCancelled())) { fallback(item); continue; }
            if (item.recording() != null) {
                playing = ResourceSoundPlayback.playOnboard(item.recording().sound());
                deadline=now+(long)(item.recording().seconds()*1000)+1500;
            } else {
                var artifact = item.cloud().getNow(null); if (artifact == null) continue;
                playing = CachedAudioPlayback.playOnboard(artifact);
                // 队列中的片段可在播放前被本地替换；自然结束看声源状态，硬超时只使用总预算。
                deadline=now+CachedAudioPlayback.SEQUENCE_TIMEOUT_MILLIS;
            }
            playingPhase = item.phase();
            startedTick = ticks;
            return;
        }
    }
    private static void showSubtitles(Minecraft client, Pending item) {
        if (!subtitleEvent.equals(item.event())) {
            subtitleEvent = item.event();
            SUBTITLES.clear();
        }
        if (item.text().isBlank() || !SUBTITLES.add(item.locale())) return;
        List<String> text = new ArrayList<>();
        for (Pending clip : QUEUE) {
            if (!clip.event().equals(item.event())) break;
            if (clip.locale().equals(item.locale()) && !clip.text().isBlank()) text.add(clip.text());
        }
        client.player.displayClientMessage(ComponentHelper.literal("[" + item.locale() + "] " + String.join(" ", text)), false);
    }
    private static void fallback(Pending item) {
        List<RecordingFamily.Clip> fallback = item.fallback();
        for (int i = fallback.size()-1; i >= 0; i--) {
            var clip = fallback.get(i);
            QUEUE.addFirst(new Pending(item.event(), item.phase(), clip.locale(), clip.text(), clip, null, item.queuedAt(), List.of()));
        }
    }
    private static void enqueue(RidingAnnouncementState state, String phase, String event, TrainAnnouncementRegistry.Profile profile) {
        String mode = profile.mode(); RecordingFamily family = profile.recording();
        List<RecordingFamily.Clip> recordings = family == null ? List.of() : family.resolve(phase, state.context(), profile.languages());
        String template = profile.templates().get(phase);
        if (mode.equals("cloud") && template == null) return;
        // 原包没有到站模板时保持空模板语义，仍锁存事件，不凭空生成到站文案。
        if (mode.equals("recordings") && recordings.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Pending> batch = new ArrayList<>();
        if (mode.equals("recordings")) {
            for (var clip : recordings) batch.add(new Pending(event, phase, clip.locale(), clip.text(), clip, null, now, List.of()));
        } else for (String locale : profile.languages()) {
            var resolved=resolveCloud(state,phase,profile,locale,false);
            if (resolved.isPresent()) {
                var utterance = resolved.get();
                batch.add(new Pending(event, phase, locale, utterance.displayText(), null, AnnouncementClient.generate(utterance, false), now, recordings.stream().filter(clip -> clip.locale().equals(locale)).toList()));
            } else for (var clip : recordings) if (clip.locale().equals(locale)) batch.add(new Pending(event, phase, locale, clip.text(), clip, null, now, List.of()));
        }
        if (batch.isEmpty()) return;
        // 下一站提示可以过期。接近和到站各自排队，开门时保留尚未播完的接近提示。
        QUEUE.removeIf(item -> item.phase().equals("next"));
        if (playingPhase.equals("next")) stopCurrent();
        QUEUE.addAll(batch);
    }
    private static Optional<com.fangsu.announcements.definition.AnnouncementDefinitions.ResolvedUtterance> resolveCloud(RidingAnnouncementState state,String phase,TrainAnnouncementRegistry.Profile profile,String locale,boolean warm){
        String template=profile.templates().get(phase);if(template==null)return Optional.empty();
        var context=state.context();
            // 保留原始双语站名，让解析器分别生成英文字幕和中文站名读音。
            String station = context.station().name(), destination = context.destination().name();
            Map<String,String> variables = new HashMap<>(); variables.put("station", station); variables.put("nextStation", station); variables.put("tripDestination", destination);
            variables.put("route", RecordingFamily.name(context.route().name(), Locale.forLanguageTag(locale).getLanguage()).strip());
            Set<String> conditions = new HashSet<>();
            conditions.add(context.terminal()?"terminal":"notTerminal");
            if(context.first())conditions.add("firstStop");
            if(context.left()||warm)conditions.add("leftDoors");if(context.right()||warm)conditions.add("rightDoors");
            String stationName=RecordingFamily.name(station,locale).strip().toLowerCase(Locale.ROOT);
            if(!stationName.matches(".*(站|station|zhan|zhàn)$"))conditions.add("stationSuffixNeeded");
            List<String> transfers = context.transfers().stream().map(t -> RecordingFamily.name(t.name(), locale)).filter(n -> !n.isBlank()).toList();
            if (!transfers.isEmpty()) { variables.put("interchanges", String.join(locale.startsWith("zh") ? "、" : ", ", transfers)); conditions.add("hasInterchanges"); }
            if (context.left() || context.right()) {
                conditions.add("doorSideKnown"); variables.put("doorSide", locale.startsWith("zh") ? context.left() && context.right() ? "两侧" : context.left() ? "左侧" : "右侧"
                        : context.left() && context.right() ? "both sides" : context.left() ? "the left" : "the right");
            }
            var snapshot = AnnouncementRegistry.INSTANCE.snapshot();
            var definition = snapshot.templates().get(template); if (definition == null) return Optional.empty();
            String selectedVariant = profile.variant().isEmpty() ? definition.defaultVariant() : profile.variant();
            String voice = AnnouncementClient.settings().languageVoices.getOrDefault(template + "/" + selectedVariant + "/" + locale, profile.languageVoices().get(locale));
            return com.fangsu.announcements.resolve.AnnouncementResolver.resolve(snapshot, template, selectedVariant, locale, variables, conditions, voice);
    }
}
