package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The tablet "home screen". Two selectable layouts (persisted client-side
 * via {@link ClientPrefs}):
 * <ul>
 *   <li><b>Grid</b> — colored app tiles with icons</li>
 *   <li><b>List</b> — one row per app with a toggle switch</li>
 * </ul>
 * In both modes:
 * <ul>
 *   <li>Left-click an app → toggle it on/off</li>
 *   <li>Right-click an app → edit it</li>
 *   <li>Click the add tile/row → add a new app</li>
 * </ul>
 * The tablet body sizes itself to its content (scrolling once it would
 * exceed the screen).
 */
public class TabletScreen extends Screen {

    // Grid layout
    static final int TILE_SIZE = 44;
    static final int TILE_GAP = 8;
    static final int MAX_COLUMNS = 4;

    /** Grid row height: tile + name label + gap. */
    static final int ROW_STRIDE = TILE_SIZE + TILE_GAP + 12;

    // List layout
    static final int LIST_WIDTH = 200;
    static final int ROW_HEIGHT = 24;
    static final int ROW_GAP = 4;
    static final int LIST_STRIDE = ROW_HEIGHT + ROW_GAP;

    /** Toggle switch dimensions (list mode). */
    private static final int SWITCH_W = 22;
    private static final int SWITCH_H = 12;

    /** Header space inside the body (title + view-mode buttons) and bottom padding. */
    private static final int HEADER = 34;
    private static final int BOTTOM_PAD = 8;

    /** Minimum body width so the title and view-mode buttons always fit. */
    private static final int MIN_PANEL_WIDTH = 148;

    /** Size of the grid/list view-mode buttons in the header. */
    private static final int MODE_BTN_SIZE = 12;

    /**
     * Frequency accents matching Create's Redstone Link:
     * first frequency red, second blue.
     */
    static final int FREQ1_COLOR = 0xFFC93C36;
    static final int FREQ2_COLOR = 0xFF3E52C1;

    private final InteractionHand hand;
    private double scroll = 0;

    public TabletScreen(InteractionHand hand) {
        super(Component.translatable("gui.linktablet.tablet.title"));
        this.hand = hand;
    }

    public InteractionHand hand() {
        return hand;
    }

