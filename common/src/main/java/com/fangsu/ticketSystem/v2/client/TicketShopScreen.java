package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ticketSystem.v2.*;
import com.fangsu.ui.*;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

public final class TicketShopScreen extends ThemedConfigScreen {
    private TicketPackets.Message state;
    private TicketTransactions.Request request=new TicketTransactions.Request();
    private TicketTransactions.Quote offer;
    private String status="选择业务后查看报价，确认付款。";
    private boolean pending;
    public TicketShopScreen(TicketPackets.Message state) { super(ComponentHelper.literal("票务服务"));this.state=state; request.payment=state.config.mtrPayment?"mtr":"item"; }
    public boolean matches(TicketPackets.Message reply) { return state.pos==reply.pos && state.session.equals(reply.session); }
    public void response(TicketPackets.Message reply) {
        pending=false;
        if("error".equals(reply.action)) { status=reply.error; offer=null; }
        else if("quote".equals(reply.action)) { offer=reply.offer; status="请核对金额后确认付款"; }
        else { state=reply; offer=null;status="操作成功"; }
        requestRebuild();
    }
    private TicketPackets.Message message(String action) {
        TicketPackets.Message message=new TicketPackets.Message();message.action=action;message.pos=state.pos;message.session=state.session;return message;
    }
    private int button(ContentLayout layout,int y,String label,Runnable action) {
        var widget=addButton(layout.areaLeft,y,layout.contentWidth,20,ComponentHelper.literal(label),b->action.run());
        widget.active=!pending;addEntry(widget,y);return y+24;
    }
    private int text(ContentLayout layout,int y,String value) { return addWrappedText(layout.areaLeft,y,layout.contentWidth,ComponentHelper.literal(value),0xDDDDDD)+6; }
    private int number(ContentLayout layout,int y,String label,int value,java.util.function.IntConsumer setter) {
        y=text(layout,y,label);int row=y;
        EditBox box=new EditBox(font,layout.areaLeft,row,layout.contentWidth,20,ComponentHelper.literal(label));box.setMaxLength(10);box.setValue(Integer.toString(value));
        box.setResponder(v->{try{int n=Integer.parseInt(v);if(n>0){setter.accept(n);offer=null;}}catch(NumberFormatException ignored){}});addRenderableWidget(box);addEntry(box,row);return row+26;
    }
    @Override protected void buildScrollableContent(ContentLayout layout) {
        int y=layout.y;
        Network network=state.catalogue.networks.get(state.config.network);
        y=text(layout,y,network==null?"网络不可用":network.name);
        y=text(layout,y,status);
        if(state.card!=null) y=text(layout,y,"手持："+state.card.product.name+(state.card.product.kind==Kind.IC?" · 余额 "+state.card.balance+"元":""));
        if(state.kind!=2) {
            y=button(layout,y,"购买票卡",()->{request.action="buy";offer=null;requestRebuild();});
        }
        y=button(layout,y,"IC卡充值",()->{request.action="recharge";offer=null;requestRebuild();});
        if(state.kind==2) y=button(layout,y,"补票 / 修复本站出站",()->{request.action="adjust";offer=null;requestRebuild();});
        if(state.kind==2 && "buy".equals(request.action)) request.action="adjust";
        if("buy".equals(request.action)) {
            List<Product> products=state.config.products.stream().map(state.catalogue.products::get).filter(Objects::nonNull)
                .filter(p->p.enabled && (state.kind!=1||p.kind==Kind.IC)).toList();
            if(products.stream().noneMatch(p->p.id.equals(request.product)) && !products.isEmpty()) request.product=products.get(0).id;
            Product selected=state.catalogue.products.get(request.product);
            y=button(layout,y,"票种："+(selected==null?"请选择":selected.name),()->{
                List<ConfigChoiceScreen.Choice> choices=products.stream().map(p->new ConfigChoiceScreen.Choice(p.id,p.name)).toList();
                Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,"选择票务产品",choices,v->{request.product=v;offer=null;}));
            });
            if(selected!=null) {
                if(selected.kind==Kind.IC) y=text(layout,y,"卡费 "+selected.price+"元 ＋ 初始储值 "+selected.initialBalance+"元");
                else if(selected.kind!=Kind.SINGLE) y=text(layout,y,"售价 "+selected.price+"元");
                if(selected.kind==Kind.SINGLE) {
                    Station destination=state.catalogue.station(state.config.network,request.destination);
                    y=button(layout,y,"目的站："+(destination==null?"请选择":destination.name),()->{
                        var choices=state.catalogue.stations(state.config.network).stream().map(s->new ConfigChoiceScreen.Choice(s.key,s.name)).toList();
                        Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,"选择目的站",choices,v->{request.destination=v;offer=null;}));
                    });
                }
            }
            y=number(layout,y,"购买数量（1至6张）",request.count,v->request.count=v);
        } else if("recharge".equals(request.action)) y=number(layout,y,"充值金额（元）",request.recharge,v->request.recharge=v);
        y=button(layout,y,"付款方式："+("mtr".equals(request.payment)?"MTR余额":"物品货币（"+state.config.currency+"，每个抵"+state.config.currencyValue+"元）"),()->{request.payment="mtr".equals(request.payment)?"item":"mtr";offer=null;requestRebuild();});
        y=button(layout,y,"查看服务端报价",()->{pending=true;offer=null;var m=message("quote");m.request=request;TicketClient.send(m);requestRebuild();});
        if(offer!=null) {
            y=text(layout,y,offer.description+"："+offer.amount+"元");
            y=text(layout,y,"实际扣除："+offer.paymentCount+("mtr".equals(offer.payment)?"元MTR余额":"个 "+state.config.currency+"（不找零）"));
            y=button(layout,y,"确认付款",()->{var m=message("pay");m.quote=offer.id;offer=null;pending=true;TicketClient.send(m);requestRebuild();});
        }
        if(state.admin) button(layout,y,"管理设备、网络及产品",()->Minecraft.getInstance().setScreen(new TicketAdminScreen(state,this)));
    }
    @Override public boolean isPauseScreen(){return false;}
}
