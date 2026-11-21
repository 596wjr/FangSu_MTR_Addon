package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ticketSystem.v2.*;
import com.fangsu.ui.*;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
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
    private void field(String label,String initial,Consumer<String> setter){
        label(label);EditBox edit=new EditBox(font,layout.areaLeft,y,layout.contentWidth,20,ComponentHelper.literal(label));edit.setMaxLength(512);edit.setValue(initial==null?"":initial);
        edit.setResponder(setter);addRenderableWidget(edit);addEntry(edit,y);y+=26;
    }
    private void integer(String label,int value,IntConsumer setter){field(label,Integer.toString(value),v->{try{setter.accept(Integer.parseInt(v));}catch(NumberFormatException ignored){}});}
    private void decimal(String label,double value,DoubleConsumer setter){field(label,Double.toString(value),v->{try{setter.accept(Double.parseDouble(v));}catch(NumberFormatException ignored){}});}
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
            String target=pages[i];var tab=addFixedWidget(themedButton(getPanelLeft()+i*cell,getPanelTop()+3,cell-3,20,ComponentHelper.literal(names[i]),b->{page=target;scrollOffset=0;requestRebuild();}));tab.active=!pending;selected(tab,page.startsWith(target.substring(0,target.length()-1)));
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
        switch(page){case "networks"->networks();case "network"->network();case "products"->products();case "product"->product();case "unions"->unions();case "union"->union();default->device();}
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
        if(n.rule.mode==Pricing.DISTANCE){
            label("各段分别向上取整后累加；最后一档自动无上限。距离为车站中心的水平直线距离。 ");
            for(int i=0;i<n.rule.tiers.size();i++){
                int index=i;Tier t=n.rule.tiers.get(i);label("阶梯 "+(i+1));
                if(i<n.rule.tiers.size()-1)decimal("累计距离上限（格）",t.end,v->t.end=v);else label("无距离上限");
                integer("每多少格计一次费",t.unit,v->t.unit=v);integer("每单位收费（元）",t.price,v->t.price=v);
                button("移除此阶梯",()->{n.rule.tiers.remove(index);if(!n.rule.tiers.isEmpty())n.rule.tiers.get(n.rule.tiers.size()-1).end=-1;requestRebuild();});
            }
            button("追加阶梯",()->{if(!n.rule.tiers.isEmpty())n.rule.tiers.get(n.rule.tiers.size()-1).end=n.rule.tiers.size()*3000; n.rule.tiers.add(new Tier());requestRebuild();});
        }
        button("查看已绑定车站（"+catalogue.stations(n.id).size()+"，闸机自动维护）",()->choose("网络车站",catalogue.stations(n.id).stream().map(s->new ConfigChoiceScreen.Choice(s.key,s.name)).toList(),v->{}));
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
        toggle("启用新销售",u.enabled,v->u.enabled=v);label("成员网络（授权出票时保存成员快照）");
        multi("成员网络",networkChoices(),u.networks,128,v->u.networks=v);
    }
    private void product(){
        Product p=catalogue.products.get(productId);if(p==null)return;
        field("产品名称",p.name,v->p.name=v);label("产品ID："+p.id);toggle("启用销售",p.enabled,v->p.enabled=v);
        button("票种："+kind(p.kind),()->choose("选择票种",Arrays.stream(Kind.values()).map(k->new ConfigChoiceScreen.Choice(k.name(),kind(k))).toList(),v->{p.kind=Kind.valueOf(v);p.style=p.kind==Kind.IC?"fangsu:ticketing/beijing_ic.json":p.kind==Kind.DAY?"fangsu:ticketing/day_pass.json":"fangsu:ticketing/single.json";}));
        field("票面样式资源",p.style,v->p.style=v);
        if(p.kind==Kind.SINGLE)label("单程票售价由目的站和网络计费规则计算。");else integer(p.kind==Kind.IC?"卡费（元）":"售价（元）",p.price,v->p.price=v);
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
    }
    public static String kind(Kind kind){return switch(kind){case SINGLE->"单程票";case IC->"IC卡";case ITINERARY->"联程票";case DAY->"日票";};}
    private void device(){
        if(state.gate!=null){
            Map<String,String> gate=state.gate;String network=gate.getOrDefault("ticketNetwork",DEFAULT_NETWORK);
            button("接入网络："+networkName(network),()->chooseNetwork(v->gate.put("ticketNetwork",v)));
            toggle("出站闸机",Boolean.parseBoolean(gate.getOrDefault("isExit","false")),v->gate.put("isExit",v.toString()));
            toggle("换乘模式（A出B进）",Boolean.parseBoolean(gate.getOrDefault("ticketTransfer","false")),v->gate.put("ticketTransfer",v.toString()));
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
            List<Product> sellable=c.saleProducts(catalogue,state.kind);
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
        field("屏幕文本",c.text==null?TicketMachineConfig.defaultText(state.kind).replace("\n","\\n"):c.text.replace("\n","\\n"),v->c.text=v.replace("\\n","\n"));
        button("恢复默认屏幕文字",()->{c.text=null;requestRebuild();});
        integer("语言切换间隔（秒）",c.screenCycleSeconds,v->c.screenCycleSeconds=v);

    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
