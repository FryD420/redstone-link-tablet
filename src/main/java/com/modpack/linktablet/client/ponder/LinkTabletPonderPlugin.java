package com.modpack.linktablet.client.ponder;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.registry.ModItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Registers the tablet's ponder scene (hold W over the tablet item). */
public class LinkTabletPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LinkTabletMod.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(ModItems.TABLET.getId(), "tablet", TabletScenes::intro);
    }
}
