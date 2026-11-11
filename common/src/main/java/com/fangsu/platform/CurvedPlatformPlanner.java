package com.fangsu.platform;

import java.util.*;

/** 纯几何预览，不读写世界。门距沿偏移后的屏蔽门基线累计，跨轨道节点不重置。 */
public final class CurvedPlatformPlanner {
    public record Point(double x, double y, double z) {
        public Point { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("non_finite_point"); }
        Point add(double dx, double dy, double dz) { return new Point(x + dx, y + dy, z + dz); }
        double distance(Point p) { return Math.hypot(x - p.x, z - p.z); }
        Point interpolate(Point p, double t) { return new Point(x + (p.x-x)*t, y + (p.y-y)*t, z + (p.z-z)*t); }
    }
    public record Anchor(int x, int y, int z, double translateX, double translateY, double translateZ, double yaw) { }
    public record Door(int ordinal, double mileage, Point start, Point end, Point center, Anchor transform) { }
    public record FixedSegment(Point start, Point end, double width) { }
    public record Issue(String code, int ordinal) { }
    public record Plan(List<Point> baseline, List<Door> doors, List<FixedSegment> fixed, List<Issue> issues) {
        public boolean executable() { return issues.isEmpty(); }
    }
    /** 固定段只能在模型明确支持的宽度范围内适配；门扇始终保持原宽。 */
    public record Model(double doorWidth, double fixedMinWidth, double fixedMaxWidth, double maxJointAngleRadians) {
        public Model {
            if (!Double.isFinite(doorWidth) || doorWidth < .1 || doorWidth > 16 || !Double.isFinite(fixedMinWidth) || fixedMinWidth <= 0
                    || !Double.isFinite(fixedMaxWidth) || fixedMaxWidth < fixedMinWidth || fixedMaxWidth > 32
                    || !Double.isFinite(maxJointAngleRadians) || maxJointAngleRadians <= 0 || maxJointAngleRadians > Math.PI / 2) throw new IllegalArgumentException("invalid_model_geometry");
        }
    }
    public record Settings(double signedOffset, double floorHeight, double start, double end, double centerSpacing,
                           double firstDoorOffset, double carLength, int cars, List<Double> doorCentersInCar) {
        public Settings {
            if (!Double.isFinite(signedOffset) || Math.abs(signedOffset) > 32 || !Double.isFinite(floorHeight) || !Double.isFinite(start)
                    || !Double.isFinite(end) || start < 0 || end <= start || end - start > 4096 || !Double.isFinite(centerSpacing)
                    || centerSpacing < .1 || !Double.isFinite(firstDoorOffset) || firstDoorOffset < 0 || !Double.isFinite(carLength)
                    || carLength < 0 || cars < 0 || cars > 64 || doorCentersInCar == null || doorCentersInCar.size() > 32) throw new IllegalArgumentException("invalid_layout_settings");
            doorCentersInCar = List.copyOf(doorCentersInCar);
            double previous = -1;
            for (double center : doorCentersInCar) {
                if (!Double.isFinite(center) || center <= previous || center < 0 || center >= carLength) throw new IllegalArgumentException("invalid_car_door_centers");
                previous = center;
            }
        }
    }
    private record Path(List<Point> points, double[] mileages, double length) {
        Point at(double s) {
            int index = Arrays.binarySearch(mileages, s);
            if (index >= 0) return points.get(index);
            index = Math.max(1, Math.min(points.size() - 1, -index - 1));
            double t = (s - mileages[index - 1]) / (mileages[index] - mileages[index - 1]);
            return points.get(index - 1).interpolate(points.get(index), Math.max(0, Math.min(1, t)));
        }
    }
    public static Plan preview(List<Point> railSamples, Settings settings, Model model) {
        if (railSamples.size() < 2 || railSamples.size() > 65536) throw new IllegalArgumentException("invalid_path_size");
        List<Issue> issues = new ArrayList<>(); List<Point> baseline = new ArrayList<>();
        double referenceY = railSamples.get(0).y;
        for (int i = 0; i < railSamples.size(); i++) {
            Point p = railSamples.get(i), before = railSamples.get(Math.max(0, i - 1)), after = railSamples.get(Math.min(railSamples.size() - 1, i + 1));
            if (Math.abs(p.y - referenceY) > .05 && issues.stream().noneMatch(issue -> issue.code.equals("sloped_track"))) issues.add(new Issue("sloped_track", i));
            double dx = after.x - before.x, dz = after.z - before.z, length = Math.hypot(dx, dz);
            if (length < 1e-6) { issues.add(new Issue("degenerate_tangent", i)); continue; }
            baseline.add(new Point(p.x + dz / length * settings.signedOffset, settings.floorHeight, p.z - dx / length * settings.signedOffset));
        }
        if (baseline.size() < 2) return new Plan(List.copyOf(baseline), List.of(), List.of(), List.copyOf(issues));
        double[] mileage = new double[baseline.size()];
        for (int i = 1; i < baseline.size(); i++) {
            double distance = baseline.get(i).distance(baseline.get(i-1));
            if (distance < 1e-6) { issues.add(new Issue("collapsed_offset_path", i)); distance = 1e-6; }
            mileage[i] = mileage[i-1] + distance;
        }
        Path path = new Path(baseline, mileage, mileage[mileage.length-1]);
        if (settings.end > path.length) issues.add(new Issue("end_outside_path", -1));
        List<Double> centers = new ArrayList<>();
        if (settings.doorCentersInCar.isEmpty()) {
            for (double s = settings.start + settings.firstDoorOffset; s <= Math.min(settings.end, path.length) && centers.size() < 4096; s += settings.centerSpacing) centers.add(s);
        } else {
            for (int car = 0; car < settings.cars; car++) for (double door : settings.doorCentersInCar) {
                double s = settings.start + settings.firstDoorOffset + car * settings.carLength + door;
                if (s <= Math.min(settings.end, path.length)) centers.add(s);
            }
        }
        List<Door> doors = new ArrayList<>(); List<FixedSegment> fixed = new ArrayList<>(); Set<String> anchors = new HashSet<>();
        Point previousEnd = path.at(settings.start); double previousEndMileage = settings.start;
        for (int i = 0; i < centers.size(); i++) {
            double center = centers.get(i), low = model.doorWidth / 2, high = Math.min(center - settings.start, Math.min(settings.end, path.length) - center);
            if (high < low || path.at(center-high).distance(path.at(center+high)) + 1e-6 < model.doorWidth) { issues.add(new Issue("door_outside_layout_or_sharp_curve", i)); continue; }
            // 解实际弦长=刚性门宽，不能按弧长把门扇拉伸。
            for (int iteration = 0; iteration < 40; iteration++) {
                double mid = (low + high) / 2;
                if (path.at(center-mid).distance(path.at(center+mid)) < model.doorWidth) low = mid; else high = mid;
            }
            double half = (low + high) / 2;
            Point a = path.at(center-half), b = path.at(center+half), midpoint = a.interpolate(b, .5);
            if (center-half < previousEndMileage - 1e-6) issues.add(new Issue("overlapping_doors", i));
            double yaw = Math.atan2(-(b.z-a.z), b.x-a.x);
            Point beforeA = path.at(Math.max(settings.start, center-half-.01));
            Point afterB = path.at(Math.min(settings.end, center+half+.01));
            double headingA = Math.atan2(-(a.z-beforeA.z), a.x-beforeA.x), headingB = Math.atan2(-(afterB.z-b.z), afterB.x-b.x);
            if (Math.abs(Math.atan2(Math.sin(yaw-headingA), Math.cos(yaw-headingA))) > model.maxJointAngleRadians
                    || Math.abs(Math.atan2(Math.sin(yaw-headingB), Math.cos(yaw-headingB))) > model.maxJointAngleRadians) issues.add(new Issue("joint_angle_exceeded", i));
            int x = (int) Math.floor(midpoint.x), y = (int) Math.floor(midpoint.y), z = (int) Math.floor(midpoint.z);
            if (!anchors.add(x + "/" + y + "/" + z)) issues.add(new Issue("door_anchor_conflict", i));
            Anchor anchor = new Anchor(x,y,z,midpoint.x-x-.5,midpoint.y-y,midpoint.z-z-.5,yaw);
            fillFixed(previousEnd, a, model, fixed, issues, i);
            doors.add(new Door(i, center, a,b,midpoint,anchor)); previousEnd = b; previousEndMileage = center+half;
        }
        fillFixed(previousEnd, path.at(Math.min(settings.end, path.length)), model, fixed, issues, centers.size());
        if (doors.isEmpty()) issues.add(new Issue("no_doors", -1));
        return new Plan(List.copyOf(baseline), List.copyOf(doors), List.copyOf(fixed), List.copyOf(issues));
    }
    private static void fillFixed(Point a, Point b, Model model, List<FixedSegment> fixed, List<Issue> issues, int ordinal) {
        double distance = a.distance(b); if (distance < 1e-5) return;
        int count = (int) Math.ceil(distance / model.fixedMaxWidth);
        double width = distance / count;
        if (width < model.fixedMinWidth) { issues.add(new Issue("fixed_segment_outside_model_range", ordinal)); return; }
        for (int i = 0; i < count; i++) fixed.add(new FixedSegment(a.interpolate(b, i/(double)count), a.interpolate(b, (i+1)/(double)count), width));
    }
}
