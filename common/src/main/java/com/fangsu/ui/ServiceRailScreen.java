package com.fangsu.ui;

import com.fangsu.items.ItemServiceRail;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.rail.ServiceRail;
import com.fangsu.network.ServiceRailPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** 连接工具和仪表盘共用的参数编辑页。 */
public final class ServiceRailScreen extends BasicConfigScreen {
    private final Screen parent;
    private final ServiceRail.Kind kind;
    private final Consumer<ServiceRail> save;
    private final boolean editNumber;
    private int value;
    private String number;
    private String seconds;

    private ServiceRailScreen(Screen parent, ServiceRail settings, boolean editNumber, Consumer<ServiceRail> save) {
        super(ComponentHelper.translatable("item.fangsu." + settings.kind().itemId));
        this.parent = parent;
        this.kind = settings.kind();
        this.save = save;
        this.editNumber = editNumber;
        this.value = settings.value();
        this.number = settings.number();
        this.seconds = Integer.toString(value);
    }

    public static void openTool(InteractionHand hand, ItemStack stack) {
        if (stack.getItem() instanceof ItemServiceRail tool) {
            final Minecraft client = Minecraft.getInstance();
            client.setScreen(new ServiceRailScreen(client.screen, tool.settings(stack), false,
                    settings -> ServiceRailPackets.updateTool(hand, settings)));
        }
    }

    public static void openTrack(ServiceRail settings, Consumer<ServiceRail> save) {
        final Minecraft client = Minecraft.getInstance();
        client.setScreen(new ServiceRailScreen(client.screen, settings, true, save));
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        final int x = layout.centerX - 70;
        final int y = layout.y;
        if (kind == ServiceRail.Kind.BYPASS) {
            addEntry(createTextLabel(layout.centerX, y, ComponentHelper.translatable("ui.fangsu.service_rail.speed"),
                    TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            final Button speedButton = addButton(x, y + 18, 140, 20, speedLabel(), button -> {
                final int next = (ServiceRail.SPEEDS.indexOf(value) + 1) % ServiceRail.SPEEDS.size();
                value = ServiceRail.SPEEDS.get(next);
                button.setMessage(speedLabel());
            });
            addEntry(speedButton, y + 18);
            if (editNumber) {
                addEntry(createTextLabel(layout.centerX, y + 48, ComponentHelper.translatable("ui.fangsu.service_rail.number"),
                        TextLabel.Align.CENTER, 0xFFFFFF, false), y + 48);
                addInput(x, y + 66, number, 10, false);
            }
        } else {
            addEntry(createTextLabel(layout.centerX, y, ComponentHelper.translatable("ui.fangsu.service_rail.seconds"),
                    TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            addInput(x, y + 18, seconds, 9, true);
        }
        validate();
    }

    private void addInput(int x, int y, String text, int maxLength, boolean numeric) {
        final EditBox input = new EditBox(font, x, y, 140, 20, title);
        input.setMaxLength(maxLength);
        if (numeric) {
            input.setFilter(value -> value.chars().allMatch(character -> character >= '0' && character <= '9'));
        }
        input.setValue(text);
        input.setResponder(value -> {
            if (numeric) {
                seconds = value;
            } else {
                number = value;
            }
            validate();
        });
        addRenderableWidget(input);
        addEntry(input, y);
    }

    private net.minecraft.network.chat.Component speedLabel() {
        return ComponentHelper.literal(value + " km/h");
    }

    private void validate() {
        closeButton.active = kind == ServiceRail.Kind.BYPASS ? !number.isBlank()
                : !seconds.isEmpty() && Integer.parseInt(seconds) > 0 && Integer.parseInt(seconds) <= ServiceRail.MAX_SECONDS;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    protected void buildFixedWidgets() {
        final var label = ComponentHelper.translatable("ui.fangsu.block.close_and_save");
        //#if MC_VERSION >= 11903
        closeButton = addFixedWidget(Button.builder(label, button -> saveAndClose())
                .bounds(width / 2 - 50, height - 40, 100, 20).build());
        //#else
        //$ closeButton = addFixedWidget(new Button(width / 2 - 50, height - 40, 100, 20, label, button -> saveAndClose()));
        //#endif
    }

    private void saveAndClose() {
        save.accept(new ServiceRail(kind, kind == ServiceRail.Kind.PARKING ? Integer.parseInt(seconds) : value, number.trim()));
        onClose();
    }
}
