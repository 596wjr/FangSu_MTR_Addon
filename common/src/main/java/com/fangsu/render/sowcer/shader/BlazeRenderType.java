package com.fangsu.render.sowcer.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BlazeRenderType {

    private static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT = Util.memoize(resourceLocation ->
            RenderType.create(
                    "fangsu_entity_cutout_triangles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, true, false,
                    ((RenderType.CompositeRenderType) RenderType.entityCutout(resourceLocation)).state
            ));
    private static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(resourceLocation ->
            RenderType.create(
                    "fangsu_entity_translucent_cull_triangles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, true, true,
                    ((RenderType.CompositeRenderType) RenderType.entityTranslucentCull(resourceLocation)).state
            ));
    private static final BiFunction<ResourceLocation, Boolean, RenderType> BEACON_BEAM = Util.memoize((resourceLocation, translucent) ->
            RenderType.create(
                    "fangsu_beacon_beam_triangles", DefaultVertexFormat.BLOCK, VertexFormat.Mode.TRIANGLES,
                    256, false, true,
                    ((RenderType.CompositeRenderType) RenderType.beaconBeam(resourceLocation, translucent)).state
            ));

//    // 缓存 Field 对象，避免每次都反射
//    private static final Field COMPOSITE_RENDER_TYPE_STATE_FIELD;
//
//    static {
//        try {
//            COMPOSITE_RENDER_TYPE_STATE_FIELD = RenderType.CompositeRenderType.class.getDeclaredField("state");
//            COMPOSITE_RENDER_TYPE_STATE_FIELD.setAccessible(true);
//        } catch (NoSuchFieldException e) {
//            throw new RuntimeException("Failed to access CompositeRenderType.state field", e);
//        }
//    }
//
//    private static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT = Util.memoize(resourceLocation -> {
//        RenderType original = RenderType.entityCutout(resourceLocation);
//        return createCustomRenderType("fangsu_entity_cutout_triangles", DefaultVertexFormat.NEW_ENTITY,
//                VertexFormat.Mode.TRIANGLES, 256, true, false, original);
//    });
//
//    private static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(resourceLocation -> {
//        RenderType original = RenderType.entityTranslucentCull(resourceLocation);
//        return createCustomRenderType("fangsu_entity_translucent_cull_triangles", DefaultVertexFormat.NEW_ENTITY,
//                VertexFormat.Mode.TRIANGLES, 256, true, true, original);
//    });
//
//    private static final BiFunction<ResourceLocation, Boolean, RenderType> BEACON_BEAM = Util.memoize((resourceLocation, translucent) -> {
//        RenderType original = RenderType.beaconBeam(resourceLocation, translucent);
//        return createCustomRenderType("fangsu_beacon_beam_triangles", DefaultVertexFormat.BLOCK,
//                VertexFormat.Mode.TRIANGLES, 256, false, true, original);
//    });
//
//    private static RenderType createCustomRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
//                                                     int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
//                                                     RenderType original) {
//        try {
//            RenderType.CompositeState state = (RenderType.CompositeState) COMPOSITE_RENDER_TYPE_STATE_FIELD.get(original);
//            return RenderType.create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state);
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//            return original;
//        }
//    }

    public static RenderType entityCutout(ResourceLocation resourceLocation) {
        return ENTITY_CUTOUT.apply(resourceLocation);
    }

    public static RenderType entityTranslucentCull(ResourceLocation resourceLocation) {
        return ENTITY_TRANSLUCENT_CULL.apply(resourceLocation);
    }

    public static RenderType beaconBeam(ResourceLocation resourceLocation, boolean bl) {
        return BEACON_BEAM.apply(resourceLocation, bl);
    }
}
