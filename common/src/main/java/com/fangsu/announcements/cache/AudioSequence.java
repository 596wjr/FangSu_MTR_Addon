package com.fangsu.announcements.cache;

import java.util.List;

/** 播放时使用的临时片段顺序；不代表整段音频文件，也不写入磁盘。 */
public record AudioSequence(List<AudioCacheStore.Artifact> parts) {
    public static final int SAMPLE_RATE=32000;
    public AudioSequence {
        parts=List.copyOf(parts);
        if(parts.isEmpty()||parts.size()>128)throw new IllegalArgumentException("fragment_count_limit");
        double seconds=0;long frames=0;
        for(var part:parts){
            var audio=part.audio();
            if(audio==null||audio.sampleRate()<8000||audio.sampleRate()>96000||audio.channels()<1||audio.channels()>2
                    ||audio.dataOffset()<0||audio.dataLength()<=0||audio.dataLength()>24*1024*1024||audio.dataLength()%(audio.channels()*2)!=0
                    ||!Double.isFinite(audio.seconds())||audio.seconds()<=0)throw new IllegalArgumentException("invalid_sequence_audio");
            seconds+=audio.seconds();frames+=outputFrames(audio);
        }
        if(frames<=0)throw new IllegalArgumentException("invalid_sequence_audio");
        if(seconds>180||frames>SAMPLE_RATE*180L)throw new IllegalArgumentException("audio_too_long");
    }
    public static AudioSequence of(AudioCacheStore.Artifact artifact){return new AudioSequence(List.of(artifact));}
    public static long outputFrames(WavInfo audio){return Math.max(1,Math.round(audio.dataLength()/(double)(audio.channels()*2)*SAMPLE_RATE/audio.sampleRate()));}
    public double seconds(){return parts.stream().mapToLong(part->outputFrames(part.audio())).sum()/(double)SAMPLE_RATE;}
}
