package com.fangsu.announcements.playback;

import com.fangsu.Main;
import com.fangsu.announcements.cache.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;

/** 一条播报仅持有临时片段顺序，声音引擎按需逐片段读取；不拼接或保存完整 PCM/WAV。 */
public final class CachedAudioPlayback {
    private static final int MAX_VOICES=64;
    public static final long SEQUENCE_TIMEOUT_MILLIS=180000L+11500;
    private static final long MAX_SEQUENCE_FRAMES=AudioSequence.SAMPLE_RATE*180L;
    private static final long START_TIMEOUT_NANOS=10_000_000_000L;
    private static final Map<ResourceLocation,Lease> FILES=new HashMap<>();
    private static final Map<CachedSound,Voice> ACTIVE=new IdentityHashMap<>();
    private static CachedSound preview;
    private static final class Lease {
        final ResourceLocation file;final AudioSequence sequence;
        final Set<SequenceStream> streams=Collections.newSetFromMap(new IdentityHashMap<>());
        int references;boolean retired;
        Lease(ResourceLocation file,AudioSequence sequence){this.file=file;this.sequence=sequence;}
    }
    private static final class Voice {
        final Lease lease;final boolean station;final long submitted=System.nanoTime();boolean started;
        Voice(Lease lease,boolean station){this.lease=lease;this.station=station;}
    }
    private static final class Cleanup {
        final List<CachedSound> sounds=new ArrayList<>();final List<SequenceStream> streams=new ArrayList<>();
        void run(){
            var manager=Minecraft.getInstance().getSoundManager();for(var sound:sounds)manager.stop(sound);
            for(var stream:streams)try{stream.close();}catch(IOException e){Main.LOGGER.warn("[FangSu] Announcement stream could not be closed ({})",e.getClass().getSimpleName());}
        }
    }
    private static Lease lease(AudioSequence sequence){
        ResourceLocation id=new ResourceLocation("fangsu","announcement_cache/"+UUID.randomUUID());
        Lease lease=new Lease(new ResourceLocation("fangsu","sounds/"+id.getPath()+".ogg"),sequence);FILES.put(lease.file,lease);return lease;
    }
    private static ResourceLocation soundId(Lease lease){String path=lease.file.getPath();return new ResourceLocation("fangsu",path.substring(7,path.length()-4));}
    private static void add(CachedSound sound,Lease lease,boolean station){ACTIVE.put(sound,new Voice(lease,station));lease.references++;}
    /** 调用方已持有类锁；真正停止引擎和关闭文件放到锁外，避免音频读取线程与主线程互等。 */
    private static void remove(CachedSound sound,Cleanup cleanup){
        Voice voice=ACTIVE.remove(sound);if(voice==null)return;cleanup.sounds.add(sound);if(preview==sound)preview=null;
        if(--voice.lease.references==0){Lease lease=voice.lease;lease.retired=true;FILES.remove(lease.file);cleanup.streams.addAll(lease.streams);lease.streams.clear();}
    }
    private static void makeRoom(Cleanup cleanup){
        if(ACTIVE.size()<MAX_VOICES)return;
        // 极端并发时整体释放最早的临时播放，避免只截断环绕组的一只喇叭。
        var oldest=ACTIVE.entrySet().stream().min(Comparator.comparingInt((Map.Entry<CachedSound,Voice> e)->e.getValue().station?1:0).thenComparingLong(e->e.getValue().submitted)).orElse(null);
        if(oldest==null)return;Lease lease=oldest.getValue().lease;
        for(var sound:new ArrayList<>(ACTIVE.keySet()))if(ACTIVE.get(sound).lease==lease)remove(sound,cleanup);
    }
    public static void play(AudioCacheStore.Artifact artifact){play(AudioSequence.of(artifact));}
    public static void play(AudioSequence sequence){
        stopPreview();prune();Cleanup cleanup=new Cleanup();CachedSound sound;
        synchronized(CachedAudioPlayback.class){makeRoom(cleanup);Lease lease=lease(sequence);sound=new CachedSound(soundId(lease));add(sound,lease,false);preview=sound;}
        cleanup.run();Minecraft.getInstance().getSoundManager().play(sound);
    }
    public static SoundInstance playOnboard(AudioCacheStore.Artifact artifact){return playOnboard(AudioSequence.of(artifact));}
    public static SoundInstance playOnboard(AudioSequence sequence){
        prune();Cleanup cleanup=new Cleanup();CachedSound sound;
        synchronized(CachedAudioPlayback.class){makeRoom(cleanup);Lease lease=lease(sequence);sound=new CachedSound(soundId(lease),SoundSource.VOICE);add(sound,lease,false);}
        cleanup.run();Minecraft.getInstance().getSoundManager().play(sound);return sound;
    }
    public static void playSpeakers(AudioCacheStore.Artifact artifact,List<net.minecraft.core.BlockPos> positions){playSpeakers(AudioSequence.of(artifact),positions);}
    public static List<SoundInstance> playSpeakers(AudioSequence sequence,List<net.minecraft.core.BlockPos> positions){
        prune();List<CachedSound> sounds=new ArrayList<>();
        synchronized(CachedAudioPlayback.class){
            // 保留车内与手动试听各一个名额，试听不会因声源池已满而打断正常站内广播。
            int other=(int)ACTIVE.values().stream().filter(voice->!voice.station).count();
            int available=MAX_VOICES-ACTIVE.size()-Math.max(0,2-other);
            int count=Math.min(available,Math.min(16,positions.size()));if(count<=0)return List.of();
            Lease lease=lease(sequence);float gain=(float)(1/Math.sqrt(count));
            // 同一提交批次、同一片段时间轴，各只喇叭仍保留独立空间声源。
            for(int i=0;i<count;i++){var sound=new CachedSound(soundId(lease));var pos=positions.get(i);sound.position(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,gain);add(sound,lease,true);sounds.add(sound);}
        }
        var manager=Minecraft.getInstance().getSoundManager();sounds.forEach(manager::play);return List.copyOf(sounds);
    }
    public static boolean isPlayingOrPending(SoundInstance sound){
        if(sound==null)return false;var manager=Minecraft.getInstance().getSoundManager();
        synchronized(CachedAudioPlayback.class){
            var voice=sound instanceof CachedSound cached?ACTIVE.get(cached):null;
            if(manager.isActive(sound)){if(voice!=null)voice.started=true;return true;}
            return voice!=null&&!voice.started&&System.nanoTime()-voice.submitted<START_TIMEOUT_NANOS;
        }
    }
    public static void tick(){prune();}
    public static void prune(){
        Cleanup cleanup=new Cleanup();var manager=Minecraft.getInstance().getSoundManager();long now=System.nanoTime();
        synchronized(CachedAudioPlayback.class){
            for(var sound:new ArrayList<>(ACTIVE.keySet())){Voice voice=ACTIVE.get(sound);
                if(manager.isActive(sound))voice.started=true;
                else if(voice.started||now-voice.submitted>START_TIMEOUT_NANOS)remove(sound,cleanup);
            }
        }
        cleanup.run();
    }
    public static void stopSound(SoundInstance sound){
        if(sound==null)return;Cleanup cleanup=new Cleanup();
        synchronized(CachedAudioPlayback.class){if(sound instanceof CachedSound cached)remove(cached,cleanup);}
        cleanup.run();if(!(sound instanceof CachedSound))Minecraft.getInstance().getSoundManager().stop(sound);
    }
    public static void stopSpeakers(){Cleanup cleanup=new Cleanup();synchronized(CachedAudioPlayback.class){for(var sound:new ArrayList<>(ACTIVE.keySet()))if(ACTIVE.get(sound).station)remove(sound,cleanup);}cleanup.run();}
    public static void stopPreview(){Cleanup cleanup=new Cleanup();synchronized(CachedAudioPlayback.class){if(preview!=null)remove(preview,cleanup);}cleanup.run();}
    public static void stop(){Cleanup cleanup=new Cleanup();synchronized(CachedAudioPlayback.class){for(var sound:new ArrayList<>(ACTIVE.keySet()))remove(sound,cleanup);FILES.clear();preview=null;}cleanup.run();}
    public static AudioStream open(ResourceLocation id) throws IOException {
        final Lease lease;
        synchronized(CachedAudioPlayback.class){lease=FILES.get(id);if(lease==null||lease.retired)return null;}
        SequenceStream stream=new SequenceStream(lease.sequence,closed->{synchronized(CachedAudioPlayback.class){lease.streams.remove(closed);}});
        synchronized(CachedAudioPlayback.class){if(!lease.retired){lease.streams.add(stream);return stream;}}
        stream.close();throw new IOException("audio_playback_cancelled");
    }
    private static final class CachedSound extends AbstractSoundInstance {
        CachedSound(ResourceLocation id){this(id,SoundSource.BLOCKS);}
        CachedSound(ResourceLocation id,SoundSource category){
            //#if MC_VERSION >= 11900
            super(id,category,net.minecraft.util.RandomSource.create());
            //#else
            //$$ super(id,category);
            //#endif
            volume=pitch=1;relative=true;attenuation=SoundInstance.Attenuation.NONE;
        }
        void position(double px,double py,double pz,float gain){x=px;y=py;z=pz;volume=gain;relative=false;attenuation=SoundInstance.Attenuation.LINEAR;}
        @Override public WeighedSoundEvents resolve(SoundManager manager){
            //#if MC_VERSION >= 11900
            sound=new Sound(location.toString(),net.minecraft.util.valueproviders.ConstantFloat.of(1),net.minecraft.util.valueproviders.ConstantFloat.of(1),1,Sound.Type.FILE,true,false,32);
            //#else
            //$$ sound=new Sound(location.toString(),1,1,1,Sound.Type.FILE,true,false,32);
            //#endif
            WeighedSoundEvents event=new WeighedSoundEvents(location,null);event.addSound(sound);return event;
        }
    }
    private static final class SequenceStream implements AudioStream {
        private static final AudioFormat FORMAT=new AudioFormat(AudioSequence.SAMPLE_RATE,16,1,true,false);
        private final AudioSequence sequence;private final Consumer<SequenceStream> onClose;
        private int index;private FragmentReader current;private boolean closed;private long emittedFrames;
        SequenceStream(AudioSequence sequence,Consumer<SequenceStream> onClose){this.sequence=sequence;this.onClose=onClose;}
        @Override public AudioFormat getFormat(){return FORMAT;}
        @Override public synchronized ByteBuffer read(int size) throws IOException {
            int frames=(int)Math.min(Math.min(Math.max(size/2,0),131072),MAX_SEQUENCE_FRAMES-emittedFrames);
            ByteBuffer output=ByteBuffer.allocateDirect(frames*2).order(ByteOrder.LITTLE_ENDIAN);
            if(closed||frames==0){if(!closed&&emittedFrames>=MAX_SEQUENCE_FRAMES)close();output.flip();return output;}
            try{
                while(output.hasRemaining()){
                    if(current==null){if(index>=sequence.parts().size()){close();break;}current=new FragmentReader(sequence.parts().get(index++));}
                    while(output.hasRemaining()&&current.hasRemaining())output.putShort(current.next());
                    if(!current.hasRemaining()){current.close();current=null;}
                }
                emittedFrames+=output.position()/2;
                // 替换后的片段可长于原队列元数据；总预算按实际送给声卡的帧数计算。
                if(emittedFrames>=MAX_SEQUENCE_FRAMES)close();output.flip();return output;
            }catch(IOException|RuntimeException e){try{close();}catch(IOException close){e.addSuppressed(close);}throw e;}
        }
        @Override public synchronized void close() throws IOException {
            if(closed)return;closed=true;
            try{if(current!=null)current.close();}finally{current=null;onClose.accept(this);}
        }
    }
    private static final class FragmentReader implements Closeable {
        private final FileChannel file;private final WavInfo info;private final int frames,stride;private final long outputFrames;
        private final ByteBuffer input=ByteBuffer.allocate(16384).order(ByteOrder.LITTLE_ENDIAN);
        private int bufferedStart=-1,bufferedFrames;private long outputIndex;
        FragmentReader(AudioCacheStore.Artifact artifact) throws IOException {
            // 入队后至真正开始这段声音前仍可能发生替换或删除，按稳定键读取当前代际。
            artifact=AudioCacheStore.currentArtifact(artifact);
            info=artifact.audio();stride=info.channels()*2;frames=info.dataLength()/stride;outputFrames=AudioSequence.outputFrames(info);
            FileChannel opened=FileChannel.open(artifact.file(),StandardOpenOption.READ);
            try{if(opened.size()<(long)info.dataOffset()+info.dataLength())throw new EOFException("truncated_cache_audio");file=opened;}
            catch(IOException|RuntimeException e){opened.close();throw e;}
        }
        boolean hasRemaining(){return outputIndex<outputFrames;}
        short next() throws IOException {
            double position=outputIndex++*(double)info.sampleRate()/AudioSequence.SAMPLE_RATE;int a=Math.min(frames-1,(int)position),b=Math.min(frames-1,a+1);
            int first=sample(a),second=sample(b);return (short)Math.round(first+(second-first)*(position-a));
        }
        private int sample(int frame) throws IOException {
            if(frame<bufferedStart||frame>=bufferedStart+bufferedFrames){
                bufferedStart=frame;bufferedFrames=Math.min(frames-frame,input.capacity()/stride);input.clear();input.limit(bufferedFrames*stride);file.position((long)info.dataOffset()+(long)frame*stride);
                while(input.hasRemaining()){int n=file.read(input);if(n<0)throw new EOFException("truncated_cache_audio");if(n==0)throw new IOException("cache_audio_read_failed");}input.flip();
            }
            int offset=(frame-bufferedStart)*stride,first=input.getShort(offset);return info.channels()==1?first:(first+input.getShort(offset+2))/2;
        }
        @Override public void close() throws IOException {file.close();}
    }
}
