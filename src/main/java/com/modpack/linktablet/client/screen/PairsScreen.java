package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 🃏 (1.8.0, unadvertised): "Memory" (or "Pairs"). Sixteen face-down
 * cards, eight item pairs — find them in as few flips as possible.
 * Best = fewest flips.
 */
public class PairsScreen extends ArcadeScreen {

    private static final int N = 4;
    private static final int CELL = 24;

    private static final Item[] POOL = {
            Items.REDSTONE, Items.REDSTONE_TORCH, Items.REPEATER, Items.COMPARATOR,
            Items.LEVER, Items.PISTON, Items.OBSERVER, Items.TARGET,
            Items.NOTE_BLOCK, Items.HOPPER, Items.DISPENSER, Items.SLIME_BALL};

    private final RandomSource random = RandomSource.create();
    private final Item[] cards = new Item[N * N];
    private final boolean[] matched = new boolean[N * N];
    private int flipA = -1;
    private int flipB = -1;
    private int hideTicks;
    private int flips;
    private boolean solved;
    private boolean newBest;

    public PairsScreen(AppView view, boolean returnToTablet) {
        super("memory", view, returnToTablet);
        deal();
    }

    @Override
    protected int boardW() {
        return N * CELL;
    }

    @Override
    protected int boardH() {
        return N * CELL;
    }

    private void deal() {
        java.util.List<Item> pool = new java.util.ArrayList<>(java.util.List.of(POOL));
        java.util.Collections.shuffle(pool);
        java.util.List<Item> deck = new java.util.ArrayList<>();
        for (int i = 0; i < N * N / 2; i++) {
            deck.add(pool.get(i));
            deck.add(pool.get(i));
        }
        java.util.Collections.shuffle(deck);
        for (int i = 0; i < cards.length; i++) {
            cards[i] = deck.get(i);
        }
        java.util.Arrays.fill(matched, false);
        flipA = -1;
        flipB = -1;
        hideTicks = 0;
        flips = 0;
        solved = false;
        newBest = false;
    }

    @Override
    public void tick() {
        if (hideTicks > 0 && --hideTicks == 0) {
            flipA = -1;
            flipB = -1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (solved) {
            UISounds.page();
            deal();
            return true;
        }
        int cx = (int) Math.floor((mouseX - boardX()) / CELL);
        int cy = (int) Math.floor((mouseY - boardY()) / CELL);
        if (cx < 0 || cx >= N || cy < 0 || cy >= N || button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int index = cy * N + cx;
        if (matched[index] || index == flipA || hideTicks > 0) return true;
        UISounds.tick(1.2F);
        if (flipA == -1) {
            flipA = index;
            return true;
        }
        flipB = index;
        flips++;
        if (cards[flipA] == cards[flipB]) {
            matched[flipA] = true;
            matched[flipB] = true;
            flipA = -1;
            flipB = -1;
            UISounds.tick(1.6F);
            boolean all = true;
            for (boolean m : matched) {
                all &= m;
            }
            if (all) {
                solved = true;
                newBest = submitBest(flips, true);
                UISounds.confirm();
            }
        } else {
            hideTicks = 14;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int best = best();
        renderCabinet(graphics, flips + (best > 0 ? " / " + best : ""));
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < cards.length; i++) {
            int x = bx + (i % N) * CELL;
            int y = by + (i / N) * CELL;
            boolean faceUp = matched[i] || i == flipA || i == flipB;
            if (faceUp) {
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1,
                        matched[i] ? 0xFF20342A : 0xFF23262C);
                graphics.renderItem(new ItemStack(cards[i]), x + (CELL - 16) / 2, y + (CELL - 16) / 2);
            } else {
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFF4A505C);
                graphics.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, 0xFF3A3F4B);
            }
        }
        if (solved) {
            renderOverlay(graphics, "ALL PAIRS",
                    (newBest ? "new best: " : "flips: ") + flips, "tap to redeal");
        }
    }
}
