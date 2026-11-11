package com.fangsu.platform;

import com.fangsu.customItem.contents.ContentManager;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.ModelSelectScreen;
import com.fangsu.ui.ThemedConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** 配置器专用的重复单元编辑页；真实放置只使用服务器确认过的预览。 */
public final class PlatformDoorScreen extends ThemedConfigScreen {
    private final Screen parent;
    private String unitLength,firstOffset,baselineOffset,units,boardRoutes,boardScript;
    private boolean fillGlass,confirmRemove,fitLength,reverse,directionChosen,firstEnabled,lastEnabled;
    private int activeUnit;
    private String standardLength,firstLength,lastLength;
    private final List<PlatformDoorSettings.Part> standardParts=new ArrayList<>(),firstParts=new ArrayList<>(),lastParts=new ArrayList<>();
    private double viewStart;
    private ElevationCanvas elevation;
    private final List<PlatformDoorSettings.Part> components=new ArrayList<>();
    private Button previewButton,buildButton,cancelButton,removeButton;
    public PlatformDoorScreen(Screen parent){super(tr("door_title"));this.parent=parent;loadSettings();}
    public void loadSettings(){
        var value=PlatformDoorClient.settings;unitLength=number(value.unitLength());firstOffset=number(value.firstOffset());baselineOffset=number(value.baselineOffset());units=Integer.toString(value.units());
        fitLength=value.fitLength();reverse=value.reverse();directionChosen=value.directionChosen();activeUnit=0;
        standardLength=unitLength;standardParts.clear();standardParts.addAll(value.components());
        firstEnabled=value.firstUnit()!=null;lastEnabled=value.lastUnit()!=null;
        firstLength=number(firstEnabled?value.firstUnit().length():value.unitLength());lastLength=number(lastEnabled?value.lastUnit().length():value.unitLength());
        firstParts.clear();firstParts.addAll(firstEnabled?value.firstUnit().components():value.components());lastParts.clear();lastParts.addAll(lastEnabled?value.lastUnit().components():value.components());
        fillGlass=value.fillGlass();boardRoutes=value.boardRoutes();boardScript=value.boardScript();components.clear();components.addAll(value.components());confirmRemove=false;requestRebuild();
    }
    public void captureFacing(PlatformDoorSettings value){reverse=value.reverse();directionChosen=value.directionChosen();}
    public void updatePreview(){if(elevation==null||!elevation.pressed)requestRebuild();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dragX,double dragY){
        if(button==0&&elevation!=null&&elevation.pressed){elevation.dragTo(mouseX,mouseY,hasShiftDown());return true;}return super.mouseDragged(mouseX,mouseY,button,dragX,dragY);
    }
    @Override public boolean mouseReleased(double mouseX,double mouseY,int button){
        if(button==0&&elevation!=null&&elevation.pressed){var canvas=elevation;setDragging(false);canvas.release(mouseX,mouseY);return true;}return super.mouseReleased(mouseX,mouseY,button);
    }
    @Override protected int getPanelBottom(){return height-54;}
    @Override protected void buildFixedWidgets(){
        int x=getPanelLeft()+6,w=getPanelRight()-x-6,gap=4,col=(w-gap*2)/3,y=height-47;
        previewButton=addFixedWidget(themedButton(x,y,col,18,tr("door_preview"),b->preview()));
        buildButton=primary(addFixedWidget(themedButton(x+col+gap,y,col,18,tr("door_build"),b->PlatformDoorClient.build())));
        cancelButton=addFixedWidget(themedButton(x+(col+gap)*2,y,w-(col+gap)*2,18,tr("cancel"),b->{PlatformDoorClient.cancel();confirmRemove=false;}));
        int half=(w-gap)/2;
        removeButton=danger(addFixedWidget(themedButton(x,y+21,half,18,tr(confirmRemove?"door_confirm_remove":"door_remove"),b->{if(confirmRemove){PlatformDoorClient.remove();confirmRemove=false;}else{confirmRemove=true;requestRebuild();}})));
        closeButton=addFixedWidget(themedButton(x+half+gap,y+21,w-half-gap,18,ComponentHelper.translatable("gui.back"),b->onClose()));syncActions();
    }
    @Override protected void buildScrollableContent(ContentLayout layout){
        int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y,gap=4,col=(w-gap*4)/5;boolean editable=!PlatformDoorClient.running&&PlatformDoorClient.pending.isEmpty();
        for(int i=0;i<3;i++){int unit=i;Button tab=addButton(x+(col+gap)*i,y,col,18,tr("door_tab_unit_"+i),b->{if(activeUnit!=unit)switchUnit(unit);});selected(tab,activeUnit==i);tab.active=editable;addEntry(tab,y);}
        Button parameters=addButton(x+(col+gap)*3,y,col,18,tr("door_layout_button"),b->Minecraft.getInstance().setScreen(new UnitScreen(this)));parameters.active=editable;addEntry(parameters,y);
        int sideX=x+(col+gap)*4;Button side=addButton(sideX,y,x+w-sideX,18,tr(PlatformDoorClient.left?"door_side_left":"door_side_right"),b->{PlatformDoorClient.left=!PlatformDoorClient.left;PlatformDoorClient.group=null;invalidate();requestRebuild();});side.active=editable;addEntry(side,y);y+=23;
        y=addWrappedText(x,y,w,PlatformDoorClient.nodes.size()==2?PlatformDoorClient.status:tr("door_need_nodes"),PlatformDoorClient.conflicts.isEmpty()?0xAEB6C2:0xE39494)+4;
        int canvasHeight=Math.max(90,Math.min(280,getContentBottom()-y-48));
        ElevationCanvas canvas=new ElevationCanvas(x,y,w,canvasHeight,this);elevation=canvas;canvas.active=editable;addRenderableWidget(canvas);addEntry(canvas,y);y+=canvasHeight+4;
        addEntry(addButton(x,y,23,18,ComponentHelper.literal("<"),b->{viewStart=Math.max(0,viewStart-canvas.span/2);requestRebuild();}),y);
        addEntry(addButton(x+27,y,23,18,ComponentHelper.literal(">"),b->{viewStart=Math.min(canvas.maxStart,viewStart+canvas.span/2);requestRebuild();}),y);
        int addWidth=Math.max(30,(w-58-gap*2)/3);
        for(int i=0;i<3;i++){
            String kind=List.of("door","glass","board").get(i);int bx=x+58+(addWidth+gap)*i;
            Button add=addButton(bx,y,i==2?x+w-bx:addWidth,18,ComponentHelper.literal("+ "+List.of("D","G","HP").get(i)),b->openPart(-1,defaultPart(kind)));add.active=editable&&components.size()<32;addEntry(add,y);
        }y+=22;
        addWrappedText(x,y,w,tr("door_visual_hint"),0xAEB6C2);
    }
    private void openPart(int index,PlatformDoorSettings.Part part){Minecraft.getInstance().setScreen(new PartScreen(this,index,part));}
    private double length(){try{double value=Double.parseDouble(unitLength);return Double.isFinite(value)&&value>=(activeUnit==0?1:.05)&&value<=128?value:6;}catch(RuntimeException e){return 6;}}

