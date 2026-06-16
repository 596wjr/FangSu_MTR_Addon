package com.fangsu.ui.ticketMachine;

import com.fangsu.Main;
import com.fangsu.items.ModItems;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.ModNetwork;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ticketSystem.MtrTicketSystem;
import com.fangsu.utils.ColorUtil;
import com.fangsu.utils.MtrUtil;
import com.fangsu.utils.GraphicContext;
import com.fangsu.utils.ScreenUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.client.ClientData;
import mtr.data.Route;
import mtr.data.Station;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.awt.*;
import java.util.*;
import java.util.List;

public class TicketMachineMainScreen extends Screen {
    private static final ResourceLocation BG_TEXTURE = new ResourceLocation("fangsu:textures/gui/ticket_machine.png");
    protected static final int BG_TEXTURE_WIDTH = 256;
    protected static final int BG_TEXTURE_HEIGHT = 196;
    protected static final int BG_TEXTURE_DRAW_WIDTH = BG_TEXTURE_WIDTH - 16;
    protected static final int BG_TEXTURE_DRAW_HEIGHT = BG_TEXTURE_HEIGHT - 16;
    private static final ResourceLocation BUTTON_BLUE = new ResourceLocation("fangsu:textures/gui/button_blue.png");
    private static final ResourceLocation BUTTON_BLUE_SELECTED = new ResourceLocation("fangsu:textures/gui/button_blue_selected.png");
    private static final ResourceLocation BUTTON_BLUE_PRESSED = new ResourceLocation("fangsu:textures/gui/button_blue_pressed.png");
    private static final ResourceLocation BUTTON_ORANGE = new ResourceLocation("fangsu:textures/gui/button_orange.png");
    private static final ResourceLocation BUTTON_ORANGE_SELECTED = new ResourceLocation("fangsu:textures/gui/button_orange_selected.png");
    private static final ResourceLocation BUTTON_ORANGE_PRESSED = new ResourceLocation("fangsu:textures/gui/button_orange_pressed.png");
    private static final ResourceLocation BUTTON_GRAY = new ResourceLocation("fangsu:textures/gui/button_gray.png");
    private static final ResourceLocation BUTTON_GRAY_SELECTED = new ResourceLocation("fangsu:textures/gui/button_gray_selected.png");
    private static final ResourceLocation BUTTON_GRAY_PRESSED = new ResourceLocation("fangsu:textures/gui/button_gray_pressed.png");
    private static final ResourceLocation BUTTON_RED = new ResourceLocation("fangsu:textures/gui/button_red.png");
    private static final ResourceLocation BUTTON_RED_SELECTED = new ResourceLocation("fangsu:textures/gui/button_red_selected.png");
    private static final ResourceLocation BUTTON_RED_PRESSED = new ResourceLocation("fangsu:textures/gui/button_red_pressed.png");

    private static final int MAX_TICKET_COUNT = 6;
    private static final int MIN_TICKET_PRICE = 1;
    private static final int MAX_TICKET_PRICE = 32767;
    private static final int MAX_TICKET_PRICE_BUTTON = 6;
    private static final int ROUTES_PER_PAGE = 8;
    // 鏁板瓧閿洏甯冨眬
    private static final int NUM_PAD_ROWS = 4;
    private static final int NUM_PAD_COLS = 3;
    // 绔欑偣鍒楄〃鏄剧ず琛屾暟
    private static final int STATION_LIST_VISIBLE_ROWS = 8;

    private final BlockPos pos;
    private final Station station;
    private MouseClickInfo mouseClickInfo;

    private int ticketCount = 1;
    private int ticketPrice = 0;

    private int routePage = 0;          // 褰撳墠绾胯矾锟?
    private int selectedStationScroll = 0; // 绔欑偣鍒楄〃婊氬姩锛堥鐣欙級

    private List<RouteFareInfo> routes;
    private RouteFareInfo selectedRoute = null;
    private boolean isEditingCustomPrice = false;
    private boolean isConfirming = false;
    private StationFareInfo selectedStation = null;

    private String customPriceInput = "";
    private int stationScroll = 0;

