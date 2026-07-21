package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * 🔢 (1.8.0, unadvertised): "2048". Slide, merge, regret the corner
 * you ruined. Best = score (sum of all merges).
 */
public class G2048Screen extends ArcadeScreen {

    private static final int N = 4;
    private static final int CELL = 26;

    /** Tile fills by exponent (1 = "2"), wrapping past the ladder. */
    private static final int[] TILE_COLORS = {
            0, 0xFF4A505C, 0xFF5A6478, 0xFF8A6B2B, 0xFFBA8F42, 0xFFC9702E,
            0xFFC94F36, 0xFFC93C63, 0xFF8932B8, 0xFF3C44AA, 0xFF3AB3DA, 0xFF169C9C};

    private final RandomSource random = RandomSource.create();
    /** Exponents; 0 = empty, 1 = "2", 2 = "4"... */
    private final int[] board = new int[N * N];
    private int score;
    private boolean over;
    private boolean newBest;

    public G2048Screen(AppView view, boolean returnToTablet) {
        super("2048", view, returnToTablet);
        reset();
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
        java.util.Arrays.fill(board, 0);
        score = 0;
        over = false;
        newBest = false;
        spawn();
        spawn();
    }

    private void spawn() {
        int free = 0;
        for (int cell : board) {
            if (cell == 0) free++;
        }
        if (free == 0) return;
        int pick = random.nextInt(free);
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0 && pick-- == 0) {
                board[i] = random.nextFloat() < 0.9F ? 1 : 2;
                return;
            }
        }
    }

    /**
     * Slides one line toward index 0, merging equal neighbors once.
     * {@code line} holds board indices in traversal order.
     */
    private boolean slide(int[] line) {
        int[] values = new int[N];
        boolean[] mergedAt = new boolean[N];
        int out = 0;
        boolean moved = false;
        for (int i = 0; i < N; i++) {
            int v = board[line[i]];
            if (v == 0) continue;
            if (out > 0 && values[out - 1] == v && !mergedAt[out - 1]) {
                values[out - 1]++;
                mergedAt[out - 1] = true;
                score += 1 << values[out - 1];
                moved = true;
            } else {
                values[out++] = v;
            }
        }
        for (int i = 0; i < N; i++) {
            if (board[line[i]] != values[i]) moved = true;
            board[line[i]] = values[i];
        }
        return moved;
    }

    private void move(int dx, int dy) {
        if (over) return;
        boolean moved = false;
        for (int lane = 0; lane < N; lane++) {
            int[] line = new int[N];
            for (int i = 0; i < N; i++) {
                int x = dx != 0 ? (dx > 0 ? N - 1 - i : i) : lane;
                int y = dy != 0 ? (dy > 0 ? N - 1 - i : i) : lane;
                if (dx != 0) {
                    y = lane;
                } else {
                    x = lane;
                }
                line[i] = y * N + x;
            }
            moved |= slide(line);
        }
        if (!moved) return;
        UISounds.tick(1.2F);
        spawn();
        if (!anyMove()) {
            over = true;
            newBest = submitBest(score, false);
            if (newBest) {
                UISounds.confirm();
            } else {
                UISounds.delete();
            }
        }
    }

    private boolean anyMove() {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0) return true;
            int x = i % N;
            int y = i / N;
            if (x + 1 < N && board[i] == board[i + 1]) return true;
            if (y + 1 < N && board[i] == board[i + N]) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 265, 87 -> move(0, -1);
            case 264, 83 -> move(0, 1);
            case 263, 65 -> move(-1, 0);
            case 262, 68 -> move(1, 0);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            reset();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, score + " / " + Math.max(score, best()));
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < board.length; i++) {
            int x = bx + (i % N) * CELL;
            int y = by + (i / N) * CELL;
            int exp = board[i];
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1,
                    exp == 0 ? 0xFF23262C : TILE_COLORS[Math.min(exp, TILE_COLORS.length - 1)]);
            if (exp > 0) {
                String s = Integer.toString(1 << exp);
                graphics.drawString(font, s, x + (CELL - font.width(s)) / 2,
                        y + (CELL - 8) / 2, 0xFFF2F4F8, false);
            }
        }
        if (over) {
            renderOverlay(graphics, "NO MOVES",
                    (newBest ? "new best: " : "score: ") + score, "tap to restart");
        }
    }
}
