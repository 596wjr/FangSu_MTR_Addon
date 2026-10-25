package com.fangsu.items;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.TurnoutPlacementPayload;
import com.fangsu.turnout.TurnoutBinding;
import com.fangsu.turnout.TurnoutTopology;
import com.fangsu.turnout.TurnoutValidation;
import com.fangsu.turnout.marker.NodeMarkerStore;
import com.fangsu.utils.RegisterUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** 事务式放置：客户端仅发意图，服务端校验通过后调用原版 BlockItem.place 一次完成放置和绑定。 */
public class ItemTurnout extends BlockItem {
    private static final ThreadLocal<TurnoutBinding> AUTHORIZED=new ThreadLocal<>();
    public ItemTurnout(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getPlayer()==null)return InteractionResult.FAIL;
        if(!context.getLevel().isClientSide)return InteractionResult.CONSUME;
        BlockPlaceContext placement=new BlockPlaceContext(context);
        if(!placement.canPlace())return InteractionResult.FAIL;
        var markers=NodeMarkerStore.getInstance().snapshot();
        TurnoutValidation check=TurnoutTopology.validateClient(context.getLevel(),placement.getClickedPos(),markers,null);
        if(!check.isSupported()){context.getPlayer().displayClientMessage(ComponentHelper.translatable(check.reasonKey()),true);return InteractionResult.FAIL;}
        TurnoutPlacementPayload.send(context,placement.getClickedPos(),markers,check.binding());
        return InteractionResult.SUCCESS;
    }
    /** 外部直接调用 place 也必须经过服务端授权，不能绕过 useOn 门禁。 */
    @Override public InteractionResult place(BlockPlaceContext context){return AUTHORIZED.get()==null?InteractionResult.FAIL:super.place(context);}
    public InteractionResult placeValidated(BlockPlaceContext context,TurnoutBinding binding) {
        if(context.getLevel().isClientSide||binding==null)return InteractionResult.FAIL;
        AUTHORIZED.set(binding);
        try{return super.place(context);}finally{AUTHORIZED.remove();}
    }
    @Nullable public static TurnoutBinding placementBinding(){return AUTHORIZED.get();}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,level,tooltip,flag);RegisterUtil.addDescTooltip(tooltip,"block.fangsu.turnout.desc");}
}