    public TicketMachineMainScreen(Component title, BlockPos pos) {
        super(title);
        this.pos = pos;

        this.station = MtrUtil.getStationAt(MtrUtil.getCenterVector3f(this.pos));

        int currentZone = this.station != null ? this.station.zone : 0;
        Map<String, RouteFareInfo> routeMap = new HashMap<>();
        for (Route route : ClientData.ROUTES) {
            String key = TextUtil.getNonExtraParts(route.name);
            RouteFareInfo routeInfo = routeMap.computeIfAbsent(
                    key,
                    k -> new RouteFareInfo(
                            k,
                            new Color(route.color).getRGB(),
                            new HashSet<>()
                    )
            );
            for (Route.RoutePlatform routePlatform : route.platformIds) {
                Station stn = MtrUtil.getStationByPlatform(
                        MtrUtil.getPlatformById(routePlatform.platformId)
                );
                if (stn == null) continue;
                routeInfo.stations().add(
                        new StationFareInfo(
                                stn.name,
                                MtrTicketSystem.calcFare(currentZone, stn.zone)
                        )
                );
            }
        }
        routes = new ArrayList<>(routeMap.values());
        routes.sort(Comparator.comparing(RouteFareInfo::name).thenComparingInt(RouteFareInfo::color));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

//#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics nativeGfx, int mouseX, int mouseY, float partialTick) {
        super.render(nativeGfx, mouseX, mouseY, partialTick);
        GraphicContext g = GraphicContext.of(nativeGfx);
        //#else
        //$$@Override
        //$$public void render(com.mojang.blaze3d.vertex.PoseStack nativeGfx, int mouseX, int mouseY, float partialTick) {
        //$$    super.render(nativeGfx, mouseX, mouseY, partialTick);
        //$$    GraphicContext g = GraphicContext.of(nativeGfx);
        //#endif

        g.blit(
                BG_TEXTURE,
                this.width / 2 - BG_TEXTURE_WIDTH / 2,
                this.height / 2 - BG_TEXTURE_HEIGHT / 2,
                0,
                0,
                BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT,
                BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT
        );

        g.fill(texturePosX(0), texturePosY(0), texturePosX(BG_TEXTURE_DRAW_WIDTH), texturePosY(24), 0xff19304b);
        g.fill(texturePosX(0), texturePosY(24), texturePosX(37), texturePosY(BG_TEXTURE_DRAW_HEIGHT - 20), 0xffdadada);
        {
            int beginY = texturePosY(25);
            int endY = texturePosY(BG_TEXTURE_DRAW_HEIGHT - 21);
            int beginColor = 0xff19304b;
            int endColor = 0xff59afc3;
            int segments = 5;
            int totalHeight = endY - beginY;
            int segmentHeight = totalHeight / segments;
            for (int i = 0; i < segments; i++) {
                float t = i / (float) (segments - 1);

                int color = ColorUtil.lerpColor(beginColor, endColor, t);

                int y0 = beginY + i * segmentHeight;
                int y1 = (i == segments - 1)
                        ? endY
                        : y0 + segmentHeight;

                g.fill(
                        texturePosX(0),
                        y0,
                        texturePosX(36),
                        y1,
                        color
                );
            }
        }
        if (!isConfirming) {
            g.fill(texturePosX(BG_TEXTURE_DRAW_WIDTH - 37), texturePosY(24), texturePosX(BG_TEXTURE_DRAW_WIDTH), texturePosY(BG_TEXTURE_DRAW_HEIGHT - 20), 0xffdadada);
            {
                int beginY = texturePosY(25);
                int endY = texturePosY(BG_TEXTURE_DRAW_HEIGHT - 21);
                int beginColor = 0xff19304b;
                int endColor = 0xff59afc3;
                int segments = 5;
                int totalHeight = endY - beginY;
                int segmentHeight = totalHeight / segments;
                for (int i = 0; i < segments; i++) {
                    float t = i / (float) (segments - 1);

                    int color = ColorUtil.lerpColor(beginColor, endColor, t);

                    int y0 = beginY + i * segmentHeight;
                    int y1 = (i == segments - 1)
                            ? endY
                            : y0 + segmentHeight;

                    g.fill(
                            texturePosX(BG_TEXTURE_DRAW_WIDTH - 36),
                            y0,
                            texturePosX(BG_TEXTURE_DRAW_WIDTH),
                            y1,
                            color
                    );
                }
            }
            g.fill(texturePosX(37), texturePosY(24), texturePosX(BG_TEXTURE_DRAW_WIDTH - 37), texturePosY(48), 0xff19304b);
        }
        g.fill(texturePosX(0), texturePosY(BG_TEXTURE_DRAW_HEIGHT - 20), texturePosX(BG_TEXTURE_DRAW_WIDTH), texturePosY(BG_TEXTURE_DRAW_HEIGHT), 0xff59afc3);

        int currentX = texturePosX(4);
        currentX = ScreenUtil.drawStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.common.title"), currentX, texturePosY(4), 0xffffffff, 1.5f, true);
        if (station != null) {
            currentX += 4;
            currentX = g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.current"), currentX, texturePosY(8), 0xffffffff, true);
            int spareX = BG_TEXTURE_DRAW_WIDTH - currentX + texturePosX(0);
            String[] lines = station.name.split("\\|");
            if (lines.length == 1)
                ScreenUtil.drawCenteredStringScale(nativeGfx, lines[0], currentX + spareX / 2, texturePosY(8), 0xffffffff, 1.2f, true);
            else {
                float baseScale = 1.25f / (lines.length + 2);
                float gap = 2f / (lines.length - 1);
                int currentY = texturePosY(4);
                for (int i = 0; i < lines.length; i++) {
                    float scale = i == 0 ? baseScale * 3 : baseScale;
                    ScreenUtil.drawCenteredStringScale(nativeGfx, lines[i], currentX + spareX / 2, currentY, 0xffffffff, scale, true);
                    currentY += (int) (gap + 8 * scale);
                }
            }
        }

        ScreenUtil.drawCenteredStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.ticketmachine.selectCount"), texturePosX(18), texturePosY(28), 0xffffffff, 1f, true);
        {
            int baseH = (BG_TEXTURE_DRAW_HEIGHT - 80) / MAX_TICKET_COUNT;
            int baseHPrice = (BG_TEXTURE_DRAW_HEIGHT - 80) / MAX_TICKET_PRICE_BUTTON;
            int gap = 16 / MAX_TICKET_COUNT;
            int gapPrice = 16 / MAX_TICKET_PRICE_BUTTON;
            for (int i = 0; i < MAX_TICKET_COUNT; i++) {
                int currentY = texturePosY(42) + i * (baseH + gap);
                boolean selected = i == ticketCount - 1;
                boolean pointed = mouseX > texturePosX(3) && mouseX < texturePosX(33) && mouseY > currentY && mouseY < currentY + baseH;
                ScreenUtil.drawNineSlice(
                        nativeGfx,
                        selected ?
                                pointed ? BUTTON_ORANGE_SELECTED : BUTTON_ORANGE :
                                pointed ? BUTTON_BLUE_SELECTED : BUTTON_BLUE,
                        texturePosX(3), currentY,
                        30, baseH
                );
                ScreenUtil.drawCenteredStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.ticketmachine.count", i + 1), texturePosX(18), currentY + 4, 0xffffffff, Math.min((baseH - 8) / 8f, 1.5f), true);
                if (mouseClickInfo != null) {
                    if (mouseClickInfo.mouseX() > texturePosX(3) && mouseClickInfo.mouseX() < texturePosX(33) && mouseClickInfo.mouseY() > currentY && mouseClickInfo.mouseY() < currentY + baseH) {
                        ticketCount = i + 1;
                        mouseClickInfo = null;
                    }
                }
            }
            if (!isConfirming)
                for (int i = 1; i <= MAX_TICKET_PRICE_BUTTON; i++) {
                    int currentY = texturePosY(42) + (i - 1) * (baseHPrice + gapPrice);
                    int val = i;
                    if (i == MAX_TICKET_PRICE_BUTTON - 1 && ticketPrice > i) val = ticketPrice;
                    boolean pointed = mouseX > texturePosX(BG_TEXTURE_DRAW_WIDTH - 33) && mouseX < texturePosX(BG_TEXTURE_DRAW_WIDTH - 3) && mouseY > currentY && mouseY < currentY + baseH;
                    if (i == MAX_TICKET_PRICE_BUTTON) {
                        boolean selected = isEditingCustomPrice;
                        ScreenUtil.drawNineSlice(
                                nativeGfx,
                                selected ?
                                        pointed ? BUTTON_ORANGE_SELECTED : BUTTON_ORANGE :
                                        pointed ? BUTTON_BLUE_SELECTED : BUTTON_BLUE,
                                texturePosX(BG_TEXTURE_DRAW_WIDTH - 33), currentY,
                                30, baseHPrice
                        );
                        ScreenUtil.drawCenteredStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.ticketmachine.otherPrice"), texturePosX(BG_TEXTURE_DRAW_WIDTH - 18), currentY + 4, 0xffffffff, Math.min((baseHPrice - 8) / 8f, 1.5f), true);
                        if (mouseClickInfo != null) {
                            if (mouseClickInfo.mouseX() > texturePosX(BG_TEXTURE_DRAW_WIDTH - 33) && mouseClickInfo.mouseX() < texturePosX(BG_TEXTURE_DRAW_WIDTH - 3) && mouseClickInfo.mouseY() > currentY && mouseClickInfo.mouseY() < currentY + baseHPrice) {
                                selectedRoute = null;
                                if (!selected) isEditingCustomPrice = true;
                            }
                        }
                    } else {
                        boolean selected = val == ticketPrice;

                        ScreenUtil.drawNineSlice(
                                nativeGfx,
                                selected ?
                                        pointed ? BUTTON_ORANGE_SELECTED : BUTTON_ORANGE :
                                        pointed ? BUTTON_BLUE_SELECTED : BUTTON_BLUE,
                                texturePosX(BG_TEXTURE_DRAW_WIDTH - 33), currentY,
                                30, baseHPrice
                        );
                        ScreenUtil.drawCenteredStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.ticketmachine.price", val), texturePosX(BG_TEXTURE_DRAW_WIDTH - 18), currentY + 4, 0xffffffff, Math.min((baseHPrice - 8) / 8f, 1.5f), true);
                        if (mouseClickInfo != null) {
                            if (mouseClickInfo.mouseX() > texturePosX(BG_TEXTURE_DRAW_WIDTH - 33) && mouseClickInfo.mouseX() < texturePosX(BG_TEXTURE_DRAW_WIDTH - 3) && mouseClickInfo.mouseY() > currentY && mouseClickInfo.mouseY() < currentY + baseHPrice) {
                                ticketPrice = val;
                                selectedStation = null;
                                isEditingCustomPrice = false;
                                mouseClickInfo = null;
                            }
                        }
                    }
                }

        }
        if (!isConfirming) {
            int startX = texturePosX(40);
            int startY = texturePosY(26);
            int cellW = (BG_TEXTURE_DRAW_WIDTH - 80) / 4;
            int cellH = 12;

            int startIndex = routePage * ROUTES_PER_PAGE;

            for (int i = 0; i < ROUTES_PER_PAGE; i++) {
                int index = startIndex + i;
                if (index >= routes.size()) break;

                RouteFareInfo route = routes.get(i + startIndex);

                int row = i / 4;
                int col = i % 4;

                int x = startX + col * cellW;
                int y = startY + row * cellH;

                boolean selected = route.equals(selectedRoute);
                boolean pointed = mouseX > x && mouseX < x + cellW && mouseY > y && mouseY < y + cellH;

                g.fill(
                        x, y,
                        x + cellW - 2, y + cellH - 2, route.color()
                );
                if (selected)
                    g.fill(
                            x, y,
                            x + cellW - 2, y + cellH - 2, 0x22000000
                    );
                else if (pointed)
                    g.fill(
                            x, y,
                            x + cellW - 2, y + cellH - 2, 0x22ffffff
                    );

                //#if MC_VERSION >= 11900
                Component labelText = Component.literal(route.name().replace("|", " "));
                //#else
                //$$ Component labelText = ComponentHelper.literal(route.name().replace("|", " "));
                //#endif
                ScreenUtil.drawScrollingText(
                        nativeGfx, font, labelText,
                        x + 2,
                        y + 2,
                        cellW - 4, cellH - 4,
                        0xffffffff, true
                );

                if (mouseClickInfo != null && pointed) {
                    selectedRoute = route;
                    selectedStationScroll = 0;
                    mouseClickInfo = null;
                }
            }
        } else {
            g.fill(texturePosX(42), texturePosY(32), texturePosX(BG_TEXTURE_DRAW_WIDTH - 8), texturePosY(BG_TEXTURE_DRAW_HEIGHT - 28), 0xff000000);
            g.fill(texturePosX(42) + 1, texturePosY(32) + 1, texturePosX(BG_TEXTURE_DRAW_WIDTH - 8) - 1, texturePosY(BG_TEXTURE_DRAW_HEIGHT - 28) - 1, 0xffffffff);

            int currentY = texturePosY(36);
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm1"), texturePosX(46), currentY, 0xffff4444, false);
            currentY += 10;
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm2", ComponentHelper.translatable("ui.fangsu.ticket.singleJourney")), texturePosX(54), currentY, 0xff000000, false);
            currentY += 10;
            if (station != null) {
                g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm3", station.name.replace("|", " ")), texturePosX(54), currentY, 0xff000000, false);
                currentY += 10;
            }
            if (selectedStation != null) {
                g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm4", selectedStation.name), texturePosX(54), currentY, 0xff000000, false);
                currentY += 10;
            }
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm5", ticketPrice), texturePosX(54), currentY, 0xff000000, false);
            currentY += 10;
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm6", ticketCount), texturePosX(54), currentY, 0xff000000, false);
            currentY += 10;
            g.drawString(font, ComponentHelper.translatable("ui.fangsu.ticketmachine.confirm7", ticketPrice * ticketCount), texturePosX(54), currentY, 0xff000000, false);
        }
        if (!isConfirming) {
            if (isEditingCustomPrice) {
                renderNumericPad(nativeGfx, mouseX, mouseY, texturePosX(BG_TEXTURE_DRAW_WIDTH / 2 - 40), texturePosY(60), 80, 100);
            } else if (selectedRoute != null) {
                List<StationFareInfo> stations = new ArrayList<>(selectedRoute.stations());
                stations.sort(Comparator.comparingInt(StationFareInfo::fare));

                int listStartX = texturePosX(48);
                int listEndX = texturePosX(BG_TEXTURE_DRAW_WIDTH - 48);
                int listStartY = texturePosY(60);
                int lineH = 14;

                int maxVisible = STATION_LIST_VISIBLE_ROWS;

                int scrollStart = stationScroll;
                int scrollEnd = Math.min(stations.size(), scrollStart + maxVisible);

                for (int i = scrollStart; i < scrollEnd; i++) {
                    StationFareInfo stn = stations.get(i);

                    boolean selected = stn.equals(selectedStation);
                    boolean pointed = mouseX > listStartX && mouseX < listEndX && mouseY > listStartY + (i - scrollStart) * lineH && mouseY < listStartY + (i - scrollStart) * lineH + lineH;
                    ScreenUtil.drawNineSlice(
                            nativeGfx,
                            selected ? BUTTON_GRAY_PRESSED :
                                    pointed ? BUTTON_GRAY_SELECTED : BUTTON_GRAY,
                            listStartX, listStartY + (i - scrollStart) * lineH,
                            listEndX - listStartX, lineH
                    );
                    int currentY = listStartY + (i - scrollStart) * lineH;
                    //#if MC_VERSION >= 11900
                    Component stnName = Component.literal(stn.name());
                    //#else
                    //$$ Component stnName = ComponentHelper.literal(stn.name());
                    //#endif
                    ScreenUtil.drawScrollingText(nativeGfx, font,
                            stnName,
                            listStartX, currentY + 4,
                            listEndX - listStartX - 15, lineH - 6, 0xffffffff, true);
                    ScreenUtil.drawRightAlignedStringScale(
                            nativeGfx, ComponentHelper.translatable("ui.fangsu.ticketmachine.price", stn.fare()),
                            listEndX, currentY + 4, 0xffffffff, 1f, true
                    );

                    if (mouseClickInfo != null && pointed) {
                        selectedStation = stn;
                        mouseClickInfo = null;
                        ticketPrice = stn.fare();
                    }
                }
            }
        }

        {
            boolean confirmAvailable = ticketPrice >= MIN_TICKET_PRICE && ticketPrice <= MAX_TICKET_PRICE;
            boolean cancelAvailable = isConfirming || isEditingCustomPrice;
            boolean confirmSelected = mouseX > texturePosX(BG_TEXTURE_DRAW_WIDTH - 36) && mouseX < texturePosX(BG_TEXTURE_DRAW_WIDTH - 8) &&
                    mouseY > texturePosY(BG_TEXTURE_DRAW_HEIGHT - 18) && mouseY < texturePosY(BG_TEXTURE_DRAW_HEIGHT - 4);
            boolean cancelSelected = mouseX > texturePosX(BG_TEXTURE_DRAW_WIDTH - 68) && mouseX < texturePosX(BG_TEXTURE_DRAW_WIDTH - 40) &&
                    mouseY > texturePosY(BG_TEXTURE_DRAW_HEIGHT - 18) && mouseY < texturePosY(BG_TEXTURE_DRAW_HEIGHT - 4);
            ScreenUtil.drawNineSlice(nativeGfx,
                    confirmAvailable ?
                            confirmSelected ? BUTTON_BLUE_SELECTED : BUTTON_BLUE : BUTTON_BLUE,
                    texturePosX(BG_TEXTURE_DRAW_WIDTH - 36), texturePosY(BG_TEXTURE_DRAW_HEIGHT - 18), 28, 14
            );
            ScreenUtil.drawNineSlice(nativeGfx,
                    cancelAvailable ?
                            cancelSelected ? BUTTON_RED_SELECTED : BUTTON_RED : BUTTON_RED,
                    texturePosX(BG_TEXTURE_DRAW_WIDTH - 68), texturePosY(BG_TEXTURE_DRAW_HEIGHT - 18), 28, 14
            );
            ScreenUtil.drawCenteredStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.block.confirm"),
                    texturePosX(BG_TEXTURE_DRAW_WIDTH - 22),
                    texturePosY(BG_TEXTURE_DRAW_HEIGHT - 14),
                    confirmAvailable ? 0xffffffff : 0xff999999, 1.25f, true);
            ScreenUtil.drawCenteredStringScale(nativeGfx, ComponentHelper.translatable("ui.fangsu.block.cancel"),
                    texturePosX(BG_TEXTURE_DRAW_WIDTH - 54),
                    texturePosY(BG_TEXTURE_DRAW_HEIGHT - 14),
                    cancelAvailable ? 0xffffffff : 0xff999999, 1.25f, true);

            if (confirmAvailable && confirmSelected && mouseClickInfo != null) {
                if (!isConfirming) isConfirming = true;
                else {
                    Main.LOGGER.info("Confirm purchase: price:{}, count:{}", ticketPrice, ticketCount);
                    ResourceLocation ticket = ModItems.ITEM_SINGLE_JOURNEY_TICKET.getId();
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeResourceLocation(ticket);
                    buf.writeVarInt(ticketPrice);
                    buf.writeVarInt(ticketCount);
                    NetworkManager.sendToServer(ModNetwork.TICKET_MACHINE_SYNC, buf);
                    onClose();
                }
                mouseClickInfo = null;
            }
            if (cancelAvailable && cancelSelected && mouseClickInfo != null) {
                if (isConfirming) isConfirming = false;
                if (isEditingCustomPrice) isEditingCustomPrice = false;
                mouseClickInfo = null;
            }
        }

