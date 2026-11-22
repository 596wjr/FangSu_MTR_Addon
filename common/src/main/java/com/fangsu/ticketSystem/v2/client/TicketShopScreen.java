package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ticketSystem.v2.*;
import com.fangsu.ui.ticketMachine.TicketMachineMainScreen;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.utils.GraphicContext;
import com.fangsu.utils.ScreenUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 普通单程票在同一界面选目的站或票额。 */
public final class TicketShopScreen extends TicketMachineMainScreen {
    private TicketPackets.Message state;
    private final TicketTransactions.Request request=new TicketTransactions.Request();
    private TicketTransactions.Quote offer;
    private String status=t("select_destination");
    private String pendingPointProduct;
    private Station departure;
    private long sequence, quoteAt, refreshAt;
    private boolean quoting, paying, explicitZero;
    public TicketShopScreen(TicketPackets.Message state){
        this(state, "");
    }
    TicketShopScreen(TicketPackets.Message state, String preferredProduct) {
        super(ComponentHelper.translatable("ui.fangsu.ticketing.title"));this.state=state;
        request.payment=state.config.mtrPayment?"mtr":"item";
        var products=products();
        request.product=products.stream().filter(p -> p.id.equals(preferredProduct)).findFirst()
                .or(() -> products.stream().findFirst()).map(p -> p.id).orElse("");
        selectDefaultDeparture();
        rebuildChoices();onTicketChoiceChanged();
    }
    public boolean matches(TicketPackets.Message reply){return state.pos==reply.pos&&state.session.equals(reply.session);}
    private List<Product> products(){return state.config.saleProducts(state.catalogue,0).stream().filter(p -> !p.requiresStations()).toList();}
    private static String t(String key,Object...args){return ComponentHelper.translatable("ui.fangsu.ticketing."+key,args).getString();}
    private Product product(){return state.catalogue.products.get(request.product);}
    private boolean single(){return product()!=null&&product().kind==Kind.SINGLE;}
    private void selectDefaultDeparture() {
        departure = state.defaultSaleOrigin();
    }
    private static String stationName(Station station) {
        return TextUtil.getNonExtraParts(station.name).replace('|', ' ');
    }
    private TicketPackets.Message message(String action){var m=new TicketPackets.Message();m.action=action;m.pos=state.pos;m.session=state.session;m.requestId=sequence;return m;}
    public void response(TicketPackets.Message reply){
        if(!matches(reply))return;
        if("paid".equals(reply.action)){
            if(paying&&reply.requestId==sequence){paying=false;offer=null;onClose();}
            return;
        }
        if("snapshot".equals(reply.action)){
            state=reply;paying=false;status=t("success");offer=null;isConfirming=false;
            selectDefaultDeparture();rebuildChoices();onTicketChoiceChanged();return;
        }
        if(reply.requestId!=sequence)return;
        quoting=false;
        if("error".equals(reply.action)){paying=false;offer=null;status=TicketMessages.text(reply.error).getString();refreshAt=System.currentTimeMillis()+10000;}
        else if("quote".equals(reply.action)){
            offer=reply.offer;refreshAt=System.currentTimeMillis()+10000;
            ticketPrice=offer.amount/Math.max(1,request.count);
            status=t("total",offer.amount);
        }
    }
    private void rebuildChoices(){
        routes=new ArrayList<>();selectedRoute=null;selectedStation=null;stationScroll=0;routePage=0;ticketPrice=0;customPriceInput="";
        explicitZero=false;
        isEditingCustomPrice=false;
        if(product()!=null&&product().kind!=Kind.SINGLE){
            Product p=product();routes.add(new RouteFareInfo(p.name,0xFF347EAC,Set.of(new StationFareInfo(p.name,p.price,p.id))));ticketPrice=p.price;
        }else{
            Map<String, StationFareInfo> destinations = new LinkedHashMap<>();
            List<Station> stations = state.saleStations();
            Network network = state.catalogue.networks.get(state.config.network);
            if (single() && departure != null && network != null) {
                for (Station station : stations) {
                    if (departure.key.equals(station.key) || !departure.dimension.equals(station.dimension)) continue;
                    try {
                        destinations.put(station.key, new StationFareInfo(stationName(station), network.rule.fare(departure, station), station.key));
                    } catch (IllegalArgumentException | ArithmeticException ignored) {
                        // 不能计价的站点不进入购买列表。
                    }
                }
            }
            if (!destinations.isEmpty()) {
                for (var route : TicketRouteChoices.load()) {
                    Set<StationFareInfo> members = new LinkedHashSet<>();
                    for (Station station : stations)
                        if (route.stations().contains(station.id) && destinations.containsKey(station.key))
                            members.add(destinations.get(station.key));
                    if (!members.isEmpty()) routes.add(new RouteFareInfo(route.name(), route.color() | 0xFF000000, members));
                }
            }
            // 恢复原界面的线路优先顺序，全部车站入口也包括没有客运线路的站。
            routes.add(new RouteFareInfo(t("all_stations"), 0xFF347EAC, new LinkedHashSet<>(destinations.values())));
        }
        selectedRoute=routes.get(0);
    }
    @Override protected String currentStationName(){return state.station==null?"":state.station.name;}
    @Override protected boolean useTextShadow(){return false;}
    @Override protected boolean isCountAllowed(int count){return !paying;}
    @Override protected void onCustomPriceEdited(String value){
        try{ticketPrice=value.isEmpty()?0:Integer.parseInt(value);}catch(NumberFormatException ex){ticketPrice=0;}
        explicitZero=!value.isEmpty()&&ticketPrice==0;
        selectedStation=null;onTicketChoiceChanged();
    }
    @Override protected void onRouteSelected(RouteFareInfo route) {
        if (single() && selectedStation != null && !route.stations().contains(selectedStation)) {
            selectedStation = null;
            ticketPrice = 0;
            onTicketChoiceChanged();
        }
    }
    @Override protected void onTicketChoiceChanged(){
        if(paying)return;
        request.origin=single()&&departure!=null?departure.key:"";
        request.destination=single()&&selectedStation!=null?selectedStation.key():"";
        if (!request.destination.isEmpty() || ticketPrice>0) explicitZero=false;
        request.fare=request.destination.isEmpty()&&(ticketPrice>0||explicitZero)?ticketPrice:-1;
        request.count=ticketCount;
        offer=null;quoting=false;sequence=TicketClient.nextRequestId();quoteAt=System.currentTimeMillis()+120;refreshAt=0;
        status=ready()?t("updating"):t("select_destination");
    }
    private boolean ready(){
        return product()!=null&&(!single()||!request.destination.isEmpty()||request.fare>=0);
    }
    @Override public void tick(){
        super.tick();long now=System.currentTimeMillis();
        if(pendingPointProduct!=null) {
            String id=pendingPointProduct;pendingPointProduct=null;
            Minecraft.getInstance().setScreen(new PointToPointScreen(state,id,this));
            return;
        }
        if(paying)return;
        if(!quoting&&ready()&&(quoteAt>0&&now>=quoteAt||refreshAt>0&&now>=refreshAt)){
            quoteAt=0;refreshAt=0;quoting=true;var m=message("quote");m.request=copy(request,TicketTransactions.Request.class);TicketClient.send(m);
        }
    }
    @Override public void removed(){
        super.removed();
        if (quoting) onTicketChoiceChanged();
    }
    @Override protected boolean canConfirmPurchase(){return !paying&&!quoting&&!isEditingCustomPrice&&offer!=null;}
    @Override protected void submitPurchase(){
        if(!canConfirmPurchase())return;var m=message("pay");m.quote=offer.id;offer=null;paying=true;status=t("paying");TicketClient.send(m);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(paying)return true;return super.mouseClicked(x,y,button);}
    @Override protected void renderConfirmationDetails(GraphicContext g){
        int y=36;String name=product()==null?t("title"):product().name;
        List<String> lines=new ArrayList<>();lines.add(t("confirm"));lines.add(name);
        if(single()&&!request.destination.isEmpty()&&departure!=null&&selectedStation!=null) {
            lines.add(t("departure")+" "+stationName(departure));
            lines.add(t("destination",selectedStation.name()));
        } else if(single()) lines.add(t("card_unit_price",request.fare));
        lines.add(t("quantity",ticketCount));
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
        int buttonWidth = single() ? 78 : 118;
        if(smallButton(g,0,190,buttonWidth,product()==null?t("select_product"):product().name,false,mouseX,mouseY)){
            var choices=state.config.saleProducts(state.catalogue,0).stream().map(p->new ConfigChoiceScreen.Choice(p.id,p.name)).toList();
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,t("products"),choices,v->{
                if(state.catalogue.products.get(v).requiresStations()){pendingPointProduct=v;return;}
                request.product=v;isConfirming=false;rebuildChoices();onTicketChoiceChanged();
            }));
        }
        if(smallButton(g,buttonWidth+3,190,buttonWidth,"mtr".equals(request.payment)?t("pay_mtr"):t("pay_items"),false,mouseX,mouseY)){
            var choices=new ArrayList<ConfigChoiceScreen.Choice>();if(state.config.mtrPayment)choices.add(new ConfigChoiceScreen.Choice("mtr",t("mtr_balance")));if(state.config.itemPayment)choices.add(new ConfigChoiceScreen.Choice("item",t("item_currency")));
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,t("select_payment"),choices,v->{request.payment=v;onTicketChoiceChanged();}));
        }
        if(single() && smallButton(g,162,190,78,departure==null?t("pick_departure"):t("departure")+" "+stationName(departure),false,mouseX,mouseY)) {
            var choices=state.saleStations().stream()
                    .map(station->new ConfigChoiceScreen.Choice(station.key,stationName(station))).toList();
            Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,t("pick_departure"),choices,key->{
                departure=state.saleStation(key);
                isConfirming=false;rebuildChoices();onTicketChoiceChanged();
            }));
        }
        if(routes.size()>8&&!isConfirming){
            if(smallButton(g,40,48,22,"‹",false,mouseX,mouseY))routePage=Math.max(0,routePage-1);
            if(smallButton(g,178,48,22,"›",false,mouseX,mouseY))routePage=Math.min((routes.size()-1)/8,routePage+1);
        }
    }
    @Override protected void renderPriceChoices(GraphicContext g,int mouseX,int mouseY){
        if(product()!=null&&product().kind==Kind.SINGLE){super.renderPriceChoices(g,mouseX,mouseY);return;}
        if(isConfirming)return;
        List<Product> products=products();for(int i=0;i<Math.min(6,products.size());i++){
            Product p=products.get(i);if(smallButton(g,207,42+i*18,30,t("itinerary_short"),p.id.equals(request.product),mouseX,mouseY)){
                request.product=p.id;rebuildChoices();onTicketChoiceChanged();
            }
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
