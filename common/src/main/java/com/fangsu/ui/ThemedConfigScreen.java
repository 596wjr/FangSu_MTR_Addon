package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 配置与报站页面共用的轻量主题，保留原生键盘导航、滚动和旁白。 */
public abstract class ThemedConfigScreen extends BasicConfigScreen {
    protected ThemedConfigScreen(Component title){super(title);}
    @Override protected int getPanelLeft(){return Math.max(8,(width-960)/2);}
    @Override protected int getPanelRight(){return width-getPanelLeft();}
    @Override protected int getPanelTop(){return 30;}
    @Override protected int getPanelBottom(){return height-48;}
    @Override protected int getContentTop(){return getPanelTop()+10;}
    @Override protected int getContentBottom(){return getPanelBottom()-6;}
    @Override protected int getLayoutMargin(){return 10;}
    @Override protected int getContentPadding(){return 10;}
    @Override protected void renderPanelBackground(GraphicContext g){
        int x=getPanelLeft(),right=getPanelRight(),top=getPanelTop(),bottom=getPanelBottom();
        g.fill(x,top,right,height-7,0xED181A1E);
        g.fill(x,10,right,top-2,0xF022252A);
        String title=getTitle().getString();var font=Minecraft.getInstance().font;
        if(font.width(title)>right-x-28)title=font.plainSubstrByWidth(title,right-x-36)+"…";
        g.drawString(font,title,x+9,15,0xFFE3E7ED,false);
        g.fill(x+6,bottom,right-6,bottom+1,0xFF383C43);
    }
    protected Button themedButton(int x,int y,int w,int h,Component label,Button.OnPress press){return new ThemeButton(x,y,w,Math.min(18,h),label,press);}
    protected Button itemRow(int x,int y,int w,Component title,Component subtitle,Button.OnPress press){ThemeButton button=new ThemeButton(x,y,w,26,title,press);button.subtitle=subtitle;return button;}
    @Override protected Button addButton(int x,int y,int w,int h,Component label,Button.OnPress press){Button button=themedButton(x,y,w,h,label,press);addRenderableWidget(button);return button;}
    protected Button primary(Button button){if(button instanceof ThemeButton themed)themed.primary=true;return button;}
    protected Button danger(Button button){if(button instanceof ThemeButton themed)themed.danger=true;return button;}
    protected void selected(Button button,boolean selected){if(button instanceof ThemeButton themed)themed.selected=selected;}
    protected int section(int x,int y,int w,String key){return addWrappedText(x,y+3,w,ComponentHelper.translatable(key).withStyle(style->style.withBold(true)),0xBBC7D6)+5;}
    @Override protected int addWrappedText(int x,int y,int w,Component message,int color){return super.addWrappedText(x,y,w,message,readable(color));}
    @Override protected TextLabel createTextLabel(int x,int y,Component text,TextLabel.Align align,int color,boolean bold){return super.createTextLabel(x,y,text,align,readable(color),bold);}
    private static int readable(int color){
        if(color==0xAA3333)return 0xE39494;
        if(color==0x996633||color==0x996622)return 0xD5B786;
        int r=color>>16&255,g=color>>8&255,b=color&255;
        return r+g+b<420?(color==0x303030?0xE0E4EB:0xAEB6C2):color;
    }
    private static final class ThemeButton extends Button {
        boolean primary,danger,selected;
        Component subtitle;
        ThemeButton(int x,int y,int w,int h,Component message,OnPress press){
            //#if MC_VERSION >= 11903
            super(x,y,w,h,message,press,DEFAULT_NARRATION);
            //#else
            //$$ super(x,y,w,h,message,press);
            //#endif
        }
        //#if MC_VERSION >= 12000
        @Override public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float delta){draw(GraphicContext.of(graphics));}
        //#elseif MC_VERSION >= 11904
        //$$ @Override public void renderWidget(com.mojang.blaze3d.vertex.PoseStack stack,int mouseX,int mouseY,float delta){draw(GraphicContext.of(stack));}
        //#else
        //$$ @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack stack,int mouseX,int mouseY,float delta){draw(GraphicContext.of(stack));}
        //#endif
        private void draw(GraphicContext g){
            //#if MC_VERSION >= 11903
            int px=getX(),py=getY();
            //#else
            //$$ int px=x,py=y;
            //#endif
            boolean hover=isHoveredOrFocused(),accent=selected||primary&&active;
            int background=accent?0xFF3A444F:!active?0xFF23262B:hover?0xFF373C44:0xFF292D33;
            int border=accent?0xFF76889D:!active?0xFF30343A:danger?0xFF795658:hover?0xFF677180:0xFF454B55;
            int text=!active&&!selected?0xFF77808D:danger?0xFFE4A0A0:0xFFE1E6EC;
            g.fill(px,py,px+width,py+height,border);g.fill(px+1,py+1,px+width-1,py+height-1,background);
            var font=Minecraft.getInstance().font;Component label=getMessage();int available=Math.max(0,width-12);
            if(font.width(label)>available)label=ComponentHelper.literal(font.plainSubstrByWidth(label.getString(),Math.max(0,available-font.width("…")))+"…");
            if(subtitle==null)g.drawString(font,label,px+(width-font.width(label))/2,py+(height-8)/2,text,false);
            else{
                g.drawString(font,label,px+6,py+4,text,false);
                Component note=subtitle;if(font.width(note)>available)note=ComponentHelper.literal(font.plainSubstrByWidth(note.getString(),Math.max(0,available-font.width("…")))+"…");
                g.drawString(font,note,px+6,py+15,0xFFADB7C4,false);
            }
            if(isFocused()&&active)g.fill(px+4,py+height-3,px+width-4,py+height-2,accent?0xFFBFD6FF:0xFF5C8FE7);
        }
    }
}
