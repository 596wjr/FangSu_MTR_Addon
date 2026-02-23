package com.fangsu.render.sowcer.model;

import com.fangsu.render.sowcer.object.InstanceBuf;
import com.fangsu.render.sowcer.object.VertArray;
import com.fangsu.render.sowcer.vertex.VertAttrMapping;
import net.minecraft.resources.ResourceLocation;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VertArrays implements Closeable {

    public final ArrayList<VertArray> meshList = new ArrayList<>();

    public static VertArrays createAll(Model model, VertAttrMapping mapping, InstanceBuf instanceBuf) {
        VertArrays result = new VertArrays();
        for (Mesh mesh : model.meshList) {
            VertArray meshVertArray = new VertArray();
            meshVertArray.create(mesh, mapping, instanceBuf);
            result.meshList.add(meshVertArray);
        }
        return result;
    }

    public void replaceTexture(String oldTexture, ResourceLocation newTexture) {
        for (VertArray vertArray : meshList) {
            if (vertArray.materialProp.texture == null) continue;
            String oldPath = vertArray.materialProp.texture.getPath();
            if (oldPath.substring(oldPath.lastIndexOf("/") + 1).equals(oldTexture)) {
                vertArray.materialProp.texture = newTexture;
            }
        }
    }

    public void replaceAllTexture(ResourceLocation newTexture) {
        for (VertArray vertArray : meshList) {
            vertArray.materialProp.texture = newTexture;
        }
    }

    public VertArrays copyForMaterialChanges() {
        VertArrays result = new VertArrays();
        for (VertArray vertArray : meshList) {
            VertArray newVertArray = vertArray.copyForMaterialChanges();
            result.meshList.add(newVertArray);
        }
        return result;
    }

    @Override
    public void close() {
        for (VertArray mesh : meshList) {
            mesh.close();
        }
    }
}
