package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.menu.SignalEditMenu;
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
 *   <li><b>Grid</b> — colored signal tiles with icons</li>
 *   <li><b>List</b> — one row per signal with a toggle switch</li>
 * </ul>
 * In both modes:
 * <ul>
 *   <li>Left-click a signal → toggle it on/off</li>
 *   <li>Right-click a signal → edit it</li>
 *   <li>Click the add tile/row → add a new signal</li>
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

    /** Grid slider bar inset from the tile edge (bar runs along the chip
     * bottom); {@link #sliderSpan} maps drags against this exact span. */
    private static final int GRID_BAR_INSET = 9;

    /** Grid row height: tile + name label + gap. */
    static final int ROW_STRIDE = TILE_SIZE + TILE_GAP + 12;

    // List layout
    static final int LIST_WIDTH = 200;
    static final int ROW_HEIGHT = 24;
    static final int ROW_GAP = 4;
    static final int LIST_STRIDE = ROW_HEIGHT + ROW_GAP;

    /** Toggle switch dimensions (list mode; shared with SignalRowPainter). */
    static final int SWITCH_W = 22;
    static final int SWITCH_H = 12;

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

    private final SignalView view;
    private double scroll = 0;

    /** Index of the momentary signal currently held down, or -1. */
    private int heldMomentary = -1;

    /**
     * Tap feedback for Timer signals: index → wall-clock millis until the
     * tile renders "pressed". Client-only flash — the real pulse state
     * lives on the server (placed screens show it via BE pips).
     */
    private final java.util.Map<Integer, Long> timerFlash = new java.util.HashMap<>();

    private boolean timerFlashActive(int index) {
        Long until = timerFlash.get(index);
        if (until == null) return false;
        if (net.minecraft.Util.getMillis() >= until) {
            timerFlash.remove(index);
            return false;
        }
        return true;
    }

    /** Index of the slider signal currently being dragged, or -1. */
    private int draggingSlider = -1;

    /** List-mode slider track width (wider than a switch — 16 stops;
     * shared with SignalRowPainter). */
    static final int LIST_SLIDER_W = 60;

    // Rearrange mode: while active, clicks grab-and-drag signals instead of
    // toggling them. The screen works on an optimistic local copy of the
    // list ({@code workingSignals}) so drags reflow instantly; each drop
    // sends one ReorderSignalPayload and the copy is retired once the
    // server-synced list matches it (no snap-back flicker).
    private boolean reorderMode = false;
    private List<Signal> workingSignals = null;
    /** Theme dropdown open (swallows clicks like the edit screen's swatches). */
    private boolean themePopupOpen = false;
    /** Frames the retired overlay has waited for server sync. */
    private int overlayFrames = 0;
    /** Current slot of the grabbed signal while dragging, or -1. */
    private int dragIndex = -1;
    /** Slot the grabbed signal was in at press time (the packet's "from"). */
    private int dragFromIndex = -1;
    private double dragOffsetX, dragOffsetY;

    public TabletScreen(SignalView view) {
        super(Component.translatable("gui.linktablet.tablet.title"));
        this.view = view;
    }

    public SignalView view() {
        return view;
    }

    /** Momentary signal currently held in this GUI, or -1 (item renderer). */
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

    ModNetworking.SignalTarget target() {
        return view.target();
    }

    private List<Signal> signals() {
        return workingSignals != null ? workingSignals : view.signals();
    }

    private boolean listView() {
        return ClientPrefs.listView();
    }

    // ------------------------------------------------------------------
    // Dynamic layout
    // ------------------------------------------------------------------

    private int totalTiles() {
        return signals().size() + 1; // + "add" tile/row
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
        // The Home button (1.10.0) widens the left cluster — grow the
        // floor with it or the title plaque overlaps the pin glyph
        return Math.max(w, MIN_PANEL_WIDTH + MODE_BTN_SIZE + 4);
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
        // Floor of 24: the title plaque hangs in its OWN band above the
        // panel (user request 2026-08-11 — it used to share the glyph
        // row and the padlock button pushed the cluster under it), so
        // a full-height body must leave it headroom.
        return Math.max((height - bodyHeight()) / 2, 24);
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

    /** Home (launcher) button, top-left corner (1.10.0) — every view:
     * kiosks return to the block-bound launcher GUI too. */
    private int homeBtnX() {
        return panelLeft() + 4;
    }

    /** Rearrange-mode toggle button, right of Home. */
    private int reorderBtnX() {
        return homeBtnX() + MODE_BTN_SIZE + 4;
    }

    /** Theme picker button, right of the rearrange button. */
    private int themeBtnX() {
        return reorderBtnX() + MODE_BTN_SIZE + 4;
    }

    /** Overlay pin button, right of the theme button (1.7.0). */
    private int pinBtnX() {
        return themeBtnX() + MODE_BTN_SIZE + 4;
    }

    /** Neighbor-link toggle, right of the pin — placed tablets only. */
    private int linkBtnX() {
        return pinBtnX() + MODE_BTN_SIZE + 4;
    }

    /** Screen-lock toggle, right of the link — placed tablets only. */
    private int lockBtnX() {
        return linkBtnX() + MODE_BTN_SIZE + 4;
    }

    private boolean isBlockView() {
        return view instanceof SignalView.Block;
    }

    /** Whether the viewed placed tablet is SOLO (opted out of merging). */
    private boolean soloScreen() {
        if (!(view instanceof SignalView.Block block) || minecraft == null
                || minecraft.level == null) {
            return false;
        }
        if (!(minecraft.level.getBlockEntity(block.pos()) instanceof TabletBlockEntity be)) {
            return false;
        }
        TabletBlockEntity resolved = be.resolveController();
        return (resolved != null ? resolved : be).isSoloScreen();
    }

    /** Whether the viewed placed tablet('s controller) is LOCKED. */
    private boolean lockedScreen() {
        if (!(view instanceof SignalView.Block block) || minecraft == null
                || minecraft.level == null) {
            return false;
        }
        if (!(minecraft.level.getBlockEntity(block.pos()) instanceof TabletBlockEntity be)) {
            return false;
        }
        TabletBlockEntity resolved = be.resolveController();
        return (resolved != null ? resolved : be).isLocked();
    }

    /** Wrench in either hand, client-side — the deny PRE-check only;
     * the server enforces the same rule regardless. */
    private boolean holdingWrench() {
        return minecraft != null && minecraft.player != null
                && (minecraft.player.getMainHandItem()
                        .is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)
                    || minecraft.player.getOffhandItem()
                        .is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH));
    }

    // Theme dropdown metrics live in HeaderGlyphs (shared with the launcher)
    private static final int THEME_ROW_H = HeaderGlyphs.THEME_ROW_H;
    private static final int THEME_POPUP_W = HeaderGlyphs.THEME_POPUP_W;

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
    // Note window
    // ------------------------------------------------------------------

    /** Note windows live in {@link NoteWindows} — they outlive this screen. */
    private void openNote(int index) {
        NoteWindows.open(view, index);
    }

    /**
     * Note glyph left edge inside a list row — right before the control,
     * mirrored by the row renderer and the click hit-test.
     */
    private int noteGlyphListX(Signal signal) {
        int controlW = signal.slider() ? LIST_SLIDER_W + font.width("15") + 4 : SWITCH_W;
        return rowX() + rowWidth() - 4 - controlW - 12;
    }

    /** True when the mouse is over an entry's note glyph (both layouts). */
    private boolean overNoteGlyph(int index, double mouseX, double mouseY) {
        List<Signal> signals = signals();
        if (index < 0 || index >= signals.size()) return false;
        int y = entryY(index);
        if (listView()) {
            int gx = noteGlyphListX(signals.get(index));
            return mouseX >= gx - 2 && mouseX < gx + 10
                    && mouseY >= y + (ROW_HEIGHT - 9) / 2 - 2 && mouseY < y + (ROW_HEIGHT + 9) / 2 + 2;
        }
        int x = entryX(index);
        // Top-left tile corner (below the frequency badge when present)
        int gy = y + (signals.get(index).frequencies().size() > 1 ? 13 : 3);
        return mouseX >= x + 1 && mouseX < x + 13 && mouseY >= gy - 2 && mouseY < gy + 11;
    }

    /** Tiny note-page glyph (7x9): outline, page, two text lines. */
    private void drawNoteGlyph(GuiGraphics graphics, int gx, int gy, int frame, int page) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200); // above 3D block icons
        graphics.fill(gx, gy, gx + 7, gy + 9, frame);
        graphics.fill(gx + 1, gy + 1, gx + 6, gy + 8, page);
        graphics.fill(gx + 2, gy + 3, gx + 5, gy + 4, frame);
        graphics.fill(gx + 2, gy + 5, gx + 5, gy + 6, frame);
        graphics.pose().popPose();
    }

    /** Tiny chain glyph (8x9, two interlocked links) — the signal-links
     * indicator (1.10.0). Display only; links are edited in the edit
     * screen's Links overlay. */
    private void drawChainGlyph(GuiGraphics graphics, int gx, int gy, int frame, int hole) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200); // above 3D block icons
        graphics.fill(gx, gy, gx + 5, gy + 5, frame);
        graphics.fill(gx + 1, gy + 1, gx + 4, gy + 4, hole);
        graphics.fill(gx + 3, gy + 4, gx + 8, gy + 9, frame);
        graphics.fill(gx + 4, gy + 5, gx + 7, gy + 8, hole);
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------
    // Rearrange mode
    // ------------------------------------------------------------------

    private boolean dragging() {
        return dragIndex != -1;
    }

    private void enterReorderMode() {
        releaseMomentary(); // never carry a held signal into the mode
        if (workingSignals == null) {
            workingSignals = new ArrayList<>(view.signals());
        }
        reorderMode = true;
        overlayFrames = 0;
        UISounds.tick(1.5F);
    }

    private void exitReorderMode() {
        // workingSignals stays until the server-synced order matches it,
        // so the exit never shows a one-frame snap-back.
        reorderMode = false;
        dragIndex = dragFromIndex = -1;
        overlayFrames = 0;
        UISounds.tick(1.0F);
    }

    /** Slot the dragged signal would land in at the mouse position, or -1. */
    private int dragSlotAt(double mouseX, double mouseY) {
        int idx = listView() ? listIndexAt(mouseX, mouseY) : gridIndexAt(mouseX, mouseY);
        if (idx == -1 || signals().isEmpty()) return -1;
        return Math.min(idx, signals().size() - 1); // add tile = move to end
    }

    private void updateDragHover(double mouseX, double mouseY) {
        int hover = dragSlotAt(mouseX, mouseY);
        if (hover != -1 && hover != dragIndex) {
            workingSignals.add(hover, workingSignals.remove(dragIndex)); // live reflow
            dragIndex = hover;
            UISounds.tick(1.3F);
        }
    }

    private void commitDrag() {
        if (dragging() && dragIndex != dragFromIndex) {
            PacketDistributor.sendToServer(
                    new ModNetworking.ReorderSignalPayload(target(), dragFromIndex, dragIndex));
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
            if (view.signals().size() != workingSignals.size()) {
                workingSignals = new ArrayList<>(view.signals());
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
        } else if (workingSignals != null) {
            // Retired overlay: hold the optimistic order until the server
            // echoes it back (or something else changed the list).
            if (view.signals().equals(workingSignals) || view.signals().size() != workingSignals.size()
                    || ++overlayFrames > 40) {
                workingSignals = null;
            }
        }

        ScreenTheme theme = theme();
        int left = panelLeft();
        int pw = panelWidth();
        int top = bodyTop();
        int bottom = top + bodyHeight();

        // Tablet body: themed canvas inside an untinted wood rail frame
        Chrome.panel(graphics, left - 6, top - 2, pw + 12, bodyHeight() + 4, theme);

        // The tablet's own (anvil) name when it has one — read live so
        // renames and merge/split re-resolutions show without reopening
        Component titleText = reorderMode
                ? Component.translatable("gui.linktablet.reorder.title")
                : view.displayName();
        // Title on a parchment plaque in its OWN band ABOVE the panel
        // (Stock-Keeper style tab) — the glyph row keeps the full header
        // width to itself
        int titleW = font.width(titleText);
        Chrome.plaque(graphics, width / 2 - titleW / 2 - 6, top - 20, titleW + 12, 18, theme.rowBg);
        drawThemedCentered(graphics, titleText, width / 2, top - 15, theme.textPrimary);
        renderModeButtons(graphics, mouseX, mouseY);
        // Rail crossbar between the header and the scrolling content
        Chrome.railH(graphics, left - 4, gridTop() - 8, pw + 8, theme.bodyOuter);

        List<Signal> signals = signals();

        graphics.enableScissor(left - 4, gridTop() - 2, left + pw + 4, gridBottom());
        if (listView()) {
            renderListContent(graphics, signals, mouseX, mouseY);
        } else {
            renderGridContent(graphics, signals, mouseX, mouseY);
        }
        graphics.disableScissor();

        // Grabbed entry floats at the cursor, above everything
        if (dragging()) {
            int fx = (int) (mouseX - dragOffsetX);
            int fy = (int) (mouseY - dragOffsetY);
            if (listView()) {
                renderSignalRow(graphics, signals.get(dragIndex), fx, fy, rowWidth(), false, false, false);
                graphics.fill(fx, fy, fx + rowWidth(), fy + ROW_HEIGHT, 0x28FFFFFF);
            } else {
                renderSignalTile(graphics, signals.get(dragIndex), fx, fy, false, false, false);
                graphics.fill(fx, fy, fx + TILE_SIZE, fy + TILE_SIZE, 0x28FFFFFF);
            }
        }

        if (signals.isEmpty()) {
            drawThemedCentered(graphics,
                    Component.translatable("gui.linktablet.no_signals"),
                    width / 2, bottom + 8, theme.textMuted);
        }

        if (themePopupOpen) {
            renderThemePopup(graphics, mouseX, mouseY, theme);
        }

        // Note windows render via NoteWindows' screen event, above us.

        // Tooltips last, on top of everything
        ScreenTips.draw(graphics, font, mouseX, mouseY);
        if (hoveredEllipsizedName != null && !themePopupOpen
                && !NoteWindows.anyContains(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(hoveredEllipsizedName), mouseX, mouseY);
        }
    }

    /** Theme dropdown, z-lifted above the batched content like the edit
     *  screen's color swatches. */
    private void renderThemePopup(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme current) {
        HeaderGlyphs.themePopup(graphics, font, themePopupX(), themePopupY(),
                current, mouseX, mouseY);
    }

    private void renderModeButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = modeBtnY();
        boolean list = listView();

        // Glyph pixel art lives in HeaderGlyphs (shared with the launcher)
        HeaderGlyphs.grid(graphics, gridBtnX(), y,
                glyphColor(!list, overModeBtn(mouseX, mouseY, gridBtnX())));
        ScreenTips.glyph(gridBtnX(), modeBtnY(), "gui.linktablet.view.grid");
        HeaderGlyphs.list(graphics, listBtnX(), y,
                glyphColor(list, overModeBtn(mouseX, mouseY, listBtnX())));
        ScreenTips.glyph(listBtnX(), modeBtnY(), "gui.linktablet.view.list");
        HeaderGlyphs.home(graphics, homeBtnX(), y,
                glyphColor(false, overModeBtn(mouseX, mouseY, homeBtnX())));
        ScreenTips.glyph(homeBtnX(), modeBtnY(), "gui.linktablet.home");
        HeaderGlyphs.reorder(graphics, reorderBtnX(), y,
                glyphColor(reorderMode, overModeBtn(mouseX, mouseY, reorderBtnX())));
        ScreenTips.glyph(reorderBtnX(), modeBtnY(), "gui.linktablet.view.reorder");
        HeaderGlyphs.themePalette(graphics, themeBtnX(), y,
                glyphColor(themePopupOpen, overModeBtn(mouseX, mouseY, themeBtnX())));
        ScreenTips.glyph(themeBtnX(), modeBtnY(), "gui.linktablet.theme.title");
        // Pin lights while THIS tablet is the pinned overlay
        HeaderGlyphs.pin(graphics, pinBtnX(), y,
                glyphColor(OverlayPin.isPinned(view), overModeBtn(mouseX, mouseY, pinBtnX())));
        ScreenTips.glyph(pinBtnX(), modeBtnY(), OverlayPin.isPinned(view)
                ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
        // Link (placed tablets only): joined while merging is allowed,
        // broken apart (and lit) while this tablet is SOLO
        if (isBlockView()) {
            boolean solo = soloScreen();
            HeaderGlyphs.link(graphics, linkBtnX(), y,
                    glyphColor(solo, overModeBtn(mouseX, mouseY, linkBtnX())), solo);
            ScreenTips.glyph(linkBtnX(), modeBtnY(), solo
                    ? "gui.linktablet.tip.link" : "gui.linktablet.tip.unlink");
            boolean locked = lockedScreen();
            HeaderGlyphs.lock(graphics, lockBtnX(), y,
                    glyphColor(locked, overModeBtn(mouseX, mouseY, lockBtnX())), locked);
            ScreenTips.glyph(lockBtnX(), modeBtnY(), locked
                    ? "gui.linktablet.lock.unlock" : "gui.linktablet.lock.lock");
        }
    }

    private int glyphColor(boolean active, boolean hovered) {
        ScreenTheme theme = theme();
        if (active) return theme.accent;
        return hovered ? theme.glyphHover : theme.textFaint;
    }

    // ---- Grid mode ----------------------------------------------------

    private void renderGridContent(GuiGraphics graphics, List<Signal> signals, int mouseX, int mouseY) {
        int total = totalTiles();
        for (int i = 0; i < total; i++) {
            int x = entryX(i);
            int y = entryY(i);
            if (y + TILE_SIZE < gridTop() - 2 || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE
                    && mouseY >= y && mouseY < y + TILE_SIZE
                    && mouseY >= gridTop() - 2 && mouseY <= gridBottom();

            if (i < signals.size()) {
                if (reorderMode && i == dragIndex) {
                    renderPlaceholderTile(graphics, x, y);
                } else {
                    if (reorderMode) {
                        graphics.fill(x - 1, y - 1, x + TILE_SIZE + 1, y + TILE_SIZE + 1, 0xFF8A93A6);
                    }
                    renderSignalTile(graphics, signals.get(i), x, y, hovered,
                            i == heldMomentary || timerFlashActive(i),
                            !reorderMode && overNoteGlyph(i, mouseX, mouseY));
                }
            } else {
                renderAddTile(graphics, x, y, hovered && !reorderMode);
                if (reorderMode) {
                    graphics.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, 0x8016181D);
                }
            }
        }
    }

    /** Empty slot the dragged signal was lifted out of. */
    private void renderPlaceholderTile(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + TILE_SIZE + 1, y + TILE_SIZE + 1, 0xFF5A6070);
        Chrome.plaque(graphics, x, y, TILE_SIZE, TILE_SIZE, theme().surfaceLo);
    }

    private void renderSignalTile(GuiGraphics graphics, Signal signal, int x, int y, boolean hovered,
                               boolean held, boolean noteHovered) {
        ScreenTheme theme = theme();
        // Base look (borders, plaque, color chip) shared with the
        // launcher via the painter; momentary signals glow while held
        int color = signal.color() | 0xFF000000;
        SignalTilePainter.base(graphics, theme, x, y, TILE_SIZE, color,
                signal.active() || held, hovered);
        int chipY0 = y + SignalTilePainter.CHIP_INSET;
        int chipX1 = x + TILE_SIZE - SignalTilePainter.CHIP_INSET;
        int chipY1 = y + TILE_SIZE - SignalTilePainter.CHIP_INSET;

        // Icon centered on the chip: custom item, or the first frequency
        // pair drawn overlapping (a one-item frequency renders centered
        // like a custom icon). Sliders sit higher — the value bar runs
        // along the chip bottom.
        int iy = signal.slider() ? y + 10 : y + (TILE_SIZE - 16) / 2;
        if (signal.hasCustomIcon() || !signal.primaryFrequency().isPair()) {
            graphics.renderItem(signal.iconStack(), x + (TILE_SIZE - 16) / 2, iy);
        } else {
            int cx = x + TILE_SIZE / 2;
            graphics.renderItem(signal.primaryFrequency().icon1(), cx - 14, iy);
            graphics.renderItem(signal.primaryFrequency().icon2(), cx - 2, iy);
            drawFreqPairMarkers(graphics, cx, iy);
        }

        if (signal.slider()) {
            // Value bar along the chip bottom; the value replaces the pip
            int tx0 = x + GRID_BAR_INSET;
            int tx1 = x + TILE_SIZE - GRID_BAR_INSET;
            int ty = chipY1 - 6;
            graphics.fill(tx0, ty, tx1, ty + 4, theme.switchOff);
            if (signal.strength() > 0) {
                int fill = tx0 + Math.round((tx1 - tx0) * signal.fillFraction());
                graphics.fill(tx0, ty, fill, ty + 4, theme.accent);
            }
            // Level readout stack-count style: chip bottom-right, above the
            // bar. Z-lifted like vanilla stack counts so the 3D block icon
            // can't cover it.
            String level = String.valueOf(signal.strength());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, level, chipX1 - 2 - font.width(level), ty - 9,
                    0xFFE2E5EB, true);
            graphics.pose().popPose();
        } else {
            // ON/OFF pip on the chip corner; momentary signals get a hollow
            // ring (solid while held)
            int px = chipX1 - 6;
            int py = chipY0 + 2;
            int pipColor = (signal.active() || held) ? theme.accent : theme.switchOff;
            graphics.fill(px, py, px + 4, py + 4, pipColor);
            // Momentary AND Timer pips read as a hollow ring while idle
            if ((signal.momentary() || signal.timed()) && !held) {
                graphics.fill(px + 1, py + 1, px + 3, py + 3, color);
            }
        }

        // Frequency count badge for scene signals
        if (signal.frequencies().size() > 1) {
            graphics.drawString(font, "x" + signal.frequencies().size(), x + 3, y + 3, 0xFFE2E5EB, true);
        }

        // Note glyph, tile top-left (below the badge when both show):
        // always visible when a note exists, on hover as the affordance
        if (signal.hasNote() || hovered || noteHovered) {
            int gy = y + (signal.frequencies().size() > 1 ? 13 : 3);
            int frame = noteHovered ? theme.glyphHover
                    : signal.hasNote() ? theme.textMuted : theme.textFaint;
            drawNoteGlyph(graphics, x + 3, gy, frame, theme.surfaceLo);
            ScreenTips.add(x + 3, gy, 7, 9, "gui.linktablet.note");
        }

        // Chain glyph (1.10.0 signal links), below the note SLOT — a
        // stable position whether the note affordance shows or not
        if (signal.hasLinks()) {
            int cy = y + (signal.frequencies().size() > 1 ? 13 : 3) + 12;
            drawChainGlyph(graphics, x + 3, cy, theme.textMuted, theme.surfaceLo);
        }

        // Name (ellipsized to tile width; full name via hover tooltip)
        String name = SignalTilePainter.label(graphics, font, theme, signal.name(), x, y, TILE_SIZE, TILE_GAP);
        if (hovered && !name.equals(signal.name())) {
            hoveredEllipsizedName = signal.name();
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
        ScreenTips.add(x, y, TILE_SIZE, TILE_SIZE, "gui.linktablet.tip.signal.new");
    }

    // ---- List mode -----------------------------------------------------

    private int rowX() {
        return panelLeft() + TILE_GAP;
    }

    private int rowWidth() {
        return panelWidth() - 2 * TILE_GAP;
    }

    private void renderListContent(GuiGraphics graphics, List<Signal> signals, int mouseX, int mouseY) {
        int total = totalTiles();
        int x = rowX();
        int w = rowWidth();
        for (int i = 0; i < total; i++) {
            int y = entryY(i);
            if (y + ROW_HEIGHT < gridTop() - 2 || y > gridBottom()) continue;

            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseY >= gridTop() - 2 && mouseY <= gridBottom();

            if (i < signals.size()) {
                if (reorderMode && i == dragIndex) {
                    renderPlaceholderRow(graphics, x, y, w);
                } else {
                    if (reorderMode) {
                        graphics.fill(x - 1, y - 1, x + w + 1, y + ROW_HEIGHT + 1, 0xFF8A93A6);
                    }
                    renderSignalRow(graphics, signals.get(i), x, y, w, hovered,
                            i == heldMomentary || timerFlashActive(i),
                            !reorderMode && overNoteGlyph(i, mouseX, mouseY));
                }
            } else {
                renderAddRow(graphics, x, y, w, hovered && !reorderMode);
                if (reorderMode) {
                    graphics.fill(x, y, x + w, y + ROW_HEIGHT, 0x8016181D);
                }
            }
        }
    }

    /** Empty slot the dragged signal was lifted out of. */
    private void renderPlaceholderRow(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + ROW_HEIGHT + 1, 0xFF5A6070);
        Chrome.plaque(graphics, x, y, w, ROW_HEIGHT, theme().surfaceLo);
    }

    private void renderSignalRow(GuiGraphics graphics, Signal signal, int x, int y, int w,
                              boolean hovered, boolean held, boolean noteHovered) {
        ScreenTheme theme = theme();
        // Shared painter (also drives the pinned mini-tablet's rows)
        String name = SignalRowPainter.paint(graphics, font, theme, signal, x, y, w, hovered, held);
        if (hovered && !name.equals(signal.name())) {
            hoveredEllipsizedName = signal.name();
        }

        // Note glyph, right before the control (mirrors noteGlyphListX) —
        // a GUI-only affordance, so it's overlaid here, not in the painter
        if (signal.hasNote() || hovered || noteHovered) {
            int controlReserve = signal.slider() ? LIST_SLIDER_W + font.width("15") + 4 : SWITCH_W;
            int gx = x + w - 4 - controlReserve - 12;
            int frame = noteHovered ? theme.glyphHover
                    : signal.hasNote() ? theme.textMuted : theme.textFaint;
            int gy = y + (ROW_HEIGHT - 9) / 2;
            drawNoteGlyph(graphics, gx, gy, frame, theme.surfaceLo);
            ScreenTips.add(gx, gy, 7, 9, "gui.linktablet.note");
        }

        // Chain glyph (1.10.0 signal links), left of the note slot
        if (signal.hasLinks()) {
            int controlReserve = signal.slider() ? LIST_SLIDER_W + font.width("15") + 4 : SWITCH_W;
            int gx = x + w - 4 - controlReserve - 12 - 11;
            drawChainGlyph(graphics, gx, y + (ROW_HEIGHT - 9) / 2, theme.textMuted, theme.surfaceLo);
        }
    }

    private void renderAddRow(GuiGraphics graphics, int x, int y, int w, boolean hovered) {
        ScreenTheme theme = theme();
        graphics.fill(x, y, x + w, y + ROW_HEIGHT, hovered ? theme.surfaceHi : theme.surfaceLo);
        drawThemedCentered(graphics,
                Component.translatable("gui.linktablet.add_signal_row"),
                x + w / 2, y + (ROW_HEIGHT - 8) / 2, theme.textMuted);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Open note window is modal: clicks inside go to it, any click
        // on the close button or outside saves + closes.
        // Note-window clicks never reach here — NoteWindows cancels them
        // at the screen-event layer before this method runs.

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
            if (overModeBtn(mouseX, mouseY, homeBtnX())) {
                // removed() releases any held momentary on the way out
                UISounds.tick(1.2F);
                ClientHooks.returnHome(view);
                return true;
            }
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
            if (overModeBtn(mouseX, mouseY, pinBtnX())) {
                if (OverlayPin.isPinned(view)) {
                    OverlayPin.unpin();
                    UISounds.tick(1.0F);
                } else {
                    OverlayPin.pin(view);
                    UISounds.tick(1.5F);
                }
                return true;
            }
            if (isBlockView() && overModeBtn(mouseX, mouseY, linkBtnX())) {
                boolean solo = soloScreen();
                UISounds.tick(solo ? 1.5F : 0.8F);
                // Currently solo → ask to re-link; currently linked →
                // unlink (dissolves the whole surface when merged)
                PacketDistributor.sendToServer(
                        new ModNetworking.SurfaceLinkPayload(target(), solo));
                return true;
            }
            if (isBlockView() && overModeBtn(mouseX, mouseY, lockBtnX())) {
                // Locking is free (an open GUI is already full config
                // trust); only UNLOCKING needs the wrench in hand
                boolean locked = lockedScreen();
                if (!locked || holdingWrench()) {
                    UISounds.tick(locked ? 1.7F : 0.6F);
                    PacketDistributor.sendToServer(
                            new ModNetworking.SetLockPayload(target(), !locked));
                } else {
                    UISounds.tick(0.7F); // deny — the wrench is the key
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
                if (idx >= 0 && idx < signals().size()) { // add tile inert
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
            handleEntryClick(index, button, mouseX, mouseY);
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

    // ---- Slider signals ---------------------------------------------------

    /** Horizontal span of an entry's slider control (screen-x px). */
    private int[] sliderSpan(int index) {
        if (listView()) {
            int right = rowX() + rowWidth() - 4;
            return new int[]{right - LIST_SLIDER_W, right};
        }
        int x = entryX(index);
        return new int[]{x + GRID_BAR_INSET, x + TILE_SIZE - GRID_BAR_INSET};
    }

    private int sliderValueFromMouse(int index, double mouseX) {
        List<Signal> signals = signals();
        if (index >= signals.size()) return 0; // signal vanished mid-drag; sendSliderValue bails too
        int[] span = sliderSpan(index);
        float rel = Mth.clamp((float) ((mouseX - span[0]) / (span[1] - span[0])), 0.0F, 1.0F);
        return signals.get(index).valueFromFraction(rel);
    }

    /** Sends only actual changes — a drag emits at most 16 packets. */
    private void sendSliderValue(int index, int value) {
        List<Signal> signals = signals();
        if (index >= signals.size()) return;
        Signal signal = signals.get(index);
        if (!signal.slider() || signal.strength() == value) return;
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

    private void handleEntryClick(int index, int button, double mouseX, double mouseY) {
        List<Signal> signals = signals();
        if (index < signals.size()) {
            if (button == 1) {
                // Right-click: edit (server opens the container menu)
                UISounds.page();
                PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                        SignalEditMenu.EditContext.plain(target(), index)));
            } else if (button == 0) {
                Signal signal = signals.get(index);
                if (overNoteGlyph(index, mouseX, mouseY)) {
                    openNote(index);
                    return;
                }
                String game = signal.secretGameId();
                if (game != null) {
                    UISounds.open();
                    minecraft.setScreen(SecretGames.create(game, view, true));
                    return;
                }
                if (signal.slider()) {
                    // Click sets the value from position; keep dragging to sweep
                    draggingSlider = index;
                    UISounds.tick(1.2F);
                    sendSliderValue(index, sliderValueFromMouse(index, mouseX));
                    return;
                }
                if (signal.timed()) {
                    // Tap: the server runs (or restarts) the timed pulse;
                    // a short client-side pressed flash sells the tap
                    UISounds.toggle(true);
                    timerFlash.put(index, net.minecraft.Util.getMillis() + 300);
                    PacketDistributor.sendToServer(
                            new ModNetworking.TimedSignalPayload(target(), index));
                    return;
                }
                if (signal.momentary()) {
                    // Press-and-hold: transmits until mouse release
                    UISounds.toggle(true);
                    heldMomentary = index;
                    PacketDistributor.sendToServer(
                            new ModNetworking.MomentarySignalPayload(target(), index, true));
                } else {
                    // Left-click: toggle
                    UISounds.toggle(!signal.active());
                    PacketDistributor.sendToServer(
                            new ModNetworking.ToggleSignalPayload(target(), index));
                }
            }
        } else if (button == 0) {
            if (signals.size() < view.maxSignals()) {
                UISounds.page();
                PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                        SignalEditMenu.EditContext.plain(target(), -1)));
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
            List<Signal> signals = signals();
            if (draggingSlider < signals.size()) {
                UISounds.tick(1.0F + signals.get(draggingSlider).strength() / 15.0F);
            }
            draggingSlider = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void releaseMomentary() {
        if (heldMomentary == -1) return;
        PacketDistributor.sendToServer(
                new ModNetworking.MomentarySignalPayload(target(), heldMomentary, false));
        heldMomentary = -1;
    }

    @Override
    public void removed() {
        // Note windows live in NoteWindows and survive this screen.
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
