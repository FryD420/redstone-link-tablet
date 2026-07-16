package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.menu.AppEditMenu;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Add/edit screen for a single app: name, a list of two-item frequencies
 * (all toggled together — a "scene"), an optional custom icon item, and a
 * tile color.
 * <p>
 * Backed by {@link AppEditMenu}, so the player inventory is real slots
 * with vanilla drag mechanics; the two staging frequency slots are GHOST
 * slots (dropping copies the item, count 1, nothing is consumed).
 * Committed frequencies show as chips; clicking a chip removes it. The
 * all-items search picker lives in a {@link PickerOverlay} (a separate
 * Screen would kill the container session).
 */
public class AppEditScreen extends AbstractContainerScreen<AppEditMenu> {

    /** Preset tile colors (ARGB). */
    private static final int[] COLORS = {
            0xFF3A3F4B, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF80C71F, 0xFF5E7C16, 0xFF3AB3DA, 0xFF169C9C,
            0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA,
            0xFF835432, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21
    };

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 230;
    private static final int RIGHT_COL = 168;

    // Frequency chips (committed pairs), image-local coords
    private static final int CHIP_W = 36;
    private static final int CHIP_H = 20;
    private static final int CHIP_GAP = 4;
    private static final int CHIPS_PER_ROW = 4;
    private static final int CHIPS_Y = 84;

    // Right column: color button opens a 4x4 swatch popup ("dropdown")
    private static final int COLOR_BTN_Y = 68;
    private static final int POPUP_Y = COLOR_BTN_Y + 24;
    private static final int POPUP_SWATCH = 16;
    private static final int POPUP_STRIDE = 18;
    private static final int POPUP_SIZE = 3 * POPUP_STRIDE + POPUP_SWATCH; // 4x4 grid

    // Right column: momentary checkbox + strength slider
    private static final int MOMENTARY_Y = 94;
    private static final int STRENGTH_LABEL_Y = 113;
    private static final int TRACK_Y = 124;
    private static final int TRACK_W = 72;
    private static final int CHECKBOX_SIZE = 12;

    // Right-of-inventory action buttons
    private static final int BTN_X = 176;
    private static final int BTN_W = 76;

    private final int index; // -1 = new app

    private EditBox nameBox;
    private PickerOverlay picker;
    private final List<Frequency> frequencies = new ArrayList<>();
    private Optional<net.minecraft.world.item.Item> iconItem = Optional.empty();
    private int color = SignalApp.DEFAULT_COLOR;
    private boolean wasActive = false;
    private boolean momentary = false;
    private boolean slider = false;
    private int strength = SignalApp.MAX_STRENGTH;
    private boolean draggingStrength = false;
    private boolean colorPopupOpen = false;

    private Button saveButton;
    private Button addFreqButton;

    public AppEditScreen(AppEditMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.index = menu.contentHolder.index();
        List<SignalApp> apps = view().apps();
        if (index >= 0 && index < apps.size()) {
            SignalApp existing = apps.get(index);
            this.frequencies.addAll(existing.frequencies());
            this.iconItem = existing.icon().map(BuiltInRegistries.ITEM::get);
            this.color = existing.color();
            this.wasActive = existing.active();
            this.momentary = existing.momentary();
            this.slider = existing.slider();
            this.strength = existing.strength();
        } else if (menu.contentHolder.prefill1().isEmpty() != menu.contentHolder.prefill2().isEmpty()) {
            // Half-set link prefill: commit the lone-item frequency directly
            this.frequencies.add(Frequency.of(menu.contentHolder.prefill1(), menu.contentHolder.prefill2()));
        }
    }

