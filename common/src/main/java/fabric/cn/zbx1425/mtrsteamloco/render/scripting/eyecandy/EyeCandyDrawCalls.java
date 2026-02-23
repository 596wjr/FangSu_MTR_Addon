package fabric.cn.zbx1425.mtrsteamloco.render.scripting.eyecandy;

import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import fabric.cn.zbx1425.sowcer.math.Matrix4f;
import fabric.cn.zbx1425.sowcerext.model.ModelCluster;
import fabric.cn.zbx1425.sowcerext.reuse.DrawScheduler;
import net.minecraft.sounds.SoundEvent;

public class EyeCandyDrawCalls {
    public void addModel(ModelCluster model, Matrix4f pose) {
    }

    public void addModel(DynamicModelHolder model, Matrix4f pose) {
    }

    public void addSound(SoundEvent soundEvent, float volume, float pitch) {
    }

    public void reset() {
    }

    public void commit(DrawScheduler scheduler, Matrix4f pose, int light) {
    }

    public void commit(DrawScheduler scheduler, Matrix4f pose, Matrix4f worldPose, int light) {
    }
}
