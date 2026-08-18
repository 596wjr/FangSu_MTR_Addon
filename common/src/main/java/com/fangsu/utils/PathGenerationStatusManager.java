package com.fangsu.utils;

import com.fangsu.mappings.ComponentHelper;
import mtr.client.ClientData;
import mtr.data.Depot;
import mtr.data.IGui;
import mtr.data.Platform;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 「刷新线路」状态登记与完成通知（客户端，MTR3 版）：触发刷新的玩家在发送 C2S 包时
 * 登记开始时间，界面轮询显示「刷新线路中（已用时 %s）...」，收到 S2C 回执后
 * （{@code generatePathS2C}，成功/失败/未找到路径/异常的唯一出口）在触发者聊天栏
 * 发送成功/失败通知。
 * <p>
 * 完成判定（MTR3 语义）：{@code successfulSegments} ≥ 2 段成功；== 1 主路径未找到；
 * == 0 生成失败。S2C 包处理在 netty 线程，聊天消息必须
 * {@code Minecraft.getInstance().execute} 切回主线程；1.18.2 用
 * {@code displayClientMessage}，1.19+ 用 {@code sendSystemMessage}（参照
 * {@link com.fangsu.events.JoinInMessage} 模板）。
 * <p>
 * 残留清理：S2C 丢失时登记不会自动移除，在下次登记时顺带清理超过 30 分钟的旧记录。
 */
public final class PathGenerationStatusManager {

    /** 残留记录（S2C 丢失等）超过该时长自动清理，防泄漏。 */
    private static final long STALE_CLEANUP_MS = 30 * 60 * 1000L;

    /** 触发刷新的 depotId → 客户端墙钟开始时刻。 */
    private static final ConcurrentHashMap<Long, Long> START_TIMES = new ConcurrentHashMap<>();

    private PathGenerationStatusManager() {
    }

    /** 登记一次「刷新线路」开始（generatePathC2S 注入处调用；重复点击只更新开始时刻）。 */
    public static void onGenerationStarted(long depotId) {
        final long now = System.currentTimeMillis();
        START_TIMES.entrySet().removeIf(entry -> now - entry.getValue() > STALE_CLEANUP_MS);
        START_TIMES.put(depotId, now);
    }

    /** 该车厂是否处于本次方速登记的刷新中（界面生成中文案判定）。 */
    public static boolean isGenerating(long depotId) {
        return START_TIMES.containsKey(depotId);
    }

    public static long getStartTime(long depotId) {
        return START_TIMES.getOrDefault(depotId, 0L);
    }

    /** legacy 颜色码：站名/车厂名黄、成功绿、失败红、重置（聊天栏与仪表盘共用）。 */
    private static final String YELLOW = ChatFormatting.YELLOW.toString();
    private static final String GREEN = ChatFormatting.GREEN.toString();
    private static final String RED = ChatFormatting.RED.toString();
    private static final String RESET = ChatFormatting.RESET.toString();

    /**
     * 仪表盘（EditDepotScreen）状态文本：生成中/成功/失败均按通知格式
     * 「[黄]车厂名 [状态]」，失败带原因行与 A/B 段行；多行以 {@code |} 分隔
     * （原版 render 按 {@code \\|} 分行绘制，文案中不能含 {@code |}）。
     * 颜色用 § 码内嵌：原版绘制颜色固定 ARGB_WHITE，§ 码可覆盖之。
     * 状态未知（successfulSegments &lt; 0 但非方速登记）返回 original 保留原版。
     */
    public static String getDashboardText(Depot depot, String original) {
        final String depotName = IGui.formatStationName(depot.name);
        if (isGenerating(depot.id)) {
            // 车厂名黄；§r 重置后再拼刷新中文案（否则 §e 会延续整行）
            return YELLOW + depotName + " " + RESET
                    + ComponentHelper.translatable("gui.fangsu.depot.refreshing",
                            formatElapsed(System.currentTimeMillis() - getStartTime(depot.id))).getString();
        }
        final int successfulSegments = depot.clientPathGenerationSuccessfulSegments;
        if (successfulSegments >= 2) {
            return YELLOW + depotName + " " + GREEN
                    + ComponentHelper.translatable("msg.fangsu.depot.refresh_success").getString();
        }
        if (successfulSegments == 1) {
            final String firstStation = firstStationName(depot);
            return YELLOW + depotName + " " + RED
                    + ComponentHelper.translatable("msg.fangsu.depot.refresh_failed").getString()
                    + "|" + RED + ComponentHelper.translatable("gui.fangsu.depot.fail.path_not_found").getString()
                    + (firstStation == null ? "" : "|" + betweenLine(depotName, firstStation));
        }
        if (successfulSegments == 0) {
            return YELLOW + depotName + " " + RED
                    + ComponentHelper.translatable("msg.fangsu.depot.refresh_failed").getString()
                    + "|" + RED + ComponentHelper.translatable("gui.fangsu.depot.fail.generic").getString();
        }
        return original; // 仍生成中（非方速登记）→ 原版「正在生成路径」
    }

