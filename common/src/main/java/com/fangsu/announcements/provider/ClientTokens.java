package com.fangsu.announcements.provider;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.cache.AudioCacheStore;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 仅客户端使用；凭据独立于普通设置，默认仅保留本次会话。 */
public final class ClientTokens {
    private static final Map<String,String> SESSION = new HashMap<>(), SAVED = new HashMap<>();
    private static final Path FILE = AnnouncementClient.CONFIG.resolve("credentials.json");
    private static boolean loaded;
    private static String key(String provider, String account) { return provider + "/" + account; }
    private static void load() throws IOException {
        if (loaded) return;
        if (Files.exists(FILE)) {
            if (Files.size(FILE)>65536) throw new IOException("credentials_size_limit");
            try {
                JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                if (json.get("schemaVersion").getAsInt()!=1) throw new IOException("credentials_schema");
                for (var entry : json.getAsJsonObject("tokens").entrySet()) {
                    String token=entry.getValue().getAsString(); if(token.length()>2048)throw new IOException("token_size_limit");
                    SAVED.put(entry.getKey(),token);
                }
            } catch(RuntimeException e){SAVED.clear();throw new IOException("invalid_credentials");}
        }
        loaded=true;
    }
    public static synchronized String entered(String provider,String account) throws IOException {load();return SESSION.getOrDefault(key(provider,account),SAVED.getOrDefault(key(provider,account),""));}
    public static synchronized boolean remembered(String provider,String account) throws IOException {load();return SAVED.containsKey(key(provider,account));}
    public static synchronized String resolve(String provider,String account) throws IOException {
        String value=entered(provider,account);if(!value.isBlank())return value;
        return System.getenv(provider.equals("mimo")?"MIMO_API_KEY":provider.equals("minimax-cn")?"MINIMAX_CN_API_KEY":"MINIMAX_API_KEY");
    }
    public static synchronized void save(String provider,String account,String token,boolean remember) throws IOException {
        load();token=token.trim();if(token.length()>2048 || token.contains("\n") || token.contains("\r"))throw new IOException("invalid_token");
        String key=key(provider,account); Map<String,String> next=new HashMap<>(SAVED);
        if(remember&&!token.isBlank())next.put(key,token);else next.remove(key);
        JsonObject json=new JsonObject(),tokens=new JsonObject();json.addProperty("schemaVersion",1);next.forEach(tokens::addProperty);json.add("tokens",tokens);
        byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>65536)throw new IOException("credentials_size_limit");
        AudioCacheStore.atomic(FILE,bytes);SAVED.clear();SAVED.putAll(next);
        if(token.isBlank())SESSION.remove(key);else SESSION.put(key,token);
    }
    public static synchronized void clearSession(){SESSION.clear();SAVED.clear();loaded=false;}
}
