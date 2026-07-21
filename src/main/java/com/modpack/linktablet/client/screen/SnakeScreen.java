package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 🐍 (1.7.1, unadvertised): opens from any app named "Snake" whose icon
 * is a Linked Controller — see {@code TabletScreen.isSnakeShortcut}.
 * Entirely client-side: no payloads, no lang keys (a lang entry would
 * spoil the hunt — all strings are literals on purpose), high score in
 * {@link ClientPrefs}. Chrome rules apply: surfaces blit the atlas,
 * the snake itself is procedural {@code fill()}.
 */
public class SnakeScreen extends Screen {

    private static final int COLS = 20;
    private static final int ROWS = 14;
    private static final int CELL = 8;
    private static final int PAD = 6;
    private static final int HEADER = 16;

    // GLFW key codes (same literal style as NoteWindows' ESC)
    private static final int K_UP = 265, K_DOWN = 264, K_LEFT = 263, K_RIGHT = 262;
    private static final int K_W = 87, K_A = 65, K_S = 83, K_D = 68;

    private final AppView view;
    /** GUI launches return to the tablet home; world taps back to the world. */
    private final boolean returnToTablet;
    private final RandomSource random = RandomSource.create();

    /** Snake cells, head first, packed x<<8|y; occupied mirrors it. */
    private final Deque<Integer> snake = new ArrayDeque<>();
    private final Set<Integer> occupied = new HashSet<>();
    private int dirX, dirY;
    private int nextDirX, nextDirY;
    private int foodX, foodY;
    private int score;
    private boolean gameOver;
    private boolean won;
    private boolean newBest;
    private int tickCount;

    public SnakeScreen(AppView view, boolean returnToTablet) {
        super(Component.literal("Snake"));
        this.view = view;
        this.returnToTablet = returnToTablet;
        reset();
    }

    private static int key(int x, int y) {
        return x << 8 | y;
    }

    private void reset() {
        snake.clear();
        occupied.clear();
        int startY = ROWS / 2;
        for (int i = 0; i < 3; i++) {
            int k = key(COLS / 2 - i, startY);
            snake.addLast(k);
            occupied.add(k);
        }
        dirX = 1;
        dirY = 0;
        nextDirX = 1;
        nextDirY = 0;
        score = 0;
        gameOver = false;
        won = false;
        newBest = false;
        tickCount = 0;
        spawnFood();
    }

    private void spawnFood() {
        if (occupied.size() >= COLS * ROWS) return; // board full — handled as a win
        do {
            foodX = random.nextInt(COLS);
            foodY = random.nextInt(ROWS);
        } while (occupied.contains(key(foodX, foodY)));
    }

    /** Speed ramps with score: every 4 ticks, then 3, then 2. */
    private int moveInterval() {
        return score < 6 ? 4 : score < 15 ? 3 : 2;
    }

    @Override
    public void tick() {
        if (gameOver) return;
        if (++tickCount % moveInterval() != 0) return;

        dirX = nextDirX;
        dirY = nextDirY;
        int head = snake.peekFirst();
        int hx = (head >> 8) + dirX;
        int hy = (head & 0xFF) + dirY;
        if (hx < 0 || hx >= COLS || hy < 0 || hy >= ROWS) {
            die();
            return;
        }

        boolean eating = hx == foodX && hy == foodY;
        if (!eating) {
            // The tail cell vacates this step, so it's a legal target
            occupied.remove(snake.pollLast());
        }
        int newHead = key(hx, hy);
        if (!occupied.add(newHead)) {
            die();
            return;
        }
        snake.addFirst(newHead);

        if (eating) {
            score++;
            UISounds.tick(1.0F + Math.min(score, 20) * 0.04F);
            if (occupied.size() >= COLS * ROWS) {
                won = true;
                die();
                return;
            }
            spawnFood();
        }
    }

    private void die() {
        gameOver = true;
        int best = ClientPrefs.snakeHigh();
        if (score > best) {
            ClientPrefs.setSnakeHigh(score);
            newBest = true;
            UISounds.confirm();
        } else {
            UISounds.delete();
        }
    }

    // ---- Input ---------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (gameOver && keyCode != 256) {
            UISounds.page();
            reset();
            return true;
        }
        int wantX = 0, wantY = 0;
        switch (keyCode) {
            case K_UP, K_W -> wantY = -1;
            case K_DOWN, K_S -> wantY = 1;
            case K_LEFT, K_A -> wantX = -1;
            case K_RIGHT, K_D -> wantX = 1;
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        // No reversing straight into yourself
        if (wantX != -dirX || wantY != -dirY) {
            nextDirX = wantX;
            nextDirY = wantY;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (gameOver) {
            UISounds.page();
            reset();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- Rendering -----------------------------------------------------

    private int panelWidth() {
        return COLS * CELL + PAD * 2;
    }

    private int panelHeight() {
        return HEADER + ROWS * CELL + PAD * 2 + 4;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = view.theme();
        int left = (width - panelWidth()) / 2;
        int top = (height - panelHeight()) / 2;

        Chrome.panel(graphics, left - 6, top - 2, panelWidth() + 12, panelHeight() + 4, theme);

        // Header plaque: title left, score / best right
        Chrome.plaque(graphics, left, top + 2, panelWidth(), HEADER - 2, theme.rowBg);
        graphics.drawString(font, "SNAKE", left + PAD, top + 5, theme.textPrimary, theme.textShadow);
        String tally = score + " / " + Math.max(score, ClientPrefs.snakeHigh());
        graphics.drawString(font, tally,
                left + panelWidth() - PAD - font.width(tally), top + 5,
                theme.textMuted, theme.textShadow);
        Chrome.railH(graphics, left, top + HEADER + 1, panelWidth(), theme.bodyOuter);

        // Board
        int bx = left + PAD;
        int by = top + HEADER + PAD;
        graphics.fill(bx - 1, by - 1, bx + COLS * CELL + 1, by + ROWS * CELL + 1, theme.screenBgOff);

        // Food: the first-frequency red, one texel inset
        graphics.fill(bx + foodX * CELL + 1, by + foodY * CELL + 1,
                bx + foodX * CELL + CELL - 1, by + foodY * CELL - 1 + CELL, TabletScreen.FREQ1_COLOR);

        // Snake: accent head, dimmed body
        boolean head = true;
        for (int k : snake) {
            int x = bx + (k >> 8) * CELL;
            int y = by + (k & 0xFF) * CELL;
            graphics.fill(x, y, x + CELL, y + CELL, head ? theme.accent : theme.accentDim);
            head = false;
        }

        if (gameOver) {
            graphics.fill(bx - 1, by - 1, bx + COLS * CELL + 1, by + ROWS * CELL + 1, 0xB0101216);
            String headline = won ? "YOU WIN?!" : "GAME OVER";
            String sub = newBest ? "new best: " + score : "score: " + score;
            int cx = bx + COLS * CELL / 2;
            int cy = by + ROWS * CELL / 2;
            graphics.drawCenteredString(font, headline, cx, cy - 12, theme.accent);
            graphics.drawCenteredString(font, sub, cx, cy, 0xFFE8EAF0);
            graphics.drawCenteredString(font, "tap to restart", cx, cy + 14, 0xFF8A93A6);
        }
    }

    // ---- Lifecycle -----------------------------------------------------

    /** GUI path: the AppEditScreen return-trip idiom. World path: out. */
    @Override
    public void onClose() {
        minecraft.setScreen(returnToTablet ? new TabletScreen(view) : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
