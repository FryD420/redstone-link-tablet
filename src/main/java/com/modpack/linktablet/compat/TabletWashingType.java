package com.modpack.linktablet.compat;

import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Bulk-washing a dyed tablet strips the case dye but keeps the apps.
 * Splashing recipes can't do this — Create's {@code RecipeApplier} rolls
 * fixed recipe outputs without ever seeing the input stack, so a
 * data-driven recipe would wipe the tablet's components.
 *
 * Fan processing picks ONE type per position by priority, so sitting
 * above SPLASHING (400) means we shadow it in water. Everything that
 * isn't a dyed tablet is therefore delegated straight back to
 * SPLASHING — same validity, same visuals, same recipes.
 */
public class TabletWashingType implements FanProcessingType {

    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        return AllFanProcessingTypes.SPLASHING.isValidAt(level, pos);
    }

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        return isDyedTablet(stack) || AllFanProcessingTypes.SPLASHING.canProcess(stack, level);
    }

    @Override
    public List<ItemStack> process(ItemStack stack, Level level) {
        if (isDyedTablet(stack)) {
            ItemStack washed = stack.copy();
            washed.remove(ModDataComponents.CASE_COLOR.get());
            return List.of(washed);
        }
        return AllFanProcessingTypes.SPLASHING.process(stack, level);
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        AllFanProcessingTypes.SPLASHING.spawnProcessingParticles(level, pos);
    }

    @Override
    public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
        AllFanProcessingTypes.SPLASHING.morphAirFlow(particleAccess, random);
    }

    @Override
    public void affectEntity(Entity entity, Level level) {
        AllFanProcessingTypes.SPLASHING.affectEntity(entity, level);
    }

    private static boolean isDyedTablet(ItemStack stack) {
        return stack.is(ModItems.TABLET.get()) && stack.has(ModDataComponents.CASE_COLOR.get());
    }
}
