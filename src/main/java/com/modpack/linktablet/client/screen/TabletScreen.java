package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.menu.AppEditMenu;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
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
/*
 * 1.5.0 chrome note: SURFACES (body panel, plaques, tiles, rows, popup
 * frames) blit the chrome atlas; MECHANISMS (switches, pips, glyphs,
 * value bars, swatches, glow borders) stay procedural fill() — see
 * Chrome's class javadoc. No hit-test geometry changed.
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

    private final AppView view;
    private double scroll = 0;

    /** Index of the momentary app currently held down, or -1. */
    private int heldMomentary = -1;

    /** Index of the slider app currently being dragged, or -1. */
    private int draggingSlider = -1;

    /** List-mode slider track width (wider than a switch — 16 stops). */
    private static final int LIST_SLIDER_W = 60;

    // Rearrange mode: while active, clicks grab-and-drag apps instead of
    // toggling them. The screen works on an optimistic local copy of the
    // list ({@code workingApps}) so drags reflow instantly; each drop
    // sends one ReorderAppPayload and the copy is retired once the
    // server-synced list matches it (no snap-back flicker).
    private boolean reorderMode = false;
    private List<SignalApp> workingApps = null;
    /** Theme dropdown open (swallows clicks like the edit screen's swatches). */
    private boolean themePopupOpen = false;
    /** Frames the retired overlay has waited for server sync. */
    private int overlayFrames = 0;
    /** Current slot of the grabbed app while dragging, or -1. */
    private int dragIndex = -1;
    /** Slot the grabbed app was in at press time (the packet's "from"). */
    private int dragFromIndex = -1;
    private double dragOffsetX, dragOffsetY;

    public TabletScreen(AppView view) {
        super(Component.translatable("gui.linktablet.tablet.title"));
        this.view = view;
    }

    public AppView view() {
        return view;
    }

    /** Momentary app currently held in this GUI, or -1 (item renderer). */
    public int heldMomentaryIndex() {
        return heldMomentary;
    }

    ScreenTheme theme() {
        return view.theme();
    }

    // drawCenteredString always drops a shadow; themed text needs the
    // shadow off on light backgrounds, so center manually.
    private void drawThemedCentered(GuiGraphics graphics, Component text, int cx, int y, int color) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color, theme().textShadow);
    }

    private void drawThemedCentered(GuiGraphics graphics, String text, int cx, int y, int color) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color, theme().textShadow);
    }

    ModNetworking.AppTarget target() {
        return view.target();
    }

    private List<SignalApp> apps() {
        return workingApps != null ? workingApps : view.apps();
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

    /** Rearrange-mode toggle button, top-left of the body. */
    private int reorderBtnX() {
        return panelLeft() + 4;
    }

    /** Theme picker button, right of the rearrange button. */
    private int themeBtnX() {
        return reorderBtnX() + MODE_BTN_SIZE + 4;
    }

    // Theme dropdown: one row per theme (swatches + name), below the button
    private static final int THEME_ROW_H = 14;
    private static final int THEME_POPUP_W = 96;

    private int themePopupX() {
        return themeBtnX();
    }

    private int themePopupY() {
        return modeBtnY() + MODE_BTN_SIZE + 4;
    }

    private boolean overModeBtn(double mouseX, double mouseY, int btnX) {
        return mouseX >= btnX && mouseX < btnX + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    /** Left edge of the entry (tile or row) at an index, layout-aware. */
    private int entryX(int i) {
        return listView()
                ? rowX()
                : gridLeft() + TILE_GAP + (i % columns()) * (TILE_SIZE + TILE_GAP);
    }

    /** Top edge of the entry at an index, layout- and scroll-aware. */
    private int entryY(int i) {
        return listView()
                ? gridTop() + i * LIST_STRIDE - (int) scroll
                : gridTop() + (i / columns()) * ROW_STRIDE - (int) scroll;
    }

    // ------------------------------------------------------------------
    // Rearrange mode
    // ------------------------------------------------------------------

    private boolean dragging() {
        return dragIndex != -1;
    }

    private void enterReorderMode() {
        releaseMomentary(); // never carry a held signal into the mode
        if (workingApps == null) {
            workingApps = new ArrayList<>(view.apps());
        }
        reorderMode = true;
        overlayFrames = 0;
        UISounds.tick(1.5F);
    }

    private void exitReorderMode() {
        // workingApps stays until the server-synced order matches it,
        // so the exit never shows a one-frame snap-back.
        reorderMode = false;
        dragIndex = dragFromIndex = -1;
        overlayFrames = 0;
        UISounds.tick(1.0F);
    }

    /** Slot the dragged app would land in at the mouse position, or -1. */
    private int dragSlotAt(double mouseX, double mouseY) {
        int idx = listView() ? listIndexAt(mouseX, mouseY) : gridIndexAt(mouseX, mouseY);
        if (idx == -1 || apps().isEmpty()) return -1;
        return Math.min(idx, apps().size() - 1); // add tile = move to end
    }

    private void updateDragHover(double mouseX, double mouseY) {
        int hover = dragSlotAt(mouseX, mouseY);
        if (hover != -1 && hover != dragIndex) {
            workingApps.add(hover, workingApps.remove(dragIndex)); // live reflow
            dragIndex = hover;
            UISounds.tick(1.3F);
        }
    }

    private void commitDrag() {
        if (dragging() && dragIndex != dragFromIndex) {
            PacketDistributor.sendToServer(
                    new ModNetworking.ReorderAppPayload(target(), dragFromIndex, dragIndex));
        }
        dragIndex = dragFromIndex = -1;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Full name of a hovered entry whose label got ellipsized this frame. */
    private String hoveredEllipsizedName;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        hoveredEllipsizedName = null;
        scroll = Mth.clamp(scroll, 0, maxScroll());

        if (reorderMode) {
            // External add/remove synced in: refresh the copy, drop the drag
            if (view.apps().size() != workingApps.size()) {
                workingApps = new ArrayList<>(view.apps());
                dragIndex = dragFromIndex = -1;
            }
            if (dragging()) {
                // Auto-scroll while holding a tile near the top/bottom edge
                if (mouseY < gridTop() + 10) {
                    scroll = Mth.clamp(scroll - 3, 0, maxScroll());
                } else if (mouseY > gridBottom() - 10) {
                    scroll = Mth.clamp(scroll + 3, 0, maxScroll());
                }
                updateDragHover(mouseX, mouseY);
            }
        } else if (workingApps != null) {
            // Retired overlay: hold the optimistic order until the server
            // echoes it back (or something else changed the list).
            if (view.apps().equals(workingApps) || view.apps().size() != workingApps.size()
                    || ++overlayFrames > 40) {
                workingApps = null;
            }
        }

        ScreenTheme theme = theme();
        int left = panelLeft();
        int pw = panelWidth();
        int top = bodyTop();
        int bottom = top + bodyHeight();

        // Tablet body: themed canvas inside an untinted wood rail frame
        Chrome.panel(graphics, left - 6, top - 2, pw + 12, bodyHeight() + 4, theme);

        Component titleText = reorderMode
                ? Component.translatable("gui.linktablet.reorder.title")
                : title;
        // Title on a parchment plaque hung over the top rail, Stock-Keeper style
        int titleW = font.width(titleText);
        Chrome.plaque(graphics, width / 2 - titleW / 2 - 6, top + 2, titleW + 12, 18, theme.rowBg);
        drawThemedCentered(graphics, titleText, width / 2, top + 7, theme.textPrimary);
        renderModeButtons(graphics, mouseX, mouseY);
        // Rail crossbar between the header and the scrolling content
        Chrome.railH(graphics, left - 4, gridTop() - 8, pw + 8, theme.bodyOuter);

        List<SignalApp> apps = apps();

        graphics.enableScissor(left - 4, gridTop() - 2, left + pw + 4, gridBottom());
        if (listView()) {
            renderListContent(graphics, apps, mouseX, mouseY);
        } else {
            renderGridContent(graphics, apps, mouseX, mouseY);
        }
        graphics.disableScissor();

        // Grabbed entry floats at the cursor, above everything
        if (dragging()) {
            int fx = (int) (mouseX - dragOffsetX);
            int fy = (int) (mouseY - dragOffsetY);
            if (listView()) {
                renderAppRow(graphics, apps.get(dragIndex), fx, fy, rowWidth(), false, false);
                graphics.fill(fx, fy, fx + rowWidth(), fy + ROW_HEIGHT, 0x28FFFFFF);
            } else {
                renderAppTile(graphics, apps.get(dragIndex), fx, fy, false, false);
                graphics.fill(fx, fy, fx + TILE_SIZE, fy + TILE_SIZE, 0x28FFFFFF);
            }
        }

        if (apps.isEmpty()) {
            drawThemedCentered(graphics,
                    Component.translatable("gui.linktablet.no_apps"),
                    width / 2, bottom + 8, theme.textMuted);
        }

        if (themePopupOpen) {
            renderThemePopup(graphics, mouseX, mouseY, theme);
        }

        // Tooltips last, on top of everything
        if (overModeBtn(mouseX, mouseY, gridBtnX())) {
            graphics.renderTooltip(font, Component.translatable("gui.linktablet.view.grid"), mouseX, mouseY);
        } else if (overModeBtn(mouseX, mouseY, listBtnX())) {
            graphics.renderTooltip(font, Component.translatable("gui.linktablet.view.list"), mouseX, mouseY);
        } else if (overModeBtn(mouseX, mouseY, reorderBtnX())) {
            graphics.renderTooltip(font, Component.translatable("gui.linktablet.view.reorder"), mouseX, mouseY);
        } else if (!themePopupOpen && overModeBtn(mouseX, mouseY, themeBtnX())) {
            graphics.renderTooltip(font, Component.translatable("gui.linktablet.theme.title"), mouseX, mouseY);
        } else if (hoveredEllipsizedName != null && !themePopupOpen) {
            graphics.renderTooltip(font, Component.literal(hoveredEllipsizedName), mouseX, mouseY);
        }
    }

    /** Theme dropdown, z-lifted above the batched content like the edit
     *  screen's color swatches. */
    private void renderThemePopup(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme current) {
        int px = themePopupX();
        int py = themePopupY();
        ScreenTheme[] themes = ScreenTheme.values();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        Chrome.panel(graphics, px - 8, py - 8, THEME_POPUP_W + 16, themes.length * THEME_ROW_H + 16,
                current);
        for (int i = 0; i < themes.length; i++) {
            ScreenTheme t = themes[i];
            int y = py + i * THEME_ROW_H;
            boolean hovered = mouseX >= px && mouseX < px + THEME_POPUP_W
                    && mouseY >= y && mouseY < y + THEME_ROW_H;
            if (t == current || hovered) {
                graphics.fill(px, y, px + THEME_POPUP_W, y + THEME_ROW_H,
                        t == current ? current.rowBgHover : current.rowBg);
            }
            // Swatch trio: panel, accent, text — the theme at a glance
            graphics.fill(px + 2, y + 3, px + 10, y + 11, t.screenBgLit);
            graphics.fill(px + 12, y + 3, px + 20, y + 11, t.accent);
            graphics.fill(px + 22, y + 3, px + 30, y + 11, t.textPrimary);
            graphics.drawString(font, t.displayName(), px + 34, y + 3,
                    t == current ? current.accent : current.textPrimary, current.textShadow);
        }
        graphics.pose().popPose();
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

        // Rearrange glyph: up/down arrow pair (top-left)
        int rx = reorderBtnX();
        int reorderColor = glyphColor(reorderMode, overModeBtn(mouseX, mouseY, rx));
        graphics.fill(rx + 2, y + 2, rx + 4, y + 3, reorderColor);
        graphics.fill(rx + 1, y + 3, rx + 5, y + 4, reorderColor);
        graphics.fill(rx + 2, y + 4, rx + 4, y + 10, reorderColor);
        graphics.fill(rx + 8, y + 2, rx + 10, y + 8, reorderColor);
        graphics.fill(rx + 7, y + 8, rx + 11, y + 9, reorderColor);
        graphics.fill(rx + 8, y + 9, rx + 10, y + 10, reorderColor);

        // Theme glyph: frame + three color dots (a tiny palette)
        int tx = themeBtnX();
        int frame = glyphColor(themePopupOpen, overModeBtn(mouseX, mouseY, tx));
        graphics.fill(tx + 1, y + 1, tx + 11, y + 2, frame);
        graphics.fill(tx + 1, y + 10, tx + 11, y + 11, frame);
        graphics.fill(tx + 1, y + 2, tx + 2, y + 10, frame);
        graphics.fill(tx + 10, y + 2, tx + 11, y + 10, frame);
        graphics.fill(tx + 3, y + 3, tx + 6, y + 6, 0xFF4ADE80);
        graphics.fill(tx + 6, y + 6, tx + 9, y + 9, 0xFFB07CFF);
        graphics.fill(tx + 3, y + 6, tx + 6, y + 9, 0xFF3AB3DA);
    }

    private int glyphColor(boolean active, boolean hovered) {
        ScreenTheme theme = theme();
        if (active) return theme.accent;
        return hovered ? theme.glyphHover : theme.textFaint;
    }

    // ---- Grid mode ----------------------------------------------------

    private void renderGridContent(GuiGraphics graphics, List<SignalApp> apps, int mouseX, int mouseY) {
        int total = totalTiles();
        for (int i = 0; i < total; i++) {
            int x = entryX(i);
            int y = entryY(i);
            if (y + TILE_SIZE < gridTop() - 2 || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE
                    && mouseY >= y && mouseY < y + TILE_SIZE
                    && mouseY >= gridTop() - 2 && mouseY <= gridBottom();

            if (i < apps.size()) {
                if (reorderMode && i == dragIndex) {
                    renderPlaceholderTile(graphics, x, y);
                } else {
                    if (reorderMode) {
                        graphics.fill(x - 1, y - 1, x + TILE_SIZE + 1, y + TILE_SIZE + 1, 0xFF8A93A6);
                    }
                    renderAppTile(graphics, apps.get(i), x, y, hovered, i == heldMomentary);
                }
            } else {
                renderAddTile(graphics, x, y, hovered && !reorderMode);
                if (reorderMode) {
                    graphics.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, 0x8016181D);
                }
            }
        }
    }

    /** Empty slot the dragged app was lifted out of. */
    private void renderPlaceholderTile(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + TILE_SIZE + 1, y + TILE_SIZE + 1, 0xFF5A6070);
        Chrome.plaque(graphics, x, y, TILE_SIZE, TILE_SIZE, theme().surfaceLo);
    }

    private void renderAppTile(GuiGraphics graphics, SignalApp app, int x, int y, boolean hovered, boolean held) {
        ScreenTheme theme = theme();
        // Active glow border (momentary apps glow while held)
        if (app.active() || held) {
            graphics.fill(x - 2, y - 2, x + TILE_SIZE + 2, y + TILE_SIZE + 2, theme.accent);
        } else if (hovered) {
            graphics.fill(x - 1, y - 1, x + TILE_SIZE + 1, y + TILE_SIZE + 1, 0xFF5A6070);
        }

        int color = app.color() | 0xFF000000;
        Chrome.tile(graphics, x, y, TILE_SIZE, TILE_SIZE, color);
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

        if (app.slider()) {
            // Value bar along the tile bottom; the value replaces the pip
            int tx0 = x + 4;
            int tx1 = x + TILE_SIZE - 4;
            int ty = y + TILE_SIZE - 9;
            graphics.fill(tx0, ty, tx1, ty + 5, theme.switchOff);
            if (app.strength() > 0) {
                int fill = tx0 + Math.round((tx1 - tx0) * app.fillFraction());
                graphics.fill(tx0, ty, fill, ty + 5, theme.accent);
            }
            graphics.drawString(font, String.valueOf(app.strength()),
                    x + TILE_SIZE - 4 - font.width(String.valueOf(app.strength())), y + 4,
                    0xFFE2E5EB, true);
        } else {
            // ON/OFF pip; momentary apps get a hollow ring (solid while held)
            int pipColor = (app.active() || held) ? theme.accent : theme.switchOff;
            graphics.fill(x + TILE_SIZE - 8, y + 4, x + TILE_SIZE - 4, y + 8, pipColor);
            if (app.momentary() && !held) {
                graphics.fill(x + TILE_SIZE - 7, y + 5, x + TILE_SIZE - 5, y + 7, app.color() | 0xFF000000);
            }
        }

        // Frequency count badge for scene apps
        if (app.frequencies().size() > 1) {
            graphics.drawString(font, "x" + app.frequencies().size(), x + 3, y + 3, 0xFFE2E5EB, true);
        }

        // Name (ellipsized to tile width; full name via hover tooltip)
        String name = TextFit.ellipsize(font, app.name(), TILE_SIZE + TILE_GAP - 2);
        drawThemedCentered(graphics, name, x + TILE_SIZE / 2, y + TILE_SIZE + 3, theme.textPrimary);
        if (hovered && !name.equals(app.name())) {
            hoveredEllipsizedName = app.name();
        }
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
        ScreenTheme theme = theme();
        int bg = hovered ? theme.surfaceHi : theme.rowBg;
        Chrome.tile(graphics, x, y, TILE_SIZE, TILE_SIZE, bg);
        drawThemedCentered(graphics, "+", x + TILE_SIZE / 2, y + TILE_SIZE / 2 - 4, theme.textMuted);
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
            int y = entryY(i);
            if (y + ROW_HEIGHT < gridTop() - 2 || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseY >= gridTop() - 2 && mouseY <= gridBottom();

            if (i < apps.size()) {
                if (reorderMode && i == dragIndex) {
                    renderPlaceholderRow(graphics, x, y, w);
                } else {
                    if (reorderMode) {
                        graphics.fill(x - 1, y - 1, x + w + 1, y + ROW_HEIGHT + 1, 0xFF8A93A6);
                    }
                    renderAppRow(graphics, apps.get(i), x, y, w, hovered, i == heldMomentary);
                }
            } else {
                renderAddRow(graphics, x, y, w, hovered && !reorderMode);
                if (reorderMode) {
                    graphics.fill(x, y, x + w, y + ROW_HEIGHT, 0x8016181D);
                }
            }
        }
    }

    /** Empty slot the dragged app was lifted out of. */
    private void renderPlaceholderRow(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + ROW_HEIGHT + 1, 0xFF5A6070);
        Chrome.plaque(graphics, x, y, w, ROW_HEIGHT, theme().surfaceLo);
    }

    private void renderAppRow(GuiGraphics graphics, SignalApp app, int x, int y, int w,
                              boolean hovered, boolean held) {
        ScreenTheme theme = theme();
        boolean lit = app.active() || held;
        Chrome.plaque(graphics, x, y, w, ROW_HEIGHT, hovered ? theme.rowBgHover : theme.rowBg);

        // Colored icon chip
        graphics.fill(x + 4, y + 4, x + 20, y + 20, app.color() | 0xFF000000);
        graphics.renderItem(app.iconStack(), x + 4, y + 4);

        // Name (leave room for chip + switch/track + optional count tag)
        String countTag = app.frequencies().size() > 1 ? " x" + app.frequencies().size() : "";
        int tagWidth = countTag.isEmpty() ? 0 : font.width(countTag);
        // Sliders reserve extra room for the numeric level readout
        int controlW = app.slider() ? LIST_SLIDER_W + font.width("15") + 4 : SWITCH_W;
        String name = TextFit.ellipsize(font, app.name(), w - 24 - controlW - 12 - tagWidth);
        if (hovered && !name.equals(app.name())) {
            hoveredEllipsizedName = app.name();
        }
        int nameY = y + (ROW_HEIGHT - 8) / 2;
        graphics.drawString(font, name, x + 26, nameY,
                lit ? theme.textPrimary : theme.textMuted, theme.textShadow);
        if (!countTag.isEmpty()) {
            graphics.drawString(font, countTag, x + 26 + font.width(name), nameY,
                    0xFF6A7284, theme.textShadow);
        }

        if (app.slider()) {
            // Wide track with a knob at the current value, level readout beside it
            int tx1 = x + w - 4;
            int tx0 = tx1 - LIST_SLIDER_W;
            int ty = y + ROW_HEIGHT / 2 - 2;
            String level = String.valueOf(app.strength());
            graphics.drawString(font, level, tx0 - 4 - font.width(level), nameY,
                    lit ? theme.textPrimary : theme.textMuted, theme.textShadow);
            graphics.fill(tx0, ty, tx1, ty + 4, theme.switchOff);
            int knobX = tx0 + Math.round((tx1 - tx0 - 4) * app.fillFraction());
            if (app.strength() > 0) {
                graphics.fill(tx0, ty, knobX + 2, ty + 4, theme.accentDim);
            }
            graphics.fill(knobX, ty - 3, knobX + 4, ty + 7, lit ? theme.accent : theme.textMuted);
            return;
        }

        int sx = x + w - SWITCH_W - 4;
        int sy = y + (ROW_HEIGHT - SWITCH_H) / 2;
        if (app.momentary()) {
            // Push button: center dot lights while held
            graphics.fill(sx, sy, sx + SWITCH_W, sy + SWITCH_H, held ? theme.accentDim : theme.switchOff);
            int cx = sx + SWITCH_W / 2;
            int cy = sy + SWITCH_H / 2;
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, held ? theme.accent : theme.textMuted);
        } else {
            // Toggle switch
            int track = app.active() ? theme.accentDim : theme.switchOff;
            graphics.fill(sx, sy, sx + SWITCH_W, sy + SWITCH_H, track);
            int knobX = app.active() ? sx + SWITCH_W - 10 : sx + 2;
            graphics.fill(knobX, sy + 2, knobX + 8, sy + SWITCH_H - 2,
                    app.active() ? theme.accent : theme.textMuted);
        }
    }

    private void renderAddRow(GuiGraphics graphics, int x, int y, int w, boolean hovered) {
        ScreenTheme theme = theme();
        graphics.fill(x, y, x + w, y + ROW_HEIGHT, hovered ? theme.surfaceHi : theme.surfaceLo);
        drawThemedCentered(graphics,
                Component.translatable("gui.linktablet.add_app_row"),
                x + w / 2, y + (ROW_HEIGHT - 8) / 2, theme.textMuted);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Open theme popup swallows every click until it closes
        if (themePopupOpen) {
            int px = themePopupX();
            int py = themePopupY();
            ScreenTheme[] themes = ScreenTheme.values();
            if (mouseX >= px && mouseX < px + THEME_POPUP_W
                    && mouseY >= py && mouseY < py + themes.length * THEME_ROW_H) {
                ScreenTheme picked = themes[(int) ((mouseY - py) / THEME_ROW_H)];
                if (picked != theme()) {
                    UISounds.tick(1.4F);
                    PacketDistributor.sendToServer(
                            new ModNetworking.SetThemePayload(target(), picked));
                }
            }
            themePopupOpen = false;
            return true;
        }

        if (button == 0) {
            if (overModeBtn(mouseX, mouseY, themeBtnX())) {
                themePopupOpen = true;
                UISounds.tick(1.3F);
                return true;
            }
            if (overModeBtn(mouseX, mouseY, reorderBtnX())) {
                if (reorderMode) {
                    exitReorderMode();
                } else {
                    enterReorderMode();
                }
                return true;
            }
            if (overModeBtn(mouseX, mouseY, gridBtnX())) {
                if (listView()) UISounds.tick(1.8F);
                ClientPrefs.setListView(false);
                // The tablet's physical mini-screen remembers the last
                // view used on it (per-tablet, unlike the client pref)
                PacketDistributor.sendToServer(
                        new ModNetworking.ScreenLayoutPayload(target(), false));
                scroll = 0;
                return true;
            }
            if (overModeBtn(mouseX, mouseY, listBtnX())) {
                if (!listView()) UISounds.tick(1.8F);
                ClientPrefs.setListView(true);
                PacketDistributor.sendToServer(
                        new ModNetworking.ScreenLayoutPayload(target(), true));
                scroll = 0;
                return true;
            }
        }

        if (reorderMode) {
            // Grab-and-drag only; toggling/editing is suspended in the mode
            if (button == 0) {
                int idx = listView() ? listIndexAt(mouseX, mouseY) : gridIndexAt(mouseX, mouseY);
                if (idx >= 0 && idx < apps().size()) { // add tile inert
                    dragIndex = dragFromIndex = idx;
                    dragOffsetX = mouseX - entryX(idx);
                    dragOffsetY = mouseY - entryY(idx);
                    UISounds.tick(0.9F);
                }
            }
            return true;
        }

        int index = listView() ? listIndexAt(mouseX, mouseY) : gridIndexAt(mouseX, mouseY);
        if (index != -1) {
            handleEntryClick(index, button, mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging()) {
            updateDragHover(mouseX, mouseY);
            return true;
        }
        if (button == 0 && draggingSlider != -1) {
            sendSliderValue(draggingSlider, sliderValueFromMouse(draggingSlider, mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // ---- Slider apps ---------------------------------------------------

    /** Horizontal span of an entry's slider control (screen-x px). */
    private int[] sliderSpan(int index) {
        if (listView()) {
            int right = rowX() + rowWidth() - 4;
            return new int[]{right - LIST_SLIDER_W, right};
        }
        int x = entryX(index);
        return new int[]{x + 4, x + TILE_SIZE - 4};
    }

    private int sliderValueFromMouse(int index, double mouseX) {
        List<SignalApp> apps = apps();
        if (index >= apps.size()) return 0; // app vanished mid-drag; sendSliderValue bails too
        int[] span = sliderSpan(index);
        float rel = Mth.clamp((float) ((mouseX - span[0]) / (span[1] - span[0])), 0.0F, 1.0F);
        return apps.get(index).valueFromFraction(rel);
    }

    /** Sends only actual changes — a drag emits at most 16 packets. */
    private void sendSliderValue(int index, int value) {
        List<SignalApp> apps = apps();
        if (index >= apps.size()) return;
        SignalApp app = apps.get(index);
        if (!app.slider() || app.strength() == value) return;
        PacketDistributor.sendToServer(new ModNetworking.SetSliderPayload(target(), index, value));
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

    private void handleEntryClick(int index, int button, double mouseX) {
        List<SignalApp> apps = apps();
        if (index < apps.size()) {
            if (button == 1) {
                // Right-click: edit (server opens the container menu)
                UISounds.page();
                PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                        AppEditMenu.EditContext.plain(target(), index)));
            } else if (button == 0) {
                SignalApp app = apps.get(index);
                if (app.slider()) {
                    // Click sets the value from position; keep dragging to sweep
                    draggingSlider = index;
                    UISounds.tick(1.2F);
                    sendSliderValue(index, sliderValueFromMouse(index, mouseX));
                    return;
                }
                if (app.momentary()) {
                    // Press-and-hold: transmits until mouse release
                    UISounds.toggle(true);
                    heldMomentary = index;
                    PacketDistributor.sendToServer(
                            new ModNetworking.MomentaryAppPayload(target(), index, true));
                } else {
                    // Left-click: toggle
                    UISounds.toggle(!app.active());
                    PacketDistributor.sendToServer(
                            new ModNetworking.ToggleAppPayload(target(), index));
                }
            }
        } else if (button == 0) {
            if (apps.size() < ModNetworking.MAX_APPS) {
                UISounds.page();
                PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                        AppEditMenu.EditContext.plain(target(), -1)));
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging()) {
            commitDrag();
            UISounds.tick(1.6F);
            return true;
        }
        if (button == 0 && heldMomentary != -1) {
            UISounds.toggle(false);
            releaseMomentary();
            return true;
        }
        if (button == 0 && draggingSlider != -1) {
            List<SignalApp> apps = apps();
            if (draggingSlider < apps.size()) {
                UISounds.tick(1.0F + apps.get(draggingSlider).strength() / 15.0F);
            }
            draggingSlider = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void releaseMomentary() {
        if (heldMomentary == -1) return;
        PacketDistributor.sendToServer(
                new ModNetworking.MomentaryAppPayload(target(), heldMomentary, false));
        heldMomentary = -1;
    }

    @Override
    public void removed() {
        // Screen closed mid-drag: commit the move at its previewed slot
        commitDrag();
        // Screen closed or replaced mid-press: never leave a held signal on
        releaseMomentary();
        super.removed();
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
