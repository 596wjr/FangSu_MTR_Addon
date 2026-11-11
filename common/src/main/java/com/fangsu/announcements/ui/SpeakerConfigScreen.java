package com.fangsu.announcements.ui;

import com.fangsu.announcements.definition.AnnouncementRegistry;
import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker;
import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker.Binding;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.AnnouncementSpeakerPackets;
import com.fangsu.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import java.util.*;

/** 喇叭独立设置，复用既有站台/线路选择器；保存是显式服务器操作。 */
public final class SpeakerConfigScreen extends ThemedConfigScreen {
    private final BlockEntityAnnouncementSpeaker speaker;
    private List<Binding> bindings;
    private String template, variant;
    private boolean allStops;
    private String leadSeconds,status="";
    public static void open(BlockEntityAnnouncementSpeaker speaker) { Minecraft.getInstance().setScreen(new SpeakerConfigScreen(speaker)); }
    private SpeakerConfigScreen(BlockEntityAnnouncementSpeaker speaker) {
        super(ComponentHelper.translatable("block.fangsu.announcement_speaker")); this.speaker = speaker;
        allStops = speaker.allStops(); bindings = speaker.bindings(); template = speaker.template(); variant = speaker.variant();
        leadSeconds=String.valueOf(speaker.leadSeconds());
    }
    @Override protected void buildFixedWidgets() {
        int x = getPanelLeft(), w = getPanelRight() - x;
        primary(addFixedWidget(themedButton(x, height - 40, w / 2 - 2, 20, ComponentHelper.translatable("ui.fangsu.config.apply"), b ->{
            try{
                int seconds=Integer.parseInt(leadSeconds.trim());
                if(seconds<BlockEntityAnnouncementSpeaker.MIN_LEAD_SECONDS||seconds>BlockEntityAnnouncementSpeaker.MAX_LEAD_SECONDS)throw new IllegalArgumentException();
                AnnouncementSpeakerPackets.send(speaker.getBlockPos(),bindings,template,variant,allStops,seconds);status="ui.fangsu.ann.speaker_sent";
            }catch(RuntimeException e){status="ui.fangsu.ann.lead_invalid";}requestRebuild();
        })));
        closeButton = addFixedWidget(themedButton(x + w / 2 + 2, height - 40, w / 2 - 2, 20, ComponentHelper.translatable("gui.back"), b -> onClose()));
    }
    @Override protected void buildScrollableContent(ContentLayout layout) {
        int x = layout.areaLeft, w = layout.areaRight - x, y = layout.y;
        y = addWrappedText(x, y, w, ComponentHelper.translatable("ui.fangsu.ann.speaker_hint"), 0x555555) + 8;
        if(!status.isEmpty())y=addWrappedText(x,y,w,ComponentHelper.translatable(status),0x996633)+8;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.title"),b->Minecraft.getInstance().setScreen(new AnnouncementCenterScreen(this))),y);y+=26;
        y=section(x,y,w,"ui.fangsu.ann.speaker_routes");
        addEntry(addButton(x, y, w, 20, ComponentHelper.translatable("ui.fangsu.common.selectRoute"), b -> Minecraft.getInstance().setScreen(
                new RouteSelectionScreen(ComponentHelper.translatable("ui.fangsu.common.selectRoute"), bindings.stream().map(Binding::routeId).toList(), selected -> {
                    bindings = selected.stream().filter(info -> info.route != null && info.localPlatform != null)
                            .map(info -> new Binding(info.route.id, info.localPlatform.id)).distinct().toList(); requestRebuild();
                }, speaker.getBlockPos(), 32, this))), y); y += 26;
        for (Binding binding : bindings) {
            var route = com.fangsu.utils.MtrUtil.getRouteById(binding.routeId());
            String platform=new com.fangsu.mtr.LocalPlatform(com.fangsu.utils.MtrUtil.getPlatformById(binding.platformId())).getName();
            String routeName=route==null?Long.toString(binding.routeId()):route.name.split("\\|\\|",2)[0].replace("|"," / ");
            y = addWrappedText(x, y, w, ComponentHelper.translatable("ui.fangsu.ann.speaker_binding",routeName,platform.isBlank()?Long.toString(binding.platformId()):platform), 0x303030) + 6;
        }
        y=section(x,y,w,"ui.fangsu.ann.speaker_content");
        List<String> choices = AnnouncementRegistry.INSTANCE.snapshot().templates().values().stream()
                .filter(t -> t.event().equals("platform.approaching")).map(t -> t.id()).sorted().toList();
        addEntry(addButton(x, y, w, 20, ComponentHelper.translatable("ui.fangsu.menu.template",AnnouncementLabels.template(template)), b -> {
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,"ui.fangsu.menu.choose_template",choices.stream().map(id->new ConfigChoiceScreen.Choice(id,AnnouncementLabels.template(id))).toList(),id->{template=id;variant=AnnouncementRegistry.INSTANCE.snapshot().templates().get(id).defaultVariant();requestRebuild();}));
        }), y); y += 26;
        var definition = AnnouncementRegistry.INSTANCE.snapshot().templates().get(template);
        addEntry(addButton(x, y, w, 20, ComponentHelper.translatable("ui.fangsu.ann.variant", AnnouncementLabels.variant(variant)), b -> {
            if(definition!=null)Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,"ui.fangsu.menu.choose_variant",definition.variants().stream().map(v->new ConfigChoiceScreen.Choice(v.id(),AnnouncementLabels.variant(v.id()))).toList(),id->{variant=id;requestRebuild();}));
        }), y); y += 26;
        addEntry(addButton(x, y, w, 20, ComponentHelper.translatable("ui.fangsu.ann.all_stops", ComponentHelper.translatable(allStops?"options.on":"options.off")), b -> { allStops = !allStops; requestRebuild(); }), y); y += 26;
        y=section(x,y,w,"ui.fangsu.ann.speaker_timing");
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.lead_seconds"),0x555555)+4;
        EditBox lead=new EditBox(font,x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.lead_seconds"));lead.setMaxLength(3);lead.setValue(leadSeconds);lead.setResponder(value->leadSeconds=value);addRenderableWidget(lead);addEntry(lead,y);y+=26;
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.lead_hint"),0x555555)+8;
        var clear=addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.clear_routes"),b->{bindings=List.of();requestRebuild();});danger(clear);addEntry(clear,y);
    }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
