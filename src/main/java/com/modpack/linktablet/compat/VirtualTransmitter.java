package com.modpack.linktablet.compat;

import com.modpack.linktablet.frequency.Frequency;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A transmit-only participant in Create's Redstone Link network,
 * representing one active tablet app held by a player.
 * <p>
 * VERSION NOTE: the {@code Couple} import is
 * {@code net.createmod.catnip.data.Couple} on Create 6.x (MC 1.21.1).
 * If you compile against an older Create (0.5.x), change it to
 * {@code com.simibubi.create.foundation.utility.Couple}.
 */
public class VirtualTransmitter implements IRedstoneLinkable {

    private final Frequency frequency;
    private final Couple<RedstoneLinkNetworkHandler.Frequency> networkKey;

    private ServerLevel level;
    private BlockPos position;
    private int strength;
    private boolean alive = true;

    public VirtualTransmitter(Frequency frequency, ServerLevel level, BlockPos position, int strength) {
        this.frequency = frequency;
        this.level = level;
        this.position = position;
        this.strength = strength;
        // The real stored stacks (components intact) reach Create's
        // network — component-bearing frequency items match receivers
        // exactly as Create's own links would.
        this.networkKey = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(frequency.stack1()),
                RedstoneLinkNetworkHandler.Frequency.of(frequency.stack2()));
    }

    /**
     * Follows the player around and tracks the desired strength;
     * re-registers on dimension change, re-evaluates the network when
     * the position or strength changes.
     */
    public void update(ServerLevel newLevel, BlockPos newPosition, int newStrength) {
        boolean strengthChanged = newStrength != this.strength;
        this.strength = newStrength;
        if (newLevel != this.level) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this);
            this.level = newLevel;
            this.position = newPosition;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(newLevel, this);
            return;
        }
        if (!newPosition.equals(this.position) || strengthChanged) {
            this.position = newPosition;
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(this.level, this);
        }
    }

    public void removeFromNetwork() {
        this.alive = false;
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this);
    }

    public Frequency getFrequency() {
        return frequency;
    }

    // ------------------------------------------------------------------
    // IRedstoneLinkable
    // ------------------------------------------------------------------

    @Override
    public int getTransmittedStrength() {
        return strength;
    }

    @Override
    public void setReceivedStrength(int power) {
        // Transmit-only; nothing to receive.
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
        return networkKey;
    }

    @Override
    public BlockPos getLocation() {
        return position;
    }
}
