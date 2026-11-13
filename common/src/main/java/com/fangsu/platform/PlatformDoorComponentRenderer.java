package com.fangsu.platform;

import com.fangsu.blockEntities.*;
import com.fangsu.blockEntities.client.BaseBlockEntityRender;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.fangsu.customItem.contents.ScreendoorGlassContent;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** 共用方速原门、玻璃与吊板的加载/绘制管线；统一宿主只保存布局描述。 */
public final class PlatformDoorComponentRenderer implements BlockEntityRenderer<BlockEntityAutoPlatformComponent> {
    private final BaseBlockEntityRender<FunctionalObjBlockEntity> renderer;
    private final Map<BlockEntityAutoPlatformComponent,Cached> cache=new IdentityHashMap<>();
    private static final class Cached {
        final long revision,resources,neighbors;final List<FunctionalObjBlockEntity> children=new ArrayList<>();
        long nextNeighborCheck;
        Cached(BlockEntityAutoPlatformComponent host,long neighbors){revision=host.revision();resources=ResourceUtil.getReloadVersion();this.neighbors=neighbors;nextNeighborCheck=host.getLevel().getGameTime()+5;}
        void close(){children.stream().filter(Objects::nonNull).forEach(FunctionalObjBlockEntity::setRemoved);children.clear();}
    }
    private static final class Door extends BlockEntityScreendoor {
        final BlockEntityAutoPlatformComponent host;final PlatformDoorComponent part;
        float partialTick;
        Door(BlockEntityAutoPlatformComponent host,PlatformDoorComponent part){super(host.getBlockPos(),ModBlocks.BLOCK_SCREENDOOR.get().defaultBlockState());this.host=host;this.part=part;}
        @Override public float getDoorValue(){return progress(part,host.getDoorValue());}
        @Override public boolean getDoorTarget(){return part.options().getOrDefault("isolation","false").equals("true")?part.options().getOrDefault("doorOpenOverride","false").equals("true"):host.getDoorTarget();}
        @Override protected double displayDoorProgress(long delta){return progress(part,host.interpolatedProgress(partialTick));}
        @Override public void sendUpdateC2S(){ }
    }
    private static final class Glass extends BlockEntityScreendoorGlass {
        final BlockEntityAutoPlatformComponent host;
        Glass(BlockEntityAutoPlatformComponent host){super(host.getBlockPos(),ModBlocks.BLOCK_SCREENDOOR_GLASS.get().defaultBlockState());this.host=host;}
        @Override protected float adjacentDoorValue(boolean left){return host.getDoorValue();}
        @Override public void sendUpdateC2S(){ }
    }
    private static final class Board extends BlockEntityDiaoban {
        final BlockEntityAutoPlatformComponent host;final PlatformDoorComponent part;
        volatile BoardBounds bounds;
        Board(BlockEntityAutoPlatformComponent host,PlatformDoorComponent part){super(host.getBlockPos(),ModBlocks.BLOCK_DIAOBAN.get().defaultBlockState());this.host=host;this.part=part;bounds=BoardBounds.fallback(part);}
        @Override public void whenLoading(){super.whenLoading();bounds=measureBoard(part);}

