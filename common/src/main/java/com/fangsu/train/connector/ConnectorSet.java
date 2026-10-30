package com.fangsu.train.connector;

import com.google.gson.*;
import java.util.*;

/** 一个车型可引用多份风挡；每份定义的 side 决定接管的原版绘制层。 */
public final class ConnectorSet {
    public static final int EXTERIOR=1, INTERIOR=2, BOTH=3;
    public final List<ConnectorDef> definitions;
    public final int mask;
    public final String signature;
    public final boolean skipMismatch;
    private ConnectorSet(List<ConnectorDef> definitions) {
        this.definitions=List.copyOf(definitions);
        int mask=0; boolean skip=false;List<String> ids=new ArrayList<>();
        for(ConnectorDef d:definitions) { mask|=d.mask();skip|=d.mismatch.equals("skip");ids.add(d.mask()+":"+d.id); }
        Collections.sort(ids);this.mask=mask;signature=String.join("|",ids);skipMismatch=skip;
    }
    public static ConnectorSet parse(JsonElement value,String key,JsonObject shared,Map<String,ConnectorDef> cache) {
        List<ConnectorDef> list=new ArrayList<>();
        if(value!=null && value.isJsonArray()) {
            int i=0;for(JsonElement e:value.getAsJsonArray()) add(e,key+"["+(i++)+"]",shared,cache,list);
        } else add(value,key,shared,cache,list);
        return list.isEmpty()?null:new ConnectorSet(list);
    }
    private static void add(JsonElement value,String key,JsonObject shared,Map<String,ConnectorDef> cache,List<ConnectorDef> list) {
        if(value==null || value.isJsonNull()) return;
        String id=key;
        if(value.isJsonPrimitive()) {
            if(!value.getAsJsonPrimitive().isString()) return;
            id=value.getAsString();value=shared.get(id);
            if(value==null || !value.isJsonObject()) throw new IllegalArgumentException("共享 connector 不存在: "+id);
        }
        if(!value.isJsonObject()) throw new IllegalArgumentException("connector 数组元素必须是共享名称或对象");
        String cacheKey=id+":"+value;
        ConnectorDef d=cache.get(cacheKey);
        if(d==null) { d=new ConnectorDef(value.getAsJsonObject(),id);cache.put(cacheKey,d); }
        if(!list.contains(d)) list.add(d);
    }
}
