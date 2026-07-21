package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * ⏱️ (1.8.0, unadvertised): "Reflex". Wait for the panel to go
 * green, tap as fast as you can. Jumping the gun resets the round.
 * Best = fastest reaction in milliseconds.
 */
public class ReflexScreen extends ArcadeScreen {

    private static final int W = 110;
    private static final int H = 80;

    private final RandomSource random = RandomSource.create();
    /** 0 = armed/waiting red, 1 = green (go!), 2 = result, 3 = false start */
    private int phase;
    private int armTicks;
    private long greenAt;
    private int resultMs;
    private boolean newBest;

    public ReflexScreen(AppView view, boolean returnToTablet) {
        super("reflex", view, returnToTablet);
        arm();
    }

    @Override
    protected int boardW() {
        return W;
    }

    @Override
    protected int boardH() {
        return H;
    }

    private void arm() {
        phase = 0;
        armTicks = 25 + random.nextInt(45); // 1.25–3.5s of suspense
        newBest = false;
    }

    @Override
    public void tick() {
        if (phase == 0 && --armTicks <= 0) {
            phase = 1;
            greenAt = Util.getMillis();
            UISounds.tick(1.6F);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        switch (phase) {
            case 0 -> {
                phase = 3; // jumped the gun
                UISounds.delete();
            }
            case 1 -> {
                resultMs = (int) (Util.getMillis() - greenAt);
                phase = 2;
                newBest = submitBest(Math.max(1, resultMs), true);
                if (newBest) {
                    UISounds.confirm();
                } else {
                    UISounds.tick(1.2F);
                }
            }
            default -> {
                UISounds.page();
                arm();
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int best = best();
        renderCabinet(graphics, best > 0 ? best + "ms" : "—");
        int bx = boardX();
        int by = boardY();
        int color = switch (phase) {
            case 0 -> 0xFF8A2C27;
            case 1 -> 0xFF3F9E37;
            case 3 -> 0xFF5A2020;
            default -> 0xFF23262C;
        };
        graphics.fill(bx, by, bx + W, by + H, color);
        String line = switch (phase) {
            case 0 -> "wait for green...";
            case 1 -> "TAP!";
            case 3 -> "too soon! tap to retry";
            default -> null;
        };
        if (line != null) {
            graphics.drawCenteredString(font, line, bx + W / 2, by + H / 2 - 4, 0xFFE8EAF0);
        } else {
            renderOverlay(graphics, resultMs + " ms",
                    newBest ? "new best!" : "best: " + best(), "tap to go again");
        }
    }
}
