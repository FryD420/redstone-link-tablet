package com.modpack.linktablet.compat;

import com.modpack.linktablet.frequency.Frequency;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.IntConsumer;

/**
 * A listen-only participant in Create's Redstone Link network (1.10.0
 * gauges) — the mirror image of {@link VirtualTransmitter}: it
 * transmits nothing, and Create PUSHES the channel's power into
 * {@link #setReceivedStrength} whenever the network re-evaluates.
 * Create range-gates by {@link #getLocation}, so a receiver riding a
 * player (or sitting on a kiosk) hears exactly what a real Redstone
 * Link at that spot would.
 */
public class VirtualReceiver implements IRedstoneLinkable {

    private final Frequency frequency;
    private final Couple<RedstoneLinkNetworkHandler.Frequency> networkKey;
    /** Invoked on every pushed change (the BE/handler sync hook). */
    private final IntConsumer onChange;

    private ServerLevel level;
    private BlockPos position;
    private int received;
    private boolean alive = true;

    public VirtualReceiver(Frequency frequency, ServerLevel level, BlockPos position,
                           IntConsumer onChange) {
        this.frequency = frequency;
        this.level = level;
        this.position = position;
        this.onChange = onChange;
        this.networkKey = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(frequency.stack1()),
                RedstoneLinkNetworkHandler.Frequency.of(frequency.stack2()));
    }

    /** Follows its host around; re-registers on dimension change,
     * re-evaluates the network when the position changes (range gate). */
    public void update(ServerLevel newLevel, BlockPos newPosition) {
        if (newLevel != this.level) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this);
            this.level = newLevel;
            this.position = newPosition;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(newLevel, this);
            return;
        }
        if (!newPosition.equals(this.position)) {
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

    /** Last strength Create pushed in (0 when nothing transmits in range). */
    public int getReceivedStrength() {
        return received;
    }

    // ------------------------------------------------------------------
    // IRedstoneLinkable
    // ------------------------------------------------------------------

    @Override
    public int getTransmittedStrength() {
        return 0; // listen-only
    }

    @Override
    public void setReceivedStrength(int power) {
        if (power == received) return;
        received = power;
        onChange.accept(power);
    }

    @Override
    public boolean isListening() {
        return true;
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
