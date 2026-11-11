package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;

/** 可搜索的明确选项列表，避免依靠反复点击按钮猜当前选项。 */
public final class ConfigChoiceScreen extends ThemedConfigScreen {
    public record Choice(String value,String label) { }
    private final Screen parent;private final List<Choice> choices;private final Consumer<String> select;
    private String search="";
    public ConfigChoiceScreen(Screen parent,String title,List<Choice> choices,Consumer<String> select){super(ComponentHelper.translatable(title));this.parent=parent;this.choices=List.copyOf(choices);this.select=select;}
    @Override protected void buildFixedWidgets(){
        EditBox filter=new EditBox(font,getPanelLeft(),getPanelTop()+4,getPanelRight()-getPanelLeft(),20,ComponentHelper.translatable("ui.fangsu.menu.search"));
        filter.setValue(search);filter.setResponder(value->{search=value;scrollOffset=0;requestRebuild();});addRenderableWidget(filter);fixedWidgets.add(filter);setFocused(filter);
        //#if MC_VERSION >= 11904
        filter.setFocused(true);
        //#else
        //$$ filter.setFocus(true);
        //#endif
        closeButton=addFixedWidget(themedButton(getPanelLeft(),height-40,getPanelRight()-getPanelLeft(),20,ComponentHelper.translatable("gui.back"),b->onClose()));
    }
    @Override protected int getContentTop(){return super.getContentTop()+12;}
    @Override protected void buildScrollableContent(ContentLayout layout){int y=layout.y;
        for(Choice choice:choices)if((choice.label()+" "+choice.value()).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))){
            addEntry(addButton(layout.areaLeft,y,layout.areaRight-layout.areaLeft,20,ComponentHelper.literal(choice.label()),b->{select.accept(choice.value());onClose();}),y);y+=24;
        }
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
}
