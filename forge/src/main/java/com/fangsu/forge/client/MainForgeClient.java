package com.fangsu.forge.client;

import com.fangsu.client.ClientHooks;
import com.fangsu.client.ClientHooksImpl;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MainForgeClient {
    static {
        ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
        ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
        ClientHooks.OPEN_TICKET_MACHINE_SCREEN = ClientHooksImpl::openTicketMachineScreen;
    }
}
