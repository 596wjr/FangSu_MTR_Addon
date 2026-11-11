package com.fangsu.announcements.mtradapter;

import com.fangsu.announcements.recordings.RecordingFamily.Context;

/** 每次 tick 的乘车快照，原生核心不依赖 MTR3/4 类。 */
public record RidingAnnouncementState(long vehicleId, String vehicleType, long routeId, long platformId, int stopIndex,
                                      double progress, double remaining, boolean doorsOpen, boolean onRoute, Context context) { }