        mouseClickInfo = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseClickInfo = new MouseClickInfo(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selectedRoute != null) {
            List<StationFareInfo> stations = new ArrayList<>(selectedRoute.stations());
            int maxScroll = Math.max(0, stations.size() - STATION_LIST_VISIBLE_ROWS);
            stationScroll -= delta; // 涓婃粴涓烘
            if (stationScroll < 0) stationScroll = 0;
            if (stationScroll > maxScroll) stationScroll = maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private record MouseClickInfo(double mouseX, double mouseY, int button) {
    }

    private record RouteFareInfo(String name, int color, Set<StationFareInfo> stations) {
        @Override
        public int hashCode() {
            return Objects.hash(name, color);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (this == obj) return true;
            if (obj instanceof RouteFareInfo) {
                return ((RouteFareInfo) obj).name.equals(name) && color == ((RouteFareInfo) obj).color;
            }
            return false;
        }
    }

    private record StationFareInfo(String name, int fare) {
        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (this == obj) return true;
            if (obj instanceof StationFareInfo) {
                return ((StationFareInfo) obj).name.equals(name) && fare == ((StationFareInfo) obj).fare;
            }
            return false;
        }
    }

    private int texturePosX(double x) {
        return (int) (this.width / 2 - BG_TEXTURE_DRAW_WIDTH / 2 + x);
    }

