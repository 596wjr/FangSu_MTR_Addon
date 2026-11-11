package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Path;
import java.util.List;

/** 配置首页只负责导航，各页保留自己的草稿和保存按钮。 */
public final class FangSuConfigScreen extends ThemedConfigScreen {
    private final Screen parent;
    public FangSuConfigScreen(Screen parent){super(ComponentHelper.translatable("ui.fangsu.config.title"));this.parent=parent;}
    @Override protected void buildFixedWidgets(){int x=getPanelLeft(),w=getPanelRight()-x;
        addFixedWidget(themedButton(x,height-40,w/2-2,18,ComponentHelper.translatable("ui.fangsu.ann.import_home"),b->com.fangsu.announcements.ui.AudioLibraryScreen.chooseImport(this)));
        closeButton=addFixedWidget(themedButton(x+w/2+2,height-40,w/2-2,18,ComponentHelper.translatable("gui.back"),b->onClose()));
    }
    @Override public void onFilesDrop(List<Path> paths){com.fangsu.announcements.ui.AudioLibraryScreen.importDropped(this,paths);}
    @Override protected void buildScrollableContent(ContentLayout layout){int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
        y=card(x,y,w,"ui.fangsu.menu.rendering","ui.fangsu.menu.rendering_hint",()->new RenderingConfigScreen(this));
        y=card(x,y,w,"ui.fangsu.ann.title","ui.fangsu.menu.announcements_hint",()->new com.fangsu.announcements.ui.AnnouncementCenterScreen(this));
        y=card(x,y,w,"ui.fangsu.ann.library_title","ui.fangsu.ann.library_card_hint",()->new com.fangsu.announcements.ui.AudioLibraryScreen(this));
        card(x,y,w,"ui.fangsu.menu.provider","ui.fangsu.menu.provider_card_hint",()->new com.fangsu.announcements.ui.TtsProviderScreen(this));
    }
    private int card(int x,int y,int w,String title,String hint,java.util.function.Supplier<Screen> screen){
        addEntry(addButton(x,y,w,18,ComponentHelper.translatable(title),b->Minecraft.getInstance().setScreen(screen.get())),y);
        return addWrappedText(x+5,y+23,w-10,ComponentHelper.translatable(hint),0x555555)+10;
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
}
