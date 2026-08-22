package com.fangsu.client;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityScreendoorCentralControl;
import com.fangsu.blockEntities.FunctionalObjBlockEntity;
import com.fangsu.drawing.sign.SignFaceData;
import com.fangsu.drawing.sign.SignItem;
import com.fangsu.ui.*;
import com.fangsu.ui.ticketMachine.TicketMachineMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

//@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientHooksImpl {

    private ClientHooksImpl() {
    }

    //    static {
//        ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
//        ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
//    }
    public static void openObjBlockConfigScreen(FunctionalObjBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(new ObjBlockConfigScreen(blockEntity));
    }

    public static void openRotatingRailConfigScreen(BaseObjBlockEntity be) {
        Minecraft.getInstance().setScreen(
                new ConfigScreen(com.fangsu.mappings.ComponentHelper.translatable("ui.fangsu.block.extras"), ((com.fangsu.blockEntities.BlockEntityRotatingRail) be).getConfigs())
        );
    }

    public static void openRotatingRailModelSelectScreen(com.fangsu.blockEntities.BlockEntityRotatingRail rail) {
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
                com.fangsu.mappings.ComponentHelper.translatable("ui.fangsu.rotating_rail.rail"),
                rail,
                com.fangsu.customItem.NteRailManager.getInstance().getRails(),
                target -> (target instanceof com.fangsu.blockEntities.BlockEntityRotatingRail)
                        ? ((com.fangsu.blockEntities.BlockEntityRotatingRail) target).getExtraConfig("railId", "pujiang_line_track_only")
                        : "pujiang_line_track_only",
                (target, value) -> {
                    if (target instanceof com.fangsu.blockEntities.BlockEntityRotatingRail r) {
                        r.setExtraConfig("railId", value);
                        if (r == rail) {
                            r.reloadModel();
                        }
                    }
                },
                Minecraft.getInstance().screen,
                rail::reloadModel
        ));
    }

    public static void openSignConfigScreen(
            List<SignFaceData> faces, Consumer<List<SignFaceData>> setter
    ) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new SignConfigUI(faces, setter)));
    }

    public static void openTicketMachineScreen(Component title, BlockPos pos) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new TicketMachineMainScreen(title, pos)));
    }

    public static void openPlatformSelectScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new PlatformSelectionScreen(
                    component, defaultValue, setter, pos, maxSelect, Minecraft.getInstance().screen
            ));
        });
    }

    public static void openRouteSelectScreen(Component component, List<Long> defaultValue, Consumer<List<RouteSelectInfo>> setter, BlockPos pos, int maxSelect) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new RouteSelectionScreen(
                    component, defaultValue, setter, pos, maxSelect, Minecraft.getInstance().screen
            ));
        });
    }

    public static void openStationSelectScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new StationSelectionScreen(
                    component, defaultValue, setter, pos, maxSelect, Minecraft.getInstance().screen
            ));
        });
    }

    public static void openScreendoorCentralControlScreen(BlockEntityScreendoorCentralControl ctrl) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new ScreendoorCentralControlScreen(ctrl));
        });
    }
}