    private int texturePosY(double y) {
        return (int) (this.height / 2 - BG_TEXTURE_DRAW_HEIGHT / 2 + y);
    }

    //#if MC_VERSION >= 12000
    private void renderNumericPad(GuiGraphics nativeGfx, int mouseX, int mouseY,
                                  //#else
                                  //$$private void renderNumericPad(com.mojang.blaze3d.vertex.PoseStack nativeGfx, int mouseX, int mouseY,
                                  //#endif
                                  int panelX, int panelY, int panelWidth, int panelHeight) {
        final int padCols = NUM_PAD_COLS;
        final int padRows = NUM_PAD_ROWS;
        final int gap = 2; // 鎸夐挳闂磋窛

        // 璁＄畻鎸夐挳楂樺害锛屾樉绀烘涔熶笌鎸夐挳鍚岄珮
        int buttonH = (panelHeight - (padRows + 1) * gap) / (padRows + 1); // +1 鏄樉绀烘
        int buttonW = (panelWidth - (padCols - 1) * gap) / padCols;

        // 鏄剧ず妗嗕綅缃紙鍦ㄩ潰鏉夸笂鏂癸級
        int displayX = panelX;
        int displayY = panelY;
        int displayWidth = panelWidth;
        int displayHeight = buttonH;

        // 缁樺埗鏄剧ず锟?
        ScreenUtil.drawNineSlice(nativeGfx, BUTTON_GRAY, displayX, displayY, displayWidth, displayHeight);
        //#if MC_VERSION >= 11900
        Component priceText = Component.literal(customPriceInput.isEmpty() ? "0" : customPriceInput);
        //#else
        //$$ Component priceText = ComponentHelper.literal(customPriceInput.isEmpty() ? "0" : customPriceInput);
        //#endif
        ScreenUtil.drawCenteredStringScale(nativeGfx,
                priceText,
                displayX + displayWidth / 2,
                displayY + (displayHeight - 8) / 2,
                0xffffff00,
                1.25f,
                true);

        // 鏁板瓧鎸夐挳璧峰浣嶇疆锛堟樉绀烘涓嬫柟锟?
        int padStartX = panelX;
        int padStartY = displayY + displayHeight + gap;

        String[] keys = {
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "Del", "0", "OK"
        };

        int padIdx = 0;
        for (int r = 0; r < padRows; r++) {
            for (int c = 0; c < padCols; c++) {
                int idx = padIdx++;
                if (idx >= keys.length) break;

                String key = keys[idx];
                int x = padStartX + c * (buttonW + gap);
                int y = padStartY + r * (buttonH + gap);

                boolean pointed = mouseX >= x && mouseX <= x + buttonW && mouseY >= y && mouseY <= y + buttonH;

                // 缁樺埗鎸夐挳
                ScreenUtil.drawNineSlice(nativeGfx, pointed ? BUTTON_GRAY_SELECTED : BUTTON_GRAY, x, y, buttonW, buttonH);
                //#if MC_VERSION >= 11900
                Component keyText = Component.literal(key);
                //#else
                //$$ Component keyText = ComponentHelper.literal(key);
                //#endif
                ScreenUtil.drawCenteredStringScale(nativeGfx, keyText, x + buttonW / 2, y + 4, 0xffffffff, 0.8f, true);

                // 鐐瑰嚮澶勭悊
                if (mouseClickInfo != null && pointed) {
                    if ("Del".equals(key) && !customPriceInput.isEmpty()) {
                        customPriceInput = customPriceInput.substring(0, customPriceInput.length() - 1);
                    } else if ("OK".equals(key)) {
                        if (!customPriceInput.isEmpty()) {
                            ticketPrice = Integer.parseInt(customPriceInput);
                        }
                        isEditingCustomPrice = false;
                        customPriceInput = "";
                        selectedStation = null;
                        isConfirming = true;
                    } else if (key.matches("\\d")) {
                        String newInput = customPriceInput + key;
                        try {
                            long val = Long.parseLong(newInput);
                            customPriceInput = val <= MAX_TICKET_PRICE ? newInput : String.valueOf(MAX_TICKET_PRICE);
                        } catch (NumberFormatException e) {
                            customPriceInput = String.valueOf(MAX_TICKET_PRICE);
                        }
                    }
                    mouseClickInfo = null;
                }
            }
        }
    }
}
