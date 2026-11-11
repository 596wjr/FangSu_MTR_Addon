package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.*;
import com.google.gson.*;
import dev.potato.curvedplatform.geometry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import java.util.*;

/** 配置器与扳手共用站台几何参数页。 */
public final class CurvedPlatformScreen extends ThemedConfigScreen {
    private static final PlatformSettings.Style[] SELECTABLE_STYLES=Arrays.stream(PlatformSettings.Style.values())
            .filter(style->style!=PlatformSettings.Style.LONDON_GAP).toArray(PlatformSettings.Style[]::new);
    private final Screen parent;
    private final JsonObject draft;
    private final UUID wrenchGroup;
    private final List<BlockPos> wrenchNodes;
    private int side;
    private boolean details,confirmRemove;
    private Button previewButton,applyButton;

    public CurvedPlatformScreen(Screen parent){this(parent,null);}
    public CurvedPlatformScreen(Screen parent,BlockEntityCurvedPlatform cell){
        super(ComponentHelper.translatable(cell==null?"ui.fangsu.menu.building":"ui.fangsu.curve.wrench_edit"));
        this.parent=parent;
        wrenchGroup=cell==null?null:cell.group;
        wrenchNodes=cell==null?List.of():List.of(cell.first,cell.last);
        side=cell==null?CurvedPlatformClient.draftSide:(cell.left?1:-1);
        draft=JsonParser.parseString(PlatformPreset.encode(cell==null?CurvedPlatformClient.settings:cell.settings)).getAsJsonObject();
        if(wrenchGroup!=null)invalidate();
    }
    public void updatePreview(){requestRebuild();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        super.tick();
        updateWrenchButtons();
    }
    @Override protected void buildFixedWidgets(){
        int x=getPanelLeft(),y=height-36,w=getPanelRight()-x;
        if(wrenchGroup==null){
            closeButton=addFixedWidget(themedButton(x,y,w,18,ComponentHelper.translatable("gui.back"),b->onClose()));
            return;
        }
        int column=(w-8)/3;
        previewButton=addFixedWidget(themedButton(x,y,column,18,ComponentHelper.translatable("ui.fangsu.curve.preview"),b->preview()));
        applyButton=addFixedWidget(primary(themedButton(x+column+4,y,column,18,ComponentHelper.translatable("ui.fangsu.curve.wrench_apply"),b->apply())));
        int right=x+2*(column+4);
        closeButton=addFixedWidget(themedButton(right,y,w+x-right,18,ComponentHelper.translatable("gui.back"),b->onClose()));
        updateWrenchButtons();
    }
    private void updateWrenchButtons(){
        if(applyButton==null)return;
        previewButton.active=!CurvedPlatformClient.jobRunning&&CurvedPlatformClient.pending.isEmpty();
        applyButton.active=previewButton.active&&!CurvedPlatformClient.previewToken.isEmpty()&&System.currentTimeMillis()<=CurvedPlatformClient.expires;
    }
    @Override protected void buildScrollableContent(ContentLayout layout){int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
        if(wrenchGroup==null){
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.curve.native_hint"),0xAEB6C2)+6;
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.curve.selected",CurvedPlatformClient.nodes().size()),0xE0E4EB)+4;
        }else y=addWrappedText(x,y,w,CurvedPlatformClient.status,0xAEB6C2)+6;
        int sideWidth=wrenchGroup==null?w/2-2:w;
        addEntry(addButton(x,y,sideWidth,18,ComponentHelper.translatable("ui.fangsu.curve.side",ComponentHelper.translatable(side==1?"ui.fangsu.curve.left":"ui.fangsu.curve.right")),b->{side=-side;invalidate();requestRebuild();}),y);
        if(wrenchGroup==null)addEntry(addButton(x+w/2+2,y,w/2-2,18,ComponentHelper.translatable("ui.fangsu.curve.clear"),b->{CurvedPlatformClient.clear();requestRebuild();}),y);
        y+=22;
        y=choice(x,y,w,"style",SELECTABLE_STYLES);y=choice(x,y,w,"profile",PlatformSettings.Profile.values());
        y=field(x,y,w,"edgeOffset",false);y=field(x,y,w,"rearCoverage",false);y=field(x,y,w,"rearBlock",true);
        y=choice(x,y,w,"rearMode",PlatformSettings.RearMode.values());
        addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.geometry_details"),b->{details=!details;requestRebuild();}),y);y+=22;
        if(details){
            y=field(x,y,w,"innerBlock",true);y=field(x,y,w,"baseDepth",false);y=field(x,y,w,"endSupport",false);y=choice(x,y,w,"end",PlatformSettings.End.values());
            y=toggle(x,y,w,"squareEnds");y=choice(x,y,w,"squareEnd",PlatformSettings.End.values());
            y=toggle(x,y,w,"customMaterial");y=field(x,y,w,"customBlock",true);y=choice(x,y,w,"customMode",PlatformSettings.RearMode.values());
            y=field(x,y,w,"firstEndBlock",true);y=field(x,y,w,"lastEndBlock",true);
        }
        if(wrenchGroup==null){
            y=addWrappedText(x,y,w,CurvedPlatformClient.status,0xAEB6C2)+6;
            var preview=addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.preview"),b->preview());
            preview.active=CurvedPlatformClient.nodes().size()==2;
            addEntry(preview,y);y+=22;
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.curve.build_with_item"),0xAEB6C2)+5;
        }
        addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.copy_preset"),b->{try{Minecraft.getInstance().keyboardHandler.setClipboard(PlatformPreset.encode(PlatformPreset.decode(draft.toString())));}catch(RuntimeException e){CurvedPlatformClient.status=ComponentHelper.translatable("ui.fangsu.curve.invalid");requestRebuild();}}),y);y+=22;
        addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.paste_preset"),b->{try{String text=Minecraft.getInstance().keyboardHandler.getClipboard();if(text.length()>32767)throw new IllegalArgumentException();var settings=PlatformPreset.decode(text);var imported=JsonParser.parseString(PlatformPreset.encode(settings)).getAsJsonObject();for(var entry:imported.entrySet())draft.add(entry.getKey(),entry.getValue());invalidate();requestRebuild();}catch(RuntimeException e){CurvedPlatformClient.status=ComponentHelper.translatable("ui.fangsu.curve.invalid");requestRebuild();}}),y);y+=22;
        if(wrenchGroup==null&&CurvedPlatformClient.editingGroup!=null){var remove=addButton(x,y,w,18,ComponentHelper.translatable(confirmRemove?"ui.fangsu.curve.confirm_remove":"ui.fangsu.curve.remove_group"),b->{if(confirmRemove){CurvedPlatformPackets.action(3);CurvedPlatformClient.clear();onClose();}else{confirmRemove=true;requestRebuild();}});danger(remove);addEntry(remove,y);y+=22;}
        addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.cancel"),b->{CurvedPlatformPackets.action(2);invalidate();requestRebuild();}),y);
    }
    private void preview(){
        try{
            PlatformSettings settings=PlatformPreset.decode(draft.toString());
            if(wrenchGroup==null){
                CurvedPlatformClient.settings=settings;
                CurvedPlatformClient.draftSide=side;
            }
            CurvedPlatformClient.visualWidth=settings.style().source().width()+settings.rearCoverage();
            CurvedPlatformClient.visualOffset=settings.edgeOffset();
            CurvedPlatformClient.visualHeight=(int)Math.ceil(settings.top());
            CurvedPlatformClient.visualSide=side;
            CurvedPlatformPackets.preview(wrenchGroup==null?CurvedPlatformClient.nodes():wrenchNodes,
                    settings,side==1,wrenchGroup==null?CurvedPlatformClient.editingGroup:wrenchGroup);
        }catch(RuntimeException e){
            CurvedPlatformClient.status=ComponentHelper.translatable("ui.fangsu.curve.invalid");
        }
        requestRebuild();
    }
    private void apply(){
        updateWrenchButtons();
        if(!applyButton.active)return;
        CurvedPlatformPackets.action(1);
        CurvedPlatformClient.previewToken="";
        CurvedPlatformClient.status=ComponentHelper.translatable("ui.fangsu.curve.building");
        CurvedPlatformClient.jobRunning=true;
        requestRebuild();
    }
    private int toggle(int x,int y,int w,String key){addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.param_"+key).append(": ").append(ComponentHelper.translatable(draft.get(key).getAsBoolean()?"options.on":"options.off")),b->{draft.addProperty(key,!draft.get(key).getAsBoolean());invalidate();requestRebuild();}),y);return y+22;}
    private int choice(int x,int y,int w,String key,Enum<?>[] values){
        addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.param_"+key).append(": ").append(ComponentHelper.translatable("ui.fangsu.curve.option_"+key+"_"+draft.get(key).getAsString())),b->{
            var choices=Arrays.stream(values).map(v->new ConfigChoiceScreen.Choice(v.name(),ComponentHelper.translatableString("ui.fangsu.curve.option_"+key+"_"+v.name()))).toList();
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,"ui.fangsu.curve.param_"+key,choices,value->selectChoice(key,value)));
        }),y);return y+22;
    }
    private void selectChoice(String key,String value){
        if(key.equals("style")){
            var previous=PlatformSettings.Style.valueOf(draft.get("style").getAsString());
            var next=PlatformSettings.Style.valueOf(value);
            String rear=draft.get("rearBlock").getAsString().trim();
            if(rear.isEmpty()||rear.equals(previous.defaultRearBlock())){
                draft.addProperty("rearBlock",next.defaultRearBlock());
            }
        }
        draft.addProperty(key,value);
        invalidate();
        requestRebuild();
    }
    private int field(int x,int y,int w,String key,boolean text){
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.curve.param_"+key),0xAEB6C2)+3;EditBox box=new EditBox(font,x,y,w,18,ComponentHelper.translatable("ui.fangsu.curve.param_"+key));box.setMaxLength(192);box.setValue(draft.get(key).getAsString());box.setResponder(value->{draft.addProperty(key,value);invalidate();});addRenderableWidget(box);addEntry(box,y);return y+22;
    }
    private void invalidate(){
        CurvedPlatformClient.invalidatePreview();
        if(wrenchGroup!=null)CurvedPlatformClient.status=ComponentHelper.translatable("ui.fangsu.curve.wrench_hint");
        confirmRemove=false;
    }
    @Override public void onClose(){
        if(wrenchGroup!=null)CurvedPlatformClient.invalidatePreview();
        else try{CurvedPlatformClient.settings=PlatformPreset.decode(draft.toString());CurvedPlatformClient.draftSide=side;CurvedPlatformClient.saveSettings();}catch(RuntimeException ignored){}
        Minecraft.getInstance().setScreen(parent);
    }
}
