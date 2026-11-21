package com.fangsu.ticketSystem.v2.client;

import com.fangsu.Main;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 发现所有资源包命名空间下的票务预设，导入只编辑管理员草稿。 */
public final class TicketPresets {
    public static final class Definition {
        public String name="票务预设";
        public Map<String,Network> networks=new LinkedHashMap<>();
        public Map<String,Product> products=new LinkedHashMap<>();
        public Map<String,NetworkUnion> unions=new LinkedHashMap<>();
    }
    public static Map<String,Definition> load(){
        Map<String,Definition> result=new TreeMap<>();
        for(ResourceLocation resource:ResourceUtil.listResources(null,"ticketing/presets",List.of(".json")))try{
            Definition preset=JSON.fromJson(ResourceUtil.loadAsJSON(resource),Definition.class);
            require(preset!=null&&preset.name!=null&&preset.networks!=null&&preset.products!=null&&preset.unions!=null,"预设结构无效");
            require(preset.networks.size()<=128&&preset.products.size()<=512&&preset.unions.size()<=128,"预设条目过多");
            preset.networks.forEach((id,n)->n.id=id);preset.products.forEach((id,p)->p.id=id);preset.unions.forEach((id,u)->u.id=id);
            result.put(resource.toString(),preset);
        }catch(RuntimeException ex){Main.LOGGER.warn("票务资源包预设加载失败: {}",resource,ex);}
        return result;
    }
    public static List<ConfigChoiceScreen.Choice> productChoices(){
        List<ConfigChoiceScreen.Choice> choices=new ArrayList<>();choices.add(new ConfigChoiceScreen.Choice("","空白产品"));
        load().forEach((resource,preset)->preset.products.forEach((id,p)->choices.add(new ConfigChoiceScreen.Choice(resource+"#"+id,p.name+" · "+preset.name))));return choices;
    }
    public static List<ConfigChoiceScreen.Choice> catalogueChoices(){return load().entrySet().stream().map(e->new ConfigChoiceScreen.Choice(e.getKey(),e.getValue().name)).toList();}
    private static void dependencies(Catalogue target,Definition preset){
        preset.networks.forEach((id,n)->target.networks.putIfAbsent(id,copy(n,Network.class)));
        preset.unions.forEach((id,u)->target.unions.putIfAbsent(id,copy(u,NetworkUnion.class)));
    }
    public static Product newProduct(Catalogue target,String choice){
        Product product=new Product();
        if(!choice.isEmpty()){
            int split=choice.indexOf('#');require(split>0,"产品预设无效");
            Definition preset=load().get(choice.substring(0,split));require(preset!=null,"预设已被移除，请重新选择");
            Product template=preset.products.get(choice.substring(split+1));require(template!=null,"产品预设不存在");
            dependencies(target,preset);product=copy(template,Product.class);
        }
        product.id="fangsu:product_"+UUID.randomUUID().toString().replace("-","");target.products.put(product.id,product);return product;
    }
    public static void importCatalogue(Catalogue target,String resource){
        Definition preset=load().get(resource);require(preset!=null,"预设已被移除，请重新选择");dependencies(target,preset);
        preset.products.forEach((id,p)->target.products.putIfAbsent(id,copy(p,Product.class)));
    }
    private TicketPresets(){ }
}
