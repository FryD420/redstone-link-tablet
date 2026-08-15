package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.Programs;
import com.modpack.linktablet.api.TabletProgram;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The tablet's home screen (1.10.0 "OS" framework): a grid of launchable
 * {@link Program} tiles drawn with the same painter as the signal grid,
 * plus the DEVICE-level header row (theme, overlay pin, solo link,
 * grid/list) — the launcher owns the standard features, not the Signals
 * app (user decision after the first kiosk test). ESC leaves the tablet
 * entirely — home is the bottom of the nav stack. The arcade never
 * appears here; its residents keep their own door.
 */
public class LauncherScreen extends Screen {

    /** Reuses the signal grid's tile metrics so the two screens line up. */
    private static final int TILE = TabletScreen.TILE_SIZE;
    private static final int GAP = TabletScreen.TILE_GAP;

    /** Header space (title plaque) and bottom padding, as TabletScreen. */
    private static final int HEADER = 34;
    private static final int BOTTOM_PAD = 8;
    private static final int MIN_PANEL_WIDTH = 148;
    private static final int MODE_BTN_SIZE = 12;

    private final SignalView view;
    /** Theme dropdown open (swallows clicks, like TabletScreen's). */
    private boolean themePopupOpen = false;
    /** Tile scroll (1.10.0, third pass: 19 game apps can overflow). */
    private double scroll = 0;

