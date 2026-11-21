package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ticketSystem.v2.*;
import com.fangsu.ui.ticketMachine.TicketMachineMainScreen;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import com.fangsu.utils.ScreenUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 直接使用原售票机界面；只接入网络数据、产品选择和自动报价。 */
public final class TicketShopScreen extends TicketMachineMainScreen {
    private TicketPackets.Message state;
    private final TicketTransactions.Request request=new TicketTransactions.Request();
    private TicketTransactions.Quote offer;
    private String status=t("select_destination");
    private long sequence, quoteAt, refreshAt;
    private boolean quoting, paying;
    private String held="";
    public TicketShopScreen(TicketPackets.Message state){
        super(ComponentHelper.translatable("ui.fangsu.ticketing.title"));this.state=state;
        request.payment=state.config.mtrPayment?"mtr":"item";
        var products=products();request.product=products.isEmpty()?"":products.get(0).id;
        if(state.kind==2)request.action="adjust";
        rebuildChoices();onTicketChoiceChanged();
    }
    public boolean matches(TicketPackets.Message reply){return state.pos==reply.pos&&state.session.equals(reply.session);}
    private List<Product> products(){return state.config.saleProducts(state.catalogue,state.kind);}
    private int displayPrice(Product p){return p.kind==Kind.IC?(int)Math.min(Integer.MAX_VALUE,(long)p.price+p.initialBalance):p.price;}
    private static String t(String key,Object...args){return ComponentHelper.translatable("ui.fangsu.ticketing."+key,args).getString();}
    private Product product(){return state.catalogue.products.get(request.product);}
    private TicketPackets.Message message(String action){var m=new TicketPackets.Message();m.action=action;m.pos=state.pos;m.session=state.session;m.requestId=sequence;return m;}
    public void response(TicketPackets.Message reply){
        if(!matches(reply))return;
        if("paid".equals(reply.action)){
            if(paying&&reply.requestId==sequence){paying=false;offer=null;onClose();}
            return;
        }
        if("snapshot".equals(reply.action)){
            state=reply;paying=false;status=t("success");offer=null;isConfirming=false;rebuildChoices();onTicketChoiceChanged();return;
        }
        if(reply.requestId!=sequence)return;
        quoting=false;
        if("error".equals(reply.action)){paying=false;offer=null;status=TicketMessages.text(reply.error).getString();refreshAt=System.currentTimeMillis()+10000;}
        else if("quote".equals(reply.action)){
            offer=reply.offer;refreshAt=System.currentTimeMillis()+10000;
            if(offer.cardBefore!=null)state.card=JSON.fromJson(offer.cardBefore,Card.class);
            ticketPrice="buy".equals(request.action)?offer.amount/Math.max(1,request.count):offer.amount;
            status=t("total",offer.amount);
        }
    }
    private void rebuildChoices(){
        routes=new ArrayList<>();selectedRoute=null;selectedStation=null;stationScroll=0;
        if(state.kind==0&&product()!=null&&product().kind!=Kind.SINGLE){
            Product p=product();routes.add(new RouteFareInfo(p.name,0xFF347EAC,Set.of(new StationFareInfo(p.name,p.price,p.id))));ticketPrice=p.price;
        }else if(state.kind==0){
            Map<String,StationFareInfo> stations=new LinkedHashMap<>();
            for(Station station:state.catalogue.stations(state.config.network))if(state.fares!=null&&state.fares.containsKey(station.key))
                stations.put(station.key,new StationFareInfo(station.name,state.fares.get(station.key),station.key));
            routes.add(new RouteFareInfo(t("all_stations"),0xFF347EAC,new LinkedHashSet<>(stations.values())));
            for(var route:TicketRouteChoices.load()){
                Set<StationFareInfo> members=new LinkedHashSet<>();
                for(Station station:state.catalogue.stations(state.config.network))if(route.stations().contains(station.id)&&stations.containsKey(station.key))members.add(stations.get(station.key));
                if(!members.isEmpty())routes.add(new RouteFareInfo(route.name(),route.color()|0xFF000000,members));
            }
        }else if(state.kind==1){
            Set<StationFareInfo> rows=new LinkedHashSet<>();for(Product p:products())rows.add(new StationFareInfo(p.name,displayPrice(p),p.id));
            routes.add(new RouteFareInfo(t("buy_cards"),0xFF347EAC,rows));
            Set<StationFareInfo> amounts=new LinkedHashSet<>();for(int value:new int[]{10,20,50,100})amounts.add(new StationFareInfo(t("amount",value),value,"recharge/"+value));
            routes.add(new RouteFareInfo(t("recharge"),0xFFDD8D32,amounts));
        }else routes.add(new RouteFareInfo(t("adjust"),0xFF347EAC,Set.of(new StationFareInfo(t("adjust_here"),0,"adjust"))));
        selectedRoute=routes.get(0);
        if(state.kind==1){selectedStation=selectedRoute.stations().stream().filter(s->s.key().equals(request.product)).findFirst().orElse(null);ticketPrice=selectedStation==null?0:selectedStation.fare();}
    }
    @Override protected void onRouteSelected(RouteFareInfo route){
        if(state.kind==1){request.action=route==routes.get(1)?"recharge":"buy";if("recharge".equals(request.action))ticketCount=1;selectedStation=route.stations().stream().findFirst().orElse(null);ticketPrice=selectedStation==null?0:selectedStation.fare();onTicketChoiceChanged();}
    }
    @Override protected String currentStationName(){return state.station==null?"":state.station.name;}
    @Override protected boolean useTextShadow(){return false;}
    @Override protected boolean isCountAllowed(int count){return !paying&&("buy".equals(request.action)||count==1);}
    @Override protected void onCustomPriceEdited(String value){
        try{ticketPrice=value.isEmpty()?0:Integer.parseInt(value);}catch(NumberFormatException ex){ticketPrice=0;}
        selectedStation=null;onTicketChoiceChanged();
    }
    @Override protected void onTicketChoiceChanged(){
        if(paying)return;
        if(state.kind==0){
            request.destination=selectedStation==null?"":selectedStation.key();request.fare=selectedStation==null&&ticketPrice>0?ticketPrice:-1;
        }else if(state.kind==1){
            if("buy".equals(request.action)&&selectedStation!=null)request.product=selectedStation.key();
            if("recharge".equals(request.action))request.recharge=ticketPrice;
        }
        request.count="buy".equals(request.action)?ticketCount:1;
        offer=null;quoting=false;sequence++;quoteAt=System.currentTimeMillis()+120;refreshAt=0;
        status=ready()?t("updating"):state.kind==2?t("hold_card"):t("select_options");
    }
    private boolean ready(){
        if("buy".equals(request.action))return product()!=null&&(product().kind!=Kind.SINGLE||!request.destination.isEmpty()||request.fare>=0);
        var player=Minecraft.getInstance().player;return player!=null&&TicketCards.isNative(player.getMainHandItem())&&(!"recharge".equals(request.action)||request.recharge>0);
    }
    @Override public void tick(){
        super.tick();long now=System.currentTimeMillis();
        if(paying)return;
        var player=Minecraft.getInstance().player;
        String current=player==null?"":player.getMainHandItem().toString()+"/"+player.getMainHandItem().getTag();
        if(!"buy".equals(request.action)&&!current.equals(held)){held=current;offer=null;sequence++;quoteAt=now+120;quoting=false;}
        if(!quoting&&ready()&&(quoteAt>0&&now>=quoteAt||refreshAt>0&&now>=refreshAt)){
            quoteAt=0;refreshAt=0;quoting=true;var m=message("quote");m.request=copy(request,TicketTransactions.Request.class);TicketClient.send(m);
        }
    }
    @Override protected boolean canConfirmPurchase(){return !paying&&!quoting&&!isEditingCustomPrice&&offer!=null;}
    @Override protected void submitPurchase(){
        if(!canConfirmPurchase())return;var m=message("pay");m.quote=offer.id;offer=null;paying=true;status=t("paying");TicketClient.send(m);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(paying)return true;return super.mouseClicked(x,y,button);}
    @Override protected void renderConfirmationDetails(GraphicContext g){
        int y=36;String name=product()==null?t("title"):product().name;
        List<String> lines=new ArrayList<>();lines.add(t("confirm"));lines.add("buy".equals(request.action)?name:"recharge".equals(request.action)?t("recharge"):t("adjust_here"));
        if(state.station!=null)lines.add(t("origin",state.station.name.replace('|',' ')));
        if(state.kind==0&&product()!=null&&product().kind==Kind.SINGLE&&selectedStation!=null)lines.add(t("destination",selectedStation.name().replace('|',' ')));
        if("buy".equals(request.action))lines.add(t("quantity",ticketCount));
        if(offer!=null){lines.add(t("total",offer.amount));lines.add(t("mtr".equals(offer.payment)?"deduct_mtr":"deduct_items",offer.paymentCount));if("item".equals(offer.payment))lines.add(t("no_change",state.config.currency));}
        else lines.add(status);
        for(String text:lines){ScreenUtil.drawScrollingText(g,font,ComponentHelper.literal(text),texturePosX(48),texturePosY(y),180,10,0xFF19304B,true,false);y+=12;}
    }
    @Override protected void renderLiveQuote(GraphicContext g){
        String text=paying?t("paying"):offer==null?status:t("mtr".equals(offer.payment)?"summary_mtr":"summary_items",offer.amount,offer.paymentCount);
        ScreenUtil.drawScrollingText(g,font,ComponentHelper.literal(text),texturePosX(3),texturePosY(166),164,10,0xFFFFFFFF,true,false);
    }
    private boolean smallButton(GraphicContext g,int x,int y,int w,String text,boolean selected,int mouseX,int mouseY){
        int px=texturePosX(x),py=texturePosY(y);boolean hovered=mouseX>=px&&mouseX<px+w&&mouseY>=py&&mouseY<py+14;
        String color=selected?"orange":"blue";ScreenUtil.drawNineSlice(g.asMinecraft(),new ResourceLocation("fangsu:textures/gui/button_"+color+(hovered?"_selected":"")+".png"),px,py,w,14);
        ScreenUtil.drawScrollingText(g,font,ComponentHelper.literal(text),px+2,py+3,w-4,9,0xFFFFFFFF,true,false);
        if(!paying&&hovered&&mouseClickInfo!=null){mouseClickInfo=null;return true;}return false;
    }
    @Override protected void renderServiceControls(GraphicContext g,int mouseX,int mouseY){
        if(state.kind!=2&&smallButton(g,0,190,118,product()==null?t("select_product"):product().name,false,mouseX,mouseY)){
            var choices=products().stream().map(p->new ConfigChoiceScreen.Choice(p.id,p.name)).toList();
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,t("products"),choices,v->{request.product=v;request.action="buy";isConfirming=false;rebuildChoices();onTicketChoiceChanged();}));
        }
        if(smallButton(g,122,190,118,"mtr".equals(request.payment)?t("pay_mtr"):t("pay_items"),false,mouseX,mouseY)){
            var choices=new ArrayList<ConfigChoiceScreen.Choice>();if(state.config.mtrPayment)choices.add(new ConfigChoiceScreen.Choice("mtr",t("mtr_balance")));if(state.config.itemPayment)choices.add(new ConfigChoiceScreen.Choice("item",t("item_currency")));
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,t("select_payment"),choices,v->{request.payment=v;onTicketChoiceChanged();}));
        }
        if(routes.size()>8&&!isConfirming){
            if(smallButton(g,40,48,22,"‹",false,mouseX,mouseY))routePage=Math.max(0,routePage-1);
            if(smallButton(g,178,48,22,"›",false,mouseX,mouseY))routePage=Math.min((routes.size()-1)/8,routePage+1);
        }
    }
    @Override protected void renderPriceChoices(GraphicContext g,int mouseX,int mouseY){
        if(state.kind==0&&product()!=null&&product().kind==Kind.SINGLE||"recharge".equals(request.action)){super.renderPriceChoices(g,mouseX,mouseY);return;}
        if(isConfirming)return;
        List<Product> products=products();for(int i=0;i<Math.min(6,products.size());i++){
            Product p=products.get(i);if(smallButton(g,207,42+i*18,30,p.kind==Kind.DAY?t("day_short",p.validDays):p.kind==Kind.IC?"IC":t("itinerary_short"),p.id.equals(request.product),mouseX,mouseY)){
                request.product=p.id;selectedStation=selectedRoute.stations().stream().filter(s->s.key().equals(p.id)).findFirst().orElse(null);ticketPrice=displayPrice(p);onTicketChoiceChanged();
            }
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
