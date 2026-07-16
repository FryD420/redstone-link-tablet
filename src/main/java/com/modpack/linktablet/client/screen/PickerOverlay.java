package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The all-items search picker as a modal overlay INSIDE the app edit
 * screen — switching to a separate Screen would close the container
 * session, so this draws z-lifted over the editor and swallows input
 * while open (same pattern as the color popup, bigger). Picks return a
 * fresh stack via the callback; "use default" (icon mode) returns EMPTY.
 */
public class PickerOverlay {

    private static final int SLOT = 20;
    private static final int GRID_COLS = 9;
    private static final int PANEL_W = GRID_COLS * SLOT + 16;

    private final Font font;
    private final List<Item> allItems = new ArrayList<>();
    private List<Item> filtered;

    private EditBox searchBox;
    private Consumer<ItemStack> onPick;
    private boolean allowClear;
    private boolean open;
    private double scroll;

    // Panel rect, computed on open from the host screen's dimensions
    private int x, y, w, h;

    public PickerOverlay(Font font) {
        this.font = font;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            allItems.add(item);
        }
        filtered = allItems;
    }

    public boolean isOpen() {
        return open;
    }

    public void open(int screenWidth, int screenHeight, Consumer<ItemStack> onPick, boolean allowClear) {
        this.onPick = onPick;
        this.allowClear = allowClear;
        this.w = PANEL_W;
        this.h = Mth.clamp(screenHeight - 60, 140, 220);
        this.x = (screenWidth - w) / 2;
        this.y = (screenHeight - h) / 2;
        this.scroll = 0;
        this.searchBox = new EditBox(font, x + 8, y + 8, w - 16, 16,
                Component.translatable("gui.linktablet.picker.search"));
        this.searchBox.setHint(Component.translatable("gui.linktablet.picker.search"));
        this.searchBox.setResponder(this::applyFilter);
        this.searchBox.setFocused(true);
        applyFilter("");
        this.open = true;
    }

    public void close() {
        open = false;
        onPick = null;
        searchBox = null;
    }

    private void applyFilter(String query) {
        scroll = 0;
        if (query == null || query.isBlank()) {
            filtered = allItems;
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<Item> result = new ArrayList<>();
        for (Item item : allItems) {
            String name = item.getDescription().getString().toLowerCase(Locale.ROOT);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (name.contains(q) || id.contains(q)) {
                result.add(item);
            }
        }
        filtered = result;
    }

    private int gridLeft() {
        return x + 8;
    }

    private int gridTop() {
        return y + 30;
    }

    private int gridBottom() {
        return y + h - (allowClear ? 30 : 8);
    }

    private int clearButtonY() {
        return y + h - 24;
    }

    /** Draw the overlay (call after everything else; z-lifted). */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, ScreenTheme theme) {
        if (!open) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // Dim everything behind, frame the panel
        graphics.fill(0, 0, screenWidth, screenHeight, 0xB0000000);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, theme.switchOff);
        graphics.fill(x, y, x + w, y + h, theme.bodyOuter);

        searchBox.render(graphics, mouseX, mouseY, partialTick);

        graphics.enableScissor(gridLeft(), gridTop(), gridLeft() + GRID_COLS * SLOT, gridBottom());
        Item hoveredItem = null;
        for (int i = 0; i < filtered.size(); i++) {
            int sx = gridLeft() + (i % GRID_COLS) * SLOT;
            int sy = gridTop() + (i / GRID_COLS) * SLOT - (int) scroll;
            if (sy + SLOT < gridTop() || sy > gridBottom()) continue;

            boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT
                    && mouseY >= gridTop() && mouseY < gridBottom();
            if (hovered) {
                graphics.fill(sx, sy, sx + SLOT, sy + SLOT, 0x66FFFFFF);
                hoveredItem = filtered.get(i);
            }
            graphics.renderItem(new ItemStack(filtered.get(i)), sx + 2, sy + 2);
        }
        graphics.disableScissor();

        if (allowClear) {
            boolean hovered = overClearButton(mouseX, mouseY);
            graphics.fill(x + 8, clearButtonY(), x + w - 8, clearButtonY() + 18,
                    hovered ? theme.rowBgHover : theme.rowBg);
            Component label = Component.translatable("gui.linktablet.picker.clear_icon");
            graphics.drawString(font, label, x + w / 2 - font.width(label) / 2,
                    clearButtonY() + 5, theme.textPrimary, theme.textShadow);
        }

        if (hoveredItem != null) {
            graphics.renderTooltip(font, new ItemStack(hoveredItem), mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private boolean overClearButton(double mouseX, double mouseY) {
        return allowClear && mouseX >= x + 8 && mouseX < x + w - 8
                && mouseY >= clearButtonY() && mouseY < clearButtonY() + 18;
    }

    /** Returns true while open (the overlay swallows all clicks). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true;

        if (overClearButton(mouseX, mouseY)) {
            pick(ItemStack.EMPTY);
            return true;
        }
        if (button == 0 && mouseY >= gridTop() && mouseY < gridBottom()
                && mouseX >= gridLeft() && mouseX < gridLeft() + GRID_COLS * SLOT) {
            int col = (int) ((mouseX - gridLeft()) / SLOT);
            int row = (int) ((mouseY - gridTop() + scroll) / SLOT);
            if (col >= 0 && col < GRID_COLS && row >= 0) {
                int index = row * GRID_COLS + col;
                if (index < filtered.size()) {
                    pick(new ItemStack(filtered.get(index)));
                    return true;
                }
            }
        }
        // Click outside the panel closes it
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            close();
        }
        return true;
    }

    private void pick(ItemStack stack) {
        Consumer<ItemStack> callback = onPick;
        close();
        callback.accept(stack);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!open) return false;
        int rows = Mth.positiveCeilDiv(filtered.size(), GRID_COLS);
        int maxScroll = Math.max(0, rows * SLOT - (gridBottom() - gridTop()));
        scroll = Mth.clamp(scroll - scrollY * SLOT, 0, maxScroll);
        return true;
    }

    /** Returns true if the overlay consumed the key (it eats everything but ESC). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == 256) { // ESC closes just the overlay
            close();
            return true;
        }
        searchBox.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!open) return false;
        searchBox.charTyped(codePoint, modifiers);
        return true;
    }
}
