package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * 🐸 (1.8.0, unadvertised): "Crossing". Hop across six conveyor lanes
 * of moving crates without getting swept. Every crossing scores and
 * speeds the belts up. Best = crossings.
 */
public class CrossingScreen extends ArcadeScreen {

    private static final int STEP = 14;
    private static final int LANES = 6;
    private static final int COLS = 9;
    private static final int W = COLS * STEP;
    private static final int H = (LANES + 2) * STEP;
    private static final int CRATE_W = 18;

    private final RandomSource random = RandomSource.create();
    /** Per lane: {offsetTenths, speedTenths (signed), gapPx}. */
    private final int[][] lanes = new int[LANES][];
    private int playerCol;
    private int playerRow; // 0 = top (goal), LANES+1 = start
    private int score;
    private boolean over;
    private boolean newBest;
    private double speedScale;

    public CrossingScreen(AppView view, boolean returnToTablet) {
        super("crossing", view, returnToTablet);
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
        score = 0;
        over = false;
        newBest = false;
        speedScale = 1.0;
        buildLanes();
        respawn();
    }

    private void buildLanes() {
        for (int i = 0; i < LANES; i++) {
            int dir = i % 2 == 0 ? 1 : -1;
            lanes[i] = new int[]{random.nextInt(W * 10),
                    dir * (7 + random.nextInt(8)), 26 + random.nextInt(18)};
        }
    }

    private void respawn() {
        playerCol = COLS / 2;
        playerRow = LANES + 1;
    }

    @Override
    public void tick() {
        if (over) return;
        for (int[] lane : lanes) {
            lane[0] += (int) (lane[1] * speedScale);
        }
        if (playerRow >= 1 && playerRow <= LANES && hitByCrate(playerRow - 1)) {
            over = true;
            newBest = submitBest(score, false);
            if (newBest) {
                UISounds.confirm();
            } else {
                UISounds.delete();
            }
        }
    }

    /** Crates repeat every (CRATE_W + gap) along the lane. */
    private boolean hitByCrate(int lane) {
        int period = CRATE_W + lanes[lane][2];
        int px0 = playerCol * STEP + 3;
        int px1 = px0 + STEP - 6;
        int offset = Math.floorMod(lanes[lane][0] / 10, period);
        for (int cx = -period + offset; cx < W + period; cx += period) {
            if (px1 > cx && px0 < cx + CRATE_W) return true;
        }
        return false;
    }

    private void hop(int dx, int dy) {
        if (over) return;
        int nc = playerCol + dx;
        int nr = playerRow + dy;
        if (nc < 0 || nc >= COLS || nr < 0 || nr > LANES + 1) return;
        playerCol = nc;
        playerRow = nr;
        UISounds.tick(1.3F);
        if (playerRow == 0) {
            score++;
            speedScale = Math.min(2.2, speedScale + 0.12);
            UISounds.confirm();
            respawn();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 265, 87 -> hop(0, -1);
            case 264, 83 -> hop(0, 1);
            case 263, 65 -> hop(-1, 0);
            case 262, 68 -> hop(1, 0);
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
        // Safe strips top and bottom
        graphics.fill(bx, by, bx + W, by + STEP, 0xFF2C3A2C);
        graphics.fill(bx, by + (LANES + 1) * STEP, bx + W, by + H, 0xFF2C3A2C);
        for (int lane = 0; lane < LANES; lane++) {
            int y = by + (lane + 1) * STEP;
            graphics.fill(bx, y, bx + W, y + STEP, lane % 2 == 0 ? 0xFF26282E : 0xFF1F2126);
            int period = CRATE_W + lanes[lane][2];
            int offset = Math.floorMod(lanes[lane][0] / 10, period);
            for (int cx = -period + offset; cx < W + period; cx += period) {
                int x0 = Math.max(0, cx);
                int x1 = Math.min(W, cx + CRATE_W);
                if (x1 > x0) {
                    graphics.fill(bx + x0, y + 2, bx + x1, y + STEP - 2, 0xFF8A6B2B);
                    graphics.fill(bx + x0, y + 2, bx + x1, y + 4, 0xFFBA8F42);
                }
            }
        }
        int px = bx + playerCol * STEP + 3;
        int py = by + playerRow * STEP + 3;
        graphics.fill(px, py, px + STEP - 6, py + STEP - 6, theme().accent);
        if (over) {
            renderOverlay(graphics, "SWEPT AWAY",
                    (newBest ? "new best: " : "crossings: ") + score, "tap to restart");
        }
    }
}
