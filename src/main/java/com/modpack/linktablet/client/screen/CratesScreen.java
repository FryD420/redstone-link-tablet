package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 📦 (1.8.0, unadvertised): "Crates" (or "Sokoban"). Push every crate
 * onto a depot; you can push, never pull. R restarts the level.
 * Best = highest level reached (1-based).
 */
public class CratesScreen extends ArcadeScreen {

    private static final int CELL = 12;

    /** #wall .floor g:goal c:crate C:crate-on-goal p:player P:player-on-goal */
    private static final String[][] LEVELS = {
            {"########",
             "#..p...#",
             "#..c.g.#",
             "#......#",
             "########"},
            {"########",
             "#......#",
             "#.gcp..#",
             "#..c...#",
             "#..g...#",
             "########"},
            {"#########",
             "#...#...#",
             "#.c.#.g.#",
             "#.p.c..g#",
             "#...#...#",
             "#########"},
            {"#########",
             "##..g..##",
             "#..ccc..#",
             "#.g.p.g.#",
             "#...c...#",
             "##..g..##",
             "#########"},
            {"##########",
             "#p.......#",
             "#.c..c...#",
             "#...#....#",
             "#.g..g...#",
             "#........#",
             "##########"},
            {"#########",
             "#p......#",
             "#.c.c.c.#",
             "#.......#",
             "#.g.g.g.#",
             "#..cg...#",
             "#########"}
    };

    private char[][] grid;
    private int level;
    private int px, py;
    private int pushes;
    private boolean levelDone;
    private boolean allDone;

    public CratesScreen(AppView view, boolean returnToTablet) {
        super("crates", view, returnToTablet);
        loadLevel(0);
    }

    @Override
    protected int boardW() {
        return 10 * CELL;
    }

    @Override
    protected int boardH() {
        return 7 * CELL;
    }

    private void loadLevel(int index) {
        level = index;
        String[] rows = LEVELS[index];
        grid = new char[7][10];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c == 'p' || c == 'P') {
                    px = x;
                    py = y;
                    grid[y][x] = c == 'P' ? 'g' : '.';
                } else {
                    grid[y][x] = c;
                }
            }
        }
        pushes = 0;
        levelDone = false;
        submitBest(index + 1, false);
    }

    private boolean isCrate(char c) {
        return c == 'c' || c == 'C';
    }

    private boolean isOpen(char c) {
        return c == '.' || c == 'g';
    }

    private void move(int dx, int dy) {
        if (levelDone || allDone) return;
        int nx = px + dx;
        int ny = py + dy;
        char at = grid[ny][nx];
        if (at == '#' || at == ' ') return;
        if (isCrate(at)) {
            int cx = nx + dx;
            int cy = ny + dy;
            char behind = grid[cy][cx];
            if (!isOpen(behind)) return;
            grid[ny][nx] = at == 'C' ? 'g' : '.';
            grid[cy][cx] = behind == 'g' ? 'C' : 'c';
            pushes++;
            UISounds.tick(0.9F);
        } else {
            UISounds.tick(1.3F);
        }
        px = nx;
        py = ny;
        checkDone();
    }

    private void checkDone() {
        for (char[] row : grid) {
            for (char c : row) {
                if (c == 'c') return; // a crate off its depot
            }
        }
        levelDone = true;
        UISounds.confirm();
        if (level + 1 >= LEVELS.length) {
            allDone = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 82) { // R restarts the level
            UISounds.page();
            loadLevel(level);
            return true;
        }
        switch (keyCode) {
            case 265, 87 -> move(0, -1);
            case 264, 83 -> move(0, 1);
            case 263, 65 -> move(-1, 0);
            case 262, 68 -> move(1, 0);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (allDone) {
            UISounds.page();
            loadLevel(0);
            return true;
        }
        if (levelDone) {
            loadLevel(level + 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, "lvl " + (level + 1) + "/" + LEVELS.length + " · " + pushes);
        int bx = boardX();
        int by = boardY();
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[y].length; x++) {
                int cx = bx + x * CELL;
                int cy = by + y * CELL;
                switch (grid[y][x]) {
                    case '#' -> {
                        graphics.fill(cx, cy, cx + CELL, cy + CELL, 0xFF3A3F4B);
                        graphics.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0xFF474F5C);
                    }
                    case '.' -> graphics.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1B1D22);
                    case 'g' -> {
                        graphics.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1B1D22);
                        graphics.fill(cx + 4, cy + 4, cx + CELL - 4, cy + CELL - 4, 0xFF2E5C3A);
                    }
                    case 'c', 'C' -> {
                        graphics.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1B1D22);
                        int body = grid[y][x] == 'C' ? 0xFF80C71F : 0xFFBA8F42;
                        graphics.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, body);
                        graphics.fill(cx + 3, cy + 3, cx + CELL - 3, cy + CELL - 3,
                                grid[y][x] == 'C' ? 0xFF5C8F17 : 0xFF8A6B2B);
                    }
                    default -> { }
                }
            }
        }
        graphics.fill(bx + px * CELL + 2, by + py * CELL + 2,
                bx + px * CELL + CELL - 2, by + py * CELL + CELL - 2, theme().accent);
        if (allDone) {
            renderOverlay(graphics, "WAREHOUSE CLEAR", "all " + LEVELS.length + " levels!",
                    "tap to start over");
        } else if (levelDone) {
            renderOverlay(graphics, "LEVEL CLEAR", "pushes: " + pushes, "tap for the next one");
        }
    }
}
