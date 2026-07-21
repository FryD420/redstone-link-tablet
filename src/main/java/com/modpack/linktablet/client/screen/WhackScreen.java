package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * 🔨 (1.8.0, unadvertised): "Whack" + a Wooden Shovel icon. Pips light
 * up across a 4×4 grid — tap them before they fade. Thirty seconds,
 * speed ramps, misses cost nothing but pride. It is, of course, the
 * tablet's own pip grid having fun. Best score in {@link ClientPrefs}.
 */
public class WhackScreen extends ArcadeScreen {

    private static final int N = 4;
    private static final int CELL = 22;
    private static final int ROUND_TICKS = 600;

    private final RandomSource random = RandomSource.create();
    /** Remaining lit ticks per cell; 0 = dark. */
    private final int[] litTicks = new int[N * N];
    private int score;
    private int roundLeft;
    private boolean over;
    private boolean newBest;

    public WhackScreen(AppView view, boolean returnToTablet) {
        super("whack", view, returnToTablet);
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
        java.util.Arrays.fill(litTicks, 0);
        score = 0;
        roundLeft = ROUND_TICKS;
        over = false;
        newBest = false;
    }

    /** Ramp: pips spawn faster and fade quicker as the round runs down. */
    private float progress() {
        return 1f - roundLeft / (float) ROUND_TICKS;
    }

    @Override
    public void tick() {
        if (over) return;
        if (--roundLeft <= 0) {
            over = true;
            newBest = submitBest(score, false);
            if (newBest) {
                UISounds.confirm();
            } else {
                UISounds.delete();
            }
            return;
        }
        for (int i = 0; i < litTicks.length; i++) {
            if (litTicks[i] > 0) litTicks[i]--;
        }
        // Spawn chance 8% → 22% per tick; lifetime 30 → 14 ticks
        if (random.nextFloat() < 0.08F + 0.14F * progress()) {
            int cell = random.nextInt(N * N);
            if (litTicks[cell] == 0) {
                litTicks[cell] = 30 - (int) (16 * progress());
            }
        }
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
        if (cx >= 0 && cx < N && cy >= 0 && cy < N && button == 0) {
            int index = cy * N + cx;
            if (litTicks[index] > 0) {
                litTicks[index] = 0;
                score++;
                UISounds.tick(1.0F + Math.min(score, 25) * 0.03F);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        renderCabinet(graphics, score + " · " + (roundLeft + 19) / 20 + "s");
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < N * N; i++) {
            int x = bx + (i % N) * CELL;
            int y = by + (i / N) * CELL;
            boolean on = litTicks[i] > 0;
            // The classic pip look: plaque cell, colored core while lit
            graphics.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xFF23262C);
            if (on) {
                // Fading pips dim toward the end of their life
                int core = litTicks[i] > 8 ? theme.accent : theme.accentDim;
                graphics.fill(x + 4, y + 4, x + CELL - 4, y + CELL - 4, core);
            }
        }
        if (over) {
            renderOverlay(graphics, "TIME!",
                    (newBest ? "new best: " : "score: ") + score, "tap to restart");
        }
    }
}
