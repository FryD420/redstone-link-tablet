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
 * 🪨 (1.8.0, unadvertised): "Dodge". Anvils rain, you sidestep with
 * the mouse. Best = seconds survived.
 */
public class DodgeScreen extends ArcadeScreen {

    private static final int W = 120;
    private static final int H = 120;
    private static final int PLAYER = 8;
    private static final int ANVIL = 8;

    private final RandomSource random = RandomSource.create();
    /** Anvils as {x, y(*10 fixed point), speed(*10)}. */
    private final List<int[]> anvils = new ArrayList<>();
    private double playerX = (W - PLAYER) / 2.0;
    private int ticks;
    private boolean over;
    private boolean newBest;

    public DodgeScreen(AppView view, boolean returnToTablet) {
        super("dodge", view, returnToTablet);
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
        anvils.clear();
        ticks = 0;
        over = false;
        newBest = false;
    }

    @Override
    public void tick() {
        if (over) return;
        ticks++;
        double progress = Math.min(1.0, ticks / 1200.0);
        if (random.nextFloat() < 0.06F + 0.14F * progress) {
            anvils.add(new int[]{random.nextInt(W - ANVIL), -ANVIL * 10,
                    14 + random.nextInt(10) + (int) (14 * progress)});
        }
        int py = H - PLAYER - 2;
        for (Iterator<int[]> it = anvils.iterator(); it.hasNext(); ) {
            int[] anvil = it.next();
            anvil[1] += anvil[2];
            int ay = anvil[1] / 10;
            if (ay > H) {
                it.remove();
                continue;
            }
            if (ay + ANVIL > py && ay < py + PLAYER
                    && anvil[0] + ANVIL > playerX && anvil[0] < playerX + PLAYER) {
                over = true;
                newBest = submitBest(Math.max(1, ticks / 20), false);
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
    public void mouseMoved(double mouseX, double mouseY) {
        playerX = Mth.clamp(mouseX - boardX() - PLAYER / 2.0, 0, W - PLAYER);
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
        renderCabinet(graphics, ticks / 20 + "s / " + Math.max(ticks / 20, best()) + "s");
        int bx = boardX();
        int by = boardY();
        int py = H - PLAYER - 2;
        graphics.fill(bx + (int) playerX, by + py,
                bx + (int) playerX + PLAYER, by + py + PLAYER, theme().accent);
        for (int[] anvil : anvils) {
            int x = bx + anvil[0];
            int y = by + anvil[1] / 10;
            // A tiny anvil: cap, waist, foot
            graphics.fill(x, y, x + ANVIL, y + 3, 0xFF494D57);
            graphics.fill(x + 2, y + 3, x + ANVIL - 2, y + 6, 0xFF33363D);
            graphics.fill(x + 1, y + 6, x + ANVIL - 1, y + ANVIL, 0xFF494D57);
        }
        if (over) {
            renderOverlay(graphics, "CLANG",
                    (newBest ? "new best: " : "survived: ") + ticks / 20 + "s", "tap to restart");
        }
    }
}