    /**
     * 「&gt; 在 A 与 B 之间找不到路径」行（§ 码着色：整行红、A/B 黄）。
     * 参数带 § 码的 literal 经 translatable %s 替换后保留样式码，行首
     * {@code §c} 使翻译文本部分回到红色。
     */
    private static String betweenLine(String a, String b) {
        return RED + "> " + ComponentHelper.translatable("msg.fangsu.depot.fail.between",
                ComponentHelper.literal(YELLOW + a + RED),
                ComponentHelper.literal(YELLOW + b + RED)).getString();
    }

    /**
     * S2C 回执（generatePathS2C HEAD 读取后调用）：仅触发者（有登记记录）收到通知。
     * <p>
     * 通知结构（多行）：
     * <pre>
     * 成功：[黄]车厂名 [绿]线路刷新成功！
     * 失败：[黄]车厂名 [红]线路刷新失败：
     *       [红]路径未找到
     *       [红]> 在 [黄]车厂 [红]与 [黄]首站 [红] 之间找不到路径
     * </pre>
     * 子组件各自 withStyle 后 append 组装；translatable 的 %s 参数可传带样式的
     * Component，嵌入时保留其样式。主路径未找到（successfulSegments==1）时
     * A/B 为车厂与第一个站台（与 mtr3 EditDepotScreen 界面文案同语义）。
     */
    public static void onGenerationResult(long depotId, int successfulSegments) {
        if (!START_TIMES.containsKey(depotId)) {
            return; // 非本机触发的刷新（其他人触发时本端也收到 S2C）
        }
        START_TIMES.remove(depotId);
        final Depot depot = ClientData.DATA_CACHE.depotIdMap.get(depotId);
        final MutableComponent message = ComponentHelper.empty()
                .append(ComponentHelper.literal(depot == null ? "" : IGui.formatStationName(depot.name)).withStyle(ChatFormatting.YELLOW))
                .append(ComponentHelper.literal(" ").withStyle(ChatFormatting.YELLOW));
        if (successfulSegments >= 2) {
            message.append(ComponentHelper.translatable("msg.fangsu.depot.refresh_success").withStyle(ChatFormatting.GREEN));
        } else {
            message.append(ComponentHelper.translatable("msg.fangsu.depot.refresh_failed").withStyle(ChatFormatting.RED));
            message.append(ComponentHelper.literal("\n"))
                    .append(ComponentHelper.translatable(successfulSegments == 1
                            ? "gui.fangsu.depot.fail.path_not_found"
                            : "gui.fangsu.depot.fail.generic").withStyle(ChatFormatting.RED));
            if (successfulSegments == 1 && depot != null) {
                final String firstStationName = firstStationName(depot);
                if (firstStationName != null) {
                    message.append(ComponentHelper.literal("\n"))
                            .append(ComponentHelper.literal("> ").withStyle(ChatFormatting.RED))
                            .append(ComponentHelper.translatable("msg.fangsu.depot.fail.between",
                                    ComponentHelper.literal(IGui.formatStationName(depot.name)).withStyle(ChatFormatting.YELLOW),
                                    ComponentHelper.literal(firstStationName).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED));
                }
            }
        }
        sendChatMessage(message);
    }

    /** 车厂第一条线路的第一个站台名（主路径未找到时作失败段端点）；无则 null。 */
    private static String firstStationName(Depot depot) {
        if (depot.routeIds.isEmpty()) {
            return null;
        }
        final Route route = ClientData.DATA_CACHE.routeIdMap.get(depot.routeIds.get(0));
        if (route == null || route.platformIds.isEmpty()) {
            return null;
        }
        final Platform platform = ClientData.DATA_CACHE.platformIdMap.get(route.platformIds.get(0).platformId);
        final Station station = platform == null ? null : MtrUtil.getStationByPlatform(platform);
        return station == null ? null : IGui.formatStationName(station.name);
    }

    /** 已用时格式化：最多两个单位（如「10分30秒」/ "1 h 05 m"），界面与通知共用。 */
    public static String formatElapsed(long elapsedMs) {
        final long seconds = Math.max(0, elapsedMs) / 1000;
        final long hours = seconds / 3600;
        final long minutes = (seconds % 3600) / 60;
        final long secs = seconds % 60;
        if (hours > 0) {
            return ComponentHelper.translatable("gui.fangsu.time.hours", hours).getString()
                    + ComponentHelper.translatable("gui.fangsu.time.minutes", minutes).getString();
        }
        if (minutes > 0) {
            return ComponentHelper.translatable("gui.fangsu.time.minutes", minutes).getString()
                    + ComponentHelper.translatable("gui.fangsu.time.seconds", secs).getString();
        }
        return ComponentHelper.translatable("gui.fangsu.time.seconds", secs).getString();
    }

    private static void sendChatMessage(Component message) {
        // S2C 包处理在 netty 线程，聊天消息必须在主线程发送
        Minecraft.getInstance().execute(() -> {
            final Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            //#if MC_VERSION >= 11900
            player.sendSystemMessage(message);
            //#else
            //$$ player.displayClientMessage(message, false);
            //#endif
        });
    }
}
