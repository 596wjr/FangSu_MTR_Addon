package com.fangsu.items;

import com.fangsu.client.ClientHooks;
import com.fangsu.mtr.rail.ServiceRail;
import mtr.block.BlockNode;
import mtr.data.RailType;
import mtr.data.TransportMode;
import mtr.item.ItemRailModifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ItemServiceRail extends ItemRailModifier {
    public ItemServiceRail(ServiceRail.Kind kind) {
        super(true, false, false, false, RailType.IRON);
        this.kind = kind;
    }

    private final ServiceRail.Kind kind;

    public ServiceRail.Kind kind() {
        return kind;
    }

    public ServiceRail settings(ItemStack stack) {
        final var tag = stack.getTag();
        final ServiceRail settings = tag == null ? null : ServiceRail.decode(tag.getString(ServiceRail.KEY));
        return settings == null || settings.kind() != kind ? kind.defaults() : settings;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof BlockNode node) {
            return node.transportMode == TransportMode.TRAIN ? super.useOn(context) : InteractionResult.FAIL;
        }
        if (context.getLevel().isClientSide) {
            ClientHooks.OPEN_SERVICE_RAIL_TOOL.accept(context.getHand(), context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientHooks.OPEN_SERVICE_RAIL_TOOL.accept(hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    //#if MC_VERSION < 11903
    //$$ @Override
    //$$ public void fillItemCategory(net.minecraft.world.item.CreativeModeTab tab, net.minecraft.core.NonNullList<ItemStack> items) {
    //$$     if (tab == com.fangsu.creativeTabs.ModCreativeTabs.FANGSU_MAIN || tab == net.minecraft.world.item.CreativeModeTab.TAB_SEARCH) {
    //$$         items.add(new ItemStack(this));
    //$$     }
    //$$ }
    //#endif
}
