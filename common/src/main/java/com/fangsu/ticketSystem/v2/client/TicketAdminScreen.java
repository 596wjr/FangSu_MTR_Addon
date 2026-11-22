package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ticketSystem.v2.*;
import com.fangsu.ui.*;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 表单式票务管理：客户端只编辑草稿，保存由服务端验证目录版本和管理员权限。 */
public final class TicketAdminScreen extends ThemedConfigScreen {
    private TicketPackets.Message state;
    private Catalogue catalogue;
    private final Screen parent;
    private String page="device", networkId=DEFAULT_NETWORK, productId="fangsu:single", unionId=DEFAULT_UNION, status="修改后点击保存；停用保留既有票卡权益。";
    private boolean pending;
    private ContentLayout layout;
    private int y;
    public TicketAdminScreen(TicketPackets.Message state,Screen parent) {
        super(ComponentHelper.literal("票务管理"));this.state=copy(state,TicketPackets.Message.class);this.catalogue=this.state.catalogue;this.parent=parent;
    }
    public boolean matches(TicketPackets.Message reply){return state.session.equals(reply.session)&&state.pos==reply.pos;}
    public void error(String message){status=TicketMessages.text(message).getString();pending=false;requestRebuild();}
    public void saved(TicketPackets.Message reply){state=reply;catalogue=reply.catalogue;status="配置已保存";pending=false;if(parent instanceof TicketShopScreen shop)shop.response(reply);requestRebuild();}
    private void label(String text){y=addWrappedText(layout.areaLeft,y,layout.contentWidth,ComponentHelper.literal(text),0xDDDDDD)+6;}
    private void button(String text,Runnable action){var b=addButton(layout.areaLeft,y,layout.contentWidth,20,ComponentHelper.literal(text),w->action.run());b.active=!pending;addEntry(b,y);y+=24;}
    private void deleteButton(String text, String warning, Runnable action) {
        var button = danger(addButton(layout.areaLeft, y, layout.contentWidth, 20, ComponentHelper.literal(text),
                widget -> Minecraft.getInstance().setScreen(new ConfirmScreen(confirm -> {
                    Minecraft.getInstance().setScreen(this);
                    if (!confirm) return;
                    try {
                        action.run();
                        status = "已从草稿删除，保存后生效";
                        scrollOffset = 0;
                        requestRebuild();
                    } catch (RuntimeException exception) {
                        error(exception.getMessage());
                    }
                }, ComponentHelper.literal(text), ComponentHelper.literal(warning)))));
        button.active = !pending;
        addEntry(button, y);
        y += 24;
    }
    private void field(String label,String initial,Consumer<String> setter){
        label(label);EditBox edit=new EditBox(font,layout.areaLeft,y,layout.contentWidth,20,ComponentHelper.literal(label));edit.setMaxLength(512);edit.setValue(initial==null?"":initial);
        edit.setResponder(setter);addRenderableWidget(edit);addEntry(edit,y);y+=26;
    }
    private void integer(String label,int value,IntConsumer setter){field(label,Integer.toString(value),v->{try{setter.accept(Integer.parseInt(v));}catch(NumberFormatException ignored){}});}
    private void toggle(String label,boolean value,Consumer<Boolean> setter){button(label+"："+(value?"是":"否"),()->{setter.accept(!value);requestRebuild();});}
    private void choose(String title,List<ConfigChoiceScreen.Choice> choices,Consumer<String> setter){Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,title,choices,setter));}
    private void chooseNetwork(Consumer<String> setter){choose("选择票务网络",catalogue.networks.values().stream().map(n->new ConfigChoiceScreen.Choice(n.id,n.name+(n.enabled?"":"（停用）"))).toList(),setter);}
    private void multi(String title,List<ConfigChoiceScreen.Choice> choices,Set<String> values,int maximum,Consumer<Set<String>> setter){
        button(title+"：已选"+values.size()+"项",()->Minecraft.getInstance().setScreen(new TicketSelectionScreen(this,title,choices,values,maximum,setter)));
    }
    private List<ConfigChoiceScreen.Choice> networkChoices(){return catalogue.networks.values().stream().map(n->new ConfigChoiceScreen.Choice(n.id,n.name)).toList();}
    private String networkName(String id){Network n=catalogue.networks.get(id);return n==null?id:n.name;}
    private void station(String title,String network,String value,Consumer<String> setter){
        Station found=catalogue.station(network,value);
        button(title+"："+(value.isEmpty()?"不限":found==null?value:found.name),()->{
            List<ConfigChoiceScreen.Choice> choices=new ArrayList<>();choices.add(new ConfigChoiceScreen.Choice("","不限"));
            for(Station s:catalogue.stations(network))choices.add(new ConfigChoiceScreen.Choice(s.key,s.name));choose(title,choices,setter);
        });
    }
    @Override protected void buildFixedWidgets(){
        String[] pages={"device","networks","products","unions"},names={"设备","网络","产品","联合组"};
        int cell=(getPanelRight()-getPanelLeft())/4;
        for(int i=0;i<pages.length;i++){
            String target=pages[i];var tab=addFixedWidget(themedButton(getPanelLeft()+i*cell,getPanelTop()+3,cell-3,20,ComponentHelper.literal(names[i]),b->{page=target;scrollOffset=0;requestRebuild();}));tab.active=!pending;selected(tab,page.startsWith(target.substring(0,target.length()-1)) || ("networks".equals(target) && "tiers".equals(page)));
        }
        closeButton=addFixedWidget(themedButton(getPanelLeft(),height-40,Math.max(80,(getPanelRight()-getPanelLeft())/2-4),20,ComponentHelper.literal("返回（未保存修改不生效）"),b->onClose()));
        var save=addFixedWidget(themedButton(width/2+4,height-40,Math.max(80,(getPanelRight()-getPanelLeft())/2-4),20,ComponentHelper.literal("保存所有修改"),b->save()));save.active=!pending;
    }
    @Override protected int getContentTop(){return super.getContentTop()+22;}
    private void save(){
        if(pending)return;
        try{
            for(Network n:catalogue.networks.values())n.rule.validate();
            for(Product p:catalogue.products.values())p.validate(catalogue);
            if(state.config!=null)state.config.validate(catalogue,state.kind);
            TicketPackets.Message message=copy(state,TicketPackets.Message.class);message.action="save";message.catalogue=catalogue;
            pending=true;status="正在保存…";TicketClient.send(message);requestRebuild();
        }catch(RuntimeException ex){error(ex.getMessage());}
    }
    @Override protected void buildScrollableContent(ContentLayout layout){
        this.layout=layout;y=layout.y;label(status);
        switch(page){case "networks"->networks();case "network"->network();case "tiers"->tiers();case "products"->products();case "product"->product();case "unions"->unions();case "union"->union();default->device();}
    }
    private void networks(){
        importPreset();
        button("新建收费网络",()->{Network n=new Network();n.id="fangsu:network_"+UUID.randomUUID().toString().replace("-","");catalogue.networks.put(n.id,n);networkId=n.id;page="network";requestRebuild();});
        button("选择要编辑的网络（"+catalogue.networks.size()+"）",()->chooseNetwork(v->{networkId=v;page="network";}));
    }
    private void network(){
        Network n=catalogue.networks.get(networkId);if(n==null)return;
        field("网络名称",n.name,v->n.name=v);label("网络ID："+n.id);
        if(!DEFAULT_NETWORK.equals(n.id))toggle("启用销售",n.enabled,v->n.enabled=v);
        integer("IC卡最低进站余额（元）",n.minimumBalance,v->n.minimumBalance=v);
        if(!DEFAULT_NETWORK.equals(n.id))button("计费模式："+switch(n.rule.mode){case MTR->"MTR分区计费";case FIXED->"固定票价";case DISTANCE->"阶梯里程计费";},()->{
            choose("选择计费模式",List.of(new ConfigChoiceScreen.Choice("MTR","MTR分区计费"),new ConfigChoiceScreen.Choice("FIXED","固定票价"),new ConfigChoiceScreen.Choice("DISTANCE","阶梯里程计费")),v->{n.rule.mode=Pricing.valueOf(v);if(n.rule.mode==Pricing.DISTANCE&&n.rule.tiers.isEmpty())n.rule.tiers.add(new Tier());});
        });
        if(n.rule.mode==Pricing.FIXED)integer("每次行程票价（元）",n.rule.fixed,v->n.rule.fixed=v);
        if(n.rule.mode==Pricing.DISTANCE) button("编辑计费阶梯（"+n.rule.tiers.size()+" 档）",()->{
            page="tiers";scrollOffset=0;requestRebuild();
        });
        button("查看已绑定车站（"+catalogue.stations(n.id).size()+"，闸机自动维护）",()->choose("网络车站",catalogue.stations(n.id).stream().map(s->new ConfigChoiceScreen.Choice(s.key,s.name)).toList(),v->{}));
        if (!DEFAULT_NETWORK.equals(n.id)) deleteButton("删除网络",
                "删除「" + n.name + "」后，接入此网络的售票设备须重新绑定网络。请先解绑闸机并移除产品和联合组引用。", () -> {
                    require(state.config == null || !n.id.equals(state.config.network), "msg.fangsu.ticketing.remove_current_network");
                    catalogue.checkNetworkRemoval(n.id);
                    catalogue.networks.remove(n.id);
                    page = "networks";
                });
    }
    private void tiers() {
        Network network = catalogue.networks.get(networkId);
        if (network == null) return;
        List<Tier> tiers = network.rule.tiers;
        int left = layout.areaLeft;
        int column = (layout.contentWidth - 65) / 3;
        int limitX = left + 24, unitX = limitX + column, priceX = unitX + column, removeX = priceX + column;
        tableText(left, y, "档");
        tableText(limitX, y, "上限/格");
        tableText(unitX, y, "步长/格");
        tableText(priceX, y, "单价/元");
        y += 13;
        for (int i = 0; i < tiers.size(); i++) {
            int index = i;
            Tier tier = tiers.get(i);
            tableText(left, y + 4, Integer.toString(i + 1));
            if (i + 1 == tiers.size()) tableText(limitX, y + 4, "不限");
            else tierInput(limitX, column - 4, "累计距离上限", Double.toString(tier.end), value -> tier.end = Double.parseDouble(value));
            tierInput(unitX, column - 4, "计费步长", Integer.toString(tier.unit), value -> tier.unit = Integer.parseInt(value));
            tierInput(priceX, column - 4, "每步单价", Integer.toString(tier.price), value -> tier.price = Integer.parseInt(value));
            var remove = addButton(removeX, y, 40, 18, ComponentHelper.literal("移除"), widget -> {
                tiers.remove(index);
                tiers.get(tiers.size() - 1).end = -1;
                requestRebuild();
            });
            remove.active = !pending && tiers.size() > 1;
            addEntry(remove, y);
            y += 21;
        }
        int half = (layout.contentWidth - 6) / 2;
        var back = addButton(left, y, half, 18, ComponentHelper.literal("返回网络"), widget -> {
            page = "network"; scrollOffset = 0; requestRebuild();
        });
        addEntry(back, y);
        var add = addButton(left + half + 6, y, half, 18, ComponentHelper.literal("追加阶梯"), widget -> {
            double previous = tiers.size() < 2 ? 0 : tiers.get(tiers.size() - 2).end;
            if (!tiers.isEmpty()) tiers.get(tiers.size() - 1).end = Math.max(0, previous) + 3000;
            tiers.add(new Tier());
            requestRebuild();
        });
        add.active = !pending && tiers.size() < 64;
        addEntry(add, y);
        y += 24;
        label("各段分别向上取整后累加；最后一档不限距离。");
    }

    private void tableText(int x, int y, String text) {
        addEntry(createTextLabel(x, y, ComponentHelper.literal(text), TextLabel.Align.LEFT, 0xDDDDDD, false), y);
    }

    private void tierInput(int x, int width, String name, String initial, Consumer<String> setter) {
        var edit = new EditBox(font, x, y, width, 18, ComponentHelper.literal(name));
        edit.setMaxLength(24);
        edit.setEditable(!pending);
        edit.setValue(initial);
        edit.setResponder(text -> {
            try { setter.accept(text); } catch (NumberFormatException ignored) { }
        });
        addEntry(addRenderableWidget(edit), y);
    }
    private void products(){
        importPreset();
        button("新增票务产品",()->choose("选择产品预设",TicketPresets.productChoices(),v->{
            try{Product p=TicketPresets.newProduct(catalogue,v);productId=p.id;page="product";status="已应用产品预设，保存后生效";}
            catch(RuntimeException ex){error(ex.getMessage());}
        }));
        button("选择要编辑的产品（"+catalogue.products.size()+"）",()->choose("选择票务产品",catalogue.products.values().stream().map(p->new ConfigChoiceScreen.Choice(p.id,p.name+(p.enabled?"":"（停用）"))).toList(),v->{productId=v;page="product";}));
    }
    private void unions(){
        importPreset();
        button("新建网络联合组",()->{NetworkUnion u=new NetworkUnion();u.id="fangsu:union_"+UUID.randomUUID().toString().replace("-","");catalogue.unions.put(u.id,u);unionId=u.id;page="union";requestRebuild();});
        button("选择要编辑的联合组（"+catalogue.unions.size()+"）",()->choose("选择联合组",catalogue.unions.values().stream().map(u->new ConfigChoiceScreen.Choice(u.id,u.name)).toList(),v->{unionId=v;page="union";}));
    }
    private void importPreset(){
        button("从资源包导入网络、产品和联合组",()->choose("选择票务预设",TicketPresets.catalogueChoices(),v->{
            try{TicketPresets.importCatalogue(catalogue,v);status="已导入预设；同ID的已有配置保持原值，保存后生效";}
            catch(RuntimeException ex){error(ex.getMessage());}
        }));
    }
    private void union(){
        NetworkUnion u=catalogue.unions.get(unionId);if(u==null)return;
        field("联合名称",u.name,v->u.name=v);field("独立标识贴图（PNG资源）",u.texture,v->u.texture=v);
        toggle("启用新销售",u.enabled,v->u.enabled=v);label("IC 卡按当前成员网络授权；其他票种保留出票时的授权。");
        multi("成员网络",networkChoices(),u.networks,128,v->u.networks=v);
    }
    private void product(){
        Product p=catalogue.products.get(productId);if(p==null)return;
        field("产品名称",p.name,v->p.name=v);label("产品ID："+p.id);toggle("启用销售",p.enabled,v->p.enabled=v);
        button("票种："+kind(p.kind),()->choose("选择票种",Arrays.stream(Kind.values()).map(k->new ConfigChoiceScreen.Choice(k.name(),kind(k))).toList(),v->{p.kind=Kind.valueOf(v);p.style=p.kind==Kind.IC?"fangsu:ticketing/beijing_ic.json":p.kind==Kind.DAY?"fangsu:ticketing/day_pass.json":"fangsu:ticketing/single.json";}));
        field("票面样式资源",p.style,v->p.style=v);
        if (p.kind == Kind.SINGLE) {
            toggle("点对点车票（仅按起终站出售）", p.requiresStations(), value -> p.pointToPointOnly = value);
        } else integer(p.kind==Kind.IC?"卡费（元）":"售价（元）",p.price,v->p.price=v);
        if(p.kind==Kind.IC)integer("初始储值（元）",p.initialBalance,v->p.initialBalance=v);
        label("可用网络（日票只能选择一个）");
        multi("授权网络",networkChoices(),p.networks,p.kind==Kind.DAY?1:128,v->p.networks=v);
        label("联合组授权（票面标识独立叠加）");
        multi("联合组授权",catalogue.unions.values().stream().map(u->new ConfigChoiceScreen.Choice(u.id,u.name)).toList(),p.unions,16,v->p.unions=v);
        if(p.kind==Kind.DAY){
            button("日票时长："+p.validDays+"天",()->choose("选择有效天数",List.of(new ConfigChoiceScreen.Choice("1","一日 · 24小时"),new ConfigChoiceScreen.Choice("3","三日 · 72小时"),new ConfigChoiceScreen.Choice("7","七日 · 168小时")),v->{p.validDays=Integer.parseInt(v);p.style="fangsu:ticketing/"+(p.validDays==3?"three_day_pass":p.validDays==7?"seven_day_pass":"day_pass")+".json";}));
            label("首次成功进站起连续"+p.durationHours()+"小时，可完成到期前已开始的行程。");
        }
        if(p.kind==Kind.ITINERARY){
            integer("首次使用后有效小时数（0为不限期）",p.validHours,v->p.validHours=v);
            toggle("按顺序使用权益",p.ordered,v->p.ordered=v);
            for(int i=0;i<p.legs.size();i++){
                int index=i;Leg leg=p.legs.get(i);label("权益 "+(i+1));
                button("网络："+networkName(leg.network),()->chooseNetwork(v->leg.network=v));
                station("指定起点",leg.network,leg.from,v->leg.from=v);station("指定终点",leg.network,leg.to,v->leg.to=v);
                integer("本段票额（元）",leg.allowance,v->leg.allowance=v);
                if(i>0)button("上移此权益",()->{Collections.swap(p.legs,index,index-1);requestRebuild();});
                button("删除此权益",()->{p.legs.remove(index);requestRebuild();});
            }
            button("添加乘车权益",()->{Leg leg=new Leg();leg.network=p.networks.stream().findFirst().orElse(DEFAULT_NETWORK);p.legs.add(leg);requestRebuild();});
        }
        deleteButton("删除产品", "保存后售票设备将停止销售「" + p.name + "」。已发售票卡保留原有权益。", () -> {
            catalogue.products.remove(p.id);
            if (state.config != null) state.config.products.remove(p.id);
            page = "products";
        });
    }
    public static String kind(Kind kind){return switch(kind){case SINGLE->"单程票";case IC->"IC卡";case ITINERARY->"联程票";case DAY->"日票";};}
    private void device(){
        if(state.gate!=null){
            Map<String,String> gate=state.gate;String network=gate.getOrDefault("ticketNetwork",DEFAULT_NETWORK);
            button("接入网络："+networkName(network),()->chooseNetwork(v->gate.put("ticketNetwork",v)));
            boolean transfer = Boolean.parseBoolean(gate.getOrDefault("ticketTransfer", "false"));
            if (DEFAULT_NETWORK.equals(network) && !transfer) {
                toggle("固定收费", TicketGate.isFixedFare(gate), v -> gate.put("fareType", v ? "1" : "0"));
                if (TicketGate.isFixedFare(gate))
                    field("单次收费（元）", gate.getOrDefault("fareVal", "10"), v -> gate.put("fareVal", v));
            }
            if (!TicketGate.isFixedFare(gate))
                toggle("出站闸机",Boolean.parseBoolean(gate.getOrDefault("isExit","false")),v->gate.put("isExit",v.toString()));
            toggle("换乘模式（A出B进）",transfer,v->gate.put("ticketTransfer",v.toString()));
            if(Boolean.parseBoolean(gate.getOrDefault("ticketTransfer","false")))button("换乘目标："+networkName(gate.getOrDefault("ticketTargetNetwork",DEFAULT_NETWORK)),()->chooseNetwork(v->gate.put("ticketTargetNetwork",v)));
            Set<String> kinds=new LinkedHashSet<>(Arrays.asList(gate.getOrDefault("ticketKinds","SINGLE,IC,ITINERARY,DAY").split(",")));
            multi("接受票种",Arrays.stream(Kind.values()).map(k->new ConfigChoiceScreen.Choice(k.name(),kind(k))).toList(),kinds,4,v->gate.put("ticketKinds",String.join(",",v)));
            return;
        }
        TicketMachineConfig c=state.config;
        button("设备网络："+networkName(c.network),()->chooseNetwork(v->{
            c.network=v;
            Set<String> valid=new LinkedHashSet<>();
            for(Product product:c.saleProducts(catalogue,state.kind))valid.add(product.id);
            c.products.retainAll(valid);
            requestRebuild();
        }));
        if(state.kind!=2)label("本机销售白名单：只列出已授权当前设备网络且适用于本机的产品；切换网络会移除不兼容选择。");
        if(state.kind!=2){
            List<Product> sellable=c.availableProducts(catalogue,state.kind);
            Set<String> valid=sellable.stream().map(p->p.id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            c.products.retainAll(valid);
            var choices=sellable.stream().map(p->new ConfigChoiceScreen.Choice(p.id,p.name)).toList();
            Set<String> selected=new LinkedHashSet<>(c.products);
            multi("本机销售产品",choices,selected,128,v->c.products=v);
            if(state.kind==1)label("默认只售IC卡。一日、三日、七日通可在销售产品选择中分别开启。");
        }
        toggle("支持MTR余额支付",c.mtrPayment,v->c.mtrPayment=v);toggle("支持物品货币",c.itemPayment,v->c.itemPayment=v);
        field("物品货币ID",c.currency,v->c.currency=v);integer("每个物品抵扣金额（元，不找零）",c.currencyValue,v->c.currencyValue=v);
        button("选择设备模型",()->Minecraft.getInstance().setScreen(new ModelSelectScreen(ComponentHelper.literal("选择票务设备模型"),null,TicketMachineModels.options(),be->TicketMachineModels.selected(c),(be,v)->{
            try{TicketMachineModels.apply(c,v);}catch(RuntimeException ex){error("模型配置加载失败："+ex.getMessage());}
        },this)));
        if (TicketMachineModels.screenTexture(c) != null) {
            label("屏幕使用模型提供的静态贴图");
        } else {
            field("屏幕文本",c.text==null?TicketMachineConfig.defaultText(state.kind).replace("\n","\\n"):c.text.replace("\n","\\n"),v->c.text=v.replace("\\n","\n"));
            button("恢复默认屏幕文字",()->{c.text=null;requestRebuild();});
            if(state.kind!=2) integer("语言切换间隔（秒）",c.screenCycleSeconds,v->c.screenCycleSeconds=v);
        }

    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
