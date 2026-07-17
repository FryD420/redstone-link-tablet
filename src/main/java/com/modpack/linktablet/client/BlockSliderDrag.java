package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlock;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client side of a placed tablet's slider click-and-slide. The first
 * click on a slider tile grabs it (via the block's use handler); from
 * then on THIS drives the drag: every client tick while the use key is
 * held, the look-ray is projected onto the screen's infinite plane and
 * the value follows — sweeping past the tablet's edge, over neighboring
 * blocks, or into open air keeps sliding (values pin to the ends).
 * Values travel over the same {@code SetSliderPayload} the GUI uses;
 * the server validates range and distance as usual.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class BlockSliderDrag {

    private static final double MAX_DISTANCE_SQ = 64.0;

    private static BlockPos pos;
    private static int index = -1;
    private static int lastSent = -1;

    private BlockSliderDrag() {
    }

    /** Grabs a slider (called from the block's use handler, client side). */
    public static void start(BlockPos clickedPos, int appIndex) {
        pos = clickedPos.immutable();
        index = appIndex;
        lastSent = -1;
    }

    public static boolean isActive() {
        return pos != null;
    }

    private static void stop() {
        pos = null;
        index = -1;
        lastSent = -1;
    }

    /**
     * While sliding, swallow the repeating use-key triggers so sweeping
     * the crosshair over other tiles or blocks can't click them.
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (isActive() && event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !mc.options.keyUse.isDown()) {
            stop();
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof TabletBlock)
                || !(mc.level.getBlockEntity(pos) instanceof TabletBlockEntity be)
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQ) {
            stop();
            return;
        }
        List<SignalApp> apps = be.getApps();
        if (index >= apps.size() || !apps.get(index).slider()) {
            stop();
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        float logicalU = TabletScreenMath.logicalUFromRay(state, pos, eye, look, be.getScreenRotation());
        if (Float.isNaN(logicalU)) return;
        // Map against the bar's exact span so its end tracks the crosshair
        float[] bar = TabletScreenMath.sliderBarU(index, apps.size(), be.isScreenList(), be.getScreenRotation());
        float frac = net.minecraft.util.Mth.clamp((logicalU - bar[0]) / (bar[1] - bar[0]), 0.0F, 1.0F);
        int value = apps.get(index).valueFromFraction(frac);
        if (value == lastSent) return;
        lastSent = value;
        if (value != apps.get(index).strength()) {
            PacketDistributor.sendToServer(new ModNetworking.SetSliderPayload(
                    ModNetworking.AppTarget.ofBlock(pos), index, value));
        }
    }
}
