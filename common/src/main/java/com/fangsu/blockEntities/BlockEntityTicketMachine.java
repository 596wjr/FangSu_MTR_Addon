package com.fangsu.blockEntities;

import com.fangsu.blocks.*;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.ticketSystem.v2.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** 三类交易设备共用持久化和模型渲染，旧售票机仍保留双格方块结构。 */
public class BlockEntityTicketMachine extends FunctionalObjBlockEntity {
    public volatile TicketMachineConfig ticketConfig;
    private DynamicModelHolder model;
    public BlockEntityTicketMachine(BlockPos pos, BlockState state) {
        super(type(state), pos, state); ticketConfig = defaults(kind());
    }
    private static net.minecraft.world.level.block.entity.BlockEntityType<?> type(BlockState state) {
        if (state.getBlock() == ModBlocks.BLOCK_CARD_MACHINE.get()) return ModBlocks.BLOCK_ENTITY_CARD_MACHINE.get();
        if (state.getBlock() == ModBlocks.BLOCK_FARE_ADJUSTMENT_MACHINE.get()) return ModBlocks.BLOCK_ENTITY_FARE_ADJUSTMENT_MACHINE.get();
        return ModBlocks.BLOCK_ENTITY_TICKET_MACHINE.get();
    }
    public int kind() { return ((BlockTicketMachine) getBlockState().getBlock()).ticketMachineKind; }
    @Override public String getMainModelKey() { return "ticketMachine"; }
    public static TicketMachineConfig defaults(int kind) {
        TicketMachineConfig config = new TicketMachineConfig();
        if (kind == 1) config.products = new LinkedHashSet<>(List.of("fangsu:ic"));
        if (kind == 2) config.products.clear();
        config.model = kind == 0 ? "fangsu:ticketing/ticket_machine_old.bbmodel" : "fangsu:ticketing/card_machine.bbmodel";
        config.shape = "[[0,0,3,16,32,13.5]]";
        config.screenX=.0125;config.screenY=1.40906;config.screenZ=-.25816;config.screenPitch=5.56135;
        config.screenWidth=.50875;config.screenHeight=.87977;config.screenPixelsX=256;config.screenPixelsY=448;
        if(kind==0){
            config.modelPreset="fangsu:ticketing/machine_old.json";config.shape="[[0,0,2.5,16,32,13.5]]";
            config.screenX=-.2141;config.screenY=1.309;config.screenZ=-.218;config.screenPitch=22.5;
            config.screenWidth=.4289;config.screenHeight=.3819;config.screenPixelsX=256;config.screenPixelsY=192;
        }else config.modelPreset="fangsu:ticketing/card_machine.json";
        return config;
    }
    @Override public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putString("TicketMachine", TicketDefinitions.JSON.toJson(ticketConfig));
    }
    @Override public void load(CompoundTag tag) {
        ticketConfig = tag.contains("TicketMachine") ? TicketDefinitions.JSON.fromJson(tag.getString("TicketMachine"), TicketMachineConfig.class) : defaults(kind());
        if (ticketConfig == null) ticketConfig = defaults(kind());
        super.load(tag);
    }
    public void configure(TicketMachineConfig config) {
        ticketConfig = config; setChanged();
        if (level != null) {
            BlockState state = getBlockState().setValue(BlockTicketMachine.CUSTOM_MODEL, !config.model.isEmpty());
            level.setBlock(worldPosition, state, 3);
            BlockPos upper = worldPosition.above();
            if (level.getBlockState(upper).getBlock() == state.getBlock())
                level.setBlock(upper, level.getBlockState(upper).setValue(BlockTicketMachine.CUSTOM_MODEL, !config.model.isEmpty()), 3);
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
    public String screenText() {
        if (ticketConfig.text != null) return ticketConfig.text;
        return TicketMachineConfig.defaultText(kind());
    }
    @Override public void whenLoading() {
        if (level == null || !level.isClientSide) return;
        whenDisposing();
        if (!ticketConfig.model.isEmpty()) {
            try { model = com.fangsu.utils.ResourceUtil.loadDmh(new ResourceLocation(ticketConfig.model), ticketConfig.flipV); }
            catch (Exception ex) { com.fangsu.Main.LOGGER.warn("票务设备模型加载失败: {}", ticketConfig.model, ex); }
        }
    }
    @Override public void whenRendering() {
        if (model != null) {
            com.fangsu.render.sowcer.math.Matrices matrices = new com.fangsu.render.sowcer.math.Matrices();
            matrices.translate(0, 0, ticketConfig.modelOffsetZ);
            scriptContext.drawModel(model, matrices);
        }
    }
    @Override public void setLevel(net.minecraft.world.level.Level level) { super.setLevel(level); if(level.isClientSide) triggerAsyncLoading(); }
    @Override public void whenDisposing() {
        if (model != null) { model.closeIfOwned(); model = null; }
        if(level!=null && level.isClientSide)
            com.fangsu.client.ClientHooks.REMOVE_TICKET_TEXTURE.accept("machine/"+level.dimension().location()+"/"+worldPosition.asLong());
    }
    @Override public List<ConfigEntry<?>> getConfigs() { return List.of(); }
    public VoxelShape ticketShape(boolean upper) {
        try {
            double[][] boxes = TicketDefinitions.JSON.fromJson(ticketConfig.shape, double[][].class);
            VoxelShape result = Shapes.empty();
            for (double[] b : boxes) result = Shapes.or(result, Shapes.box(b[0]/16-.0, b[1]/16-(upper?1:0), b[2]/16, b[3]/16, b[4]/16-(upper?1:0), b[5]/16));
            return result.move(0, 0, ticketConfig.modelOffsetZ);
        } catch (RuntimeException ex) { return Shapes.block(); }
    }
}
