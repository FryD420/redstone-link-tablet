package com.modpack.linktablet.item;

import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shapeless: one tablet + one dye → the same tablet (apps and all) with
 * a dyed case. Re-dye any time; the new color simply replaces the old.
 */
public class TabletDyeRecipe extends CustomRecipe {

    public TabletDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean foundTablet = false;
        boolean foundDye = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof TabletItem) {
                if (foundTablet) return false;
                foundTablet = true;
            } else if (stack.getItem() instanceof DyeItem) {
                if (foundDye) return false;
                foundDye = true;
            } else {
                return false;
            }
        }
        return foundTablet && foundDye;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack tablet = ItemStack.EMPTY;
        DyeItem dye = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof TabletItem) tablet = stack;
            else if (stack.getItem() instanceof DyeItem dyeItem) dye = dyeItem;
        }
        if (tablet.isEmpty() || dye == null) return ItemStack.EMPTY;
        ItemStack result = tablet.copyWithCount(1);
        result.set(ModDataComponents.CASE_COLOR.get(), dye.getDyeColor());
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TABLET_DYE.get();
    }
}