    private AppView view() {
        ModNetworking.AppTarget target = menu.contentHolder.target();
        return target.pos().isPresent()
                ? new AppView.Block(target.pos().get())
                : new AppView.Hand(target.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    private ScreenTheme theme() {
        return view().theme();
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos;
        int top = topPos;
        picker = new PickerOverlay(font);

        String previousName = nameBox != null ? nameBox.getValue() : null;
        nameBox = new EditBox(font, left + 8, top + 26, 150, 18,
                Component.translatable("gui.linktablet.edit_app.name"));
        nameBox.setMaxLength(SignalApp.MAX_NAME_LENGTH);
        if (previousName != null) {
            nameBox.setValue(previousName);
        } else if (index != -1) {
            List<SignalApp> apps = view().apps();
            if (index < apps.size()) nameBox.setValue(apps.get(index).name());
        } else if (!menu.contentHolder.prefillName().isEmpty()) {
            nameBox.setValue(menu.contentHolder.prefillName());
        }
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        // Commit the staged ghost pair to the frequency list
        addFreqButton = Button.builder(
                        Component.translatable("gui.linktablet.edit_app.add_frequency"),
                        b -> commitStagedFrequency())
                .bounds(left + 60, top + 58, 40, 20).build();
        addRenderableWidget(addFreqButton);

        // All-items search (for frequency items you don't carry)
        Button searchButton = Button.builder(Component.literal("..."), b ->
                        picker.open(width, height, this::stageFromPicker, false))
                .bounds(left + 104, top + 58, 24, 20).build();
        searchButton.setTooltip(Tooltip.create(Component.translatable("gui.linktablet.picker.search")));
        addRenderableWidget(searchButton);

        // Icon slot button (picker with a "use default" option)
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                        picker.open(width, height, stack ->
                                        iconItem = stack.isEmpty() ? Optional.empty() : Optional.of(stack.getItem()),
                                true))
                .bounds(left + RIGHT_COL, top + 26, 24, 24).build());

        // Save / Cancel / Remove, right of the inventory block
        saveButton = Button.builder(Component.translatable("gui.linktablet.edit_app.save"), b -> save())
                .bounds(left + BTN_X, top + 146, BTN_W, 20).build();
        addRenderableWidget(saveButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.linktablet.edit_app.cancel"),
                        b -> onClose())
                .bounds(left + BTN_X, top + 170, BTN_W, 20).build());

