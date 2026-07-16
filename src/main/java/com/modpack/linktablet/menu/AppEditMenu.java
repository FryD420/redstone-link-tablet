package com.modpack.linktablet.menu;

import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModMenus;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu behind the app edit screen: the full player inventory
 * as real slots plus two GHOST slots for the staged frequency pair —
 * vanilla drag/click mechanics via Create's {@link GhostItemMenu}
 * (ghost placement copies the carried stack, count 1, never consumes).
 * <p>
 * Slot order matters: 36 player slots first, then the two ghost slots —
 * Create's {@code GhostItemSubmitPacket} (reused by the picker overlay)
 * hardcodes ghost indices as {@code 36 + slot}.
 * <p>
 * The menu itself saves nothing ({@link #saveData} is empty): the
 * authoritative save stays the screen's {@code UpsertAppPayload}.
 */
public class AppEditMenu extends GhostItemMenu<AppEditMenu.EditContext> {

    // Screen-local slot coordinates (shared with AppEditScreen's layout)
    public static final int PLAYER_SLOTS_X = 8;
    public static final int PLAYER_SLOTS_Y = 146;
    public static final int GHOST_Y = 60;
    public static final int GHOST1_X = 8;
    public static final int GHOST2_X = 34;

    /** What the editor is pointed at, plus optional link-click prefill. */
    public record EditContext(ModNetworking.AppTarget target, int index,
                              ItemStack prefill1, ItemStack prefill2, String prefillName) {

        public static final StreamCodec<RegistryFriendlyByteBuf, EditContext> STREAM_CODEC =
                StreamCodec.composite(
                        ModNetworking.AppTarget.STREAM_CODEC, EditContext::target,
                        ByteBufCodecs.VAR_INT, EditContext::index,
                        ItemStack.OPTIONAL_STREAM_CODEC, EditContext::prefill1,
                        ItemStack.OPTIONAL_STREAM_CODEC, EditContext::prefill2,
                        ByteBufCodecs.STRING_UTF8, EditContext::prefillName,
                        EditContext::new);

        public static EditContext plain(ModNetworking.AppTarget target, int index) {
            return new EditContext(target, index, ItemStack.EMPTY, ItemStack.EMPTY, "");
        }
    }

    public AppEditMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(type, id, inv, buf);
    }

    public AppEditMenu(MenuType<?> type, int id, Inventory inv, EditContext context) {
        super(type, id, inv, context);
    }

    public static AppEditMenu create(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new AppEditMenu(ModMenus.APP_EDIT.get(), id, inv, buf);
    }

    @Override
    protected EditContext createOnClient(RegistryFriendlyByteBuf buf) {
        return EditContext.STREAM_CODEC.decode(buf);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(2);
    }

    @Override
    protected boolean allowRepeats() {
        return true;
    }

    @Override
    protected void initAndReadInventory(EditContext context) {
        super.initAndReadInventory(context);
        // Only a full pair lands in the staging slots; a half-set link's
        // lone item is committed directly by the screen instead (staging
        // requires both slots).
        if (!context.prefill1().isEmpty() && !context.prefill2().isEmpty()) {
            ghostInventory.setStackInSlot(0, context.prefill1().copyWithCount(1));
            ghostInventory.setStackInSlot(1, context.prefill2().copyWithCount(1));
        }
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_SLOTS_X, PLAYER_SLOTS_Y);
        addSlot(new SlotItemHandler(ghostInventory, 0, GHOST1_X, GHOST_Y));
        addSlot(new SlotItemHandler(ghostInventory, 1, GHOST2_X, GHOST_Y));
    }

    @Override
    protected void saveData(EditContext context) {
    }
}
