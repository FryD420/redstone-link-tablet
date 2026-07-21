package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 🏓 (1.8.0, unadvertised): "Pong". You on the left (mouse), a
 * slightly nearsighted machine on the right; first to five. Best =
 * career wins.
 */
public class PongScreen extends ArcadeScreen {

    private static final int W = 150;
    private static final int H = 90;
    private static final int PADDLE_H = 22;
    private static final int PADDLE_W = 3;
    private static final int BALL = 4;
    private static final int WIN_SCORE = 5;

    private final RandomSource random = RandomSource.create();
    private double playerY = (H - PADDLE_H) / 2.0;
    private double aiY = (H - PADDLE_H) / 2.0;
    private double ballX, ballY, velX, velY;
    private int playerScore, aiScore;
    private boolean serving = true;
    private boolean over;
    private boolean playerWon;
    private boolean newBest;

    public PongScreen(AppView view, boolean returnToTablet) {
        super("pong", view, returnToTablet);
        centerBall();
    }

    @Override
    protected int boardW() {
        return W;
    }

    @Override
    protected int boardH() {
        return H;
    }

    private void centerBall() {
        ballX = W / 2.0;
        ballY = H / 2.0;
        serving = true;
    }

    private void serve(boolean towardPlayer) {
        serving = false;
        double angle = Math.toRadians(20 + 40 * random.nextDouble());
        velX = 2.2 * (towardPlayer ? -1 : 1);
        velY = 1.6 * Math.sin(angle) * (random.nextBoolean() ? 1 : -1);
    }

    @Override
    public void tick() {
        if (over || serving) return;
        ballX += velX;
        ballY += velY;
        if (ballY <= 0) {
            ballY = 0;
            velY = Math.abs(velY);
        } else if (ballY >= H - BALL) {
            ballY = H - BALL;
            velY = -Math.abs(velY);
        }
        // The machine follows with a speed cap (its whole personality)
        double target = ballY + BALL / 2.0 - PADDLE_H / 2.0;
        double maxStep = 1.55 + 0.1 * (playerScore - aiScore);
        aiY += Mth.clamp(target - aiY, -maxStep, maxStep);
        aiY = Mth.clamp(aiY, 0, H - PADDLE_H);

        // Player paddle at x=2, machine at x=W-5
        if (velX < 0 && ballX <= 2 + PADDLE_W && ballX >= 2
                && ballY + BALL > playerY && ballY < playerY + PADDLE_H) {
            bounce(playerY);
            velX = Math.abs(velX) + 0.08;
            UISounds.tick(1.1F);
        }
        if (velX > 0 && ballX + BALL >= W - 5 && ballX + BALL <= W - 5 + PADDLE_W
                && ballY + BALL > aiY && ballY < aiY + PADDLE_H) {
            bounce(aiY);
            velX = -(Math.abs(velX) + 0.08);
            UISounds.tick(0.9F);
        }
        if (ballX < -BALL) {
            score(false);
        } else if (ballX > W) {
            score(true);
        }
    }

    private void bounce(double paddleY) {
        double hit = (ballY + BALL / 2.0 - paddleY) / PADDLE_H - 0.5;
        velY = 3.2 * hit;
    }

    private void score(boolean player) {
        if (player) {
            playerScore++;
        } else {
            aiScore++;
        }
        UISounds.tick(player ? 1.5F : 0.7F);
        if (playerScore >= WIN_SCORE || aiScore >= WIN_SCORE) {
            over = true;
            playerWon = playerScore >= WIN_SCORE;
            if (playerWon) {
                newBest = submitBest(best() + 1, false); // career wins
                UISounds.confirm();
            } else {
                UISounds.delete();
            }
        } else {
            centerBall();
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        playerY = Mth.clamp(mouseY - boardY() - PADDLE_H / 2.0, 0, H - PADDLE_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            playerScore = 0;
            aiScore = 0;
            over = false;
            newBest = false;
            centerBall();
            return true;
        }
        if (serving) {
            serve(random.nextBoolean());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, playerScore + " · " + aiScore);
        int bx = boardX();
        int by = boardY();
        // Center net
        for (int y = 2; y < H; y += 8) {
            graphics.fill(bx + W / 2 - 1, by + y, bx + W / 2, by + y + 4, 0xFF3A3F4B);
        }
        graphics.fill(bx + 2, by + (int) playerY, bx + 2 + PADDLE_W,
                by + (int) playerY + PADDLE_H, theme().accent);
        graphics.fill(bx + W - 5, by + (int) aiY, bx + W - 5 + PADDLE_W,
                by + (int) aiY + PADDLE_H, 0xFFC93C36);
        graphics.fill(bx + (int) ballX, by + (int) ballY,
                bx + (int) ballX + BALL, by + (int) ballY + BALL, 0xFFE8EAF0);
        if (serving && !over) {
            graphics.drawCenteredString(font, "tap to serve", bx + W / 2, by + H - 14, 0xFF8A93A6);
        }
        if (over) {
            renderOverlay(graphics, playerWon ? "YOU WIN" : "MACHINE WINS",
                    "career wins: " + best(), "tap to rematch");
        }
    }
}