    private List<SignalApp> apps() {
        if (minecraft == null || minecraft.player == null) return List.of();
        ItemStack stack = minecraft.player.getItemInHand(hand);
        return stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of());
    }

    private boolean listView() {
        return ClientPrefs.listView();
    }

    // ------------------------------------------------------------------
    // Dynamic layout
    // ------------------------------------------------------------------

    private int totalTiles() {
        return apps().size() + 1; // + "add" tile/row
    }

    private int columns() {
        return Mth.clamp(totalTiles(), 1, MAX_COLUMNS);
    }

    private int rows() {
        return Mth.positiveCeilDiv(totalTiles(), columns());
    }

    /** Width the grid tiles actually need (may be less than the body width). */
    private int gridNaturalWidth() {
        return columns() * TILE_SIZE + (columns() + 1) * TILE_GAP;
    }

    private int panelWidth() {
        int w = listView() ? LIST_WIDTH : gridNaturalWidth();
        return Math.max(w, MIN_PANEL_WIDTH);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    /** Left edge the grid tiles are laid out from (grid centered in the body). */
    private int gridLeft() {
        return panelLeft() + (panelWidth() - gridNaturalWidth()) / 2;
    }

    /** Height of the scrollable content (last row has no trailing gap). */
    private int contentHeight() {
        return listView()
                ? totalTiles() * LIST_STRIDE - ROW_GAP
                : rows() * ROW_STRIDE - TILE_GAP;
    }

    private int bodyHeight() {
        return Math.min(HEADER + contentHeight() + BOTTOM_PAD, height - 28);
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int gridTop() {
        return bodyTop() + HEADER;
    }

    private int gridBottom() {
        return bodyTop() + bodyHeight() - BOTTOM_PAD;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (gridBottom() - gridTop()));
    }

    // View-mode header buttons (grid | list), top-right of the body
    private int listBtnX() {
        return panelLeft() + panelWidth() - MODE_BTN_SIZE - 2;
    }

    private int gridBtnX() {
        return listBtnX() - MODE_BTN_SIZE - 4;
    }

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    private boolean overModeBtn(double mouseX, double mouseY, int btnX) {
        return mouseX >= btnX && mouseX < btnX + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        scroll = Mth.clamp(scroll, 0, maxScroll());

        int left = panelLeft();
        int pw = panelWidth();
        int top = bodyTop();
        int bottom = top + bodyHeight();

        // Tablet body
        graphics.fill(left - 6, top - 2, left + pw + 6, bottom + 2, 0xFF16181D);
        graphics.fill(left - 4, top, left + pw + 4, bottom, 0xFF23262E);

        graphics.drawCenteredString(font, title, width / 2, top + 12, 0xFFFFFF);
        renderModeButtons(graphics, mouseX, mouseY);

        List<SignalApp> apps = apps();

        graphics.enableScissor(left - 4, gridTop() - 2, left + pw + 4, gridBottom());
        if (listView()) {
            renderListContent(graphics, apps, mouseX, mouseY);
        } else {
            renderGridContent(graphics, apps, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (apps.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.linktablet.no_apps"),
                    width / 2, bottom + 8, 0x9AA0AC);
        }

        // Tooltips last, on top of everything
        if (overModeBtn(mouseX, mouseY, gridBtnX())) {
            graphics.renderTooltip(font, Component.translatable("gui.linktablet.view.grid"), mouseX, mouseY);
        } else if (overModeBtn(mouseX, mouseY, listBtnX())) {
            graphics.renderTooltip(font, Component.translatable("gui.linktablet.view.list"), mouseX, mouseY);
        }
    }

    private void renderModeButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = modeBtnY();
        boolean list = listView();

        // Grid glyph: four small squares
        int gx = gridBtnX();
        int gridColor = glyphColor(!list, overModeBtn(mouseX, mouseY, gx));
        graphics.fill(gx + 1, y + 1, gx + 5, y + 5, gridColor);
        graphics.fill(gx + 7, y + 1, gx + 11, y + 5, gridColor);
        graphics.fill(gx + 1, y + 7, gx + 5, y + 11, gridColor);
        graphics.fill(gx + 7, y + 7, gx + 11, y + 11, gridColor);

        // List glyph: three bars
        int lx = listBtnX();
        int listColor = glyphColor(list, overModeBtn(mouseX, mouseY, lx));
        graphics.fill(lx + 1, y + 2, lx + 11, y + 4, listColor);
        graphics.fill(lx + 1, y + 5, lx + 11, y + 7, listColor);
        graphics.fill(lx + 1, y + 8, lx + 11, y + 10, listColor);
    }

    private static int glyphColor(boolean active, boolean hovered) {
        if (active) return 0xFF4ADE80;
        return hovered ? 0xFFB0B6C2 : 0xFF565D6B;
    }

    // ---- Grid mode ----------------------------------------------------

    private void renderGridContent(GuiGraphics graphics, List<SignalApp> apps, int mouseX, int mouseY) {
        int total = totalTiles();
        int base = gridLeft();
        for (int i = 0; i < total; i++) {
            int col = i % columns();
            int row = i / columns();
            int x = base + TILE_GAP + col * (TILE_SIZE + TILE_GAP);
            int y = gridTop() + row * ROW_STRIDE - (int) scroll;
            if (y + TILE_SIZE < gridTop() - 2 || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE
                    && mouseY >= y && mouseY < y + TILE_SIZE
                    && mouseY >= gridTop() - 2 && mouseY <= gridBottom();

            if (i < apps.size()) {
                renderAppTile(graphics, apps.get(i), x, y, hovered);
            } else {
                renderAddTile(graphics, x, y, hovered);
            }
        }
    }

    private void renderAppTile(GuiGraphics graphics, SignalApp app, int x, int y, boolean hovered) {
        // Active glow border
        if (app.active()) {
            graphics.fill(x - 2, y - 2, x + TILE_SIZE + 2, y + TILE_SIZE + 2, 0xFF4ADE80);
        } else if (hovered) {
            graphics.fill(x - 1, y - 1, x + TILE_SIZE + 1, y + TILE_SIZE + 1, 0xFF5A6070);
        }

        int color = app.color() | 0xFF000000;
        graphics.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, color);
        if (hovered) {
            graphics.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, 0x22FFFFFF);
        }

        // Icon: custom item, or the first frequency pair drawn overlapping
        if (app.hasCustomIcon()) {
            graphics.renderItem(app.iconStack(), x + (TILE_SIZE - 16) / 2, y + (TILE_SIZE - 16) / 2 - 2);
        } else {
            int cx = x + TILE_SIZE / 2;
            int iy = y + (TILE_SIZE - 16) / 2 - 2;
            graphics.renderItem(app.primaryFrequency().icon1(), cx - 14, iy);
            graphics.renderItem(app.primaryFrequency().icon2(), cx - 2, iy);
            drawFreqPairMarkers(graphics, cx, iy);
        }

        // ON/OFF pip
        int pipColor = app.active() ? 0xFF4ADE80 : 0xFF444955;
        graphics.fill(x + TILE_SIZE - 8, y + 4, x + TILE_SIZE - 4, y + 8, pipColor);

        // Frequency count badge for scene apps
        if (app.frequencies().size() > 1) {
            graphics.drawString(font, "x" + app.frequencies().size(), x + 3, y + 3, 0xFFE2E5EB, true);
        }

        // Name (clipped to tile width)
        String name = font.plainSubstrByWidth(app.name(), TILE_SIZE + TILE_GAP - 2);
        graphics.drawCenteredString(font, name, x + TILE_SIZE / 2, y + TILE_SIZE + 3, 0xFFE2E5EB);
    }

    /**
     * Red/blue bars under the default frequency-pair icon, matching the
     * Redstone Link's slot colors (first item red, second blue).
     */
    static void drawFreqPairMarkers(GuiGraphics graphics, int centerX, int iconY) {
        graphics.fill(centerX - 14, iconY + 17, centerX, iconY + 19, FREQ1_COLOR);
        graphics.fill(centerX, iconY + 17, centerX + 14, iconY + 19, FREQ2_COLOR);
    }

    private void renderAddTile(GuiGraphics graphics, int x, int y, boolean hovered) {
        int bg = hovered ? 0xFF3A3F4B : 0xFF2C303A;
        graphics.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, bg);
        graphics.drawCenteredString(font, "+", x + TILE_SIZE / 2, y + TILE_SIZE / 2 - 4, 0xFF9AA0AC);
    }

    // ---- List mode -----------------------------------------------------

    private int rowX() {
        return panelLeft() + TILE_GAP;
    }

    private int rowWidth() {
        return panelWidth() - 2 * TILE_GAP;
    }

    private void renderListContent(GuiGraphics graphics, List<SignalApp> apps, int mouseX, int mouseY) {
        int total = totalTiles();
        int x = rowX();
        int w = rowWidth();
        for (int i = 0; i < total; i++) {
            int y = gridTop() + i * LIST_STRIDE - (int) scroll;
            if (y + ROW_HEIGHT < gridTop() - 2 || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseY >= gridTop() - 2 && mouseY <= gridBottom();

            if (i < apps.size()) {
                renderAppRow(graphics, apps.get(i), x, y, w, hovered);
            } else {
                renderAddRow(graphics, x, y, w, hovered);
            }
        }
    }

    private void renderAppRow(GuiGraphics graphics, SignalApp app, int x, int y, int w, boolean hovered) {
        graphics.fill(x, y, x + w, y + ROW_HEIGHT, hovered ? 0xFF353A46 : 0xFF2C303A);

        // Colored icon chip
        graphics.fill(x + 4, y + 4, x + 20, y + 20, app.color() | 0xFF000000);
        graphics.renderItem(app.iconStack(), x + 4, y + 4);

        // Name (leave room for chip + switch + optional count tag)
        String countTag = app.frequencies().size() > 1 ? " x" + app.frequencies().size() : "";
        int tagWidth = countTag.isEmpty() ? 0 : font.width(countTag);
        String name = font.plainSubstrByWidth(app.name(), w - 24 - SWITCH_W - 12 - tagWidth);
        int nameY = y + (ROW_HEIGHT - 8) / 2;
        graphics.drawString(font, name, x + 26, nameY,
                app.active() ? 0xFFE2E5EB : 0xFF9AA0AC);
        if (!countTag.isEmpty()) {
            graphics.drawString(font, countTag, x + 26 + font.width(name), nameY, 0xFF6A7284);
        }

        // Toggle switch
        int sx = x + w - SWITCH_W - 4;
        int sy = y + (ROW_HEIGHT - SWITCH_H) / 2;
        int track = app.active() ? 0xFF2F855A : 0xFF444955;
        graphics.fill(sx, sy, sx + SWITCH_W, sy + SWITCH_H, track);
        int knobX = app.active() ? sx + SWITCH_W - 10 : sx + 2;
        graphics.fill(knobX, sy + 2, knobX + 8, sy + SWITCH_H - 2,
                app.active() ? 0xFF4ADE80 : 0xFF9AA0AC);
    }

    private void renderAddRow(GuiGraphics graphics, int x, int y, int w, boolean hovered) {
        graphics.fill(x, y, x + w, y + ROW_HEIGHT, hovered ? 0xFF3A3F4B : 0xFF262A33);
        graphics.drawCenteredString(font,
                Component.translatable("gui.linktablet.add_app_row"),
                x + w / 2, y + (ROW_HEIGHT - 8) / 2, 0xFF9AA0AC);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overModeBtn(mouseX, mouseY, gridBtnX())) {
                if (listView()) UISounds.tick(1.8F);
                ClientPrefs.setListView(false);
                scroll = 0;
                return true;
            }
            if (overModeBtn(mouseX, mouseY, listBtnX())) {
                if (!listView()) UISounds.tick(1.8F);
                ClientPrefs.setListView(true);
                scroll = 0;
                return true;
            }
        }

        int index = listView() ? listIndexAt(mouseX, mouseY) : gridIndexAt(mouseX, mouseY);
        if (index != -1) {
            handleEntryClick(index, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Tile index under the mouse in grid mode, or -1. */
    private int gridIndexAt(double mouseX, double mouseY) {
        if (mouseY < gridTop() - 2 || mouseY > gridBottom()) return -1;
        int total = totalTiles();
        int base = gridLeft();
        for (int i = 0; i < total; i++) {
            int col = i % columns();
            int row = i / columns();
            int x = base + TILE_GAP + col * (TILE_SIZE + TILE_GAP);
            int y = gridTop() + row * ROW_STRIDE - (int) scroll;
            if (mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /** Row index under the mouse in list mode, or -1. */
    private int listIndexAt(double mouseX, double mouseY) {
        if (mouseY < gridTop() - 2 || mouseY > gridBottom()) return -1;
        if (mouseX < rowX() || mouseX >= rowX() + rowWidth()) return -1;
        int total = totalTiles();
        for (int i = 0; i < total; i++) {
            int y = gridTop() + i * LIST_STRIDE - (int) scroll;
            if (mouseY >= y && mouseY < y + ROW_HEIGHT) return i;
        }
        return -1;
    }

    private void handleEntryClick(int index, int button) {
        List<SignalApp> apps = apps();
        if (index < apps.size()) {
            if (button == 1) {
                // Right-click: edit
                UISounds.page();
                minecraft.setScreen(new AppEditScreen(this, index, apps.get(index)));
            } else if (button == 0) {
                // Left-click: toggle
                UISounds.toggle(!apps.get(index).active());
                PacketDistributor.sendToServer(
                        new ModNetworking.ToggleAppPayload(hand == InteractionHand.MAIN_HAND, index));
            }
        } else if (button == 0) {
            if (apps.size() < ModNetworking.MAX_APPS) {
                UISounds.page();
                minecraft.setScreen(new AppEditScreen(this, -1, null));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * 16, 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        UISounds.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
