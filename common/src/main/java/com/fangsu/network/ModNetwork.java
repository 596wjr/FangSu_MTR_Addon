package com.fangsu.network;

import com.fangsu.Main;
import net.minecraft.core.Registry;
import com.fangsu.blockEntities.BlockEntityMultiDirectionNode;
import com.fangsu.blockEntities.BlockEntityScreendoorCentralControl;
import com.fangsu.blockEntities.BlockEntityTicketBarrier;
import com.fangsu.blockEntities.Syncable;
import com.fangsu.items.TicketItem;
import com.fangsu.util.NodeConnector;
import dev.architectury.networking.NetworkManager;
import mtr.data.EnumHelper;
import mtr.data.RailType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
//#if MC_VERSION >= 11903
import net.minecraft.core.registries.BuiltInRegistries;
//#endif
//#if MC_VERSION >= 12000
import net.minecraft.core.registries.Registries;
//#endif
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ModNetwork {
    private static final int EMERALD_VALUE = 11;

    public static final ResourceLocation BE_SYNC =
            new ResourceLocation("fangsu", "be_sync");
    public static final ResourceLocation TICKET_MACHINE_SYNC =
            new ResourceLocation("fangsu", "ticket_machine_sync");
    public static final ResourceLocation CENTRAL_CONTROL_SYNC =
            new ResourceLocation("fangsu", "central_control_sync");
    public static final ResourceLocation TICKET_BARRIER_SYNC =
            new ResourceLocation("fangsu", "ticket_barrier_sync");
    /**
     * 万向节点轨道刷新（C2S → 服务端原地重建）。
     * <p>
     * 对应 MTR4 版的同名通道 {@code NODE_REFRESH_RAIL}：节点姿态（平移 / 俯仰 / 翻滚 /
     * 外轨超高开关 / 半轨距）或方向变更后，客户端把「节点姿态快照 + 每条相连轨道的属性」
     * 发到服务端，由 {@link NodeConnector#refreshNodeRail} 重建这些轨道的几何。
     */
    public static final ResourceLocation NODE_REFRESH_RAIL =
            new ResourceLocation("fangsu", "node_refresh_rail");

    public static void init() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                BE_SYNC,
                ModNetwork::handleBeSync
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                NODE_REFRESH_RAIL,
                ModNetwork::handleNodeRefreshRail
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                TICKET_MACHINE_SYNC,
                ModNetwork::ticketMachineSync
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CENTRAL_CONTROL_SYNC,
                ModNetwork::handleCentralControlSync
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                TICKET_BARRIER_SYNC,
                ModNetwork::handleTicketBarrierSync
        );
        HybridCreatorPackets.registerServer();
        DisplacementToolPackets.registerServer();
    }

    private static void handleBeSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext ctx
    ) {
        BlockPos pos = buf.readBlockPos();
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);


        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof Syncable syncable) {
                FriendlyByteBuf safeBuf =
                        new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload));

                syncable.readC2S(safeBuf);
