package com.fangsu.fabric.client;

import com.fangsu.Main;
import net.fabricmc.api.ClientModInitializer;

public final class MainFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        Main.initClient();
    }
    protected void fabricClientInit(){
    }
}