        @Override public float getDoorValue(){return host.getDoorValue();}
        @Override public boolean getDoorTarget(){return host.getDoorTarget();}
        @Override public void sendUpdateC2S(){ }
        @Override protected boolean displayUnboundModel(){return true;}
    }
    public PlatformDoorComponentRenderer(BlockEntityRenderDispatcher dispatcher){renderer=new BaseBlockEntityRender<>(dispatcher);}
    private Cached create(BlockEntityAutoPlatformComponent host,Neighborhood neighbors){
        Cached result=new Cached(host,neighbors.signature());
        for(var part:host.components()){
            if(part.kind().endsWith("_proxy")){result.children.add(null);continue;}
            FunctionalObjBlockEntity child=switch(part.kind()){case "door"->new Door(host,part);case "glass"->new Glass(host);default->new Board(host,part);};
            child.setLevel(host.getLevel());CompoundTag tag=new CompoundTag(),sub=new CompoundTag(),extra=new CompoundTag();tag.putString("mainModel",part.model());tag.putBoolean("fullLight",Boolean.parseBoolean(part.options().getOrDefault("fullLight","false")));part.options().forEach(extra::putString);
            if(part.kind().equals("door")){
                boolean right=part.variant().equals("right")||part.variant().startsWith("right.");String style=part.variant().contains(".")?part.variant().substring(part.variant().indexOf('.')+1):"common";
                sub.putString("subModel",style);extra.putString("isAuto","false");extra.putString("doorSide",part.variant().startsWith("flex.")?"2":right?"1":"0");
            }else if(part.kind().equals("glass")){
                var pair=glassVariants(host,part,neighbors);
                sub.putString("subModelLeft",pair[0]);sub.putString("subModelRight",pair[1]);
                // 统一宿主已按世界几何解析 auto，旧实体无需再次按方块朝向计算邻居。
                extra.putString("isAutoLeft","false");extra.putString("isAutoRight","false");
            }else{
                sub.putString("subModel",part.variant());var meta=host.metadata();var content=PlatformDoorBoardContent.resolve(part.options(),meta.getString("drawScript").isEmpty()?PlatformDoorBoardContent.DEFAULT_DRAWING:meta.getString("drawScript"),meta.getString("routes").isEmpty()?"[]":meta.getString("routes"));sub.putString("drawScript",content.drawing());
                extra.putString("length",part.options().getOrDefault("length",String.valueOf(Math.max(2,(int)Math.round(part.width()/boardUnit(part))))));extra.putString("routes",content.routes());extra.putString("withDoorlight",part.options().getOrDefault("withDoorlight","false"));
            }
            tag.put("subModel",sub);tag.put("extraConfig",extra);child.load(tag);result.children.add(child);
        }
        host.installClientDisposer(()->{Cached old=cache.remove(host);if(old!=null)old.close();});return result;
    }
    @Override public void render(BlockEntityAutoPlatformComponent host,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(host.getLevel()==null)return;Cached data=cache.get(host);Neighborhood neighbors=null;
        if(data!=null&&host.getLevel().getGameTime()>=data.nextNeighborCheck){
            data.nextNeighborCheck=host.getLevel().getGameTime()+5;
            if(hasAutomaticGlass(host))neighbors=neighborhood(host);
        }
        if(data==null||data.revision!=host.revision()||data.resources!=ResourceUtil.getReloadVersion()||neighbors!=null&&neighbors.signature()!=data.neighbors){
            if(data!=null)data.close();if(neighbors==null)neighbors=neighborhood(host);data=create(host,neighbors);cache.put(host,data);
            if(cache.size()>1024){var iterator=cache.entrySet().iterator();while(cache.size()>1024&&iterator.hasNext()){var entry=iterator.next();if(entry.getKey()!=host){entry.getValue().close();iterator.remove();}}}
        }
        for(int i=0;i<host.components().size();i++){
            var part=host.components().get(i);if(part.kind().endsWith("_proxy"))continue;
            var child=data.children.get(i);double sx=part.kind().equals("door")?1:part.width(),sy=part.height()/2;
            double tx=-.5,ty=0,tz=-.5+(part.kind().equals("door")?13d/32:9d/32);
            if(child instanceof Door door)door.partialTick=partial;
            if(child instanceof Board board){
                var bounds=board.bounds;sx=part.width()/bounds.width();sy=part.height()/bounds.height();tx-=bounds.centerX();ty=-bounds.minY();tz=-.5-bounds.centerZ();
                board.setDoorTarget(host.getDoorTarget());board.setDoorValue(host.interpolatedProgress(partial));
            }
            poses.pushPose();poses.translate(.5+part.dx(),part.dy(),.5+part.dz());PoseStackUtil.rotY(poses,(float)part.yaw());poses.scale((float)sx,(float)sy,1);poses.translate(tx,ty,tz);
            renderer.render(data.children.get(i),partial,poses,buffers,light,overlay);poses.popPose();
        }
    }

