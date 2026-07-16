package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Add/edit screen for a single app: name, a list of two-item frequencies
 * (all toggled together — a "scene"), an optional custom icon item, and a
 * tile color.
 * <p>
 * Frequencies are staged in the two slot buttons and committed to the
 * list with the Add button (or automatically on Save, so the classic
 * single-frequency flow is unchanged). Committed frequencies show as
 * chips; clicking a chip removes it.
 */
public class AppEditScreen extends Screen {

    /** Preset tile colors (ARGB). */
    private static final int[] COLORS = {
            0xFF3A3F4B, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF80C71F, 0xFF5E7C16, 0xFF3AB3DA, 0xFF169C9C,
            0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA,
            0xFF835432, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21
    };

    private static final int PANEL_WIDTH = 260;
    private static final int RIGHT_COL = 160;

    // Frequency chips (committed pairs)
    private static final int CHIP_W = 36;
    private static final int CHIP_H = 20;
    private static final int CHIP_GAP = 4;
    private static final int CHIPS_PER_ROW = 4;
    private static final int CHIPS_Y = 118;

    // Right column: color button opens a 4x4 swatch popup ("dropdown")
    private static final int COLOR_BTN_Y = 86;
    private static final int POPUP_Y = COLOR_BTN_Y + 24;
    private static final int POPUP_SWATCH = 16;
    private static final int POPUP_STRIDE = 18;
    private static final int POPUP_SIZE = 3 * POPUP_STRIDE + POPUP_SWATCH; // 4x4 grid

    // Right column: momentary checkbox + strength slider
    private static final int MOMENTARY_Y = 118;
    private static final int STRENGTH_LABEL_Y = 142;
    private static final int TRACK_Y = 154;
    private static final int TRACK_W = 72;
    private static final int CHECKBOX_SIZE = 12;

    private final TabletScreen parent;
    private final int index; // -1 = new app

    private EditBox nameBox;
    private Item stagedItem1 = Items.AIR;
    private Item stagedItem2 = Items.AIR;
    private final List<Frequency> frequencies = new ArrayList<>();
    private Optional<Item> iconItem = Optional.empty();
    private int color = SignalApp.DEFAULT_COLOR;
    private boolean wasActive = false;
    private boolean momentary = false;
    private int strength = SignalApp.MAX_STRENGTH;
    private boolean draggingStrength = false;
    private boolean colorPopupOpen = false;

    private Button saveButton;
    private Button addFreqButton;

    /** Name applied to the (fresh) name box on first init — link prefill. */
    private String pendingName;

    /**
     * New app pre-filled from a Redstone Link's frequency. A full pair
     * lands in the staging slots (the classic flow — Save auto-commits
     * it); a half-set link commits its lone-item frequency directly,
     * since staging requires both slots.
     */
    public static AppEditScreen withLinkFrequency(TabletScreen parent, Item item1, Item item2, String name) {
        AppEditScreen screen = new AppEditScreen(parent, -1, null);
        if (item1 != Items.AIR && item2 != Items.AIR) {
            screen.stagedItem1 = item1;
            screen.stagedItem2 = item2;
        } else {
            screen.frequencies.add(Frequency.of(item1, item2));
        }
        screen.pendingName = name;
        return screen;
    }

