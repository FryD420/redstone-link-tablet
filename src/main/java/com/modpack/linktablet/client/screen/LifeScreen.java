package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * 🦠 (1.8.0, unadvertised): "Life". Conway's garden as a pocket toy —
 * click to sow, space runs it, R reseeds, C clears. No score; the
 * reward is watching.
 */
public class LifeScreen extends ArcadeScreen {

    private static final int COLS = 26;
    private static final int ROWS = 18;
    private static final int CELL = 5;

    private final RandomSource random = RandomSource.create();
    private boolean[] cells = new boolean[COLS * ROWS];
    private boolean running;
    private int generation;
    private int stepCounter;

    public LifeScreen(AppView view, boolean returnToTablet) {
        super("life", view, returnToTablet);
        seed();
    }

    @Override
    protected int boardW() {
        return COLS * CELL;
    }

    @Override
    protected int boardH() {
        return ROWS * CELL;
    }

    private void seed() {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = random.nextFloat() < 0.28F;
        }
        generation = 0;
    }

    @Override
    public void tick() {
        if (!running) return;
        // 5 generations a second is a comfortable watching pace
        if (++stepCounter % 4 != 0) return;
        boolean[] next = new boolean[cells.length];
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx | dy) == 0) continue;
                        int nx = Math.floorMod(x + dx, COLS);
                        int ny = Math.floorMod(y + dy, ROWS);
                        if (cells[ny * COLS + nx]) n++;
                    }
                }
                boolean live = cells[y * COLS + x];
                next[y * COLS + x] = live ? n == 2 || n == 3 : n == 3;
            }
        }
        cells = next;
        generation++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = (int) Math.floor((mouseX - boardX()) / CELL);
        int cy = (int) Math.floor((mouseY - boardY()) / CELL);
        if (cx >= 0 && cx < COLS && cy >= 0 && cy < ROWS) {
            cells[cy * COLS + cx] ^= true;
            UISounds.tick(cells[cy * COLS + cx] ? 1.4F : 1.0F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 32 -> { // space: run/pause
                running = !running;
                UISounds.tick(running ? 1.5F : 0.9F);
            }
            case 82 -> { // R: reseed
                seed();
                UISounds.page();
            }
            case 67 -> { // C: clear
                java.util.Arrays.fill(cells, false);
                generation = 0;
                UISounds.tick(0.8F);
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, "gen " + generation + (running ? " ▶" : " ⏸"));
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i]) continue;
            int x = bx + (i % COLS) * CELL;
            int y = by + (i / COLS) * CELL;
            graphics.fill(x, y, x + CELL - 1, y + CELL - 1, theme().accent);
        }
        graphics.drawCenteredString(font, "click sow · space run · R seed · C clear",
                bx + boardW() / 2, by + boardH() + 3, 0xFF5A6070);
    }
}
