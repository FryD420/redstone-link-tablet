package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 🏃 (1.8.0, unadvertised): "Runner". The endless jump-over-things
 * game for when the train ride is longer than the factory tour.
 * Best = distance.
 */
public class RunnerScreen extends ArcadeScreen {

    private static final int W = 160;
    private static final int H = 70;
    private static final int GROUND = H - 12;
    private static final int RUNNER_X = 22;
    private static final int RUNNER = 10;

    private final RandomSource random = RandomSource.create();
    /** Obstacles as {x, w, h}. */
    private final List<int[]> obstacles = new ArrayList<>();
    private double runnerY = GROUND - RUNNER;
    private double velY;
    private double speed;
    private int ticks;
    private int untilNext;
    private boolean waiting;
    private boolean over;
    private boolean newBest;

    public RunnerScreen(AppView view, boolean returnToTablet) {
        super("runner", view, returnToTablet);
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
        obstacles.clear();
        runnerY = GROUND - RUNNER;
        velY = 0;
        speed = 2.0;
        ticks = 0;
        untilNext = 40;
        waiting = true;
        over = false;
        newBest = false;
    }

    private boolean grounded() {
        return runnerY >= GROUND - RUNNER - 0.01;
    }

    private void jump() {
        if (grounded()) {
            velY = -3.6;
            UISounds.tick(1.3F);
        }
    }

    @Override
    public void tick() {
        if (over || waiting) return;
        ticks++;
        speed = Math.min(4.2, 2.0 + ticks / 400.0);
        velY += 0.32;
        runnerY = Math.min(GROUND - RUNNER, runnerY + velY);
        if (--untilNext <= 0) {
            boolean tall = random.nextFloat() < 0.25F;
            obstacles.add(new int[]{W, 6 + random.nextInt(6), tall ? 16 : 9});
            untilNext = 28 + random.nextInt(34);
        }
        for (Iterator<int[]> it = obstacles.iterator(); it.hasNext(); ) {
            int[] ob = it.next();
            ob[0] -= (int) Math.ceil(speed);
            if (ob[0] + ob[1] < 0) {
                it.remove();
                continue;
            }
            if (ob[0] < RUNNER_X + RUNNER - 1 && ob[0] + ob[1] > RUNNER_X + 1
                    && runnerY + RUNNER > GROUND - ob[2]) {
                over = true;
                newBest = submitBest(ticks / 4, false);
                if (newBest) {
                    UISounds.confirm();
                } else {
                    UISounds.delete();
                }
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        press();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32 || keyCode == 265 || keyCode == 87) {
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
            jump();
        } else {
            jump();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int distance = ticks / 4;
        renderCabinet(graphics, distance + " / " + Math.max(distance, best()));
        int bx = boardX();
        int by = boardY();
        graphics.fill(bx, by + GROUND, bx + W, by + GROUND + 2, 0xFF4A505C);
        // Scrolling ground ticks sell the speed
        int phase = (int) (ticks * speed) % 16;
        for (int x = -phase; x < W; x += 16) {
            if (x >= 0) {
                graphics.fill(bx + x, by + GROUND + 4, bx + x + 6, by + GROUND + 6, 0xFF33363D);
            }
        }
        graphics.fill(bx + RUNNER_X, by + (int) runnerY,
                bx + RUNNER_X + RUNNER, by + (int) runnerY + RUNNER, theme().accent);
        for (int[] ob : obstacles) {
            graphics.fill(bx + ob[0], by + GROUND - ob[2],
                    bx + Math.min(W, ob[0] + ob[1]), by + GROUND, 0xFF8A6B2B);
        }
        if (waiting && !over) {
            graphics.drawCenteredString(font, "tap to run", bx + W / 2, by + 14, 0xFF8A93A6);
        }
        if (over) {
            renderOverlay(graphics, "TRIPPED",
                    (newBest ? "new best: " : "distance: ") + distance, "tap to restart");
        }
    }
}