    public LauncherScreen(SignalView view) {
        super(Component.translatable("gui.linktablet.tablet.title"));
        this.view = view;
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    // ------------------------------------------------------------------
    // Layout — tiles come from the TABLET's home roster (1.10.0
    // settable roster), read live so drawer edits reflow immediately
    // once the server echo lands; wraps at 4 like the signal grid
    // ------------------------------------------------------------------

    private List<TabletProgram> homeApps() {
        return view.homeApps();
    }

    /** Tiles including the App Store door — always the LAST entry, not
     * part of the stored roster (see {@link #storeTileIndex}). */
    private int totalTiles() {
        return homeApps().size() + 1;
    }

    /** Index the store tile renders/hit-tests at. */
    private int storeTileIndex() {
        return homeApps().size();
    }

    /** Home screen list mode (1.10.0 user feedback: the list button
     * used to do nothing here) — same client pref as the signals grid. */
    private boolean listView() {
        return ClientPrefs.listView();
    }

    private int columns() {
        return Mth.clamp(totalTiles(), 1, TabletScreen.MAX_COLUMNS);
    }

    private int rows() {
        return Mth.positiveCeilDiv(totalTiles(), columns());
    }

    private int gridNaturalWidth() {
        return columns() * TILE + (columns() + 1) * GAP;
    }

    private int panelWidth() {
        // Four left buttons (theme/pin/link + Home cluster) need the same
        // wider floor TabletScreen grew for its Home button
        int w = listView() ? TabletScreen.LIST_WIDTH : gridNaturalWidth();
        return Math.max(w, MIN_PANEL_WIDTH + MODE_BTN_SIZE + 4);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int gridLeft() {
        return panelLeft() + (panelWidth() - gridNaturalWidth()) / 2;
    }

    private int contentHeight() {
        return listView()
                ? totalTiles() * TabletScreen.LIST_STRIDE - TabletScreen.ROW_GAP
                : rows() * TabletScreen.ROW_STRIDE - GAP;
    }

    private int bodyHeight() {
        // Caps to the window and scrolls, like the signal grid
        return Math.min(HEADER + contentHeight() + BOTTOM_PAD, height - 28);
    }

    private int bodyTop() {
        // Floor of 24: headroom for the title plaque's own band above
        // the panel (the TabletScreen rule — keep them identical)
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

    private int rowX() {
        return panelLeft() + GAP;
    }

    private int rowWidth() {
        return panelWidth() - 2 * GAP;
    }

    private int tileX(int i) {
        return listView() ? rowX() : gridLeft() + GAP + (i % columns()) * (TILE + GAP);
    }

    private int tileY(int i) {
        return gridTop() - (int) scroll + (listView()
                ? i * TabletScreen.LIST_STRIDE
                : (i / columns()) * TabletScreen.ROW_STRIDE);
    }

    private int tileIndexAt(double mouseX, double mouseY) {
        if (mouseY < gridTop() - 2 || mouseY > gridBottom()) return -1;
        for (int i = 0; i < totalTiles(); i++) {
            int x = tileX(i);
            int y = tileY(i);
            int w = listView() ? rowWidth() : TILE;
            int h = listView() ? TabletScreen.ROW_HEIGHT : TILE;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Header buttons — same conventions as TabletScreen (12px glyphs at
    // bodyTop()+8, 4px gaps); glyph art shared via HeaderGlyphs
    // ------------------------------------------------------------------

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    /** The drawer button is gone (user decision, first test pass) —
     * apps come and go through the App Store tile instead. */
    private int themeBtnX() {
        return panelLeft() + 4;
    }

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

    private int listBtnX() {
        return panelLeft() + panelWidth() - MODE_BTN_SIZE - 2;
    }

    private int gridBtnX() {
        return listBtnX() - MODE_BTN_SIZE - 4;
    }

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

    private int glyphColor(boolean active, boolean hovered) {
        ScreenTheme theme = theme();
        if (active) return theme.accent;
        return hovered ? theme.glyphHover : theme.textFaint;
    }

    // ------------------------------------------------------------------
    // Program tile art — the dress table lives on the enum so the kiosk
    // face and the drawer read the same colors/icons
    // ------------------------------------------------------------------

    private static ItemStack icon(TabletProgram program) {
        net.minecraft.resources.ResourceLocation id = program.iconItem();
        if (id == null) return ItemStack.EMPTY;
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
    }

    private static Component label(TabletProgram program) {
        return program.displayName();
    }

    // ------------------------------------------------------------------
    // Render + input
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        int left = panelLeft();
        int top = bodyTop();

        // Same body chrome as the signal grid: themed canvas in a wood
        // rail frame, title on a plaque in its OWN band above the panel
        Chrome.panel(graphics, left - 6, top - 2, panelWidth() + 12, bodyHeight() + 4, theme);
        Component titleText = view.displayName();
        int titleW = font.width(titleText);
        Chrome.plaque(graphics, width / 2 - titleW / 2 - 6, top - 20, titleW + 12, 18, theme.rowBg);
        graphics.drawString(font, titleText, width / 2 - titleW / 2, top - 15,
                theme.textPrimary, theme.textShadow);
        renderModeButtons(graphics, mouseX, mouseY);
        Chrome.railH(graphics, left - 4, top + HEADER - 8, panelWidth() + 8, theme.bodyOuter);

        scroll = Mth.clamp(scroll, 0, maxScroll());
        List<TabletProgram> roster = homeApps();
        graphics.enableScissor(left - 4, gridTop() - 2, left + panelWidth() + 4, gridBottom());
        for (int i = 0; i < totalTiles(); i++) {
            boolean store = i == storeTileIndex();
            TabletProgram program = store ? Program.STORE : roster.get(i);
            int x = tileX(i);
            int y = tileY(i);
            int h = listView() ? TabletScreen.ROW_HEIGHT : TILE;
            if (y + h < gridTop() - 2 || y > gridBottom()) continue;
            boolean hovered = !themePopupOpen && tileIndexAt(mouseX, mouseY) == i;
            // Viewport-gated (Fix 5): a tile scrolled only partially into
            // view is scissor-clipped and unclickable at its off-screen edge.
            boolean tileVisible = y >= gridTop() - 2 && y + h <= gridBottom();
            if (listView()) {
                renderProgramRow(graphics, theme, program, x, y, hovered);
                if (store && tileVisible) {
                    addBackgroundTip(x, y, rowWidth(), TabletScreen.ROW_HEIGHT, "gui.linktablet.tip.store");
                }
            } else {
                SignalTilePainter.base(graphics, theme, x, y, TILE, program.chipColor(), false, hovered);
                ItemStack icon = icon(program);
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, x + (TILE - 16) / 2, y + (TILE - 16) / 2);
                }
                SignalTilePainter.label(graphics, font, theme, label(program).getString(), x, y, TILE, GAP);
                if (store && tileVisible) {
                    addBackgroundTip(x, y, TILE, TILE, "gui.linktablet.tip.store");
                }
            }
        }
        graphics.disableScissor();

        if (themePopupOpen) {
            HeaderGlyphs.themePopup(graphics, font, themePopupX(), themePopupY(),
                    theme, mouseX, mouseY);
        }

        // Tooltips last, on top of everything
        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    /**
     * Registers a tooltip for a CONTENT-AREA control (the store tile) —
     * suppressed while the theme popup is open, since the popup panel
     * overlaps the tile grid and is drawn AFTER these controls register
     * (mirrors {@code SignalEditScreen#addBackgroundTip} /
     * {@code TabletScreen}'s twin). Final-review Fix 2.
     */
    private void addBackgroundTip(int x, int y, int w, int h, String key) {
        if (themePopupOpen) return;
        ScreenTips.add(x, y, w, h, key);
    }

    /** Home entry as a list row (1.10.0 user feedback): chip + icon +
     * name, full panel width — the signals list's proportions. */
    private void renderProgramRow(GuiGraphics graphics, ScreenTheme theme, TabletProgram program,
                                  int x, int y, boolean hovered) {
        int w = rowWidth();
        int h = TabletScreen.ROW_HEIGHT;
        Chrome.plaque(graphics, x, y, w, h, hovered ? theme.rowBgHover : theme.rowBg);
        graphics.fill(x + 4, y + 4, x + h - 4, y + h - 4, program.chipColor() | 0xFF000000);
        ItemStack icon = icon(program);
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + (h - 16) / 2, y + (h - 16) / 2);
        }
        graphics.drawString(font, label(program), x + h + 4, y + (h - 8) / 2,
                theme.textPrimary, theme.textShadow);
    }

    private void renderModeButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = modeBtnY();
        boolean list = ClientPrefs.listView();
        HeaderGlyphs.themePalette(graphics, themeBtnX(), y,
                glyphColor(themePopupOpen, overModeBtn(mouseX, mouseY, themeBtnX())));
        if (!themePopupOpen) {
            ScreenTips.glyph(themeBtnX(), modeBtnY(), "gui.linktablet.theme.title");
        }
        // The launcher's pin pins the LAUNCHER — a home-roster dock
        // overlay (user decision; each app screen pins itself)
        HeaderGlyphs.pin(graphics, pinBtnX(), y,
                glyphColor(OverlayPin.isPinned(view, Program.LAUNCHER),
                        overModeBtn(mouseX, mouseY, pinBtnX())));
        if (!themePopupOpen) {
            ScreenTips.glyph(pinBtnX(), modeBtnY(), OverlayPin.isPinned(view, Program.LAUNCHER)
                    ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
        }
        if (isBlockView()) {
            boolean solo = soloScreen();
            HeaderGlyphs.link(graphics, linkBtnX(), y,
                    glyphColor(solo, overModeBtn(mouseX, mouseY, linkBtnX())), solo);
            if (!themePopupOpen) {
                ScreenTips.glyph(linkBtnX(), modeBtnY(), solo
                        ? "gui.linktablet.tip.link" : "gui.linktablet.tip.unlink");
            }
            boolean locked = lockedScreen();
            HeaderGlyphs.lock(graphics, lockBtnX(), y,
                    glyphColor(locked, overModeBtn(mouseX, mouseY, lockBtnX())), locked);
            if (!themePopupOpen) {
                ScreenTips.glyph(lockBtnX(), modeBtnY(), locked
                        ? "gui.linktablet.lock.unlock" : "gui.linktablet.lock.lock");
            }
        }
        HeaderGlyphs.grid(graphics, gridBtnX(), y,
                glyphColor(!list, overModeBtn(mouseX, mouseY, gridBtnX())));
        ScreenTips.glyph(gridBtnX(), modeBtnY(), "gui.linktablet.view.grid");
        HeaderGlyphs.list(graphics, listBtnX(), y,
                glyphColor(list, overModeBtn(mouseX, mouseY, listBtnX())));
        ScreenTips.glyph(listBtnX(), modeBtnY(), "gui.linktablet.view.list");
    }

    private void sendHome(List<TabletProgram> home) {
        PacketDistributor.sendToServer(new ModNetworking.SetHomeAppsPayload(
                view.target(), Programs.keys(home)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Open theme popup swallows every click until it closes —
        // identical mechanics to TabletScreen's dropdown
        if (themePopupOpen) {
            int px = themePopupX();
            int py = themePopupY();
            ScreenTheme[] themes = ScreenTheme.values();
            if (mouseX >= px && mouseX < px + HeaderGlyphs.THEME_POPUP_W
                    && mouseY >= py && mouseY < py + themes.length * HeaderGlyphs.THEME_ROW_H) {
                ScreenTheme picked = themes[(int) ((mouseY - py) / HeaderGlyphs.THEME_ROW_H)];
                if (picked != theme()) {
                    UISounds.tick(1.4F);
                    PacketDistributor.sendToServer(
                            new ModNetworking.SetThemePayload(view.target(), picked));
                }
            }
            themePopupOpen = false;
            return true;
        }

        // Right-click a home tile: remove it (Android long-press
        // stand-in); the store door is not removable
        if (button == 1) {
            int index = tileIndexAt(mouseX, mouseY);
            if (index == storeTileIndex()) {
                UISounds.tick(0.7F);
                return true;
            }
            if (index >= 0) {
                List<TabletProgram> home = new java.util.ArrayList<>(homeApps());
                if (home.size() > 1) {
                    TabletProgram removed = home.remove(index);
                    sendHome(home);
                    UISounds.tick(1.0F);
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.translatable(
                                "message.linktablet.home_removed", label(removed)), true);
                    }
                } else {
                    UISounds.tick(0.7F);
                }
                return true;
            }
        }

        if (button == 0) {
            if (overModeBtn(mouseX, mouseY, themeBtnX())) {
                themePopupOpen = true;
                UISounds.tick(1.3F);
                return true;
            }
            if (overModeBtn(mouseX, mouseY, pinBtnX())) {
                if (OverlayPin.isPinned(view, Program.LAUNCHER)) {
                    OverlayPin.unpin();
                    UISounds.tick(1.0F);
                } else {
                    OverlayPin.pin(view, Program.LAUNCHER);
                    UISounds.tick(1.5F);
                }
                return true;
            }
            if (isBlockView() && overModeBtn(mouseX, mouseY, linkBtnX())) {
                boolean solo = soloScreen();
                UISounds.tick(solo ? 1.5F : 0.8F);
                PacketDistributor.sendToServer(
                        new ModNetworking.SurfaceLinkPayload(view.target(), solo));
                return true;
            }
            if (isBlockView() && overModeBtn(mouseX, mouseY, lockBtnX())) {
                // Locking is free (an open GUI is already full config
                // trust); only UNLOCKING needs the wrench in hand
                boolean locked = lockedScreen();
                if (!locked || holdingWrench()) {
                    UISounds.tick(locked ? 1.7F : 0.6F);
                    PacketDistributor.sendToServer(
                            new ModNetworking.SetLockPayload(view.target(), !locked));
                } else {
                    UISounds.tick(0.7F); // deny — the wrench is the key
                }
                return true;
            }
            if (overModeBtn(mouseX, mouseY, gridBtnX())) {
                if (ClientPrefs.listView()) UISounds.tick(1.8F);
                ClientPrefs.setListView(false);
                PacketDistributor.sendToServer(
                        new ModNetworking.ScreenLayoutPayload(view.target(), false));
                return true;
            }
            if (overModeBtn(mouseX, mouseY, listBtnX())) {
                if (!ClientPrefs.listView()) UISounds.tick(1.8F);
                ClientPrefs.setListView(true);
                PacketDistributor.sendToServer(
                        new ModNetworking.ScreenLayoutPayload(view.target(), true));
                return true;
            }
            int index = tileIndexAt(mouseX, mouseY);
            if (index == storeTileIndex()) {
                ClientHooks.openProgram(Program.STORE, view);
                return true;
            }
            if (index >= 0) {
                ClientHooks.openProgram(homeApps().get(index), view);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * 16, 0, maxScroll());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
