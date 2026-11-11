package com.fangsu.announcements.cache;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 本地导入只复制解码后的声音，不上传、不修改原文件。所有文件共用同一条批处理队列。 */
public final class AudioImporter {
    public record Failure(String file,String reason) { }
    public record Result(int imported,int duplicates,List<Failure> failures) {
        public Result { failures=List.copyOf(failures); }
    }
    private static final int MAX_BYTES=24*1024*1024;
    public static byte[] decode(Path file) throws IOException {
        if(!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES)throw new IOException("import_size");
        byte[] input;
        try(var stream=Files.newInputStream(file)){input=stream.readNBytes(MAX_BYTES+1);}
        if(input.length>MAX_BYTES)throw new IOException("import_size");
        if(input.length>=4&&input[0]=='O'&&input[1]=='g'&&input[2]=='g'&&input[3]=='S')return ogg(input);
        try(var source=AudioSystem.getAudioInputStream(new ByteArrayInputStream(input))){
            AudioFormat format=source.getFormat();int channels=format.getChannels(),rate=Math.round(format.getSampleRate());
            validate(channels,rate);
            AudioFormat target=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,rate,16,channels,channels*2,rate,false);
            try(var converted=AudioSystem.getAudioInputStream(target,source)){
                int max=(int)Math.min(MAX_BYTES-44,(long)rate*channels*2*180);
                ByteArrayOutputStream pcm=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int read;
                while((read=converted.read(chunk))!=-1){
                    if(read==0)continue;
                    if(pcm.size()+(long)read>max)throw new IOException("import_duration");
                    pcm.write(chunk,0,read);
                }
                return wav(pcm.toByteArray(),channels,rate);
            }
        }catch(UnsupportedAudioFileException|IllegalArgumentException e){throw new IOException("import_format",e);}
    }
    private static void validate(int channels,int rate) throws IOException {
        if(channels<1||channels>2||rate<8000||rate>96000)throw new IOException("import_format");
    }
    private static byte[] ogg(byte[] bytes) throws IOException {
        ByteBuffer input=MemoryUtil.memAlloc(bytes.length);long decoder=0;
        try(MemoryStack stack=MemoryStack.stackPush()){
            input.put(bytes).flip();IntBuffer error=stack.mallocInt(1);
            decoder=STBVorbis.stb_vorbis_open_memory(input,error,null);if(decoder==0)throw new IOException("import_format");
            STBVorbisInfo info=STBVorbis.stb_vorbis_get_info(decoder,STBVorbisInfo.malloc(stack));
            int channels=info.channels(),rate=info.sample_rate();validate(channels,rate);
            int max=(int)Math.min(MAX_BYTES-44,(long)rate*channels*2*180);
            ShortBuffer chunk=stack.mallocShort(8192);ByteArrayOutputStream pcm=new ByteArrayOutputStream();int frames;
            while((frames=STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder,channels,chunk))>0){
                int samples=frames*channels;if(pcm.size()+(long)samples*2>max)throw new IOException("import_duration");
                for(int i=0;i<samples;i++){short value=chunk.get(i);pcm.write(value&255);pcm.write((value>>>8)&255);}
            }
            return wav(pcm.toByteArray(),channels,rate);
        }finally{if(decoder!=0)STBVorbis.stb_vorbis_close(decoder);MemoryUtil.memFree(input);}
    }
    private static byte[] wav(byte[] pcm,int channels,int rate) throws IOException {
        ByteBuffer wav=ByteBuffer.allocate(44+pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+pcm.length).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);byte[] result=wav.array();WavInfo.inspect(result);return result;
    }
    public static Result importFiles(AudioLibrary library,AudioCacheStore cache,List<Path> paths){
        int imported=0,duplicates=0;List<Failure> failures=new ArrayList<>();
        for(Path file:paths){
            String name=file.getFileName()==null?file.toString():file.getFileName().toString();
            try{
                byte[] audio=decode(file);String text=name;int dot=text.lastIndexOf('.');if(dot>0)text=text.substring(0,dot);
                if(text.length()>4096)throw new IOException("import_name");
                Map<String,String> parameters=Map.of("protocol","imported-audio-v1","contentHash",SynthesisKey.digest(audio),"fileName",name);
                String key=SynthesisKey.of(parameters);
                if(cache.find(key).isPresent()){duplicates++;continue;}
                library.remember(key,new AudioLibrary.Recipe("imported",text,text,"und","fangsu:imported","local","default","imported","",parameters,List.of()));
                cache.restore(key);cache.publish(key,cache.epoch(key),audio);imported++;
            }catch(IOException|RuntimeException e){String reason=e.getMessage();failures.add(new Failure(name,reason!=null&&reason.startsWith("import_")?reason:"import_failed"));}
        }
        return new Result(imported,duplicates,failures);
    }
}
