package com.fangsu.announcements.mtradapter;

/** 只接受已验证的本次停站和正向路径剩余距离，不根据世界直线距离猜邻线列车。 */
public record ApproachingTrain(long vehicleId, long routeId, long platformId, int stopIndex,
                               double progress, double remainingDistance, double secondsUntilArrival,long departureIndex,String destination) { }