//                be.setChanged();
//
//                level.sendBlockUpdated(
//                        pos,
//                        be.getBlockState(),
//                        be.getBlockState(),
//                        3
//                );
            }
        });
    }

    /**
     * 万向节点轨道刷新请求（C2S）。
     * <p>
     * <b>载荷必须在主线程排队之前读完</b>：排队回调可能在网络缓冲区释放之后才执行，
     * 因此这里先在网络线程把 {@link NodeRefreshRailPayload} 解成不可变的 record，
     * 再把「应用 + 重建」排到服务端主线程。
     * <p>
     * <b>服务端校验（不做「C2S 数据无服务端校验」那个已知问题）</b>：
     * <ul>
     *   <li>节点位置必须是已加载的方块实体，且必须是 {@link BlockEntityMultiDirectionNode}；
     *       不是的话直接丢弃（不新建、不改别的方块）。</li>
     *   <li>每条轨道的另一端必须<b>确实相连</b>（服务端自己的 {@code rails} 表里查得到）——
     *       由 {@link NodeConnector#refreshNodeRail} 判空，客户端不能凭一个坐标让服务端重建
     *       任意不存在的轨道。</li>
     *   <li>限速 / 单向 / 类型全部由服务端按<b>旧轨道的属性</b>重建，客户端不能通过本包改这些属性。</li>
     *   <li>姿态数值一律经 {@link BlockEntityMultiDirectionNode} 的公开钳制函数落库（NaN / 无穷归零），
     *       与 BE_SYNC 路径共用同一套硬边界。</li>
     * </ul>
     */
    private static void handleNodeRefreshRail(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext ctx
    ) {
        // 网络线程：先解载荷（排队回调里再读会读到已释放的缓冲区）
        final NodeRefreshRailPayload.Payload payload = NodeRefreshRailPayload.read(buf);

        ctx.queue(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) {
                return;
            }
            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            if (!(level.getBlockEntity(payload.nodePos()) instanceof BlockEntityMultiDirectionNode node)) {
                Main.LOGGER.warn("[NodeRefreshRail] 目标不是万向节点，丢弃刷新请求 {}（玩家 {}）",
                        payload.nodePos(), player.getName().getString());
                return;
            }
            // 1) 节点姿态落库（与 BE_SYNC 共用同一套钳制；到达顺序因此不影响最终几何）
            node.applyRefreshedState(payload);
            // 2) 按服务端权威姿态原地重建每一条相连轨道。
            //    客户端随包带了的「轨道属性」只作一致性校验：新轨道的一切属性都取自服务端自己的旧轨道
            //    （见 NodeConnector.refreshNodeRail），客户端无法通过本包伪造或修改轨道类型。
            int refreshed = 0;
            for (final NodeRefreshRailPayload.RailEntry entry : payload.rails()) {
                final RailType railType = EnumHelper.valueOf(RailType.IRON, entry.railTypeName());
                if (!NodeConnector.railPropsMatch(level, payload.nodePos(), entry.otherPos(), railType, entry.isOneWay())) {
                    Main.LOGGER.warn("[NodeRefreshRail] 轨道属性与旧轨道不一致，丢弃 {}->{}（type={} oneWay={}）",
                            payload.nodePos(), entry.otherPos(), entry.railTypeName(), entry.isOneWay());
                    continue;
                }
                if (NodeConnector.refreshNodeRail(level, payload.nodePos(), entry.otherPos(), entry.tilt())) {
                    refreshed++;
                }
            }
            if (refreshed == 0) {
                Main.LOGGER.warn("[NodeRefreshRail] 没有轨道被重建 {}（相连端点 {} 个）",
                        payload.nodePos(), payload.rails().size());
            } else {
                Main.debug("[NodeRefreshRail] 重建 {} 条轨道 @ {}", refreshed, payload.nodePos());
            }
        });
    }

    private static void ticketMachineSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext context
    ) {
        ResourceLocation itemLocation = buf.readResourceLocation();
        int price = buf.readVarInt();
        int count = buf.readVarInt();

        context.queue(() -> {
            Main.LOGGER.info("1");
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            // -------- 基础校验 --------
            if (price <= 0 || count <= 0 || count > 64) return;
            Main.LOGGER.info(itemLocation.toString());
            //#if MC_VERSION >= 11903
            Item item = BuiltInRegistries.ITEM.get(itemLocation);
            //#else
            //$$ Item item = net.minecraft.core.Registry.ITEM.get(itemLocation);
            //#endif
            if (!(item instanceof TicketItem ticketItem)) return;
            Main.LOGGER.info("2");

            int totalPrice = price * count;

            // -------- 创造模式：直接给 --------
            if (player.isCreative()) {
                ItemStack stack = ticketItem.createTicket(price);
                Main.LOGGER.info("giving {} stack {} for {}", count, stack, player);
                for (int i = 0; i < count; i++)
                    player.getInventory().add(stack.copy());
                return;
            }

            // -------- 计算绿宝石 --------
            int emeraldCost = totalPrice / EMERALD_VALUE;
            if (emeraldCost * EMERALD_VALUE < totalPrice) {
                emeraldCost++; // 不找零，向上取整
            }

            Inventory inv = player.getInventory();

            if (countItem(inv, Items.EMERALD) < emeraldCost) {
                return; // 钱不够
            }

            // -------- 扣钱 --------
            removeItem(inv, Items.EMERALD, emeraldCost);

            // -------- 给票 --------
            ItemStack stack = ticketItem.createTicket(price);
            Main.LOGGER.info("giving {} stack {} for {}", count, stack, player);
            for (int i = 0; i < count; i++)
                player.getInventory().add(stack.copy());
        });
    }

    private static int countItem(Inventory inv, Item item) {
        int count = 0;
        for (ItemStack stack : inv.items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeItem(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.is(item)) continue;

            int remove = Math.min(stack.getCount(), amount);
            stack.shrink(remove);
            amount -= remove;

            if (stack.isEmpty()) {
                inv.items.set(i, ItemStack.EMPTY);
            }

            if (amount <= 0) {
                inv.setChanged();
                return;
            }
        }
        inv.setChanged();
    }

    private static void handleTicketBarrierSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext ctx
    ) {
        BlockPos pos = buf.readBlockPos();

        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof BlockEntityTicketBarrier barrier) {
                barrier.handleServerInteraction(level, player);
            }
        });
    }

    private static void handleCentralControlSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext ctx
    ) {
        BlockPos pos = buf.readBlockPos();
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);

        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof BlockEntityScreendoorCentralControl ctrl) {
                FriendlyByteBuf safeBuf =
                        new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload));
                ctrl.readSync(safeBuf);
            }
        });
    }
}