    /** 原生吊板模型高度未必是一格；在后台复用原模型边界，使 height 表示实际米数。 */
    private record BoardBounds(double centerX,double minY,double centerZ,double width,double height){
        static BoardBounds fallback(PlatformDoorComponent part){return new BoardBounds(0,0,-11d/32,boardLength(part)*boardUnit(part),1);}
    }
    private static BoardBounds measureBoard(PlatformDoorComponent part){
        try{
            var content=ContentInfoUtil.getDiaobanContent(part.model(),part.variant());if(content==null)return BoardBounds.fallback(part);
            var models=ResourceUtil.loadPartedModel(new ResourceLocation(content.getModel()),content.isFlipV());var names=content.getSubModel();
            int length=boardLength(part);double unit=boardUnit(part),start=-unit*(length-1)/2;
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(int i=0;i<length;i++){
                String key=i==0?"left":i==length-1?"right":"center",fallback=i==0?"l":i==length-1?"r":"center";
                var raw=models.get(names.getOrDefault(key,fallback));if(raw==null)continue;
                for(var mesh:raw.meshList.values())for(var vertex:mesh.vertices){
                    var p=vertex.position;double x=p.x()+start+i*unit,y=p.y(),z=p.z();
                    if(!Double.isFinite(x+y+z))continue;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                }
            }
            if(Double.isFinite(minX+maxX+minY+maxY+minZ+maxZ)&&maxX-minX>.001&&maxY-minY>.001)return new BoardBounds((minX+maxX)/2,minY,(minZ+maxZ)/2,maxX-minX,maxY-minY);
        }catch(Exception ignored){ }
        return BoardBounds.fallback(part);
    }

