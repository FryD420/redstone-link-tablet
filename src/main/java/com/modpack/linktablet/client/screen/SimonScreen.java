package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 🎵 (1.8.0, unadvertised): "Simon". Four pads, a growing sequence,
 * pitched blips per pad — watch, repeat, survive. Best = longest
 * sequence completed.
 */
public class SimonScreen extends ArcadeScreen {

    private static final int PADS = 84;

    private static final int[] PAD_COLORS = {0xFFC93C36, 0xFF80C71F, 0xFF3AB3DA, 0xFFFED83D};
    private static final int[] PAD_DIM = {0xFF5E2320, 0xFF3E5C17, 0xFF20535F, 0xFF71601F};
    private static final float[] PAD_PITCH = {0.8F, 1.0F, 1.2F, 1.5F};

    private final RandomSource random = RandomSource.create();
    private final List<Integer> sequence = new ArrayList<>();
    private int showIndex;
    private int inputIndex;
    private int flashPad = -1;
    private int flashTicks;
    private int pauseTicks;
    private boolean showing;
    private boolean over;
    private boolean newBest;

    public SimonScreen(AppView view, boolean returnToTablet) {
        super("simon", view, returnToTablet);
        reset();
    }

    @Override
    protected int boardW() {
        return PADS;
    }

    @Override
    protected int boardH() {
        return PADS;
    }

    private void reset() {
        sequence.clear();
        over = false;
        newBest = false;
        extend();
    }

    private void extend() {
        sequence.add(random.nextInt(4));
        showIndex = 0;
        inputIndex = 0;
        showing = true;
        pauseTicks = 14;
        flashPad = -1;
    }

    @Override
    public void tick() {
        if (over) return;
        if (flashTicks > 0 && --flashTicks == 0) {
            flashPad = -1;
        }
        if (!showing) return;
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }
        if (flashPad != -1) return; // let the current flash finish
        if (showIndex >= sequence.size()) {
            showing = false;
            return;
        }
        flash(sequence.get(showIndex++));
        pauseTicks = 4;
    }

    private void flash(int pad) {
        flashPad = pad;
        flashTicks = 8;
        UISounds.tick(PAD_PITCH[pad]);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            reset();
            return true;
        }
        if (showing) return true; // wait your turn
        int cx = (int) ((mouseX - boardX()) / (PADS / 2.0));
        int cy = (int) ((mouseY - boardY()) / (PADS / 2.0));
        if (mouseX < boardX() || mouseY < boardY() || cx > 1 || cy > 1 || cx < 0 || cy < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int pad = cy * 2 + cx;
        flash(pad);
        if (pad != sequence.get(inputIndex)) {
            over = true;
            newBest = submitBest(sequence.size() - 1, false);
            if (newBest) {
                UISounds.confirm();
            } else {
                UISounds.delete();
            }
            return true;
        }
        if (++inputIndex >= sequence.size()) {
            extend();
            pauseTicks = 20; // breathe before the replay
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int round = Math.max(0, sequence.size() - 1);
        renderCabinet(graphics, round + " / " + Math.max(round, best()));
        int bx = boardX();
        int by = boardY();
        int half = PADS / 2;
        for (int pad = 0; pad < 4; pad++) {
            int x = bx + (pad % 2) * half;
            int y = by + (pad / 2) * half;
            graphics.fill(x + 2, y + 2, x + half - 2, y + half - 2,
                    pad == flashPad ? PAD_COLORS[pad] : PAD_DIM[pad]);
        }
        if (over) {
            renderOverlay(graphics, "WRONG PAD",
                    (newBest ? "new best: " : "sequence: ") + (sequence.size() - 1),
                    "tap to restart");
        }
    }
}
