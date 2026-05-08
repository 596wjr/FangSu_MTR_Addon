package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackDependencyScreen extends Screen {
    private static final ResourceLocation PACK_META_DATA_LOCATION = new ResourceLocation("fangsu:fangsu_pack.json");

    private final List<dependencyInfo> dependencies;
    private final Map<String, Long> loadedPackVersions;

    protected PackDependencyScreen(Component component) {
        super(component);
        this.dependencies = new ArrayList<>();
        this.loadedPackVersions = new HashMap<>();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        List<Resource> resources;
        resources = manager.getResourceStack(PACK_META_DATA_LOCATION);

        List<JsonElement> jsons = new ArrayList<>();
        for (Resource resource : resources) {
            try (InputStream stream = resource.open()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                jsons.add(Main.GSON.fromJson(reader, JsonElement.class));
            } catch (IOException e) {
                Main.LOGGER.error("Failed to read FangSu pack meta-data json:", e);
            }
        }

    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        super.init();


    }

    private record dependencyInfo() {
    }
}
