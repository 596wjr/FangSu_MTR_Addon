package com.fangsu.render.sowcerext.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.Locale;

public class ResourceUtil {

    public static String readResource(ResourceManager manager, ResourceLocation location) throws IOException {
        final List<Resource> resources = getResources(manager, location);
        if (resources.isEmpty()) return "";
        return IOUtils.toString(new BOMInputStream(getInputStream(resources.get(0))), StandardCharsets.UTF_8);
    }

    public static ResourceLocation resolveRelativePath(ResourceLocation baseFile, String relative, String expectExtension) {
        relative = relative.toLowerCase(Locale.ROOT).replace('\\', '/');

        if (relative.contains(":")) {
            relative = relative.replaceAll("[^a-z0-9/.:_-]", "_");
            return new ResourceLocation(relative);
        }

        relative = relative.replaceAll("[^a-z0-9/._-]", "_");

        if (relative.endsWith(".jpg") || relative.endsWith(".bmp") || relative.endsWith(".tga")) {
            relative = relative.substring(0, relative.length() - 4) + ".png";
        }

        if (expectExtension != null && !relative.endsWith(expectExtension)) {
            relative += expectExtension;
        }
        String resolvedPath = FileSystems.getDefault().getPath(baseFile.getPath()).getParent().resolve(relative)
                .normalize().toString().replace('\\', '/');
        return new ResourceLocation(baseFile.getNamespace(), resolvedPath);
    }

    private static List<Resource> getResources(ResourceManager resourceManager, ResourceLocation resourceLocation) throws IOException {
        //#if MC_VERSION >= 11900
        return resourceManager.getResourceStack(resourceLocation);
        //#else
        //$$ return java.util.Collections.singletonList(resourceManager.getResource(resourceLocation));
        //#endif
    }

    private static InputStream getInputStream(Resource resource) throws IOException {
        //#if MC_VERSION >= 11900
        return resource.open();
        //#else
        //$$ return resource.getInputStream();
        //#endif
    }
}
