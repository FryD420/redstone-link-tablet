package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 🪶 (1.8.0, unadvertised): "Flappy". One button, endless cogwheel
 * gaps, instant regret. Best = gaps cleared.
 */
public class FlappyScreen extends ArcadeScreen {

    private static final int W = 120;
    private static final int H = 140;
    private static final int BIRD_X = 28;
    private static final int BIRD = 6;
    private static final int PIPE_W = 12;
    private static final int GAP = 36;
    private static final int SPACING = 55;

    private final RandomSource random = RandomSource.create();
    /** Pipe pairs as {x, gapTop, passedFlag}. */
    private final Deque<int[]> pipes = new ArrayDeque<>();
    private double birdY, velY;
    private double speed;
    private int score;
    private boolean waiting;
    private boolean over;
    private boolean newBest;

    public FlappyScreen(AppView view, boolean returnToTablet) {
        super("flappy", view, returnToTablet);
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
        pipes.clear();
        birdY = H / 2.0;
        velY = 0;
        speed = 1.4;
        score = 0;
        waiting = true;
        over = false;
        newBest = false;
        for (int x = W + 20; x < W + 20 + SPACING * 3; x += SPACING) {
            pipes.addLast(new int[]{x, gapFor(), 0});
        }
    }

    private int gapFor() {
        return 12 + random.nextInt(H - GAP - 24);
    }

    private void flap() {
        velY = -2.9;
        UISounds.tick(1.4F);
    }

    @Override
    public void tick() {
        if (over || waiting) return;
        velY += 0.35;
        birdY += velY;
        if (birdY < 0 || birdY + BIRD > H) {
            die();
            return;
        }
        boolean respawn = false;
        for (int[] pipe : pipes) {
            pipe[0] -= (int) Math.ceil(speed);
            if (pipe[2] == 0 && pipe[0] + PIPE_W < BIRD_X) {
                pipe[2] = 1;
                score++;
                speed = Math.min(2.6, speed + 0.03);
                UISounds.tick(1.0F + Math.min(score, 20) * 0.03F);
            }
            if (pipe[0] < BIRD_X + BIRD && pipe[0] + PIPE_W > BIRD_X
                    && (birdY < pipe[1] || birdY + BIRD > pipe[1] + GAP)) {
                die();
                return;
            }
            if (pipe[0] < -PIPE_W) respawn = true;
        }
        if (respawn) {
            pipes.pollFirst();
            pipes.addLast(new int[]{pipes.peekLast()[0] + SPACING, gapFor(), 0});
        }
    }

    private void die() {
        over = true;
        newBest = submitBest(score, false);
        if (newBest) {
            UISounds.confirm();
        } else {
            UISounds.delete();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        press();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) {
            press();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void press() {
        if (over) {
            UISounds.page();
            reset();
        } else if (waiting) {
            waiting = false;
            flap();
        } else {
            flap();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, score + " / " + Math.max(score, best()));
        int bx = boardX();
        int by = boardY();
        graphics.enableScissor(bx, by, bx + W, by + H);
        for (int[] pipe : pipes) {
            int x = bx + pipe[0];
            graphics.fill(x, by, x + PIPE_W, by + pipe[1], 0xFF8A6B2B);
            graphics.fill(x, by + pipe[1] + GAP, x + PIPE_W, by + H, 0xFF8A6B2B);
            graphics.fill(x + 2, by, x + PIPE_W - 2, by + pipe[1] - 2, 0xFFBA8F42);
            graphics.fill(x + 2, by + pipe[1] + GAP + 2, x + PIPE_W - 2, by + H, 0xFFBA8F42);
        }
        graphics.fill(bx + BIRD_X, by + (int) birdY, bx + BIRD_X + BIRD,
                by + (int) birdY + BIRD, theme().accent);
        graphics.disableScissor();
        if (waiting && !over) {
            graphics.drawCenteredString(font, "tap to flap", bx + W / 2, by + H / 2 + 20, 0xFF8A93A6);
        }
        if (over) {
            renderOverlay(graphics, "SPLAT", (newBest ? "new best: " : "gaps: ") + score,
                    "tap to restart");
        }
    }
}
