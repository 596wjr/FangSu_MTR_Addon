package com.fangsu.turnout;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.fangsu.turnout.marker.NodeMarker;
import com.fangsu.turnout.marker.MarkerColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 完整道岔绑定：保留旧双路径字段以读取历史单开，同时保存复合轨段和实际消耗的标记。
 * anchor/yaw 是所有轨段共用的坐标系，NBT 与网络使用同一份有界编解码。
 */
public record TurnoutBinding(BlockPos toe, BlockPos straightEnd, BlockPos branchEnd,
                             String railModelId, Vec3 anchor, float baseYawDeg,
                             List<BlockPos> straightPath, List<BlockPos> branchPath,
                             TurnoutKind kind, List<TurnoutTrack> tracks, List<NodeMarker> markers) {

    private static final String K_TOE = "toe";
    private static final String K_STRAIGHT = "straightEnd";
    private static final String K_BRANCH = "branchEnd";
    private static final String K_RAIL_MODEL = "railModelId";
    private static final String K_ANCHOR_X = "anchorX";
    private static final String K_ANCHOR_Y = "anchorY";
    private static final String K_ANCHOR_Z = "anchorZ";
    private static final String K_BASE_YAW = "baseYawDeg";
    private static final String K_STRAIGHT_PATH = "straightPath";
    private static final String K_BRANCH_PATH = "branchPath";

    /** 路径长度上限（节点数）：只用于读包/读档时挡住畸形数据。 */
    private static final int MAX_PATH_NODES = 65;

    public TurnoutBinding {
        straightPath = straightPath == null ? List.of() : List.copyOf(straightPath);
        branchPath = branchPath == null ? List.of() : List.copyOf(branchPath);
        kind = kind == null ? TurnoutKind.SINGLE : kind;
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public TurnoutBinding(BlockPos toe, BlockPos straightEnd, BlockPos branchEnd,
                          String model, Vec3 anchor, float yaw, List<BlockPos> straight, List<BlockPos> branch) {
        this(toe, straightEnd, branchEnd, model, anchor, yaw, straight, branch, TurnoutKind.SINGLE, List.of(),
                List.of(new NodeMarker(toe,MarkerColor.BLUE),new NodeMarker(straightEnd,MarkerColor.RED),new NodeMarker(branchEnd,MarkerColor.RED)));
    }

    public TurnoutBinding withModel(String model) {
        return new TurnoutBinding(toe,straightEnd,branchEnd,model,anchor,baseYawDeg,straightPath,branchPath,kind,tracks,markers);
    }

    /** 不带路径的旧签名（源代码兼容：只解三个节点时用，路径留空）。 */
    public TurnoutBinding(BlockPos toe, BlockPos straightEnd, BlockPos branchEnd,
                          String railModelId, Vec3 anchor, float baseYawDeg) {
        this(toe, straightEnd, branchEnd, railModelId, anchor, baseYawDeg, List.of(), List.of());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(K_TOE, toe.asLong());
        tag.putLong(K_STRAIGHT, straightEnd.asLong());
        tag.putLong(K_BRANCH, branchEnd.asLong());
        tag.putString(K_RAIL_MODEL, railModelId == null ? "" : railModelId);
        tag.putDouble(K_ANCHOR_X, anchor == null ? 0 : anchor.x);
        tag.putDouble(K_ANCHOR_Y, anchor == null ? 0 : anchor.y);
        tag.putDouble(K_ANCHOR_Z, anchor == null ? 0 : anchor.z);
        tag.putFloat(K_BASE_YAW, baseYawDeg);
        tag.putLongArray(K_STRAIGHT_PATH, toLongArray(straightPath));
        tag.putLongArray(K_BRANCH_PATH, toLongArray(branchPath));
        tag.putString("kind",kind.name());
        ListTag paths=new ListTag();for(TurnoutTrack track:tracks)paths.add(track.toTag());tag.put("tracks",paths);
        ListTag selected=new ListTag();for(NodeMarker marker:markers){CompoundTag entry=new CompoundTag();entry.putLong("pos",marker.pos().asLong());entry.putBoolean("blue",marker.color()==MarkerColor.BLUE);selected.add(entry);}tag.put("markers",selected);
        return tag;
    }

    @Nullable
    public static TurnoutBinding fromTag(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(K_TOE) || !tag.contains(K_STRAIGHT) || !tag.contains(K_BRANCH)) {
            return null;
        }
        String model = tag.getString(K_RAIL_MODEL);
        return new TurnoutBinding(
                BlockPos.of(tag.getLong(K_TOE)),
                BlockPos.of(tag.getLong(K_STRAIGHT)),
                BlockPos.of(tag.getLong(K_BRANCH)),
                model.isEmpty() ? null : model,
                new Vec3(tag.getDouble(K_ANCHOR_X), tag.getDouble(K_ANCHOR_Y), tag.getDouble(K_ANCHOR_Z)),
                tag.getFloat(K_BASE_YAW),
                // 旧存档没有这两个键 → 空列表（几何侧退化为不带路径）
                toPositions(tag.getLongArray(K_STRAIGHT_PATH)),
                toPositions(tag.getLongArray(K_BRANCH_PATH)),
                readKind(tag.getString("kind")), readTracks(tag), readMarkers(tag)
        );
    }

    // 同一份有界 NBT 编解码同时用于存档与网络，避免复合路径字段漏同步。
    public void write(FriendlyByteBuf buf) { buf.writeNbt(toTag()); }
    public static TurnoutBinding read(FriendlyByteBuf buf) {
        TurnoutBinding binding=fromTag(buf.readNbt());
        if(binding==null)throw new IllegalArgumentException("道岔绑定载荷不完整");
        return binding;
    }
    private static TurnoutKind readKind(String value){try{return TurnoutKind.valueOf(value);}catch(IllegalArgumentException e){return TurnoutKind.SINGLE;}}
    private static List<TurnoutTrack> readTracks(CompoundTag tag){ListTag list=tag.getList("tracks",10);if(list.size()>64)throw new IllegalArgumentException("道岔路径过多");List<TurnoutTrack> out=new ArrayList<>();for(int i=0;i<list.size();i++)out.add(TurnoutTrack.fromTag(list.getCompound(i)));return out;}
    private static List<NodeMarker> readMarkers(CompoundTag tag){ListTag list=tag.getList("markers",10);if(list.size()>64)throw new IllegalArgumentException("道岔标记过多");List<NodeMarker> out=new ArrayList<>();for(int i=0;i<list.size();i++){CompoundTag item=list.getCompound(i);out.add(new NodeMarker(BlockPos.of(item.getLong("pos")),item.getBoolean("blue")?MarkerColor.BLUE:MarkerColor.RED));}if(out.isEmpty())out.addAll(List.of(new NodeMarker(BlockPos.of(tag.getLong(K_TOE)),MarkerColor.BLUE),new NodeMarker(BlockPos.of(tag.getLong(K_STRAIGHT)),MarkerColor.RED),new NodeMarker(BlockPos.of(tag.getLong(K_BRANCH)),MarkerColor.RED)));return out;}

    private static long[] toLongArray(@Nullable List<BlockPos> path) {
        if (path == null || path.isEmpty()) return new long[0];
        final long[] out = new long[path.size()];
        for (int i = 0; i < out.length; i++) {
            final BlockPos pos = path.get(i);
            out[i] = pos == null ? BlockPos.ZERO.asLong() : pos.asLong();
        }
        return out;
    }

    private static List<BlockPos> toPositions(@Nullable long[] values) {
        if (values == null || values.length == 0) return List.of();
        if (values.length > MAX_PATH_NODES) throw new IllegalArgumentException("道岔路径过长");
        final int size = values.length;
        final List<BlockPos> path = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            path.add(BlockPos.of(values[i]));
        }
        return path;
    }

    /** 写入可空绑定（先写一个存在位），供 S2C 包统一编解码。 */
    public static void writeNullable(FriendlyByteBuf buf, @Nullable TurnoutBinding binding) {
        buf.writeBoolean(binding != null);
        if (binding != null) binding.write(buf);
    }

    @Nullable
    public static TurnoutBinding readNullable(FriendlyByteBuf buf) {
        return buf.readBoolean() ? read(buf) : null;
    }
}