    /** 原生按钮承担键盘焦点；鼠标按立面命中构件，左右键切换、回车进入详情。 */
    private static final class ElevationCanvas extends Button {
        private record Hit(PlatformDoorElevation.Tile tile,int left,int top,int right,int bottom) { }
        private final PlatformDoorScreen screen;private final List<Hit> hits=new ArrayList<>();private int current;
        private boolean pressed,moved;private double pressX,pressY;private PlatformDoorElevation.Tile pressedTile;private int dragIndex=-1;
        final double span,maxStart;private final double scale,start;private final int floor;
        ElevationCanvas(int x,int y,int w,int h,PlatformDoorScreen screen){
            //#if MC_VERSION >= 11903
            super(x,y,w,h,tr("door_canvas"),b->{},DEFAULT_NARRATION);
            //#else
            //$$ super(x,y,w,h,tr("door_canvas"),b->{});
            //#endif
            this.screen=screen;double length=screen.length(),top=3.2;
            for(var part:screen.components)top=Math.max(top,part.vertical()+part.height()+.2);
            scale=Math.max(1,Math.min((w-20)/Math.min(length*2,Math.max(4,(w-20)/26d)),(h-24)/top));
            span=(w-20)/scale;maxStart=Math.max(0,length*2-span);screen.viewStart=Math.max(0,Math.min(maxStart,screen.viewStart));start=screen.viewStart;floor=h-17;
            rebuildHits();
        }
        private void rebuildHits(){
            hits.clear();for(var tile:PlatformDoorElevation.tiles(screen.length(),screen.components,screen.fillGlass)){
                int left=(int)Math.round(10+(tile.center()-tile.width()/2-start)*scale),right=(int)Math.round(10+(tile.center()+tile.width()/2-start)*scale);
                int bottom=floor-(int)Math.round(tile.part().vertical()*scale),topY=floor-(int)Math.round((tile.part().vertical()+tile.part().height())*scale);
                if(right<=10||left>=width-10)continue;
                int clippedTop=Math.max(3,Math.min(floor-2,topY)),clippedBottom=Math.max(clippedTop+2,Math.min(floor,bottom));
                hits.add(new Hit(tile,Math.max(10,left),clippedTop,Math.min(width-10,right),clippedBottom));
            }
            current=Math.max(0,Math.min(current,hits.size()-1));
        }
        @Override public void onClick(double mouseX,double mouseY){
            if(!active)return;int found=hit(mouseX,mouseY);if(found<0)return;
            current=found;pressedTile=hits.get(found).tile();dragIndex=pressedTile.index();pressed=true;moved=false;pressX=mouseX;pressY=mouseY;
        }
        private void dragTo(double mouseX,double mouseY,boolean fine){
            if(!pressed||pressedTile==null||!active)return;double dx=mouseX-pressX,dy=mouseY-pressY;
            if(!moved&&Math.hypot(dx,dy)<3)return;
            var part=PlatformDoorElevation.translate(pressedTile.part(),dx/scale,-dy/scale,screen.length(),fine);
            if(!moved&&part.equals(pressedTile.part()))return;
            if(!moved){
                if(dragIndex<0){if(screen.components.size()>=32)return;dragIndex=screen.components.size();screen.components.add(part);}
                screen.invalidate();moved=true;
            }
            screen.components.set(dragIndex,part);rebuildHits();
        }
        private void release(double mouseX,double mouseY){
            if(!pressed)return;boolean changed=moved;pressed=false;moved=false;
            if(changed){PlatformDoorClient.settings=screen.settings();screen.requestRebuild();return;}
            int found=hit(mouseX,mouseY);
            if(found>=0){var tile=hits.get(found).tile();if(tile.index()==pressedTile.index()&&tile.repeat()==pressedTile.repeat())open(tile);}
        }
        private void open(PlatformDoorElevation.Tile tile){if(tile.generated()&&screen.components.size()>=32)return;screen.openPart(tile.index(),tile.part());}
        @Override public void onPress(){if(active&&!pressed&&!hits.isEmpty())open(hits.get(Math.min(current,hits.size()-1)).tile());}
        @Override public boolean keyPressed(int key,int scan,int modifiers){
            if(!active)return false;if((key==262||key==263)&&!hits.isEmpty()){current=Math.floorMod(current+(key==262?1:-1),hits.size());return true;}return super.keyPressed(key,scan,modifiers);
        }
        private int px(){
            //#if MC_VERSION >= 11903
            return getX();
            //#else
            //$$ return x;
            //#endif
        }
        private int py(){
            //#if MC_VERSION >= 11903
            return getY();
            //#else
            //$$ return y;
            //#endif
        }
        private int hit(double mouseX,double mouseY){
            // 吊板优先命中；薄板扩大点击范围，但绘制尺寸仍为实际尺寸。
            for(int i=hits.size()-1;i>=0;i--){var h=hits.get(i);double yy=mouseY-py();if(mouseX-px()>=h.left()&&mouseX-px()<h.right()&&yy>=h.top()-2&&yy<Math.max(h.bottom(),h.top()+8))return i;}return -1;
        }
        //#if MC_VERSION >= 12000
        @Override public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float delta){draw(com.fangsu.utils.GraphicContext.of(graphics),mouseX,mouseY);}
        //#elseif MC_VERSION >= 11904
        //$$ @Override public void renderWidget(com.mojang.blaze3d.vertex.PoseStack stack,int mouseX,int mouseY,float delta){draw(com.fangsu.utils.GraphicContext.of(stack),mouseX,mouseY);}
        //#else
        //$$ @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack stack,int mouseX,int mouseY,float delta){draw(com.fangsu.utils.GraphicContext.of(stack),mouseX,mouseY);}
        //#endif
        private void draw(com.fangsu.utils.GraphicContext g,int mouseX,int mouseY){
            int x=px(),y=py(),hovered=hit(mouseX,mouseY);var font=Minecraft.getInstance().font;
            g.fill(x,y,x+width,y+height,0xFF111418);g.fill(x+10,y+floor,x+width-10,y+floor+1,0xFF657080);
            for(int repeat=0;repeat<=2;repeat++){
                int xx=x+10+(int)Math.round((repeat*screen.length()-start)*scale);if(xx<x+10||xx>x+width-10)continue;
                for(int yy=y+3;yy<y+floor;yy+=6)g.fill(xx,yy,xx+1,Math.min(yy+3,y+floor),0xFF333B45);
            }
            for(int i=0;i<hits.size();i++){
                var hit=hits.get(i);var tile=hit.tile();boolean board=tile.part().kind().equals("board"),door=tile.part().kind().equals("door"),selected=pressed&&moved?tile.index()==dragIndex&&tile.repeat()==pressedTile.repeat():i==hovered||isFocused()&&i==current;
                int border=selected?0xFFBDCEE3:board?0xFFB27070:door?0xFF7B92AE:tile.generated()?0xFF4A5966:0xFF667783;
                int l=x+hit.left(),r=x+hit.right(),t=y+hit.top(),b=y+hit.bottom();if(r-l<1)continue;
                g.fill(l,t,r,b,border);if(r-l>2&&b-t>2)g.fill(l+1,t+1,r-1,b-1,selected?0xFF364453:board?0xFF34282C:door?0xFF263442:0xFF1E292F);
                String text=tile.label();if(font.width(text)>r-l-3&&door)text="D";
                if(font.width(text)<=r-l-3&&b-t>=10)g.drawCenteredString(font,text,(l+r)/2,(t+b-8)/2,0xFFE1E6EC);
            }
            String note=number(start)+" m  –  "+number(Math.min(screen.length()*2,start+span))+" m";
            int selected=hovered>=0?hovered:isFocused()?current:-1;
            if(selected>=0&&selected<hits.size()){
                var part=hits.get(selected).tile().part();note=tr("door_kind_"+part.kind()).getString()+" · "+number(part.width())+" × "+number(part.height())+" m · "+part.variant();
                if(pressed&&moved)note=tr("door_kind_"+screen.components.get(dragIndex).kind()).getString()+" · X "+number(screen.components.get(dragIndex).offset())+" m · Y "+number(screen.components.get(dragIndex).vertical())+" m";
            }
            if(font.width(note)>width-20)note=font.plainSubstrByWidth(note,width-24)+"…";
            g.drawString(font,note,x+10,y+height-12,0xFFAEB6C2,false);
            if(isFocused())g.fill(x+1,y+height-1,x+width-1,y+height,0xFF76889D);
        }
    }

    private abstract static class EditorPage extends ThemedConfigScreen {
        EditorPage(Component title){super(title);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class UnitScreen extends EditorPage {
        final PlatformDoorScreen parent;String length,first,lateral,count;boolean fill;Component error=ComponentHelper.empty();
        UnitScreen(PlatformDoorScreen parent){super(tr("door_unit_settings"));this.parent=parent;length=parent.unitLength;first=parent.firstOffset;lateral=parent.baselineOffset;count=parent.units;fill=parent.fillGlass;}
        @Override protected void buildFixedWidgets(){
            int x=getPanelLeft()+6,w=getPanelRight()-x-6;
            primary(addFixedWidget(themedButton(x,height-35,w/2-2,18,tr("door_apply_part"),b->{try{
                parent.candidate(length,first,lateral,count,fill);
                parent.unitLength=length;parent.firstOffset=first;parent.baselineOffset=lateral;parent.units=count;parent.fillGlass=fill;parent.invalidate();onClose();
            }catch(RuntimeException e){error=tr("door_invalid");requestRebuild();}})));
            closeButton=addFixedWidget(themedButton(x+w/2+2,height-35,w/2-2,18,ComponentHelper.translatable("gui.back"),b->onClose()));
        }
        @Override protected void buildScrollableContent(ContentLayout l){
            int x=l.areaLeft,w=l.areaRight-x,y=l.y;if(!error.getString().isEmpty())y=addWrappedText(x,y,w,error,0xE39494)+4;
            y=field(x,y,w,parent.activeUnit==0?"door_unit_length":"door_end_length",length,v->length=v);y=field(x,y,w,"door_units",count,v->count=v);
            y=field(x,y,w,"door_first_offset",first,v->first=v);y=addWrappedText(x,y,w,tr("door_head_offset_hint"),0xAEB6C2)+4;y=field(x,y,w,"door_baseline_offset",lateral,v->lateral=v);
            addEntry(addButton(x,y,w,18,ComponentHelper.literal((fill?"[x] ":"[ ] ")+tr("door_auto_fill").getString()),b->{fill=!fill;requestRebuild();}),y);y+=23;
            addEntry(addButton(x,y,w,18,ComponentHelper.literal((parent.fitLength?"[x] ":"[ ] ")+tr("door_fit_length").getString()),b->{parent.fitLength=!parent.fitLength;parent.invalidate();requestRebuild();}),y);y+=23;
            addEntry(addButton(x,y,w,18,tr("door_reverse"),b->{parent.reverse=!parent.reverse;parent.directionChosen=true;parent.invalidate();requestRebuild();}),y);y+=23;
            addEntry(addButton(x,y,w,18,tr("door_component_list"),b->Minecraft.getInstance().setScreen(new PartsScreen(parent))),y);
        }
        private int field(int x,int y,int w,String key,String value,Consumer<String> change){
            y=addWrappedText(x,y,w,tr(key),0xAEB6C2)+2;EditBox box=new EditBox(font,x,y,w,18,tr(key));box.setMaxLength(192);box.setValue(value);box.setResponder(change);addRenderableWidget(box);addEntry(box,y);return y+23;
        }
        @Override public void onClose(){parent.updatePreview();Minecraft.getInstance().setScreen(parent);}
    }
    private static final class PartsScreen extends EditorPage {
        final PlatformDoorScreen parent;
        PartsScreen(PlatformDoorScreen parent){super(tr("door_component_list"));this.parent=parent;}
        @Override protected void buildScrollableContent(ContentLayout l){
            int x=l.areaLeft,w=l.areaRight-x,y=l.y;
            for(int i=0;i<parent.components.size();i++){
                int index=i;var part=parent.components.get(i);Button row=itemRow(x,y,w,ComponentHelper.translatable("ui.fangsu.curve.door_component_row",i+1,tr("door_kind_"+part.kind()),number(part.offset())),ComponentHelper.literal(part.model()+" · "+part.variant()),b->parent.openPart(index,part));addRenderableWidget(row);addEntry(row,y);y+=30;
            }
        }
        @Override public void onClose(){parent.updatePreview();Minecraft.getInstance().setScreen(parent);}
    }
    private void switchUnit(int next){
        List<PlatformDoorSettings.Part> current=activeUnit==0?standardParts:activeUnit==1?firstParts:lastParts;current.clear();current.addAll(components);
        if(activeUnit==0)standardLength=unitLength;else if(activeUnit==1)firstLength=unitLength;else lastLength=unitLength;
        if(next==1&&!firstEnabled){firstLength=standardLength;firstParts.clear();firstParts.addAll(standardParts);}
        if(next==2&&!lastEnabled){lastLength=standardLength;lastParts.clear();lastParts.addAll(standardParts);}
        activeUnit=next;if(next==1)firstEnabled=true;if(next==2)lastEnabled=true;
        components.clear();components.addAll(next==0?standardParts:next==1?firstParts:lastParts);unitLength=next==0?standardLength:next==1?firstLength:lastLength;invalidate();requestRebuild();
    }
    private PlatformDoorSettings candidate(String length,String first,String baseline,String count,boolean fill){
        var normal=activeUnit==0?components:standardParts;
        var front=firstEnabled?new PlatformDoorSettings.Unit(Double.parseDouble(activeUnit==1?length:firstLength),activeUnit==1?components:firstParts):null;
        var rear=lastEnabled?new PlatformDoorSettings.Unit(Double.parseDouble(activeUnit==2?length:lastLength),activeUnit==2?components:lastParts):null;
        return new PlatformDoorSettings(Double.parseDouble(activeUnit==0?length:standardLength),Double.parseDouble(first),Double.parseDouble(baseline),Integer.parseInt(count),fill,normal,boardRoutes,boardScript,front,rear,fitLength,reverse,directionChosen);
    }
    private PlatformDoorSettings settings(){return candidate(unitLength,firstOffset,baselineOffset,units,fillGlass);}
    private void chooseBoardRoutes(Screen returnTo,PlatformDoorBoardContent current,Consumer<String> saved){
        var pos=Minecraft.getInstance().player.blockPosition();int limit=1;
        try{var settings=com.fangsu.drawing.diaoban.DiaobanDrawManager.getScriptSettingsByDrawKey(current.drawing());if(settings.has("max_select"))limit=Math.max(1,Math.min(16,settings.get("max_select").getAsInt()));}catch(RuntimeException ignored){}
        com.fangsu.client.ClientHooks.openRouteSelectionScreen(tr("door_board_routes"),null,selected->{
            // 关闭尚未选择的旧选择器时不丢掉绑定；清除使用详情页的显式按钮。
            if(!selected.isEmpty()){
                var rows=new com.google.gson.JsonArray();for(var info:selected)if(info.route!=null&&info.localPlatform!=null){var row=new com.google.gson.JsonArray();row.add(info.route.id);row.add(info.localPlatform.id);rows.add(row);}
                if(rows.size()>0)saved.accept(rows.toString());
            }
            Minecraft.getInstance().setScreen(returnTo);
        },pos,limit);
    }
    private void chooseBoardDrawing(Screen returnTo,String current,Consumer<String> saved){
        var options=new ArrayList<com.fangsu.customItem.ModelSelectInfo>(com.fangsu.drawing.diaoban.DiaobanDrawManager.getDrawOptions());
        if(options.stream().noneMatch(info->info.getContent().equals(current)))options.add(new com.fangsu.customItem.BasicModelSelectInfo(current,current));
        Minecraft.getInstance().setScreen(new ModelSelectScreen(tr("door_board_drawing"),null,options,ignored->current,(ignored,value)->saved.accept(value),returnTo){@Override public boolean isPauseScreen(){return false;}});
    }
    private void preview(){try{PlatformDoorClient.requestPreview(settings());}catch(RuntimeException e){PlatformDoorClient.status=tr("door_invalid");requestRebuild();}}
    private void invalidate(){PlatformDoorClient.invalidate();confirmRemove=false;syncActions();}
    private void syncActions(){
        if(previewButton==null)return;
        previewButton.active=!PlatformDoorClient.running&&PlatformDoorClient.nodes.size()==2&&PlatformDoorClient.pending.isEmpty();buildButton.active=PlatformDoorClient.canBuild();
        buildButton.setMessage(tr(PlatformDoorClient.conflicts.isEmpty()?"door_build":"door_force_build"));
        cancelButton.active=PlatformDoorClient.running||!PlatformDoorClient.pending.isEmpty()||!PlatformDoorClient.preview.isEmpty()||!PlatformDoorClient.conflicts.isEmpty();removeButton.active=!PlatformDoorClient.running&&PlatformDoorClient.group!=null&&PlatformDoorClient.pending.isEmpty();
        removeButton.setMessage(tr(confirmRemove?"door_confirm_remove":"door_remove"));
    }
    @Override public void tick(){super.tick();syncActions();}
    @Override public void onClose(){try{PlatformDoorClient.settings=settings();}catch(RuntimeException ignored){}Minecraft.getInstance().setScreen(parent);}
    private static Component tr(String key){return ComponentHelper.translatable("ui.fangsu.curve."+key);}
    private static String number(double value){return value==Math.rint(value)?Long.toString((long)value):java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private PlatformDoorSettings.Part defaultPart(String kind){return switch(kind){
        case "board"->new PlatformDoorSettings.Part(kind,Math.min(2,length()),0,2.5,2,1,"fangsu:diaoban/mtr_diaoban.json","mtr_diaoban_a");
        case "glass"->new PlatformDoorSettings.Part(kind,.5,0,0,1,2,"fangsu:screendoor/kaba.json","glass",true,"glass");
        default->new PlatformDoorSettings.Part("door",Math.min(2,length()),0,0,2,2,"fangsu:screendoor/kaba.json","common");
    };}

    /** 每个组件可单独编辑位置和外观；关闭详情不会提交半成品。 */
    private static final class PartScreen extends EditorPage {
        private final PlatformDoorScreen parent;private final int index;private final String kind;
        private String offset,lateral,vertical,widthValue,heightValue,model,variant,secondaryVariant;
        private Map<String,String> options;
        private boolean automatic,advanced;private int page;
        private Component error=ComponentHelper.empty();
        PartScreen(PlatformDoorScreen parent,int index,PlatformDoorSettings.Part part){
            super(ComponentHelper.translatable("ui.fangsu.curve.door_part_title",tr("door_kind_"+part.kind())));this.parent=parent;this.index=index;kind=part.kind();page=kind.equals("board")?2:0;
            offset=number(part.offset());lateral=number(part.lateral());vertical=number(part.vertical());widthValue=number(part.width());heightValue=number(part.height());model=part.model();variant=part.variant();secondaryVariant=part.secondaryVariant();automatic=part.automatic();options=part.options();
        }
        @Override protected int getContentTop(){return getPanelTop()+29;}
        @Override protected void buildFixedWidgets(){
            int x=getPanelLeft()+6,w=getPanelRight()-x-6,gap=4,col=(w-gap*2)/3,y=height-35;
            int tabY=getPanelTop()+5;
            for(int i=0;i<3;i++){
                int target=i;
                Button tab=addFixedWidget(themedButton(x+(col+gap)*i,tabY,i==2?w-(col+gap)*2:col,18,tr("door_part_tab_"+i),b->{page=target;scrollOffset=0;requestRebuild();}));
                selected(tab,page==i);
            }
            primary(addFixedWidget(themedButton(x,y,col,18,tr("door_apply_part"),b->apply())));
            Button remove=danger(addFixedWidget(themedButton(x+col+gap,y,col,18,tr("door_remove_part"),b->{parent.components.remove(index);parent.invalidate();onClose();})));remove.active=index>=0&&parent.components.size()>1;
            closeButton=addFixedWidget(themedButton(x+(col+gap)*2,y,w-(col+gap)*2,18,ComponentHelper.translatable("gui.back"),b->onClose()));
        }
        @Override protected void buildScrollableContent(ContentLayout layout){
            int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
            if(!error.getString().isEmpty())y=addWrappedText(x,y,w,error,0xE39494)+4;
            if(page==0)appearance(x,y,w);else if(page==1)position(x,y,w);else content(x,y,w);
        }
        private int choice(int x,int y,int w,String title,Component value,Button.OnPress action){
            Button button=itemRow(x,y,w,tr(title),value,action);addRenderableWidget(button);addEntry(button,y);return y+30;
        }
        private int doorSide(){return kind.equals("door")&&!options.getOrDefault("isAuto","true").equals("true")?Integer.parseInt(options.getOrDefault("doorSide","0")):0;}
        private void appearance(int x,int y,int w){
            y=choice(x,y,w,"door_part_model",PlatformDoorModelPicker.mainName(kind,model),b->chooseModel());
            if(kind.equals("glass")){
                Button toggle=addButton(x,y,w,18,ComponentHelper.literal((automatic?"[x] ":"[ ] ")+tr("door_glass_auto").getString()),b->{automatic=!automatic;requestRebuild();});selected(toggle,automatic);addEntry(toggle,y);y+=23;
                if(automatic)y=addWrappedText(x,y,w,tr("door_glass_auto_hint"),0xAEB6C2)+4;
                for(boolean right:new boolean[]{false,true}){
                    Button button=itemRow(x,y,w,tr(right?"door_right_variant":"door_left_variant"),automatic?tr("door_auto_value"):PlatformDoorModelPicker.variantName(kind,model,right?secondaryVariant:variant,right,doorSide()),b->chooseVariant(right));button.active=!automatic;addRenderableWidget(button);addEntry(button,y);y+=30;
                }
            }else y=choice(x,y,w,"door_part_variant",PlatformDoorModelPicker.variantName(kind,model,variant,false,doorSide()),b->chooseVariant(false));
            addEntry(addButton(x,y,w,18,tr("door_resource_ids"),b->{advanced=!advanced;requestRebuild();}),y);y+=23;
            if(advanced){y=field(x,y,w,"door_part_model",model,v->model=v);y=field(x,y,w,kind.equals("glass")?"door_left_variant":"door_part_variant",variant,v->variant=v);if(kind.equals("glass"))field(x,y,w,"door_right_variant",secondaryVariant,v->secondaryVariant=v);}
        }
        private void position(int x,int y,int w){
            if(kind.equals("board"))y=addWrappedText(x,y,w,tr("door_board_height_hint"),0xAEB6C2)+4;
            if(w>=320){int half=(w-10)/2;
                y=Math.max(field(x,y,half,kind.equals("board")?"door_board_width":"door_part_width",widthValue,v->widthValue=v),field(x+half+10,y,half,kind.equals("board")?"door_board_height":"door_part_height",heightValue,v->heightValue=v));
                y=Math.max(field(x,y,half,"door_part_offset",offset,v->offset=v),field(x+half+10,y,half,kind.equals("board")?"door_board_elevation":"door_part_vertical",vertical,v->vertical=v));
            }else{
                y=field(x,y,w,kind.equals("board")?"door_board_width":"door_part_width",widthValue,v->widthValue=v);y=field(x,y,w,kind.equals("board")?"door_board_height":"door_part_height",heightValue,v->heightValue=v);
                y=field(x,y,w,"door_part_offset",offset,v->offset=v);y=field(x,y,w,kind.equals("board")?"door_board_elevation":"door_part_vertical",vertical,v->vertical=v);
            }
            y=field(x,y,w,"door_part_lateral",lateral,v->lateral=v);if(kind.equals("door"))addWrappedText(x,y,w,tr("door_width_hint"),0xAEB6C2);
        }
        private void content(int x,int y,int w){
            if(kind.equals("board")){
                y=addWrappedText(x,y,w,tr("door_board_local_hint"),0xAEB6C2)+5;
                var content=PlatformDoorBoardContent.resolve(options,parent.boardScript,parent.boardRoutes);
                y=choice(x,y,w,"door_board_drawing",PlatformDoorModelPicker.name(com.fangsu.drawing.diaoban.DiaobanDrawManager.getDrawOptions(),content.drawing()),b->parent.chooseBoardDrawing(this,content.drawing(),value->{options=PlatformDoorBoardContent.withDrawing(options,value);requestRebuild();}));
                y=choice(x,y,w,"door_board_routes",content.routeCount()==0?tr("door_routes_none"):ComponentHelper.translatable("ui.fangsu.curve.door_routes_count",content.routeCount()),b->parent.chooseBoardRoutes(this,content,value->{options=PlatformDoorBoardContent.withRoutes(options,value);requestRebuild();}));
                addEntry(addButton(x,y,w/2-2,18,tr("door_clear_routes"),b->{options=PlatformDoorBoardContent.withRoutes(options,"[]");requestRebuild();}),y);
                addEntry(addButton(x+w/2+2,y,w/2-2,18,tr("door_inherit_content"),b->{var values=new LinkedHashMap<>(options);values.remove("drawScript");values.remove("routes");options=Map.copyOf(values);requestRebuild();}),y);y+=24;
            }
            if(kind.equals("glass")){addWrappedText(x,y,w,tr("door_glass_content_hint"),0xAEB6C2);return;}
            choice(x,y,w,"door_native_options",tr(kind.equals("board")?"door_native_board_hint":"door_native_door_hint"),b->openNative());
        }
        private PlatformDoorSettings.Part draft(){return new PlatformDoorSettings.Part(kind,Double.parseDouble(offset),Double.parseDouble(lateral),Double.parseDouble(vertical),Double.parseDouble(widthValue),Double.parseDouble(heightValue),model,variant,automatic,secondaryVariant,options);}
        private void openNative(){try{PlatformDoorNativeOptions.open(this,draft(),value->{options=value;if(kind.equals("board")&&value.containsKey("length")){var info=com.fangsu.utils.ContentInfoUtil.getDiaobanContent(model,variant);widthValue=number(Integer.parseInt(value.get("length"))*(info==null?.5:Math.max(1,info.getUnit())/16d));}requestRebuild();});}catch(RuntimeException e){error=tr("door_invalid");requestRebuild();}}
        private void chooseModel(){PlatformDoorModelPicker.openMain(this,kind,model,value->{model=value;var left=variants(false);if(!left.isEmpty()&&!left.contains(variant))variant=left.iterator().next();var right=variants(true);if(!right.isEmpty()&&!right.contains(secondaryVariant))secondaryVariant=right.iterator().next();requestRebuild();});}
        private int field(int x,int y,int w,String key,String value,Consumer<String> change){
            y=addWrappedText(x,y,w,tr(key),0xAEB6C2)+2;EditBox box=new EditBox(font,x,y,w,18,tr(key));box.setMaxLength(192);box.setValue(value);box.setResponder(change);addRenderableWidget(box);addEntry(box,y);return y+23;
        }
        private Set<String> variants(boolean right){
            var models=ContentManager.getInstance().getContent(kind.equals("board")?"diaoban":"screendoor");Set<String> ids=new TreeSet<>();
            if(models!=null&&models.get(model)!=null)for(var content:models.get(model)){
                if(kind.equals("door")&&!(content instanceof com.fangsu.customItem.contents.ScreendoorDoorContent)||kind.equals("glass")&&!(content instanceof com.fangsu.customItem.contents.ScreendoorGlassContent))continue;
                String id=content.getId();if(kind.equals("glass")&&!id.startsWith(right?"right.":"left."))continue;
                if(!kind.equals("board")&&id.contains("."))id=id.substring(id.indexOf('.')+1);if(!kind.equals("glass")||!id.equals("auto"))ids.add(id);
            }return ids;
        }
        private void chooseVariant(boolean right){
            PlatformDoorModelPicker.openVariant(this,kind,model,right?secondaryVariant:variant,right,kind.equals("door")&&options.getOrDefault("isAuto","true").equals("false")?Integer.parseInt(options.getOrDefault("doorSide","0")):0,value->{if(right)secondaryVariant=value;else variant=value;requestRebuild();});
        }
        private void apply(){
            try{var part=new PlatformDoorSettings.Part(kind,Double.parseDouble(offset),Double.parseDouble(lateral),Double.parseDouble(vertical),Double.parseDouble(widthValue),Double.parseDouble(heightValue),model,variant,automatic,secondaryVariant,options);
                if(part.offset()>parent.length())throw new IllegalArgumentException("door_part_outside_unit");
                if(index<0){if(parent.components.size()>=32)throw new IllegalArgumentException("door_component_limit");parent.components.add(part);}else parent.components.set(index,part);parent.invalidate();onClose();
            }catch(RuntimeException e){error=tr("door_invalid");requestRebuild();}
        }
        @Override public void onClose(){parent.updatePreview();Minecraft.getInstance().setScreen(parent);}
    }
}