        if (index != -1) {
            addRenderableWidget(Button.builder(Component.translatable("gui.linktablet.edit_app.remove"), b -> {
                        UISounds.delete();
                        PacketDistributor.sendToServer(
                                new ModNetworking.RemoveAppPayload(menu.contentHolder.target(), index));
                        onClose();
                    })
                    .bounds(left + BTN_X, top + 194, BTN_W, 20).build());
        }
    }

    // ---- Staging (ghost slots) -----------------------------------------

    private ItemStack staged(int slot) {
        return menu.ghostInventory.getStackInSlot(slot);
    }

    /** Sets a ghost slot on both sides (Create's packet targets slot 36+N). */
    private void setStaged(int slot, ItemStack stack) {
        menu.ghostInventory.setStackInSlot(slot, stack);
        PacketDistributor.sendToServer(new GhostItemSubmitPacket(stack, slot));
    }

    /** Picker picks land in the first empty ghost slot (else slot 1). */
    private void stageFromPicker(ItemStack stack) {
        if (stack.isEmpty()) return;
        int slot = staged(0).isEmpty() ? 0 : staged(1).isEmpty() ? 1 : 0;
        setStaged(slot, stack.copyWithCount(1));
        UISounds.tick(1.3F);
    }

    private boolean stagedComplete() {
        return !staged(0).isEmpty() && !staged(1).isEmpty();
    }

    private void commitStagedFrequency() {
        if (!stagedComplete() || frequencies.size() >= SignalApp.MAX_FREQUENCIES) return;
        Frequency freq = Frequency.of(staged(0), staged(1));
        if (!frequencies.contains(freq)) {
            frequencies.add(freq);
            UISounds.tick(1.6F);
        }
        setStaged(0, ItemStack.EMPTY);
        setStaged(1, ItemStack.EMPTY);
    }

    private void save() {
        // Classic flow: a completed staged pair counts without pressing Add
        commitStagedFrequency();
        if (frequencies.isEmpty()) return;
        String name = nameBox.getValue().isBlank() ? "App" : nameBox.getValue().strip();
        Optional<ResourceLocation> icon = iconItem.map(BuiltInRegistries.ITEM::getKey);
        SignalApp app = new SignalApp(name, List.copyOf(frequencies), wasActive, momentary, strength, color, icon, slider);
        UISounds.confirm();
        PacketDistributor.sendToServer(
                new ModNetworking.UpsertAppPayload(menu.contentHolder.target(), index, app));
        onClose();
    }

    /** Closing the container returns to the tablet home screen. */
    @Override
    public void onClose() {
        super.onClose();
        minecraft.setScreen(new TabletScreen(view()));
    }

    @Override
    protected void containerTick() {
        // Need at least one committed or fully staged frequency to save
        saveButton.active = !frequencies.isEmpty() || stagedComplete();
        addFreqButton.active = stagedComplete() && frequencies.size() < SignalApp.MAX_FREQUENCIES;
    }

    private int chipX(int i) {
        return leftPos + 8 + (i % CHIPS_PER_ROW) * (CHIP_W + CHIP_GAP);
    }

    private int chipY(int i) {
        return topPos + CHIPS_Y + (i / CHIPS_PER_ROW) * (CHIP_H + CHIP_GAP);
    }

    // ---- Input ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker.mouseClicked(mouseX, mouseY, button)) return true;
        int rightX = leftPos + RIGHT_COL;

        // Open color popup swallows every click until it closes
        if (colorPopupOpen) {
            for (int i = 0; i < COLORS.length; i++) {
                int x = rightX + (i % 4) * POPUP_STRIDE;
                int y = topPos + POPUP_Y + (i / 4) * POPUP_STRIDE;
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
                && mouseY >= topPos + COLOR_BTN_Y && mouseY < topPos + COLOR_BTN_Y + 20) {
            colorPopupOpen = true;
            UISounds.tick(1.3F);
            return true;
        }

        // App type row: click cycles Toggle → Hold → Slider
        if (mouseX >= rightX && mouseX < rightX + 90
                && mouseY >= topPos + MOMENTARY_Y - 2 && mouseY < topPos + MOMENTARY_Y + CHECKBOX_SIZE + 2) {
            if (!momentary && !slider) {
                momentary = true;
            } else if (momentary) {
                momentary = false;
                slider = true;
                if (strength < 0) strength = 0;
            } else {
                slider = false;
                if (strength < 1) strength = 1;
            }
            UISounds.tick(1.4F);
            return true;
        }

        // Strength slider
        if (mouseX >= rightX - 2 && mouseX < rightX + TRACK_W + 4
                && mouseY >= topPos + TRACK_Y - 6 && mouseY < topPos + TRACK_Y + 12) {
            draggingStrength = true;
            setStrengthFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (picker.isOpen()) return true;
        if (draggingStrength) {
            setStrengthFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (picker.isOpen()) return true;
        if (draggingStrength) {
            draggingStrength = false;
            UISounds.tick(1.0F + strength / 15.0F);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picker.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    /** Sliders may rest at 0 (off); the other types keep the 1..15 range. */
    private int minStrength() {
        return slider ? 0 : 1;
    }

    private void setStrengthFromMouse(double mouseX) {
        int min = minStrength();
        double rel = (mouseX - (leftPos + RIGHT_COL)) / TRACK_W;
        strength = net.minecraft.util.Mth.clamp((int) Math.round(min + rel * (SignalApp.MAX_STRENGTH - min)),
                min, SignalApp.MAX_STRENGTH);
    }

    // ---- Rendering -------------------------------------------------------

    /** Slot cells + the red/blue staging frames, under the slot items. */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ScreenTheme theme = theme();
        for (Slot slot : menu.slots) {
            drawSlotCell(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, theme);
        }
        // Staging slot accents (red/blue, like the Redstone Link's slots)
        drawGhostFrame(graphics, leftPos + AppEditMenu.GHOST1_X - 2, topPos + AppEditMenu.GHOST_Y - 2,
                TabletScreen.FREQ1_COLOR);
        drawGhostFrame(graphics, leftPos + AppEditMenu.GHOST2_X - 2, topPos + AppEditMenu.GHOST_Y - 2,
                TabletScreen.FREQ2_COLOR);
    }

    /**
     * One 18px slot cell in the vanilla chest style: dark edge top+left,
     * light edge bottom+right, mid interior — theme-tinted so every
     * palette keeps the familiar inset grid.
     */
    private static void drawSlotCell(GuiGraphics graphics, int x, int y, ScreenTheme theme) {
        int mid = theme.rowBg;
        int dark = scale(mid, 50);
        int light = towardWhite(mid, 35);
        graphics.fill(x, y, x + 17, y + 1, dark);            // top
        graphics.fill(x, y + 1, x + 1, y + 17, dark);        // left
        graphics.fill(x + 17, y + 1, x + 18, y + 18, light); // right
        graphics.fill(x + 1, y + 17, x + 17, y + 18, light); // bottom
        graphics.fill(x + 1, y + 1, x + 17, y + 17, mid);    // interior
        graphics.fill(x + 17, y, x + 18, y + 1, mid);        // corners
        graphics.fill(x, y + 17, x + 1, y + 18, mid);
    }

    /** Scale RGB channels to the given percentage. */
    private static int scale(int argb, int pct) {
        int r = ((argb >> 16) & 0xFF) * pct / 100;
        int g = ((argb >> 8) & 0xFF) * pct / 100;
        int b = (argb & 0xFF) * pct / 100;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Mix RGB channels the given percentage toward white. */
    private static int towardWhite(int argb, int pct) {
        int r = ((argb >> 16) & 0xFF);
        int g = ((argb >> 8) & 0xFF);
        int b = (argb & 0xFF);
        r += (255 - r) * pct / 100;
        g += (255 - g) * pct / 100;
        b += (255 - b) * pct / 100;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Slim 1px red/blue ring hugging a staging slot cell, faint tint inside. */
    private static void drawGhostFrame(GuiGraphics graphics, int x, int y, int color) {
        int size = 20;
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x, y + 1, x + 1, y + size - 1, color);
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, (color & 0x00FFFFFF) | 0x18000000);
    }

    /** All static text, in image-local coordinates (labels sit a uniform 11px above their control). */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenTheme theme = theme();
        boolean shadow = theme.textShadow;
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 5,
                theme.textPrimary, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.name"), 8, 15, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.frequencies"), 8, 48, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.icon"), RIGHT_COL, 15, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.color"), RIGHT_COL, 57, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.picker.inventory"), 8, 134, theme.textMuted, shadow);
        if (frequencies.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.linktablet.edit_app.no_frequencies"),
                    8, CHIPS_Y + 6, theme.textFaint, shadow);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        boolean shadow = theme.textShadow;
        int left = leftPos;
        int top = topPos;
        int rightX = left + RIGHT_COL;

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

        // Icon slot content (default = show first frequency's item dimmed)
        ItemStack defaultIcon = frequencies.isEmpty() ? staged(0) : frequencies.getFirst().icon1();
        if (iconItem.isPresent()) {
            graphics.renderItem(new ItemStack(iconItem.get()), rightX + 4, top + 30);
        } else if (!defaultIcon.isEmpty()) {
            graphics.renderItem(defaultIcon, rightX + 4, top + 30);
            graphics.fill(rightX + 2, top + 28, rightX + 22, top + 48, 0x88000000);
        }

        // Color button (opens the swatch popup) with a small dropdown arrow
        int colorY = top + COLOR_BTN_Y;
        graphics.fill(rightX - 1, colorY - 1, rightX + 21, colorY + 21, theme.switchOff);
        graphics.fill(rightX, colorY, rightX + 20, colorY + 20, color | 0xFF000000);
        int ax = rightX + 26;
        int ay = colorY + 8;
        graphics.fill(ax, ay, ax + 6, ay + 2, theme.textMuted);
        graphics.fill(ax + 1, ay + 2, ax + 5, ay + 4, theme.textMuted);
        graphics.fill(ax + 2, ay + 4, ax + 4, ay + 6, theme.textMuted);

        // App type row (click cycles): small accent square + current type
        int momY = top + MOMENTARY_Y;
        graphics.fill(rightX, momY, rightX + CHECKBOX_SIZE, momY + CHECKBOX_SIZE, theme.switchOff);
        graphics.fill(rightX + 1, momY + 1, rightX + CHECKBOX_SIZE - 1, momY + CHECKBOX_SIZE - 1, theme.bodyInner);
        if (momentary || slider) {
            graphics.fill(rightX + 3, momY + 3, rightX + CHECKBOX_SIZE - 3, momY + CHECKBOX_SIZE - 3, theme.accent);
        }
        String typeKey = slider ? "gui.linktablet.edit_app.type.slider"
                : momentary ? "gui.linktablet.edit_app.type.momentary"
                : "gui.linktablet.edit_app.type.toggle";
        graphics.drawString(font, Component.translatable("gui.linktablet.edit_app.type", Component.translatable(typeKey)),
                rightX + CHECKBOX_SIZE + 4, momY + 2, theme.textMuted, shadow);

        // Strength slider ("initial value" for slider apps, which allow 0)
        int trackY = top + TRACK_Y;
        int min = minStrength();
        graphics.drawString(font, Component.translatable(slider
                        ? "gui.linktablet.edit_app.initial_value"
                        : "gui.linktablet.edit_app.strength"),
                rightX, top + STRENGTH_LABEL_Y, theme.textMuted, shadow);
        graphics.fill(rightX, trackY, rightX + TRACK_W, trackY + 4, theme.switchOff);
        int handleX = rightX + (int) ((strength - min) / (float) (SignalApp.MAX_STRENGTH - min) * (TRACK_W - 4));
        graphics.fill(rightX, trackY, handleX + 2, trackY + 4, theme.accentDim);
        graphics.fill(handleX, trackY - 4, handleX + 4, trackY + 8, theme.textPrimary);
        graphics.drawString(font, String.valueOf(strength), rightX + TRACK_W + 8, trackY - 2,
                theme.textPrimary, shadow);

        // Color popup, z-lifted above the batched text/items so nothing
        // bleeds through it
        if (colorPopupOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.fill(rightX - 4, top + POPUP_Y - 4, rightX + POPUP_SIZE + 4, top + POPUP_Y + POPUP_SIZE + 4, theme.bodyOuter);
            for (int i = 0; i < COLORS.length; i++) {
                int x = rightX + (i % 4) * POPUP_STRIDE;
                int y = top + POPUP_Y + (i / 4) * POPUP_STRIDE;
                if (COLORS[i] == color) {
                    graphics.fill(x - 1, y - 1, x + POPUP_SWATCH + 1, y + POPUP_SWATCH + 1, 0xFFFFFFFF);
                }
                graphics.fill(x, y, x + POPUP_SWATCH, y + POPUP_SWATCH, COLORS[i]);
            }
            graphics.pose().popPose();
        }

        if (!picker.isOpen() && !colorPopupOpen) {
            if (hoveredChip) {
                graphics.renderTooltip(font,
                        Component.translatable("gui.linktablet.edit_app.chip_remove"), mouseX, mouseY);
            } else if (mouseX >= rightX && mouseX < rightX + 90
                    && mouseY >= momY - 2 && mouseY < momY + CHECKBOX_SIZE + 2) {
                graphics.renderTooltip(font, Component.translatable(slider
                        ? "gui.linktablet.edit_app.type.slider.tooltip"
                        : momentary
                        ? "gui.linktablet.edit_app.momentary.tooltip"
                        : "gui.linktablet.edit_app.type.toggle.tooltip"), mouseX, mouseY);
            } else {
                // Hovered inventory/ghost slot tooltip (vanilla)
                renderTooltip(graphics, mouseX, mouseY);
            }
        }

        picker.render(graphics, mouseX, mouseY, partialTick, width, height, theme);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
