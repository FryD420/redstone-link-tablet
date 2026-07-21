package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 👾 (1.8.0, unadvertised): "Invaders". A marching grid, one cannon,
 * three lives, waves without end. Best = score.
 */
public class InvadersScreen extends ArcadeScreen {

    private static final int W = 150;
    private static final int H = 120;
    private static final int IN_COLS = 8;
    private static final int IN_ROWS = 4;
    private static final int IN_SIZE = 8;
    private static final int IN_GAP = 6;
    private static final int BASE_W = 12;
    private static final int BASE_H = 5;

    private final RandomSource random = RandomSource.create();
    private final boolean[] alive = new boolean[IN_COLS * IN_ROWS];
    private double swarmX, swarmY;
    private int swarmDir = 1;
    private double baseX = (W - BASE_W) / 2.0;
    /** {x, y} tenths; player bullet only one at a time. */
    private int[] shot;
    private final List<int[]> bombs = new ArrayList<>();
    private int score;
    private int lives;
    private int wave;
    private boolean over;
    private boolean newBest;

    public InvadersScreen(AppView view, boolean returnToTablet) {
        super("invaders", view, returnToTablet);
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
        lives = 3;
        wave = 0;
        over = false;
        newBest = false;
        newWave();
    }

    private void newWave() {
        java.util.Arrays.fill(alive, true);
        swarmX = 8;
        swarmY = 10 + Math.min(20, wave * 4);
        swarmDir = 1;
        shot = null;
        bombs.clear();
    }

    private double swarmSpeed() {
        int remaining = 0;
        for (boolean a : alive) {
            if (a) remaining++;
        }
        return (0.35 + 0.1 * wave) + 0.5 * (1.0 - remaining / (double) alive.length);
    }

    private int invaderX(int index) {
        return (int) swarmX + (index % IN_COLS) * (IN_SIZE + IN_GAP);
    }

    private int invaderY(int index) {
        return (int) swarmY + (index / IN_COLS) * (IN_SIZE + IN_GAP);
    }

    @Override
    public void tick() {
        if (over) return;
        // March; edge hit drops the swarm a row
        swarmX += swarmDir * swarmSpeed();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < alive.length; i++) {
            if (!alive[i]) continue;
            minX = Math.min(minX, invaderX(i));
            maxX = Math.max(maxX, invaderX(i) + IN_SIZE);
            maxY = Math.max(maxY, invaderY(i) + IN_SIZE);
        }
        if (minX == Integer.MAX_VALUE) {
            wave++;
            UISounds.confirm();
            newWave();
            return;
        }
        if (maxX >= W - 2 && swarmDir > 0 || minX <= 2 && swarmDir < 0) {
            swarmDir = -swarmDir;
            swarmY += 4;
        }
        if (maxY >= H - BASE_H - 4) {
            gameOver();
            return;
        }
        // Player shot
        if (shot != null) {
            shot[1] -= 38;
            if (shot[1] < 0) {
                shot = null;
            } else {
                int sx = shot[0] / 10;
                int sy = shot[1] / 10;
                for (int i = 0; i < alive.length; i++) {
                    if (alive[i] && sx >= invaderX(i) && sx < invaderX(i) + IN_SIZE
                            && sy >= invaderY(i) && sy < invaderY(i) + IN_SIZE) {
                        alive[i] = false;
                        score += 10 + (IN_ROWS - 1 - i / IN_COLS) * 5;
                        shot = null;
                        UISounds.tick(1.5F);
                        break;
                    }
                }
            }
        }
        // Bombs
        if (random.nextFloat() < 0.03F + 0.01F * wave) {
            List<Integer> shooters = new ArrayList<>();
            for (int col = 0; col < IN_COLS; col++) {
                for (int row = IN_ROWS - 1; row >= 0; row--) {
                    if (alive[row * IN_COLS + col]) {
                        shooters.add(row * IN_COLS + col);
                        break;
                    }
                }
            }
            if (!shooters.isEmpty()) {
                int from = shooters.get(random.nextInt(shooters.size()));
                bombs.add(new int[]{(invaderX(from) + IN_SIZE / 2) * 10, (invaderY(from) + IN_SIZE) * 10});
            }
        }
        int baseY = H - BASE_H - 2;
        for (Iterator<int[]> it = bombs.iterator(); it.hasNext(); ) {
            int[] bomb = it.next();
            bomb[1] += 16 + wave * 2;
            int by2 = bomb[1] / 10;
            if (by2 > H) {
                it.remove();
                continue;
            }
            int bx2 = bomb[0] / 10;
            if (by2 >= baseY && bx2 >= baseX && bx2 <= baseX + BASE_W) {
                it.remove();
                if (--lives <= 0) {
                    gameOver();
                    return;
                }
                UISounds.delete();
            }
        }
    }

    private void gameOver() {
        over = true;
        newBest = submitBest(score, false);
        if (newBest) {
            UISounds.confirm();
        } else {
            UISounds.delete();
        }
    }

    private void fire() {
        if (shot == null && !over) {
            shot = new int[]{(int) (baseX + BASE_W / 2.0) * 10, (H - BASE_H - 4) * 10};
            UISounds.tick(1.1F);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        baseX = Mth.clamp(mouseX - boardX() - BASE_W / 2.0, 0, W - BASE_W);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            reset();
            return true;
        }
        fire();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) {
            if (over) {
                reset();
            } else {
                fire();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, score + " · " + "♥".repeat(Math.max(0, lives)));
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < alive.length; i++) {
            if (!alive[i]) continue;
            int x = bx + invaderX(i);
            int y = by + invaderY(i);
            int color = switch (i / IN_COLS) {
                case 0 -> 0xFFC93C36;
                case 1 -> 0xFFF9801D;
                case 2 -> 0xFF80C71F;
                default -> 0xFF3AB3DA;
            };
            graphics.fill(x, y + 2, x + IN_SIZE, y + IN_SIZE - 2, color);
            graphics.fill(x + 2, y, x + IN_SIZE - 2, y + IN_SIZE, color);
        }
        graphics.fill(bx + (int) baseX, by + H - BASE_H - 2,
                bx + (int) baseX + BASE_W, by + H - 2, theme().accent);
        graphics.fill(bx + (int) baseX + BASE_W / 2 - 1, by + H - BASE_H - 5,
                bx + (int) baseX + BASE_W / 2 + 1, by + H - BASE_H - 2, theme().accent);
        if (shot != null) {
            graphics.fill(bx + shot[0] / 10, by + shot[1] / 10,
                    bx + shot[0] / 10 + 1, by + shot[1] / 10 + 4, 0xFFE8EAF0);
        }
        for (int[] bomb : bombs) {
            graphics.fill(bx + bomb[0] / 10, by + bomb[1] / 10,
                    bx + bomb[0] / 10 + 2, by + bomb[1] / 10 + 3, 0xFFC93C36);
        }
        if (over) {
            renderOverlay(graphics, "INVADED",
                    (newBest ? "new best: " : "score: ") + score, "tap to restart");
        }
    }
}
