package com.fangsu.platform;

import com.google.gson.*;
import java.util.*;

/** 一个可编辑的重复单元；门保持原生一米门扇，玻璃和吊板由统一宿主明确适配宽度。 */
public record PlatformDoorSettings(double unitLength,double firstOffset,double baselineOffset,int units,boolean fillGlass,List<Part> components,String boardRoutes,String boardScript,Unit firstUnit,Unit lastUnit,boolean fitLength,boolean reverse,boolean directionChosen) {
    public PlatformDoorSettings(double unitLength,double firstOffset,double baselineOffset,int units,boolean fillGlass,List<Part> components){this(unitLength,firstOffset,baselineOffset,units,fillGlass,components,"[]","fangsu:diaoban/mtr_diaoban_station.js");}
    public PlatformDoorSettings(double length,double first,double baseline,int count,boolean fill,List<Part> parts,String routes,String script){this(length,first,baseline,count,fill,parts,routes,script,null,null,false,false,false);}
    public record Unit(double length,List<Part> components){public Unit{if(!Double.isFinite(length)||length<.05||length>128||components==null||components.isEmpty()||components.size()>32)throw new IllegalArgumentException("invalid_door_unit");components=List.copyOf(components);for(var part:components)if(part.offset()>length)throw new IllegalArgumentException("door_part_outside_unit");}}
    public PlatformDoorSettings withFacing(boolean reversed){return new PlatformDoorSettings(unitLength,firstOffset,baselineOffset,units,fillGlass,components,boardRoutes,boardScript,firstUnit,lastUnit,fitLength,reversed,true);}
    public PlatformDoorSettings freshFacing(){return new PlatformDoorSettings(unitLength,firstOffset,baselineOffset,units,fillGlass,components,boardRoutes,boardScript,firstUnit,lastUnit,fitLength,false,false);}
    public static final String DOOR_MODEL="fangsu:screendoor/kaba.json",BOARD_MODEL="fangsu:diaoban/mtr_diaoban.json";
    public record Part(String kind,double offset,double lateral,double vertical,double width,double height,String model,String variant,boolean automatic,String secondaryVariant,Map<String,String> options) {
        public Part(String kind,double offset,double lateral,double vertical,double width,double height,String model,String variant,boolean automatic,String secondaryVariant){this(kind,offset,lateral,vertical,width,height,model,variant,automatic,secondaryVariant,Map.of());}
        public Part(String kind,double offset,double lateral,double vertical,double width,double height,String model,String variant){this(kind,offset,lateral,vertical,width,height,model,variant,false,variant);}
        public Part {
            options=checkOptions(options);
            if(secondaryVariant==null||!secondaryVariant.matches("[A-Za-z0-9_.-]{1,64}"))throw new IllegalArgumentException("invalid_door_part");
            if(!Set.of("door","glass","board").contains(kind)||!Double.isFinite(width+height)||width<.05||width>(kind.equals("board")?128:8)||height<.05||height>8||model==null||model.length()>256||model.contains("..")||!model.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||variant==null||!variant.matches("[A-Za-z0-9_.-]{1,64}"))throw new IllegalArgumentException("invalid_door_part");
            if(!Double.isFinite(offset+lateral+vertical)||offset<0||offset>128||Math.abs(lateral)>4||vertical<0||vertical>8
                    ||kind.equals("door")&&(Math.abs(width-Math.rint(width))>1e-7||width<1||width>2||Math.abs(height-2)>.001)
                    ||kind.equals("glass")&&width>8)throw new IllegalArgumentException("invalid_door_part");
        }
    }
    public static final PlatformDoorSettings DEFAULT=new PlatformDoorSettings(6,0,0,0,true,List.of(
            new Part("door",2,0,0,2,2,DOOR_MODEL,"common"),new Part("board",2,0,2.5,2,1,BOARD_MODEL,"mtr_diaoban_a")),"[]","fangsu:diaoban/mtr_diaoban_station.js",null,null,true,false,false);
    public PlatformDoorSettings {
        if(!Double.isFinite(unitLength+firstOffset+baselineOffset)||unitLength<1||unitLength>128||firstOffset<0||firstOffset>2048||Math.abs(baselineOffset)>8||units<0||units>128||components==null||components.isEmpty()||components.size()>32)throw new IllegalArgumentException("invalid_door_settings");
        new PlatformDoorBoardContent(boardScript,boardRoutes);
        components=List.copyOf(components);for(var c:components)if(c.offset()>unitLength)throw new IllegalArgumentException("door_part_outside_unit");
    }
    public static String encode(PlatformDoorSettings s){JsonObject j=new JsonObject();j.addProperty("version",1);j.addProperty("fitLength",s.fitLength());j.addProperty("reverse",s.reverse());j.addProperty("directionChosen",s.directionChosen());if(s.firstUnit()!=null)j.add("firstUnit",writeUnit(s.firstUnit()));if(s.lastUnit()!=null)j.add("lastUnit",writeUnit(s.lastUnit()));j.addProperty("unitLength",s.unitLength());j.addProperty("firstOffset",s.firstOffset());j.addProperty("baselineOffset",s.baselineOffset());j.addProperty("units",s.units());j.addProperty("fillGlass",s.fillGlass());j.addProperty("boardRoutes",s.boardRoutes());j.addProperty("boardScript",s.boardScript());JsonArray rows=new JsonArray();for(var p:s.components()){JsonObject r=new JsonObject();r.addProperty("kind",p.kind());r.addProperty("offset",p.offset());r.addProperty("lateral",p.lateral());r.addProperty("vertical",p.vertical());r.addProperty("width",p.width());r.addProperty("height",p.height());r.addProperty("model",p.model());r.addProperty("variant",p.variant());r.addProperty("automatic",p.automatic());r.addProperty("secondaryVariant",p.secondaryVariant());r.add("options",new Gson().toJsonTree(p.options()));rows.add(r);}j.add("components",rows);return j.toString();}
    public static PlatformDoorSettings decode(String text){
        if(text==null||text.length()>32767)throw new IllegalArgumentException("invalid_door_settings");JsonObject j=JsonParser.parseString(text).getAsJsonObject();if(j.get("version").getAsInt()!=1)throw new IllegalArgumentException("invalid_door_settings");JsonArray rows=j.getAsJsonArray("components");if(rows.size()>32)throw new IllegalArgumentException("invalid_door_settings");List<Part> parts=new ArrayList<>();for(var v:rows){var r=v.getAsJsonObject();parts.add(new Part(r.get("kind").getAsString(),r.get("offset").getAsDouble(),r.get("lateral").getAsDouble(),r.get("vertical").getAsDouble(),r.get("width").getAsDouble(),r.get("height").getAsDouble(),r.get("model").getAsString(),r.get("variant").getAsString(),r.has("automatic")&&r.get("automatic").getAsBoolean(),r.has("secondaryVariant")?r.get("secondaryVariant").getAsString():r.get("variant").getAsString(),readOptions(r)));}return new PlatformDoorSettings(j.get("unitLength").getAsDouble(),j.get("firstOffset").getAsDouble(),j.get("baselineOffset").getAsDouble(),j.get("units").getAsInt(),j.get("fillGlass").getAsBoolean(),parts,j.has("boardRoutes")?j.get("boardRoutes").getAsString():"[]",j.has("boardScript")?j.get("boardScript").getAsString():"fangsu:diaoban/mtr_diaoban_station.js",j.has("firstUnit")?readUnit(j.getAsJsonObject("firstUnit")):null,j.has("lastUnit")?readUnit(j.getAsJsonObject("lastUnit")):null,j.has("fitLength")&&j.get("fitLength").getAsBoolean(),j.has("reverse")&&j.get("reverse").getAsBoolean(),j.has("directionChosen")&&j.get("directionChosen").getAsBoolean());
    }
    public static Map<String,String> checkOptions(Map<String,String> values){
        if(values==null||values.size()>32)throw new IllegalArgumentException("invalid_door_options");int size=0;
        for(var row:values.entrySet()){if(row.getKey()==null||!row.getKey().matches("[A-Za-z0-9_.-]{1,64}")||row.getValue()==null||row.getValue().length()>4096)throw new IllegalArgumentException("invalid_door_options");size+=row.getKey().length()+row.getValue().length();}
        if(size>8192)throw new IllegalArgumentException("invalid_door_options");
        for(String key:List.of("isAuto","isolation","doorOpenOverride","withDoorlight","fullLight"))if(values.containsKey(key)&&!Set.of("true","false").contains(values.get(key)))throw new IllegalArgumentException("invalid_door_options");
        for(String key:List.of("doorSide","arrowDirection","length"))if(values.containsKey(key)){int n=Integer.parseInt(values.get(key));if(n<0||n>(key.equals("length")?128:2))throw new IllegalArgumentException("invalid_door_options");}
        if(values.containsKey("drawScript")||values.containsKey("routes"))new PlatformDoorBoardContent(values.getOrDefault("drawScript",PlatformDoorBoardContent.DEFAULT_DRAWING),values.getOrDefault("routes","[]"));
        return Map.copyOf(values);
    }
    private static Map<String,String> readOptions(JsonObject row){Map<String,String> map=new LinkedHashMap<>();if(row.has("options"))for(var entry:row.getAsJsonObject("options").entrySet())map.put(entry.getKey(),entry.getValue().getAsString());return checkOptions(map);}
    private static JsonObject writeUnit(Unit unit){var j=new JsonObject();j.addProperty("length",unit.length());var value=new PlatformDoorSettings(unit.length(),0,0,1,false,unit.components());j.add("components",JsonParser.parseString(encode(value)).getAsJsonObject().getAsJsonArray("components"));return j;}
    private static Unit readUnit(JsonObject j){var row=new JsonObject();row.addProperty("version",1);row.addProperty("unitLength",j.get("length").getAsDouble());row.addProperty("firstOffset",0);row.addProperty("baselineOffset",0);row.addProperty("units",1);row.addProperty("fillGlass",false);row.add("components",j.getAsJsonArray("components"));var value=decode(row.toString());return new Unit(value.unitLength(),value.components());}
}
