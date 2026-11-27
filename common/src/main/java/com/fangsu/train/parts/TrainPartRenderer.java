package com.fangsu.train.parts;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.animation.AnimatedModel;
import com.fangsu.render.sowcerext.model.animation.AnimationClip;
import com.fangsu.train.bogie.BogieAnimationState;
import com.fangsu.train.data.TrainDataClient;
import com.fangsu.train.data.TrainDataSnapshot;
import com.fangsu.train.pantograph.PantographAnimationState;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TrainPartRenderer {
    private static final Map<Long, Runtime> STATES = new HashMap<>();
    private static final Set<AnimatedModel> FAILED = new HashSet<>();
    private static ClientLevel world;
    private static int tick;

    private static final class Runtime {
        final BogieAnimationState wheels = new BogieAnimationState();
        final Map<Bow, PantographAnimationState> bows = new HashMap<>();
        int seen;
    }

    private record Bow(int car, String id) {}

    private TrainPartRenderer() {}

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (world != client.level) {
                world = client.level;
                STATES.clear();
            }
            tick++;
            if (tick % 40 == 0) STATES.values().removeIf(state -> tick - state.seen > 180);
        });
    }

    public static void reset() {
        STATES.clear();
        FAILED.clear();
    }

    public static boolean bogie(long train, TrainPartsRegistry.Bogie part, Matrix4f pose,
                                double progress, boolean reversed, int light) {
        Runtime state = state(train);
        double distance = state.wheels.update(progress, reversed);
        double time = BogieAnimationState.time(distance, part.blocksPerCycle(), part.mount().asset().clip().length());
        return draw(part.mount(), pose, time, AnimationClip.Wrap.LOOP, light);
    }

    public static void pantographs(long train, int car, int carCount, TrainPartsRegistry.Parts parts, Matrix4f pose, int light) {
        if (parts == null || parts.pantographs().isEmpty()) return;
        Runtime state = state(train);
        TrainDataSnapshot data = TrainDataClient.get(train);
        long now = System.nanoTime();
        for (var variants : parts.pantographs().values()) {
            for (TrainPartsRegistry.Pantograph bow : variants) {
                if (!bow.mount().matches(car, carCount)) continue;
                PantographAnimationState animation = state.bows.computeIfAbsent(new Bow(car, bow.id()), ignored -> new PantographAnimationState());
                // 同一模型按车型槽位的升弓行程停止，降弓仍从当前位置反向播放。
                double raisedTime = bow.mount().asset().clip().length() * bow.raiseProgress();
                double time = animation.sample(data.pantograph(car, bow.id(), bow.group()),
                        raisedTime, now, TrainDataClient.initial(train));
                draw(bow.mount(), pose, time, AnimationClip.Wrap.CLAMP, light);
                break;
            }
        }
    }

    private static Runtime state(long id) {
        Runtime state = STATES.computeIfAbsent(id, ignored -> new Runtime());
        state.seen = tick;
        return state;
    }

    private static boolean draw(TrainPartsRegistry.Mount mount, Matrix4f base, double time, AnimationClip.Wrap wrap, int light) {
        AnimatedModel model = mount.asset().model();
        if (FAILED.contains(model) || !MainClient.is_nte_loaded && !MainClient.drawContext.drawWithBlaze
                && !MainClient.drawScheduler.shaderManager.isReady()) return false;
        try {
            Matrix4f anchor = base.copy();
            anchor.translate(mount.position().x(), mount.position().y(), mount.position().z());
            AnimatedModel.rotate(anchor, mount.rotation());
            Matrix4f[] nodes = model.samplePose(mount.asset().clip(), time, wrap);
            ModelCluster[] geometry = new ModelCluster[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                var mesh = model.nodes().get(i).geometry();
                if (mesh != null) geometry[i] = MainClient.modelManager.uploadVertArrays(mesh);
            }
            for (int i = 0; i < nodes.length; i++) {
                if (geometry[i] == null) continue;
                Matrix4f pose = anchor.copy();
                pose.multiply(nodes[i]);
                MainClient.drawScheduler.enqueue(geometry[i], pose, light);
            }
            return true;
        } catch (RuntimeException exception) {
            if (FAILED.add(model)) Main.LOGGER.warn("Failed to draw animated train part {}",
                    model.nodes().stream().filter(node -> node.geometry() != null).findFirst()
                            .map(node -> node.geometry().sourceLocation).orElse(null), exception);
            return false;
        }
    }
}
