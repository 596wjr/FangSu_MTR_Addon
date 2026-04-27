package com.fangsu.train;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import mtr.client.*;
import mtr.mappings.Utilities;
import mtr.mappings.UtilitiesClient;
import mtr.render.TrainRendererBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class FunctionalCustomTrains implements IResourcePackCreatorProperties, ICustomResources {

    public static void init(ResourceManager resourceManager) {
        Main.LOGGER.info("Initializing Custom Trains");
        readResource(resourceManager, mtr.MTR.MOD_ID + ":" + CUSTOM_RESOURCES_ID + ".json", jsonConfig -> {
            try {
                jsonConfig.get(CUSTOM_TRAINS_KEY).getAsJsonObject().entrySet().forEach(entry -> {
                    try {
                        final JsonObject jsonObject = entry.getValue().getAsJsonObject();
                        if (!(jsonObject.has("lcd") || jsonObject.has("connectors"))) return;
                        final String trainId = CUSTOM_TRAIN_ID_PREFIX + entry.getKey();

                        TrainProperties prevTrainProp = TrainClientRegistry.getTrainProperties(trainId);
                        if (prevTrainProp.baseTrainType.isEmpty()) return;

                        final boolean isJacobsBogie = getOrDefault(jsonObject, "is_jacobs_bogie", prevTrainProp.isJacobsBogie, JsonElement::getAsBoolean);
                        final float bogiePosition = getOrDefault(jsonObject, "bogie_position", prevTrainProp.bogiePosition, JsonElement::getAsFloat);

                        LcdInfo lcdInfo = null;
                        if (jsonObject.has("lcd")) {
                            JsonObject lcdJson = jsonObject.get("lcd").getAsJsonObject();
                            lcdInfo = LcdInfo.fromJson(lcdJson);
                        }

                        boolean dummyBaseTrain = jsonObject.has("base_type");
                        String baseTrainType = dummyBaseTrain ? jsonObject.get("base_type").getAsString() : prevTrainProp.baseTrainType;
                        boolean hasGangwayConnection = getOrDefault(jsonObject, "has_gangway_connection",
                                dummyBaseTrain || prevTrainProp.hasGangwayConnection, JsonElement::getAsBoolean);
                        TrainRendererBase newRenderer = new FunctionalTrainRenderer(dummyBaseTrain ? null : prevTrainProp.renderer, lcdInfo);

                        Main.LOGGER.info("registering renderer: {}", newRenderer);
                        mtr.client.TrainClientRegistry.register(trainId, new TrainProperties(
                                baseTrainType, prevTrainProp.name,
                                prevTrainProp.description, prevTrainProp.wikipediaArticle, prevTrainProp.color,
                                prevTrainProp.riderOffset, prevTrainProp.riderOffsetDismounting,
                                bogiePosition, isJacobsBogie, hasGangwayConnection,
                                newRenderer, prevTrainProp.sound
                        ));
                    } catch (Exception ex) {
                        Main.LOGGER.error("Failed reading (FangSu) functional custom train", ex);
                    }
                });
            } catch (Exception ignored) {
            }
        });

    }

    private static void readResource(ResourceManager manager, String path, Consumer<JsonObject> callback) {
        try {
            UtilitiesClient.getResources(manager, new ResourceLocation(path)).forEach(resource -> {
                try (final InputStream stream = Utilities.getInputStream(resource)) {
                    callback.accept(Main.JSON_PARSER.parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (Exception e) {
                    Main.LOGGER.error("On behalf of MTR: Parsing JSON {}", path, e);
                }
                try {
                    Utilities.closeResource(resource);
                } catch (IOException e) {
                    Main.LOGGER.error("On behalf of MTR: Closing resource {}", path, e);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static <T> T getOrDefault(JsonObject jsonObject, String key, T defaultValue, Function<JsonElement, T> function) {
        if (jsonObject.has(key)) {
            return function.apply(jsonObject.get(key));
        } else {
            return defaultValue;
        }
    }
}