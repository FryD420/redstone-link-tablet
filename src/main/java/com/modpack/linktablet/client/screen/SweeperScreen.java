package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * 💣 (1.8.0, unadvertised): "Sweeper" (or "Mines") + a TNT icon.
 * Classic 9×9 / 10 mines: left-click reveals (the first click is
 * always safe — mines place after it, avoiding its neighborhood),
 * right-click flags. Best = fastest clear in seconds, in
 * {@link ClientPrefs}.
 */
public class SweeperScreen extends ArcadeScreen {

    private static final int N = 9;
    private static final int MINES = 10;
    private static final int CELL = 12;

    /** Classic number colors, brightened for the dark glass. */
    private static final int[] NUMBER_COLORS = {
            0, 0xFF6FA8FF, 0xFF6FDD6F, 0xFFFF6F6F, 0xFF9F8FFF,
            0xFFCC8888, 0xFF66CCCC, 0xFFDDDDDD, 0xFFAAAAAA};

    private final RandomSource random = RandomSource.create();
    private final boolean[] mine = new boolean[N * N];
    private final boolean[] open = new boolean[N * N];
    private final boolean[] flag = new boolean[N * N];
    private boolean placed;
    private boolean over;
    private boolean won;
    private boolean newBest;
    private int ticks;

    public SweeperScreen(AppView view, boolean returnToTablet) {
        super("sweeper", view, returnToTablet);
    }

    @Override
    protected int boardW() {
        return N * CELL;
    }

    @Override
    protected int boardH() {
        return N * CELL;
    }

    private void reset() {
        java.util.Arrays.fill(mine, false);
        java.util.Arrays.fill(open, false);
        java.util.Arrays.fill(flag, false);
        placed = false;
        over = false;
        won = false;
        newBest = false;
        ticks = 0;
    }

    /** Mines go down after the first click, never on or beside it. */
    private void placeMines(int safe) {
        int sx = safe % N;
        int sy = safe / N;
        int laid = 0;
        while (laid < MINES) {
            int i = random.nextInt(N * N);
            if (mine[i]) continue;
            int dx = Math.abs(i % N - sx);
            int dy = Math.abs(i / N - sy);
            if (dx <= 1 && dy <= 1) continue;
            mine[i] = true;
            laid++;
        }
        placed = true;
    }

    private int neighbors(int index) {
        int cx = index % N;
        int cy = index / N;
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if ((dx | dy) != 0 && x >= 0 && x < N && y >= 0 && y < N
                        && mine[y * N + x]) {
                    count++;
                }
            }
        }
        return count;
    }

    private void reveal(int index) {
        if (open[index] || flag[index]) return;
        open[index] = true;
        if (mine[index]) {
            over = true;
            UISounds.delete();
            return;
        }
        if (neighbors(index) == 0) {
            int cx = index % N;
            int cy = index / N;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x >= 0 && x < N && y >= 0 && y < N) {
                        reveal(y * N + x);
                    }
                }
            }
        }
    }

    private void checkWin() {
        for (int i = 0; i < N * N; i++) {
            if (!mine[i] && !open[i]) return;
        }
        over = true;
        won = true;
        newBest = submitBest(Math.max(1, ticks / 20), true);
        UISounds.confirm();
    }

    @Override
    public void tick() {
        if (placed && !over) ticks++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            reset();
            return true;
        }
        int cx = (int) Math.floor((mouseX - boardX()) / CELL);
        int cy = (int) Math.floor((mouseY - boardY()) / CELL);
        if (cx >= 0 && cx < N && cy >= 0 && cy < N) {
            int index = cy * N + cx;
            if (button == 1) {
                if (!open[index]) {
                    flag[index] ^= true;
                    UISounds.tick(flag[index] ? 1.6F : 1.0F);
                }
            } else if (button == 0 && !flag[index]) {
                if (!placed) placeMines(index);
                reveal(index);
                UISounds.tick(1.2F);
                if (!over) checkWin();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        int flags = 0;
        for (boolean f : flag) {
            if (f) flags++;
        }
        renderCabinet(graphics, (MINES - flags) + " · " + ticks / 20 + "s");
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < N * N; i++) {
            int x = bx + (i % N) * CELL;
            int y = by + (i / N) * CELL;
            if (open[i]) {
                graphics.fill(x, y, x + CELL, y + CELL, 0xFF1B1D22);
                if (mine[i]) {
                    graphics.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, 0xFFC93C36);
                    graphics.fill(x + 5, y + 5, x + CELL - 5, y + CELL - 5, 0xFF16181D);
                } else {
                    int n = neighbors(i);
                    if (n > 0) {
                        String s = Integer.toString(n);
                        graphics.drawString(font, s, x + (CELL - font.width(s)) / 2 + 1,
                                y + 2, NUMBER_COLORS[n], false);
                    }
                }
            } else {
                // Raised unopened cell
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFF4A505C);
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + 2, 0xFF616879);
                graphics.fill(x + 1, y + CELL - 2, x + CELL - 1, y + CELL - 1, 0xFF383D46);
                if (flag[i]) {
                    graphics.fill(x + 5, y + 3, x + 7, y + CELL - 3, 0xFFE8EAF0);
                    graphics.fill(x + 5, y + 3, x + 9, y + 6, theme.accent);
                }
                // Losing shows every mine under its cell
                if (over && !won && mine[i]) {
                    graphics.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, 0xFFC93C36);
                }
            }
        }
        if (over) {
            renderOverlay(graphics, won ? "SWEPT!" : "BOOM",
                    won ? (newBest ? "new best: " : "time: ") + Math.max(1, ticks / 20) + "s"
                        : "the factory forgives you",
                    "tap to restart");
        }
    }
}
