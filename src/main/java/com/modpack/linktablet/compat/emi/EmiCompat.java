package com.modpack.linktablet.compat.emi;

import com.modpack.linktablet.client.screen.AppEditScreen;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * EMI integration (1.7.0): drag an item from the index onto either
 * frequency ghost slot of the app edit screen to stage it — same path as
 * dropping a carried stack, nothing is consumed. Discovered by EMI's own
 * {@code @EmiEntrypoint} scan, so this class never loads on installs
 * without EMI.
 */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(AppEditScreen.class, new FrequencyDragDropHandler());
    }

    private static class FrequencyDragDropHandler implements EmiDragDropHandler<AppEditScreen> {

        @Override
        public boolean dropStack(AppEditScreen screen, EmiIngredient ingredient, int x, int y) {
            ItemStack stack = firstStack(ingredient);
            if (stack.isEmpty()) return false;
            for (int slot = 0; slot < 2; slot++) {
                if (contains(screen.frequencySlotArea(slot), x, y)) {
                    screen.stageFrequencyItem(slot, stack);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void render(AppEditScreen screen, EmiIngredient dragged, GuiGraphics graphics,
                           int mouseX, int mouseY, float delta) {
            if (firstStack(dragged).isEmpty()) return;
            for (int slot = 0; slot < 2; slot++) {
                Rect2i a = screen.frequencySlotArea(slot);
                graphics.fill(a.getX(), a.getY(),
                        a.getX() + a.getWidth(), a.getY() + a.getHeight(), 0x8830B848);
            }
        }

        private static ItemStack firstStack(EmiIngredient ingredient) {
            for (EmiStack stack : ingredient.getEmiStacks()) {
                ItemStack item = stack.getItemStack();
                if (!item.isEmpty()) return item;
            }
            return ItemStack.EMPTY;
        }

        private static boolean contains(Rect2i area, int x, int y) {
            return x >= area.getX() && x < area.getX() + area.getWidth()
                    && y >= area.getY() && y < area.getY() + area.getHeight();
        }
    }
}