    public AppEditScreen(TabletScreen parent, int index, SignalApp existing) {
        super(Component.translatable(index == -1
                ? "gui.linktablet.edit_app.title.new"
                : "gui.linktablet.edit_app.title.edit"));
        this.parent = parent;
        this.index = index;
        if (existing != null) {
            this.frequencies.addAll(existing.frequencies());
            this.iconItem = existing.icon().map(BuiltInRegistries.ITEM::get);
            this.color = existing.color();
            this.wasActive = existing.active();
            this.momentary = existing.momentary();
            this.strength = existing.strength();
        }
    }

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    @Override
    protected void init() {
        int left = panelLeft();

        String previousName = nameBox != null ? nameBox.getValue() : null;
        nameBox = new EditBox(font, left, 46, 150, 18,
                Component.translatable("gui.linktablet.edit_app.name"));
        nameBox.setMaxLength(SignalApp.MAX_NAME_LENGTH);
        if (previousName != null) {
            nameBox.setValue(previousName);
        } else if (index != -1) {
            // Pre-fill when editing
            var apps = currentApps();
            if (index < apps.size()) nameBox.setValue(apps.get(index).name());
        } else if (pendingName != null) {
            nameBox.setValue(pendingName);
            pendingName = null;
        }
        addRenderableWidget(nameBox);

        // Staging frequency slots (open the item picker)
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                        minecraft.setScreen(new ItemPickerScreen(this, item -> stagedItem1 = item, false)))
                .bounds(left, 88, 24, 24).build());
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                        minecraft.setScreen(new ItemPickerScreen(this, item -> stagedItem2 = item, false)))
                .bounds(left + 30, 88, 24, 24).build());

        // Commit the staged pair to the frequency list
        addFreqButton = Button.builder(
                        Component.translatable("gui.linktablet.edit_app.add_frequency"),
                        b -> commitStagedFrequency())
                .bounds(left + 60, 88, 40, 24).build();
        addRenderableWidget(addFreqButton);

        // Icon slot button (picker with a "use default" option)
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                        minecraft.setScreen(new ItemPickerScreen(this,
                                item -> iconItem = item == Items.AIR ? Optional.empty() : Optional.of(item),
                                true)))
                .bounds(left + RIGHT_COL, 46, 24, 24).build());

        // Save / Cancel / Remove
        saveButton = Button.builder(Component.translatable("gui.linktablet.edit_app.save"), b -> save())
                .bounds(left, height - 52, 125, 20).build();
        addRenderableWidget(saveButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.linktablet.edit_app.cancel"),
                        b -> minecraft.setScreen(parent))
                .bounds(left + 135, height - 52, 125, 20).build());

        if (index != -1) {
            addRenderableWidget(Button.builder(Component.translatable("gui.linktablet.edit_app.remove"), b -> {
                        UISounds.delete();
                        PacketDistributor.sendToServer(
                                new ModNetworking.RemoveAppPayload(parent.target(), index));
                        minecraft.setScreen(parent);
                    })
                    .bounds(left, height - 28, PANEL_WIDTH, 20).build());
        }
    }

    private java.util.List<SignalApp> currentApps() {
        return parent.view().apps();
    }

    private boolean stagedComplete() {
        return stagedItem1 != Items.AIR && stagedItem2 != Items.AIR;
    }

    private void commitStagedFrequency() {
        if (!stagedComplete() || frequencies.size() >= SignalApp.MAX_FREQUENCIES) return;
        Frequency freq = Frequency.of(stagedItem1, stagedItem2);
        if (!frequencies.contains(freq)) {
            frequencies.add(freq);
            UISounds.tick(1.6F);
        }
        stagedItem1 = Items.AIR;
        stagedItem2 = Items.AIR;
    }

    private void save() {
        // Classic flow: a completed staged pair counts without pressing Add
        commitStagedFrequency();
        if (frequencies.isEmpty()) return;
        String name = nameBox.getValue().isBlank() ? "App" : nameBox.getValue().strip();
        Optional<ResourceLocation> icon = iconItem.map(BuiltInRegistries.ITEM::getKey);
        SignalApp app = new SignalApp(name, List.copyOf(frequencies), wasActive, momentary, strength, color, icon);
        UISounds.confirm();
        PacketDistributor.sendToServer(
                new ModNetworking.UpsertAppPayload(parent.target(), index, app));
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        // Need at least one committed or fully staged frequency to save
        saveButton.active = !frequencies.isEmpty() || stagedComplete();
        addFreqButton.active = stagedComplete() && frequencies.size() < SignalApp.MAX_FREQUENCIES;
    }

    private int chipX(int i) {
        return panelLeft() + (i % CHIPS_PER_ROW) * (CHIP_W + CHIP_GAP);
    }

    private int chipY(int i) {
        return CHIPS_Y + (i / CHIPS_PER_ROW) * (CHIP_H + CHIP_GAP);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = panelLeft();
        int rightX = left + RIGHT_COL;

        // Open color popup swallows every click until it closes
        if (colorPopupOpen) {
            for (int i = 0; i < COLORS.length; i++) {
                int x = rightX + (i % 4) * POPUP_STRIDE;
                int y = POPUP_Y + (i / 4) * POPUP_STRIDE;
                if (mouseX >= x && mouseX < x + POPUP_SWATCH && mouseY >= y && mouseY < y + POPUP_SWATCH) {
                    color = COLORS[i];
                    UISounds.tick(1.4F);
                    break;
                }
            }
            colorPopupOpen = false;
            return true;
        }

        // Frequency chips: click to remove
        for (int i = 0; i < frequencies.size(); i++) {
            int x = chipX(i);
            int y = chipY(i);
            if (mouseX >= x && mouseX < x + CHIP_W && mouseY >= y && mouseY < y + CHIP_H) {
                frequencies.remove(i);
                UISounds.tick(1.1F);
                return true;
            }
        }

        // Color button: opens the swatch popup
        if (mouseX >= rightX && mouseX < rightX + 34
                && mouseY >= COLOR_BTN_Y && mouseY < COLOR_BTN_Y + 20) {
            colorPopupOpen = true;
            UISounds.tick(1.3F);
            return true;
        }

        // Momentary checkbox (box + label)
        if (mouseX >= rightX && mouseX < rightX + 90
                && mouseY >= MOMENTARY_Y - 2 && mouseY < MOMENTARY_Y + CHECKBOX_SIZE + 2) {
            momentary = !momentary;
            UISounds.tick(momentary ? 1.6F : 1.1F);
            return true;
        }

        // Strength slider
        if (mouseX >= rightX - 2 && mouseX < rightX + TRACK_W + 4
                && mouseY >= TRACK_Y - 6 && mouseY < TRACK_Y + 12) {
            draggingStrength = true;
            setStrengthFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingStrength) {
            setStrengthFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingStrength) {
            draggingStrength = false;
            UISounds.tick(1.0F + strength / 15.0F);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setStrengthFromMouse(double mouseX) {
        double rel = (mouseX - (panelLeft() + RIGHT_COL)) / TRACK_W;
        strength = net.minecraft.util.Mth.clamp((int) Math.round(1 + rel * (SignalApp.MAX_STRENGTH - 1)),
                1, SignalApp.MAX_STRENGTH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.modpack.linktablet.theme.ScreenTheme theme = parent.view().theme();
        boolean shadow = theme.textShadow;
        int left = panelLeft();

        // Centered manually: drawCenteredString always drops a shadow,
        // which smears dark text on light themes.
        graphics.drawString(font, title, width / 2 - font.width(title) / 2, 18,
                theme.textPrimary, shadow);

        // Left column labels
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.name"), left, 34, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.frequencies"), left, 76, theme.textMuted, shadow);

        // Right column labels
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.icon"), left + RIGHT_COL, 34, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.color"), left + RIGHT_COL, 76, theme.textMuted, shadow);

        // Staging slot accents (red/blue, like the Redstone Link's slots)
        drawSlotFrame(graphics, left, 88, 24, TabletScreen.FREQ1_COLOR);
        drawSlotFrame(graphics, left + 30, 88, 24, TabletScreen.FREQ2_COLOR);

        // Staging slot contents
        if (stagedItem1 != Items.AIR) graphics.renderItem(new ItemStack(stagedItem1), left + 4, 92);
        if (stagedItem2 != Items.AIR) graphics.renderItem(new ItemStack(stagedItem2), left + 34, 92);

        // Committed frequency chips
        boolean hoveredChip = false;
        for (int i = 0; i < frequencies.size(); i++) {
            Frequency freq = frequencies.get(i);
            int x = chipX(i);
            int y = chipY(i);
            boolean hovered = mouseX >= x && mouseX < x + CHIP_W && mouseY >= y && mouseY < y + CHIP_H;
            hoveredChip |= hovered;

            graphics.fill(x, y, x + CHIP_W, y + CHIP_H, hovered ? 0xFF5A3038 : theme.rowBg);
            graphics.renderItem(freq.icon1(), x + 2, y + 1);
            graphics.renderItem(freq.icon2(), x + 18, y + 1);
            // Red/blue pair markers along the chip's bottom edge
            graphics.fill(x + 2, y + CHIP_H - 2, x + CHIP_W / 2, y + CHIP_H, TabletScreen.FREQ1_COLOR);
            graphics.fill(x + CHIP_W / 2, y + CHIP_H - 2, x + CHIP_W - 2, y + CHIP_H, TabletScreen.FREQ2_COLOR);
        }
        if (frequencies.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.linktablet.edit_app.no_frequencies"),
                    left, CHIPS_Y + 6, theme.textFaint, shadow);
        }

        // Icon slot content (default = show first frequency's item dimmed)
        ItemStack defaultIcon = frequencies.isEmpty()
                ? (stagedItem1 != Items.AIR ? new ItemStack(stagedItem1) : ItemStack.EMPTY)
                : frequencies.getFirst().icon1();
        if (iconItem.isPresent()) {
            graphics.renderItem(new ItemStack(iconItem.get()), left + RIGHT_COL + 4, 50);
        } else if (!defaultIcon.isEmpty()) {
            graphics.renderItem(defaultIcon, left + RIGHT_COL + 4, 50);
            graphics.fill(left + RIGHT_COL + 2, 48, left + RIGHT_COL + 22, 68, 0x88000000);
        }

        int rightX = left + RIGHT_COL;

        // Color button (opens the swatch popup) with a small dropdown arrow
        graphics.fill(rightX - 1, COLOR_BTN_Y - 1, rightX + 21, COLOR_BTN_Y + 21, theme.switchOff);
        graphics.fill(rightX, COLOR_BTN_Y, rightX + 20, COLOR_BTN_Y + 20, color | 0xFF000000);
        int ax = rightX + 26;
        int ay = COLOR_BTN_Y + 8;
        graphics.fill(ax, ay, ax + 6, ay + 2, theme.textMuted);
        graphics.fill(ax + 1, ay + 2, ax + 5, ay + 4, theme.textMuted);
        graphics.fill(ax + 2, ay + 4, ax + 4, ay + 6, theme.textMuted);

        // Momentary checkbox
        graphics.fill(rightX, MOMENTARY_Y, rightX + CHECKBOX_SIZE, MOMENTARY_Y + CHECKBOX_SIZE, theme.switchOff);
        graphics.fill(rightX + 1, MOMENTARY_Y + 1, rightX + CHECKBOX_SIZE - 1, MOMENTARY_Y + CHECKBOX_SIZE - 1, theme.bodyInner);
        if (momentary) {
            graphics.fill(rightX + 3, MOMENTARY_Y + 3, rightX + CHECKBOX_SIZE - 3, MOMENTARY_Y + CHECKBOX_SIZE - 3, theme.accent);
        }
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.momentary"),
                rightX + CHECKBOX_SIZE + 4, MOMENTARY_Y + 2, theme.textMuted, shadow);

        // Strength slider
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.strength"),
                rightX, STRENGTH_LABEL_Y, theme.textMuted, shadow);
        graphics.fill(rightX, TRACK_Y, rightX + TRACK_W, TRACK_Y + 4, theme.switchOff);
        int handleX = rightX + (int) ((strength - 1) / (float) (SignalApp.MAX_STRENGTH - 1) * (TRACK_W - 4));
        graphics.fill(rightX, TRACK_Y, handleX + 2, TRACK_Y + 4, theme.accentDim);
        graphics.fill(handleX, TRACK_Y - 4, handleX + 4, TRACK_Y + 8, theme.textPrimary);
        graphics.drawString(font, String.valueOf(strength), rightX + TRACK_W + 8, TRACK_Y - 2,
                theme.textPrimary, shadow);

        // Color popup, z-lifted above the batched text/items so nothing
        // bleeds through it
        if (colorPopupOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.fill(rightX - 4, POPUP_Y - 4, rightX + POPUP_SIZE + 4, POPUP_Y + POPUP_SIZE + 4, theme.bodyOuter);
            for (int i = 0; i < COLORS.length; i++) {
                int x = rightX + (i % 4) * POPUP_STRIDE;
                int y = POPUP_Y + (i / 4) * POPUP_STRIDE;
                if (COLORS[i] == color) {
                    graphics.fill(x - 1, y - 1, x + POPUP_SWATCH + 1, y + POPUP_SWATCH + 1, 0xFFFFFFFF);
                }
                graphics.fill(x, y, x + POPUP_SWATCH, y + POPUP_SWATCH, COLORS[i]);
            }
            graphics.pose().popPose();
        }

        if (hoveredChip) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.linktablet.edit_app.chip_remove"), mouseX, mouseY);
        } else if (!colorPopupOpen && mouseX >= rightX && mouseX < rightX + 90
                && mouseY >= MOMENTARY_Y - 2 && mouseY < MOMENTARY_Y + CHECKBOX_SIZE + 2) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.linktablet.edit_app.momentary.tooltip"), mouseX, mouseY);
        }
    }

    /** 2px colored frame with a faint interior tint, drawn over a slot button. */
    private static void drawSlotFrame(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 2, color);                       // top
        graphics.fill(x, y + size - 2, x + size, y + size, color);         // bottom
        graphics.fill(x, y + 2, x + 2, y + size - 2, color);               // left
        graphics.fill(x + size - 2, y + 2, x + size, y + size - 2, color); // right
        graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, (color & 0x00FFFFFF) | 0x28000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
