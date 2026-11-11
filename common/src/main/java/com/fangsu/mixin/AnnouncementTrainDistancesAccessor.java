package com.fangsu.mixin;

import mtr.data.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

/** MTR3 的站序包装原先把 distance 写成 0，使用实际累计路径里程。 */
@Mixin(value = Train.class, remap = false)
public interface AnnouncementTrainDistancesAccessor {
    @Accessor("distances") List<Double> fangsu$announcementDistances();
}
