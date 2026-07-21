package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 🧱 (1.8.0, unadvertised): "Breakout" + a Brick icon. Mouse paddle,
 * five rows of bricks, three lives; clearing the wall rebuilds it
 * faster. The paddle is basically a slider app that fought back.
 * Best score in {@link ClientPrefs}.
 */
public class BreakoutScreen extends ArcadeScreen {

    private static final int W = 160;
    private static final int H = 120;
    private static final int BRICK_COLS = 8;
    private static final int BRICK_ROWS = 5;
    private static final int BRICK_W = 20;
    private static final int BRICK_H = 6;
    private static final int PADDLE_W = 30;
    private static final int PADDLE_H = 4;
    private static final int PADDLE_Y = H - 8;
    private static final int BALL = 4;

    private static final int[] ROW_COLORS = {
            0xFFC93C36, 0xFFF9801D, 0xFFFED83D, 0xFF80C71F, 0xFF3AB3DA};

    private final boolean[] bricks = new boolean[BRICK_COLS * BRICK_ROWS];
    private double paddleX = (W - PADDLE_W) / 2.0;
    private double ballX, ballY, velX, velY;
    private boolean serving;
    private int score;
    private int lives;
    private int level;
    private boolean over;
    private boolean newBest;

    public BreakoutScreen(AppView view, boolean returnToTablet) {
        super("breakout", view, returnToTablet);
        reset();
    }

    @Override
    protected int boardW() {
        return W;
    }

    @Override
    protected int boardH() {
        return H;
    }

    private void reset() {
        java.util.Arrays.fill(bricks, true);
        score = 0;
        lives = 3;
        level = 0;
        over = false;
        newBest = false;
        serve();
    }

    private void serve() {
        serving = true;
        ballX = paddleX + PADDLE_W / 2.0;
        ballY = PADDLE_Y - BALL;
    }

    private double speed() {
        return 2.0 + 0.4 * level + Math.min(0.8, score * 0.01);
    }

    private void launch() {
        serving = false;
        double angle = Math.toRadians(60 + 60 * Math.random()); // 60–120°, upward
        velX = speed() * Math.cos(angle) * (Math.random() < 0.5 ? 1 : -1);
        velY = -speed() * Math.sin(angle);
        UISounds.tick(1.4F);
    }

    @Override
    public void tick() {
        if (over) return;
        if (serving) {
            ballX = paddleX + PADDLE_W / 2.0;
            ballY = PADDLE_Y - BALL;
            return;
        }
        // Two substeps keep the ball out of walls at higher speeds
        for (int step = 0; step < 2 && !serving && !over; step++) {
            ballX += velX / 2;
            ballY += velY / 2;
            if (ballX <= 0) {
                ballX = 0;
                velX = Math.abs(velX);
            } else if (ballX >= W - BALL) {
                ballX = W - BALL;
                velX = -Math.abs(velX);
            }
            if (ballY <= 0) {
                ballY = 0;
                velY = Math.abs(velY);
            }
            // Paddle: bounce angle follows where the ball lands on it
            if (velY > 0 && ballY + BALL >= PADDLE_Y && ballY + BALL <= PADDLE_Y + PADDLE_H
                    && ballX + BALL > paddleX && ballX < paddleX + PADDLE_W) {
                double hit = (ballX + BALL / 2.0 - paddleX) / PADDLE_W - 0.5; // -0.5..0.5
                // Edge hits leave at 30°, center hits go straight up
                double angle = Math.toRadians(90 - 120 * hit);
                double s = speed();
                velX = s * Math.cos(angle);
                velY = -Math.abs(s * Math.sin(angle));
                ballY = PADDLE_Y - BALL;
                UISounds.tick(1.1F);
            }
            hitBricks();
            if (ballY > H) {
                if (--lives <= 0) {
                    over = true;
                    newBest = submitBest(score, false);
                    if (newBest) {
                        UISounds.confirm();
                    } else {
                        UISounds.delete();
                    }
                } else {
                    UISounds.delete();
                    serve();
                }
            }
        }
    }

    private void hitBricks() {
        int cx = (int) Math.floor((ballX + BALL / 2.0) / BRICK_W);
        int cy = (int) Math.floor((ballY + BALL / 2.0) / BRICK_H);
        if (cx < 0 || cx >= BRICK_COLS || cy < 0 || cy >= BRICK_ROWS) return;
        int index = cy * BRICK_COLS + cx;
        if (!bricks[index]) return;
        bricks[index] = false;
        score += BRICK_ROWS - cy; // top rows pay more
        UISounds.tick(1.2F + cy * 0.08F);
        velY = -velY; // simple vertical rebound reads fine at brick scale
        boolean any = false;
        for (boolean b : bricks) {
            any |= b;
        }
        if (!any) {
            level++;
            java.util.Arrays.fill(bricks, true);
            UISounds.confirm();
            serve();
        }
    }

    // ---- Input ---------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        paddleX = Mth.clamp(mouseX - boardX() - PADDLE_W / 2.0, 0, W - PADDLE_W);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            reset();
            return true;
        }
        if (serving) {
            launch();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) { // space serves too
            if (over) {
                reset();
            } else if (serving) {
                launch();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- Rendering -----------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        renderCabinet(graphics, score + " · " + "♥".repeat(Math.max(0, lives)));
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < bricks.length; i++) {
            if (!bricks[i]) continue;
            int x = bx + (i % BRICK_COLS) * BRICK_W;
            int y = by + (i / BRICK_COLS) * BRICK_H;
            graphics.fill(x + 1, y + 1, x + BRICK_W - 1, y + BRICK_H - 1,
                    ROW_COLORS[i / BRICK_COLS]);
        }
        graphics.fill(bx + (int) paddleX, by + PADDLE_Y,
                bx + (int) paddleX + PADDLE_W, by + PADDLE_Y + PADDLE_H, theme.accent);
        graphics.fill(bx + (int) ballX, by + (int) ballY,
                bx + (int) ballX + BALL, by + (int) ballY + BALL, 0xFFE8EAF0);
        if (serving && !over) {
            graphics.drawCenteredString(font, "tap to serve",
                    bx + W / 2, by + H / 2 + 12, 0xFF8A93A6);
        }
        if (over) {
            renderOverlay(graphics, "GAME OVER",
                    (newBest ? "new best: " : "score: ") + score, "tap to restart");
        }
    }
}
