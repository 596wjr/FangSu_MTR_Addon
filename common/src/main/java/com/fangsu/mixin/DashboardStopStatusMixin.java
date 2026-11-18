package com.fangsu.mixin;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.RouteMapSelection;
import com.fangsu.ui.DashboardRoutePanel;
import mtr.screen.DashboardList;
import mtr.screen.WidgetMap;
import mtr.data.IGui;
import mtr.mappings.ScreenMapper;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.fangsu.mtr.DashboardRouteGroups;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.RouteBypass;
import com.fangsu.mtr.RouteEditorAccess;
import com.fangsu.mtr.RouteOverview;
import com.fangsu.mtr.RouteServices;
import com.fangsu.mtr.RouteStopStatus;
import com.fangsu.mtr.StationOpening;
import com.fangsu.mtr.ServiceStop;
import com.fangsu.ui.DashboardRouteGroupScreen;
import net.minecraft.client.Minecraft;
import mtr.data.Route;
import mtr.data.TransportMode;
import mtr.client.ClientData;
import mtr.packet.IPacket;
import mtr.packet.PacketTrainDataGuiClient;
import mtr.screen.DashboardScreen;
import mtr.screen.EditRouteScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardStopStatusMixin extends ScreenMapper implements RouteEditorAccess, DashboardRoutePanel.Host {
    @Shadow @Final private TransportMode transportMode;

    @Shadow @Final private DashboardList dashboardList;
    @Shadow @Final private WidgetMap widgetMap;
    @Shadow @Final private Button buttonTabRoutes;
    @Shadow private Route editingRoute;
    @Shadow private int editingRoutePlatformIndex;
    @Shadow private boolean isNew;

    @Unique private final DashboardRoutePanel fangsu$panel = new DashboardRoutePanel();
    @Unique private int fangsu$replaceIndex = -1;
    @Unique private boolean fangsu$replacedOnClick;
    @Unique private boolean fangsu$invalidSelection;
    @Unique private Route.RoutePlatform fangsu$replacedStop;
    @Unique private String fangsu$lastSearch = "";
    @Unique private DashboardRouteGroups.Row<Route> fangsu$overviewGroup;

    @Invoker("startEditingRoute")
    protected abstract void fangsu$startEditingRoute(Route route, boolean draft);

    @Invoker("startEditingRouteDestination")
    protected abstract void fangsu$startDestination(int index);

    protected DashboardStopStatusMixin(Component title) {
        super(title);
    }

    @Unique
    private List<DashboardRouteGroups.Route<Route>> fangsu$routes() {
        return ClientData.ROUTES.stream()
                .filter(route -> route.transportMode == transportMode)
                .map(route -> new DashboardRouteGroups.Route<>(route, route.id, route.name, route.color)).toList();
    }

    @Unique
    private void fangsu$openRow(DashboardRouteGroups.Row<Route> row) {
        if (row.individual() == null || !ClientData.hasPermission()) {
            fangsu$overviewGroup = row;
            fangsu$panel.reset();
        } else {
            fangsu$editService(row.individual().value(), false);
        }
    }

    @Unique
    private void fangsu$editRow(DashboardRouteGroups.Row<Route> row) {
        if (row.individual() != null) {
            Minecraft.getInstance().setScreen(new EditRouteScreen(row.individual().value(), (DashboardScreen) (Object) this));
        } else {
            DashboardRouteGroupScreen.open(row.label(), row.routes(), (name, color) -> {
                final var ids = row.routes().stream().map(DashboardRouteGroups.Route::id).toList();
                com.fangsu.network.RouteGroupPackets.send(ids, false, name, color);
            });
        }
    }

    @Unique
    private void fangsu$deleteRow(DashboardRouteGroups.Row<Route> row) {
        final var members = row.individual() == null ? row.routes() : row.routes().stream()
                .filter(route -> route.id() == row.individual().id() || route.parentId() == row.individual().id()).toList();
        DashboardRouteGroupScreen.confirmDelete(row.label(), members.size(), row.individual() == null, () -> {
            com.fangsu.network.RouteGroupPackets.send(members.stream().map(DashboardRouteGroups.Route::id).toList(), true, null, null);
        });
    }

    @Unique
    private RouteOverview.Line fangsu$overviewLine(DashboardRouteGroups.Route<Route> member) {
        final var stops = new ArrayList<RouteOverview.Stop>();
        final var relation = RouteServices.relation(member.value());
        for (final var stop : member.value().platformIds) {
            final var station = ClientData.DATA_CACHE.platformIdToStation.get(stop.platformId);
            final var metadata = ServiceStop.get(stop);
            stops.add(new RouteOverview.Stop(station == null ? 0 : station.id,
                    station == null ? "?" : station.name, StationOpening.displayStatus(stop, station),
                    metadata == null ? null : metadata.id(), metadata == null ? null : metadata.parentStop()));
        }
        return new RouteOverview.Line(member.id(), member.parentId(),
                relation != null && relation.express(), member.label(), member.color(), stops);
    }

    @Override
    public void fangsu$editService(Route route, boolean draft) {
        if (buttonTabRoutes.active) buttonTabRoutes.onPress();
        fangsu$startEditingRoute(route, draft);
        dashboardList.clearSearch();
        final var dashboard = (DashboardScreen) (Object) this;
        if (Minecraft.getInstance().screen != dashboard) Minecraft.getInstance().setScreen(dashboard);
    }

    @Inject(method = "startEditingRoute", at = @At("RETURN"))
    private void fangsu$enterRoute(Route route, boolean draft, CallbackInfo ci) {
        fangsu$resetPanel();
    }

    @Inject(method = "stopEditing", at = @At("RETURN"))
    private void fangsu$exitRoute(CallbackInfo ci) {
        fangsu$resetPanel();
    }

    @Unique
    private void fangsu$resetPanel() {
        fangsu$clearSelection();
        fangsu$overviewGroup = null;
        fangsu$panel.reset();
    }

    @Unique
    private void fangsu$clearSelection() {
        fangsu$replaceIndex = -1;
        fangsu$invalidSelection = false;
        fangsu$replacedStop = null;
        ((RouteMapSelection) (Object) widgetMap).fangsu$selectBypass(null);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fangsu$clearNativeRows(CallbackInfo ci) {
        if (!buttonTabRoutes.active) {
            dashboardList.setData(java.util.List.<mtr.data.NameColorDataBase>of(), false, false, false, false, false, false);
            dashboardList.mouseMoved(-1, -1);
        }
    }

    @Override
    public boolean fangsu$drawRoutePanel(DashboardRoutePanel.Canvas canvas, int mouseX, int mouseY) {
        if (buttonTabRoutes.active) return false;
        final var rows = fangsu$rows();
        final var hint = fangsu$panelHint(rows.size());
        fangsu$panel.render(canvas, rows, hint, dashboardList.x, fangsu$top(), dashboardList.x + dashboardList.width,
                dashboardList.y + dashboardList.height, mouseX, mouseY);
        return true;
    }

    @Unique
    private net.minecraft.network.chat.Component fangsu$panelHint(int rowCount) {
        return fangsu$replaceIndex >= 0 ? ComponentHelper.translatable(fangsu$invalidSelection
                ? "ui.fangsu.route.map_replace_invalid" : "ui.fangsu.route.map_replace_hint")
                : rowCount == 0 ? ComponentHelper.translatable(editingRoute == null ? "ui.fangsu.route.empty" : "ui.fangsu.route.empty_stops")
                : editingRoute != null && RouteServices.relation(editingRoute) == null && ClientData.hasPermission()
                ? ComponentHelper.translatable("ui.fangsu.route.map_add_hint") : null;
    }

    @Unique
    private int fangsu$top() {
        return dashboardList.y + IGui.SQUARE_SIZE + IGui.TEXT_FIELD_PADDING;
    }

    @Unique
    private boolean fangsu$inside(double x, double y) {
        return !buttonTabRoutes.active && x >= dashboardList.x && x < dashboardList.x + dashboardList.width
                && y >= fangsu$top() && y < dashboardList.y + dashboardList.height;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (fangsu$inside(x, y)) {
            final var rows = fangsu$rows();
            return fangsu$panel.click(rows, fangsu$panelHint(rows.size()) != null, x, y, button, dashboardList.x, fangsu$top(),
                    dashboardList.x + dashboardList.width, dashboardList.y + dashboardList.height);
        }
        fangsu$replacedOnClick = false;
        if (fangsu$replaceIndex >= 0 && x >= dashboardList.x + dashboardList.width && button != 0) {
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void fangsu$scroll(double x, double y, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (fangsu$inside(x, y)) {
            final int rowCount = fangsu$rows().size();
            final boolean scrolled = fangsu$panel.scroll(rowCount, fangsu$panelHint(rowCount) != null, x, y, amount,
                    dashboardList.x, fangsu$top(), dashboardList.x + dashboardList.width, dashboardList.y + dashboardList.height);
            cir.setReturnValue(scrolled);
        }
    }

    @Unique
    private List<DashboardRoutePanel.Row> fangsu$rows() {
        final String search = java.util.Objects.toString(ClientData.DASHBOARD_SEARCH, "");
        if (!fangsu$lastSearch.equals(search)) {
            fangsu$lastSearch = search;
            fangsu$panel.reset();
        }
        final boolean canEdit = ClientData.hasPermission();
        if (fangsu$overviewGroup != null) {
            return fangsu$overviewRows(fangsu$overviewGroup);
        }
        if (editingRoute == null) {
            return DashboardRouteGroups.rows(fangsu$routes(), search).stream().map(row -> new DashboardRoutePanel.Row(
                    ComponentHelper.literal(row.label().replace('|', ' ')).withStyle(style -> style.withBold(row.depth() == 0)),
                    null, row.depth(), row.routes().get(0).color(), true,
                    canEdit ? List.of(new DashboardRoutePanel.Action(DashboardRoutePanel.Icon.DELETE, () -> fangsu$deleteRow(row)),
                            new DashboardRoutePanel.Action(DashboardRoutePanel.Icon.EDIT, () -> fangsu$editRow(row))) : List.of(),
                    () -> fangsu$openRow(row), null, null, row.groupIndex())).toList();
        }
        final var entries = editingRoute.platformIds;
        final var rows = new ArrayList<DashboardRoutePanel.Row>();
        final boolean inherited = RouteServices.relation(editingRoute) != null;
        final String query = search.toLowerCase(java.util.Locale.ROOT);
        for (int index = 0; index < entries.size(); index++) {
            final int row = index;
            final var stop = entries.get(index);
            final var platform = ClientData.DATA_CACHE.platformIdMap.get(stop.platformId);
            final var station = ClientData.DATA_CACHE.platformIdToStation.get(stop.platformId);
            final var status = StationOpening.displayStatus(stop, station);
            final String name = station == null ? "?" : station.name.replace('|', ' ');
            final var metadata = ServiceStop.get(stop);
            final var settings = metadata == null || metadata.bypass() == null ? null
                    : RouteBypass.settings(ClientData.RAILS, metadata.bypass());
            final var number = RouteStopStatus.of(stop) == RouteStopStatus.SKIPPED
                    ? ComponentHelper.translatable("ui.fangsu.route.bypass_stop", settings == null ? "?" : settings.number())
                    : ComponentHelper.literal(platform == null ? "?" : platform.name);
            final String destination = java.util.Objects.toString(stop.customDestination, "");
            final var subtitle = number.copy();
            if (!destination.isEmpty()) {
                subtitle.append(ComponentHelper.literal(Route.destinationIsReset(destination)
                        ? "  ·  \"": "  ·  " + destination.replace('|', ' ')));
            }
            if (!(name + " " + subtitle.getString()).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            final var actions = new ArrayList<DashboardRoutePanel.Action>();
            final boolean editingDestination = editingRoutePlatformIndex >= 0;
            if (canEdit && !editingDestination) {
                actions.add(fangsu$action(fangsu$replaceIndex == row ? "gui.cancel" : "ui.fangsu.route.replace_platform",
                        57, () -> fangsu$beginReplacement(row)));
                if (fangsu$replaceIndex < 0) {
                    actions.add(new DashboardRoutePanel.Action(DashboardRoutePanel.Icon.EDIT, () -> fangsu$startDestination(row)));
                }
                if (!inherited && fangsu$replaceIndex < 0) {
                    actions.add(new DashboardRoutePanel.Action(DashboardRoutePanel.Icon.DELETE, () -> {
                        entries.remove(row);
                        fangsu$saveStops(editingRoute, isNew);
                    }));
                    if (row + 1 < entries.size()) actions.add(new DashboardRoutePanel.Action(ComponentHelper.literal("↓"), 13,
                            () -> fangsu$moveStop(row, row + 1)));
                    if (row > 0) actions.add(new DashboardRoutePanel.Action(ComponentHelper.literal("↑"), 13,
                            () -> fangsu$moveStop(row, row - 1)));
                }
            }
            final var title = ComponentHelper.literal(name).withStyle(style -> style
                    .withStrikethrough(status == RouteStopStatus.DISABLED)
                    .withColor(status == RouteStopStatus.SKIPPED ? 0xA3ADB8 : 0xE5E9EE));
            final boolean canEditStop = canEdit && fangsu$replaceIndex < 0 && !editingDestination;
            rows.add(new DashboardRoutePanel.Row(title, subtitle, 1, status == RouteStopStatus.NORMAL ? editingRoute.color : 0x818B96,
                    true, actions, canEditStop ? () -> fangsu$startDestination(row) : null,
                    canEditStop && !StationOpening.isDeferred(station) && status != RouteStopStatus.SKIPPED
                            ? () -> fangsu$toggleStop(row) : null));
        }
        return rows;
    }

    @Unique
    private List<DashboardRoutePanel.Row> fangsu$overviewRows(DashboardRouteGroups.Row<Route> group) {
        final var rows = new ArrayList<DashboardRoutePanel.Row>();
        final Runnable back = () -> {
            fangsu$overviewGroup = null;
            fangsu$panel.reset();
        };
        rows.add(new DashboardRoutePanel.Row(ComponentHelper.literal(group.label().replace('|', ' ')), null, 0, group.color(),
                false, List.of(fangsu$action("gui.back", 46, back)), back, null));
        final var diagrams = RouteOverview.build(group.routes().stream().map(this::fangsu$overviewLine).toList());
        for (final var diagram : diagrams) {
            final var down = diagram.down();
            final boolean express = diagram.upExpress() != null || diagram.downExpress() != null;
            final String direction = down == null ? diagram.up().name() : "↑ " + diagram.up().name() + "  ↓ " + down.name();
            final String rapid = (diagram.upExpress() == null ? "" : "↑ " + diagram.upExpress().name())
                    + (diagram.downExpress() == null ? "" : "  ↓ " + diagram.downExpress().name());
            rows.add(new DashboardRoutePanel.Row(ComponentHelper.literal(direction), express ? ComponentHelper.literal(rapid.trim()) : null,
                    1, diagram.up().color(), false, List.of(), null, null));
            for (int index = 0; index < diagram.rows().size(); index++) {
                final var stop = diagram.rows().get(index);
                final boolean localStops = stop.up() != null && stop.up().status() == RouteStopStatus.NORMAL
                        || stop.down() != null && stop.down().status() == RouteStopStatus.NORMAL;
                final boolean expressStops = RouteOverview.Diagram.stops(diagram.upExpress(), stop.up())
                        || RouteOverview.Diagram.stops(diagram.downExpress(), stop.down());
                final var station = stop.up() == null ? stop.down() : stop.up();
                final var title = ComponentHelper.literal(station.name().replace('|', ' ')).withStyle(style ->
                        style.withStrikethrough(!localStops && !expressStops && station.status() == RouteStopStatus.DISABLED));
                rows.add(new DashboardRoutePanel.Row(title, null, 1,
                        diagram.up().color(), false, List.of(), null, null,
                        new DashboardRoutePanel.DiagramStop(express, localStops, expressStops, index == 0, index + 1 == diagram.rows().size())));
            }
        }
        return rows;
    }

    @Unique
    private static DashboardRoutePanel.Action fangsu$action(String key, int width, Runnable run) {
        return new DashboardRoutePanel.Action(ComponentHelper.translatable(key), width, run);
    }


    @Unique
    private void fangsu$moveStop(int from, int to) {
        Collections.swap(editingRoute.platformIds, from, to);
        fangsu$saveStops(editingRoute, isNew);
    }

    @Unique
    private void fangsu$toggleStop(int index) {
        final var stop = editingRoute.platformIds.get(index);
        final var status = RouteStopStatus.of(stop) == RouteStopStatus.NORMAL ? RouteStopStatus.DISABLED : RouteStopStatus.NORMAL;
        RouteStopStatus.set(stop, status);
        final var metadata = ServiceStop.get(stop);
        if (metadata != null && metadata.parentStop() != null) {
            ServiceStop.set(stop, metadata.override(metadata.platformOverride(), status, metadata.bypass()));
        }
        fangsu$saveStops(editingRoute, isNew);
    }

    @Unique
    private void fangsu$beginReplacement(int index) {
        final boolean cancel = fangsu$replaceIndex == index;
        fangsu$clearSelection();
        if (!cancel) {
            fangsu$replaceIndex = index;
            fangsu$replacedStop = editingRoute.platformIds.get(index);
            ((RouteMapSelection) (Object) widgetMap).fangsu$selectBypass(this::fangsu$chooseBypass);
        }
    }

    @Inject(method = "onClickAddPlatformToRoute", at = @At("HEAD"), cancellable = true)
    private void fangsu$selectPlatform(long platformId, CallbackInfo ci) {
        if (editingRoute == null) return;
        if (fangsu$replacedOnClick || editingRoutePlatformIndex >= 0) {
            ci.cancel();
        } else if (fangsu$replaceIndex >= 0) {
            ci.cancel();
            fangsu$replacedOnClick = fangsu$replaceStop(platformId, null);
            fangsu$invalidSelection = !fangsu$replacedOnClick && fangsu$replaceIndex >= 0;
        } else if (RouteServices.relation(editingRoute) != null) {
            ci.cancel();
        }
    }

    @Unique
    private boolean fangsu$chooseBypass(ServiceStop.Bypass bypass) {
        if (fangsu$replaceIndex < 0 || fangsu$replacedStop == null) return false;
        final boolean replaced = fangsu$replaceStop(RouteServices.platformId(fangsu$replacedStop), bypass);
        fangsu$invalidSelection = !replaced && fangsu$replaceIndex >= 0;
        return replaced;
    }

    @Unique
    private boolean fangsu$replaceStop(long id, ServiceStop.Bypass bypass) {
        if (!ClientData.hasPermission() || editingRoute == null) return false;
        final var entries = editingRoute.platformIds;
        final int index = fangsu$replaceIndex;
        if (index < 0 || index >= entries.size() || entries.get(index) != fangsu$replacedStop) {
            fangsu$clearSelection();
            return false;
        }
        final var stop = entries.get(index);
        final var metadata = ServiceStop.get(stop);
        final var relation = RouteServices.relation(editingRoute);
        final var target = ClientData.DATA_CACHE.platformIdMap.get(id);
        final var station = ClientData.DATA_CACHE.platformIdToStation.get(stop.platformId);
        final var area = ClientData.DATA_CACHE.platformIdToStation.get(id);
        if (target == null || target.transportMode != transportMode || relation != null && (station == null || station != area)
                || bypass != null && (relation == null || !relation.express() || metadata == null
                    || !RouteBypass.sameStation(station, bypass) || RouteBypass.settings(ClientData.RAILS, bypass) == null)) {
            return false;
        }
        final var replacement = new Route.RoutePlatform(id);
        replacement.customDestination = stop.customDestination;
        final var status = bypass != null ? RouteStopStatus.SKIPPED : RouteStopStatus.of(stop) == RouteStopStatus.SKIPPED
                ? metadata == null || metadata.statusOverride() == null ? RouteStopStatus.NORMAL : metadata.statusOverride()
                : RouteStopStatus.of(stop);
        RouteStopStatus.set(replacement, status);
        if (metadata != null) {
            ServiceStop.set(replacement, metadata.override(relation != null && (bypass == null || metadata.platformOverride()),
                    metadata.statusOverride(), bypass));
        }
        entries.set(index, replacement);
        fangsu$clearSelection();
        fangsu$saveStops(editingRoute, isNew);
        return true;
    }

    @Inject(method = "onDoneEditingRoute", at = @At(value = "INVOKE", target = "Lmtr/screen/DashboardScreen;stopEditing()V"))
    private void fangsu$saveDraftStops(CallbackInfo ci) {
        if (isNew) fangsu$saveStops(editingRoute, false);
    }

    @Unique
    private static void fangsu$saveStops(Route route, boolean draft) {
        if (!draft) {
            route.setPlatformIds(packet -> PacketTrainDataGuiClient.sendUpdate(IPacket.PACKET_UPDATE_ROUTE, packet));
        }
        DrawableRoute.clearCache();
    }
}
