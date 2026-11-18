package com.fangsu.mixin;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.RouteStopStatus;
import com.fangsu.mtr.RouteEditorAccess;
import com.fangsu.mtr.RouteServices;
import com.fangsu.mtr.ServiceStop;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ui.ConfigChoiceScreen;
import mtr.data.Route;
import mtr.screen.EditRouteScreen;
import mtr.screen.DashboardScreen;
import mtr.client.ClientData;
import net.minecraft.client.Minecraft;
import mtr.mappings.ScreenMapper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = EditRouteScreen.class, remap = false)
public abstract class EditRouteServiceMixin extends ScreenMapper {
    @Unique private Route fangsu$parentRoute;
    @Unique private DashboardScreen fangsu$dashboard;

    protected EditRouteServiceMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fangsu$captureRoute(Route route, DashboardScreen parent, CallbackInfo ci) {
        fangsu$parentRoute = route;
        fangsu$dashboard = parent;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void fangsu$addServiceActions(CallbackInfo ci) {
        if (fangsu$dashboard == null) {
            return;
        }
        final var relation = RouteServices.relation(fangsu$parentRoute);
        if (relation != null) {
            final var parent = ClientData.DATA_CACHE.routeIdMap.get(relation.parentRoute());
            if (!relation.express() && parent != null && parent.platformIds.size() >= 2) {

                final var section = mtr.mappings.UtilitiesClient.newButton(ComponentHelper.translatable("ui.fangsu.route.change_section"),
                        button -> fangsu$selectSection(parent, fangsu$parentRoute));
                mtr.client.IDrawing.setPositionAndWidth(section, 12, height - 22, 120);
                addDrawableChild(section);
            }
            return;
        }
        if (fangsu$parentRoute.platformIds.size() >= 2 && ClientData.ROUTES.stream().noneMatch(route -> {
            final var child = RouteServices.relation(route);
            return child != null && child.parentRoute() == fangsu$parentRoute.id;
        })) {
            final var importRoute = mtr.mappings.UtilitiesClient.newButton(ComponentHelper.translatable("ui.fangsu.route.import"),
                    button -> fangsu$chooseImportType());
            mtr.client.IDrawing.setPositionAndWidth(importRoute, width - 120, height - 22, 108);
            addDrawableChild(importRoute);
        }
        final String direction = TextUtil.getExtraParts(fangsu$parentRoute.name);
        if (direction.isEmpty() || direction.contains("||") || fangsu$parentRoute.platformIds.size() < 2) {
            return;
        }


        final var express = mtr.mappings.UtilitiesClient.newButton(ComponentHelper.translatable("ui.fangsu.route.express"),
                button -> fangsu$configure(false));
        final var shortTurn = mtr.mappings.UtilitiesClient.newButton(ComponentHelper.translatable("ui.fangsu.route.short_turn"),
                button -> fangsu$configure(true));
        mtr.client.IDrawing.setPositionAndWidth(express, 12, height - 22, 90);
        mtr.client.IDrawing.setPositionAndWidth(shortTurn, 106, height - 22, 90);
        addDrawableChild(express);
        addDrawableChild(shortTurn);
    }

    @Unique
    private void fangsu$chooseImportType() {
        final var client = Minecraft.getInstance();
        final var editor = client.screen;
        final var types = java.util.List.of(
                new ConfigChoiceScreen.Choice("express", ComponentHelper.translatable("ui.fangsu.route.import_express").getString()),
                new ConfigChoiceScreen.Choice("short_turn", ComponentHelper.translatable("ui.fangsu.route.import_short_turn").getString()));
        client.setScreen(new ConfigChoiceScreen(editor, "ui.fangsu.route.import", types,
                value -> fangsu$chooseImportParent("express".equals(value))));
    }

    @Unique
    private void fangsu$chooseImportParent(boolean express) {
        final var client = Minecraft.getInstance();
        final var typeScreen = client.screen;
        final var candidates = new java.util.LinkedHashMap<String, Route>();
        final var choices = new ArrayList<ConfigChoiceScreen.Choice>();
        for (final var route : ClientData.ROUTES) {
            if (route.id != fangsu$parentRoute.id && route.transportMode == fangsu$parentRoute.transportMode
                    && RouteServices.matchImport(route, fangsu$parentRoute, this::fangsu$stationForPlatform) != null) {
                final String id = Long.toUnsignedString(route.id);
                candidates.put(id, route);
                choices.add(new ConfigChoiceScreen.Choice(id, route.name.replace("||", " · ").replace('|', ' ')));
            }
        }
        choices.sort(java.util.Comparator.comparing(ConfigChoiceScreen.Choice::label, String.CASE_INSENSITIVE_ORDER));
        final String title = choices.isEmpty() ? "ui.fangsu.route.import_no_parent" : "ui.fangsu.route.import_parent";
        client.setScreen(new ConfigChoiceScreen(typeScreen, title, choices,
                id -> fangsu$importRoute(candidates.get(id), express)));
    }

    @Unique
    private long fangsu$stationForPlatform(long platformId) {
        final var station = ClientData.DATA_CACHE.platformIdToStation.get(platformId);
        return station == null ? 0 : station.id;
    }

    @Unique
    private void fangsu$importRoute(Route source, boolean express) {
        if (source == null || RouteServices.matchImport(source, fangsu$parentRoute, this::fangsu$stationForPlatform) == null) return;
        fangsu$saveParent();
        if (!RouteServices.importService(source, fangsu$parentRoute, express, this::fangsu$stationForPlatform)) return;
        DrawableRoute.clearCache();
        source.setPlatformIds(packet -> mtr.packet.PacketTrainDataGuiClient.sendUpdate(mtr.packet.IPacket.PACKET_UPDATE_ROUTE, packet));
        fangsu$parentRoute.setPlatformIds(packet -> mtr.packet.PacketTrainDataGuiClient.sendUpdate(mtr.packet.IPacket.PACKET_UPDATE_ROUTE, packet));
        fangsu$openDashboard(fangsu$parentRoute, false);
    }


    @Unique
    private void fangsu$configure(boolean shortTurn) {
        final String direction = TextUtil.getExtraParts(fangsu$parentRoute.name);
        final String base = TextUtil.getNonExtraParts(fangsu$parentRoute.name) + "||" + direction + "||";
        if (!shortTurn) {
            final String childName = base + direction + "_RAPID";
            for (final var route : ClientData.ROUTES) {
                final var relation = RouteServices.relation(route);
                if (relation != null && relation.express() && relation.parentRoute() == fangsu$parentRoute.id) {
                    fangsu$open(route, false);
                    return;
                }
            }
            fangsu$open(fangsu$copy(fangsu$parentRoute, childName, 0, fangsu$parentRoute.platformIds.size() - 1, true), true);
            return;
        }

        fangsu$selectSection(fangsu$parentRoute, null);
    }

    @Unique
    private void fangsu$selectSection(Route source, Route child) {
        final var client = Minecraft.getInstance();
        final var parent = client.screen;
        final var starts = new ArrayList<ConfigChoiceScreen.Choice>();
        for (int index = 0; index < source.platformIds.size() - 1; index++) {
            starts.add(new ConfigChoiceScreen.Choice(Integer.toString(index), (index + 1) + ". " + fangsu$stationName(source, index)));
        }
        client.setScreen(new ConfigChoiceScreen(parent, "ui.fangsu.route.first_station", starts, selected -> {
            final int start = Integer.parseInt(selected);
            final var ends = new ArrayList<ConfigChoiceScreen.Choice>();
            for (int index = start + 1; index < source.platformIds.size(); index++) {
                ends.add(new ConfigChoiceScreen.Choice(Integer.toString(index), (index + 1) + ". " + fangsu$stationName(source, index)));
            }
            client.setScreen(new ConfigChoiceScreen(parent, "ui.fangsu.route.last_station", ends, endValue -> {
                final int end = Integer.parseInt(endValue);
                if (child == null) {
                    final String direction = TextUtil.getExtraParts(source.name);
                    final String name = TextUtil.getNonExtraParts(source.name) + "||" + direction + "||" + direction + " "
                            + ComponentHelper.translatable("ui.fangsu.route.short_name").getString()
                            + " (" + fangsu$stationName(source, start) + " → " + fangsu$stationName(source, end) + ")";
                    fangsu$open(fangsu$copy(source, name, start, end, false), true);
                } else {
                    fangsu$updateSection(source, child, start, end);
                }
            }));
        }));
    }

    @Unique
    private void fangsu$updateSection(Route source, Route child, int start, int end) {
        fangsu$saveParent();
        final var previous = new java.util.HashMap<java.util.UUID, Route.RoutePlatform>();
        for (final var stop : child.platformIds) {
            final var metadata = ServiceStop.get(stop);
            if (metadata != null && metadata.parentStop() != null) {
                previous.put(metadata.parentStop(), stop);
            }
        }
        final var selection = fangsu$copy(source, child.name, start, end, false);
        child.platformIds.clear();
        for (final var stop : selection.platformIds) {
            child.platformIds.add(previous.getOrDefault(ServiceStop.get(stop).parentStop(), stop));
        }
        source.setPlatformIds(packet -> mtr.packet.PacketTrainDataGuiClient.sendUpdate(mtr.packet.IPacket.PACKET_UPDATE_ROUTE, packet));
        child.setPlatformIds(packet -> mtr.packet.PacketTrainDataGuiClient.sendUpdate(mtr.packet.IPacket.PACKET_UPDATE_ROUTE, packet));
        fangsu$openDashboard(child, false);
    }

    @Unique
    private String fangsu$stationName(Route source, int index) {
        final var stop = source.platformIds.get(index);
        final var station = ClientData.DATA_CACHE.platformIdToStation.get(stop.platformId);
        final var platform = ClientData.DATA_CACHE.platformIdMap.get(stop.platformId);
        return station == null ? (platform == null ? "?" : platform.name) : station.name.replace('|', ' ');
    }

    @Unique
    private Route fangsu$copy(Route source, String name, int start, int end, boolean express) {
        final var route = new Route(source.transportMode);
        route.name = name;
        route.color = source.color;
        route.routeType = source.routeType;
        route.isLightRailRoute = source.isLightRailRoute;
        route.lightRailRouteNumber = source.lightRailRouteNumber;
        for (int index = start; index <= end; index++) {
            final var stop = source.platformIds.get(index);
            final var entry = new Route.RoutePlatform(stop.platformId);
            entry.customDestination = stop.customDestination;
            RouteStopStatus.set(entry, RouteStopStatus.of(stop));
            ServiceStop.set(entry, ServiceStop.ensure(stop).child(source.id, express));
            route.platformIds.add(entry);
        }
        return route;
    }

    @Unique
    private void fangsu$open(Route child, boolean isNew) {
        fangsu$saveParent();
        if (isNew) {
            fangsu$parentRoute.setPlatformIds(packet -> mtr.packet.PacketTrainDataGuiClient.sendUpdate(mtr.packet.IPacket.PACKET_UPDATE_ROUTE, packet));
        }
        fangsu$openDashboard(child, isNew);
    }

    @Unique
    private void fangsu$openDashboard(Route child, boolean isNew) {
        final DashboardScreen dashboard = fangsu$dashboard;
        ((RouteEditorAccess) (Object) dashboard).fangsu$editService(child, isNew);
    }

    @org.spongepowered.asm.mixin.gen.Invoker("saveData")
    protected abstract void fangsu$saveParent();
}
