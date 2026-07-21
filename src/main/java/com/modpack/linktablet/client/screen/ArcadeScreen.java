package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 🕹️ Shared scaffolding for the tablet's hidden games (1.8.0,
 * extracted from Snake): themed chrome frame with a title/tally
 * header, a board area, and the two launch paths' return trips (GUI →
 * tablet home, world tap → back to the world). All strings stay
 * literals — lang keys would spoil the hunt. Subclasses own the game;
 * this owns the cabinet.
 */
abstract class ArcadeScreen extends Screen {

    protected static final int PAD = 6;
    protected static final int HEADER = 16;

    protected final AppView view;
    /** Game id — also the best-score key and (capitalized) the title. */
    protected final String gameId;
    /** GUI launches return to the tablet home; world taps to the world. */
    private final boolean returnToTablet;

    protected ArcadeScreen(String id, AppView view, boolean returnToTablet) {
        super(Component.literal(
                id.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + id.substring(1)));
        this.gameId = id;
        this.view = view;
        this.returnToTablet = returnToTablet;
    }

    /** Persisted best for this game; 0 = never recorded. */
    protected int best() {
        return com.modpack.linktablet.client.ClientPrefs.gameBest(gameId);
    }

    /** Records a finished round; true when it set a new best. */
    protected boolean submitBest(int value, boolean lowerIsBetter) {
        int current = best();
        boolean better = lowerIsBetter ? current == 0 || value < current : value > current;
        if (better) {
            com.modpack.linktablet.client.ClientPrefs.setGameBest(gameId, value);
        }
        return better;
    }

    /** Board pixel size, excluding chrome. */
    protected abstract int boardW();

    protected abstract int boardH();

    protected ScreenTheme theme() {
        return view.theme();
    }

    protected int panelWidth() {
        return boardW() + PAD * 2;
    }

    protected int panelHeight() {
        return HEADER + boardH() + PAD * 2 + 4;
    }

    protected int left() {
        return (width - panelWidth()) / 2;
    }

    protected int top() {
        return (height - panelHeight()) / 2;
    }

    protected int boardX() {
        return left() + PAD;
    }

    protected int boardY() {
        return top() + HEADER + PAD;
    }

    /** Panel, header plaque (title left, tally right), rail, board bg. */
    protected void renderCabinet(GuiGraphics graphics, String tally) {
        ScreenTheme theme = theme();
        int l = left();
        int t = top();
        Chrome.panel(graphics, l - 6, t - 2, panelWidth() + 12, panelHeight() + 4, theme);
        Chrome.plaque(graphics, l, t + 2, panelWidth(), HEADER - 2, theme.rowBg);
        graphics.drawString(font, getTitle().getString().toUpperCase(java.util.Locale.ROOT),
                l + PAD, t + 5, theme.textPrimary, theme.textShadow);
        graphics.drawString(font, tally,
                l + panelWidth() - PAD - font.width(tally), t + 5,
                theme.textMuted, theme.textShadow);
        Chrome.railH(graphics, l, t + HEADER + 1, panelWidth(), theme.bodyOuter);
        graphics.fill(boardX() - 1, boardY() - 1,
                boardX() + boardW() + 1, boardY() + boardH() + 1, theme.screenBgOff);
    }

    /** Dim the board and center up to three lines of end-of-round text. */
    protected void renderOverlay(GuiGraphics graphics, String headline, String sub, String hint) {
        graphics.fill(boardX() - 1, boardY() - 1,
                boardX() + boardW() + 1, boardY() + boardH() + 1, 0xB0101216);
        int cx = boardX() + boardW() / 2;
        int cy = boardY() + boardH() / 2;
        graphics.drawCenteredString(font, headline, cx, cy - 12, theme().accent);
        graphics.drawCenteredString(font, sub, cx, cy, 0xFFE8EAF0);
        graphics.drawCenteredString(font, hint, cx, cy + 14, 0xFF8A93A6);
    }

    /** Same return-trip idiom as AppEditScreen for the GUI path. */
    @Override
    public void onClose() {
        minecraft.setScreen(returnToTablet ? new TabletScreen(view) : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