    private record Neighborhood(List<BlockEntityAutoPlatformComponent> hosts,long signature){ }
    private static boolean hasAutomaticGlass(BlockEntityAutoPlatformComponent host){return host.components().stream().anyMatch(p->p.kind().equals("glass")&&(p.variant().startsWith("auto|")||Arrays.asList(p.variant().split("\\|",-1)).contains("auto")));}
    /** 邻接描述只含静态布局；开门进度更新不会导致反复重载模型。 */
    private static Neighborhood neighborhood(BlockEntityAutoPlatformComponent host){
        if(!hasAutomaticGlass(host))return new Neighborhood(List.of(host),0);
        var found=new TreeMap<Long,BlockEntityAutoPlatformComponent>();found.put(host.getBlockPos().asLong(),host);
        for(var part:host.components())if(part.kind().equals("glass")){
            var origin=host.getBlockPos();int x=(int)Math.floor(origin.getX()+.5+part.dx()),y=(int)Math.floor(origin.getY()+part.dy()),z=(int)Math.floor(origin.getZ()+.5+part.dz());
            for(var pos:BlockPos.betweenClosed(x-2,y-1,z-2,x+2,y+1,z+2))if(host.getLevel().hasChunkAt(pos)&&host.getLevel().getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent other&&Objects.equals(host.group,other.group))found.put(pos.asLong(),other);
        }
        long signature=1;for(var row:found.entrySet())signature=signature*31+Objects.hash(row.getKey(),row.getValue().components());
        return new Neighborhood(List.copyOf(found.values()),signature);
    }
    private record Neighbor(PlatformDoorComponent part,double distance){ }
    private static Neighbor neighbor(BlockEntityAutoPlatformComponent host,PlatformDoorComponent part,Neighborhood neighborhood,boolean left){
        double x=host.getBlockPos().getX()+.5+part.dx(),y=host.getBlockPos().getY()+part.dy(),z=host.getBlockPos().getZ()+.5+part.dz(),cos=Math.cos(part.yaw()),sin=Math.sin(part.yaw());Neighbor nearest=null;
        for(var other:neighborhood.hosts())for(var candidate:other.components()){
            String kind=candidate.kind().replace("_proxy","");if(!kind.equals("door")&&!kind.equals("glass"))continue;
            double dx=other.getBlockPos().getX()+.5+candidate.dx()-x,dz=other.getBlockPos().getZ()+.5+candidate.dz()-z;
            double along=dx*cos-dz*sin,across=dx*sin+dz*cos,dy=other.getBlockPos().getY()+candidate.dy()-y;
            double rotation=Math.abs(Math.atan2(Math.sin(candidate.yaw()-part.yaw()),Math.cos(candidate.yaw()-part.yaw())));
            double distance=left?-along:along,gap=distance-(part.width()+candidate.width())/2;
            if(distance<.001||Math.abs(dy)>.15||Math.abs(across)>.4||rotation>.5||gap<-.15||gap>.3)continue;
            if(nearest==null||distance<nearest.distance())nearest=new Neighbor(candidate,distance);
        }
        return nearest;
    }
    private static String relation(Neighbor neighbor,String field){
        if(neighbor==null)return "NULL";var part=neighbor.part();boolean door=part.kind().startsWith("door");
        if(field.endsWith("_BLOCK"))return door?"SCREENDOOR_DOOR":"SCREENDOOR_GLASS";
        if(field.endsWith("_DOOR"))return door?(part.variant().contains(".")?part.variant().substring(part.variant().indexOf('.')+1):"common"):"NULL";
        if(door)return "NULL";var pair=part.variant().split("\\|",-1);if(pair.length!=3)return part.variant();
        return pair[0].equals("auto")?"auto":pair[field.endsWith("_LEFT")?1:2];
    }
    private static boolean matches(JsonObject command,Map<String,String> relation){
        if(!command.has("if"))return false;var test=command.get("if");
        if(test.isJsonPrimitive()){
            String key=test.getAsString();if(key.equals("TRUE"))return true;String value=relation.getOrDefault(key,"NULL");
            return command.has("is")?value.equals(command.get("is").getAsString()):command.has("not")&&!value.equals(command.get("not").getAsString());
        }
        if(!test.isJsonObject())return false;
        for(var row:test.getAsJsonObject().entrySet()){
            if(!row.getValue().isJsonObject())return false;var condition=row.getValue().getAsJsonObject();String value=relation.getOrDefault(row.getKey(),"NULL");
            if(condition.has("is")&&!value.equals(condition.get("is").getAsString())||condition.has("not")&&value.equals(condition.get("not").getAsString()))return false;
        }
        return true;
    }
    /** 和普通方速玻璃使用同一套资源包 auto 条件，邻居由曲线构件中心而非整数朝向定位。 */
    private static String[] glassVariants(BlockEntityAutoPlatformComponent host,PlatformDoorComponent part,Neighborhood neighborhood){
        String[] packed=part.variant().split("\\|",-1);boolean automatic=packed.length==3&&packed[0].equals("auto");
        String[] selected=packed.length==3?new String[]{packed[1],packed[2]}:new String[]{part.variant(),part.variant()};
        var entries=List.of(ScreendoorGlassContent.loadLeftEntries(part.model()),ScreendoorGlassContent.loadRightEntries(part.model()));
        var left=neighbor(host,part,neighborhood,true);var right=neighbor(host,part,neighborhood,false);var relations=new HashMap<String,String>();
        for(String side:List.of("LEFT","RIGHT"))for(String field:List.of("BLOCK","DOOR","GLASS_LEFT","GLASS_RIGHT"))relations.put(side+"_"+field,relation(side.equals("LEFT")?left:right,side+"_"+field));
        for(int side=0;side<2;side++){
            var available=entries.get(side);String chosen=selected[side];var entry=available.get(automatic?"auto":chosen);Object autoKey=entry==null?null:entry.get("auto");
            if(automatic||autoKey instanceof String){
                var commands=ScreendoorGlassContent.loadAutoCommands(part.model(),autoKey instanceof String key?key:"auto");
                for(int i=commands.size()-1;i>=0;i--)try{var command=commands.get(i);String key=side==0?"left":"right";if(matches(command,relations)&&command.has(key))chosen=command.get(key).getAsString();}catch(RuntimeException ignored){ }
                if(commands.isEmpty()&&available.containsKey("door")&&(side==0?left:right)!=null&&(side==0?left:right).part().kind().startsWith("door"))chosen="door";
            }
            if(!available.containsKey(chosen)||available.get(chosen).containsKey("auto"))chosen=available.containsKey(selected[side])&&!available.get(selected[side]).containsKey("auto")?selected[side]:available.containsKey("glass")?"glass":available.entrySet().stream().filter(row->!row.getValue().containsKey("auto")).map(Map.Entry::getKey).sorted().findFirst().orElse(chosen);
            selected[side]=chosen;
        }
        return selected;
    }

    private static int boardLength(PlatformDoorComponent part){return Math.max(2,Integer.parseInt(part.options().getOrDefault("length",Integer.toString((int)Math.round(part.width()/boardUnit(part))))));}
    public static float progress(PlatformDoorComponent part,float value){return part.options().getOrDefault("isolation","false").equals("true")?(part.options().getOrDefault("doorOpenOverride","false").equals("true")?1:0):value;}
    private static double boardUnit(PlatformDoorComponent part){try{var content=com.fangsu.utils.ContentInfoUtil.getDiaobanContent(part.model(),part.variant());if(content!=null)return Math.max(1,content.getUnit())/16d;}catch(RuntimeException ignored){}return .5;}
    @Override public boolean shouldRenderOffScreen(BlockEntityAutoPlatformComponent host){return true;}
}
