package com.modpack.linktablet.compat.jei;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.screen.GaugesScreen;
import com.modpack.linktablet.client.screen.MonitorScreen;
import com.modpack.linktablet.client.screen.SignalEditScreen;
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
 * either frequency ghost slot of the signal edit screen to stage it — same
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
        registration.addGhostIngredientHandler(SignalEditScreen.class, new FrequencyGhostHandler());
        // 1.11.0 (tester request: "that dragging feature should be
        // everywhere applicable"): the gauge editor's slots and the
        // Monitor's probe staging slots are drop targets too
        registration.addGhostIngredientHandler(GaugesScreen.class, new GaugeGhostHandler());
        registration.addGhostIngredientHandler(MonitorScreen.class, new MonitorGhostHandler());
    }

    /** Two-slot target list over any screen exposing 18×18 slot rects;
     * zero-area rects (closed modals) are skipped. */
    private static <I> List<IGhostIngredientHandler.Target<I>> slotTargets(
            ITypedIngredient<I> ingredient, int slots,
            java.util.function.IntFunction<Rect2i> area,
            java.util.function.ObjIntConsumer<ItemStack> stage) {
        Optional<ItemStack> stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
        if (stack.isEmpty()) return List.of();
        List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            Rect2i rect = area.apply(slot);
            if (rect.getWidth() == 0) continue;
            int target = slot;
            targets.add(new IGhostIngredientHandler.Target<>() {
                @Override
                public Rect2i getArea() {
                    return rect;
                }

                @Override
                public void accept(I dropped) {
                    stage.accept(stack.get(), target);
                }
            });
        }
        return targets;
    }

    private static class GaugeGhostHandler implements IGhostIngredientHandler<GaugesScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(GaugesScreen screen,
                                                   ITypedIngredient<I> ingredient, boolean doStart) {
            return slotTargets(ingredient, 2, screen::frequencySlotArea,
                    (stack, slot) -> screen.stageFrequencyItem(slot, stack));
        }

        @Override
        public void onComplete() {
        }
    }

    private static class MonitorGhostHandler implements IGhostIngredientHandler<MonitorScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(MonitorScreen screen,
                                                   ITypedIngredient<I> ingredient, boolean doStart) {
            return slotTargets(ingredient, 2, screen::probeSlotArea,
                    (stack, slot) -> screen.stageProbeItem(slot, stack));
        }

        @Override
        public void onComplete() {
        }
    }

    private static class FrequencyGhostHandler implements IGhostIngredientHandler<SignalEditScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(SignalEditScreen screen,
                                                   ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
            if (stack.isEmpty()) return List.of();
            List<Target<I>> targets = new ArrayList<>(3);
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
            // Icon slot (1.9.1): drop sets the signal's custom icon
            Rect2i iconArea = screen.iconSlotArea();
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return iconArea;
                }

                @Override
                public void accept(I dropped) {
                    screen.stageIconItem(stack.get());
                }
            });
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}
