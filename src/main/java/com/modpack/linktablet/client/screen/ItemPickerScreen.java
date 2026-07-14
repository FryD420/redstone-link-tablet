package com.modpack.linktablet.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
 * A searchable, scrollable grid of every registered item — no physical
 * items required. Clicking an item returns it to the calling screen via
 * the callback. When {@code allowClear} is true (icon picking), an extra
 * button lets the user reset to the default frequency-pair icon.
 */
public class ItemPickerScreen extends Screen {

    private static final int SLOT = 20;
    private static final int GRID_COLS = 9;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final boolean allowClear;

    private EditBox searchBox;
    private final List<Item> allItems = new ArrayList<>();
    private List<Item> filtered = new ArrayList<>();
    private double scroll = 0;

    public ItemPickerScreen(Screen parent, Consumer<Item> onPick, boolean allowClear) {
        super(Component.translatable("gui.linktablet.picker.search"));
        this.parent = parent;
        this.onPick = onPick;
        this.allowClear = allowClear;

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            allItems.add(item);
        }
        filtered = allItems;
    }

    private int gridLeft() {
        return (width - GRID_COLS * SLOT) / 2;
    }

    private int gridTop() {
        return 56;
    }

    private int gridBottom() {
        return height - (allowClear ? 40 : 16);
    }

    @Override
    protected void init() {
        String previous = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditBox(font, gridLeft(), 28, GRID_COLS * SLOT, 18,
                Component.translatable("gui.linktablet.picker.search"));
        searchBox.setHint(Component.translatable("gui.linktablet.picker.search"));
        searchBox.setValue(previous);
        searchBox.setResponder(this::applyFilter);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        if (allowClear) {
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.linktablet.picker.clear_icon"),
                            b -> {
                                onPick.accept(Items.AIR);
                                minecraft.setScreen(parent);
                            })
                    .bounds(gridLeft(), height - 32, GRID_COLS * SLOT, 20).build());
        }
        applyFilter(searchBox.getValue());
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.enableScissor(gridLeft(), gridTop(), gridLeft() + GRID_COLS * SLOT, gridBottom());
        Item hoveredItem = null;
        for (int i = 0; i < filtered.size(); i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridLeft() + col * SLOT;
            int y = gridTop() + row * SLOT - (int) scroll;
            if (y + SLOT < gridTop() || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT
                    && mouseY >= gridTop() && mouseY < gridBottom();
            if (hovered) {
                graphics.fill(x, y, x + SLOT, y + SLOT, 0x66FFFFFF);
                hoveredItem = filtered.get(i);
            }
            graphics.renderItem(new ItemStack(filtered.get(i)), x + 2, y + 2);
        }
        graphics.disableScissor();

        if (hoveredItem != null) {
            graphics.renderTooltip(font, hoveredItem.getDescription(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= gridTop() && mouseY < gridBottom()) {
            int col = (int) ((mouseX - gridLeft()) / SLOT);
            int row = (int) ((mouseY - gridTop() + scroll) / SLOT);
            if (col >= 0 && col < GRID_COLS && row >= 0) {
                int index = row * GRID_COLS + col;
                if (index < filtered.size()) {
                    onPick.accept(filtered.get(index));
                    minecraft.setScreen(parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = Mth.positiveCeilDiv(filtered.size(), GRID_COLS);
        int contentHeight = rows * SLOT;
        int visible = gridBottom() - gridTop();
        int maxScroll = Math.max(0, contentHeight - visible);
        scroll = Mth.clamp(scroll - scrollY * SLOT, 0, maxScroll);
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
