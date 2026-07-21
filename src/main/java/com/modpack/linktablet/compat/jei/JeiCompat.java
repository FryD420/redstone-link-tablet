package com.modpack.linktablet.compat.jei;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.screen.AppEditScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI integration (1.7.0): drag an item from the ingredient panel onto
 * either frequency ghost slot of the app edit screen to stage it — same
 * path as dropping a carried stack, nothing is consumed. Discovered by
 * JEI's own {@code @JeiPlugin} scan, so this class never loads on
 * installs without JEI.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(AppEditScreen.class, new FrequencyGhostHandler());
    }

    private static class FrequencyGhostHandler implements IGhostIngredientHandler<AppEditScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(AppEditScreen screen,
                                                   ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
            if (stack.isEmpty()) return List.of();
            List<Target<I>> targets = new ArrayList<>(2);
            for (int slot = 0; slot < 2; slot++) {
                Rect2i area = screen.frequencySlotArea(slot);
                int target = slot;
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return area;
                    }

                    @Override
                    public void accept(I dropped) {
                        screen.stageFrequencyItem(target, stack.get());
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}
