package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.client.screen.chrome.ChromeButton;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.MonitorChannels;
import com.modpack.linktablet.menu.SignalEditMenu;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Frequency Monitor's "add probe" editor (1.11.0, second tester
 * round): a REAL container menu — {@link SignalEditMenu} under the
 * PROBE_EDIT menu type — because that's what makes JEI/EMI show their
 * ingredient panels at all (both viewers only attach to
 * AbstractContainerScreen; a plain Screen can't host them, verified
 * against EMI 1.1.24's ScreenMixin). Same ghost-slot mechanics, search
 * button, and drag targets as the signal editor, just slimmed to the
 * two staging slots + Add.
 */
public class ProbeEditScreen extends AbstractContainerScreen<SignalEditMenu> {

    // Must match the signal editor's dims — the shared menu's slot
    // coordinates (player 8/146, ghosts 8|34/60) are menu-fixed.
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 230;
    private static final int BTN_X = 176;
    private static final int BTN_W = 76;

    private PickerOverlay picker;
    private Button addButton;

    public ProbeEditScreen(SignalEditMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    private SignalView view() {
        ModNetworking.SignalTarget target = menu.contentHolder.target();
        if (target.pos().isPresent()) return new SignalView.Block(target.pos().get());
        if (target.slot().isPresent()) return new SignalView.Slot(target.slot().get());
        return new SignalView.Hand(target.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    private ScreenTheme theme() {
        return view().theme();
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos;
        int top = topPos;
        picker = new PickerOverlay(font);

        // All-items search (for frequency items you don't carry) — the
        // signal editor's button, same spot beside the ghost slots
        Button searchButton = new ChromeButton(left + 104, top + 58, 24, 20,
                Component.literal("..."), b ->
                picker.open(width, height, this::stageFromPicker, false), this::theme);
        searchButton.setTooltip(ScreenTips.tooltip("gui.linktablet.picker.search"));
        addRenderableWidget(searchButton);

        addButton = new ChromeButton(left + BTN_X, top + 146, BTN_W, 20,
                Component.translatable("gui.linktablet.probe_edit.add"), b -> add(), this::theme);
        addButton.setTooltip(ScreenTips.tooltip("gui.linktablet.tip.probe.add"));
        addRenderableWidget(addButton);

        Button cancelButton = new ChromeButton(left + BTN_X, top + 170, BTN_W, 20,
                Component.translatable("gui.linktablet.edit_signal.cancel"), b -> onClose(), this::theme);
        cancelButton.setTooltip(ScreenTips.tooltip("gui.linktablet.tip.cancel"));
        addRenderableWidget(cancelButton);
    }

    // ---- Staging (ghost slots) — the signal editor's mechanics --------

    private ItemStack staged(int slot) {
        return menu.ghostInventory.getStackInSlot(slot);
    }

    /** Sets a ghost slot on both sides (Create's packet targets slot 36+N). */
    private void setStaged(int slot, ItemStack stack) {
        menu.ghostInventory.setStackInSlot(slot, stack);
        PacketDistributor.sendToServer(new GhostItemSubmitPacket(stack, slot));
    }

    /** Picker picks land in the first empty ghost slot (else slot 1). */
    private void stageFromPicker(ItemStack stack) {
        if (stack.isEmpty()) return;
        int slot = staged(0).isEmpty() ? 0 : staged(1).isEmpty() ? 1 : 0;
        setStaged(slot, stack.copyWithCount(1));
        UISounds.tick(1.3F);
    }

    /**
     * Absolute 18×18 rect (border included) of ghost slot 0/1 — the drop
     * targets for the JEI/EMI drag handlers in {@code compat/}.
     */
    public Rect2i frequencySlotArea(int slot) {
        int x = leftPos + (slot == 0 ? SignalEditMenu.GHOST1_X : SignalEditMenu.GHOST2_X) - 1;
        return new Rect2i(x, topPos + SignalEditMenu.GHOST_Y - 1, 18, 18);
    }

    /** Stages a viewer-dragged ingredient into a ghost slot (count 1). */
    public void stageFrequencyItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        setStaged(slot & 1, stack.copyWithCount(1));
        UISounds.tick(1.3F);
    }

    // ---- Add -----------------------------------------------------------

    private boolean stagedAddable() {
        if (staged(0).isEmpty() && staged(1).isEmpty()) return false;
        List<Frequency> probes = view().monitorProbes();
        return probes.size() < MonitorChannels.MAX_PROBES
                && !probes.contains(Frequency.of(staged(0), staged(1)));
    }

    private void add() {
        if (!stagedAddable()) {
            UISounds.tick(0.7F);
            return;
        }
        List<Frequency> probes = new ArrayList<>(view().monitorProbes());
        probes.add(Frequency.of(staged(0), staged(1)));
        PacketDistributor.sendToServer(new ModNetworking.SetProbePayload(
                menu.contentHolder.target(), probes));
        UISounds.confirm();
        onClose();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (addButton != null) addButton.active = stagedAddable();
    }

    // ---- Render --------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ScreenTheme theme = theme();
        Chrome.panel(graphics, leftPos - 5, topPos - 4, imageWidth + 10, imageHeight + 8, theme);
        int titleW = font.width(title);
        Chrome.plaque(graphics, leftPos + imageWidth / 2 - titleW / 2 - 6, topPos, titleW + 12, 16,
                theme.rowBg);
        for (Slot slot : menu.slots) {
            Chrome.slot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, theme.rowBg);
        }
        // Staging slot accents (red/blue, like the Redstone Link's slots)
        Chrome.ghostRing(graphics, leftPos + SignalEditMenu.GHOST1_X - 2, topPos + SignalEditMenu.GHOST_Y - 2,
                TabletScreen.FREQ1_COLOR);
        Chrome.ghostRing(graphics, leftPos + SignalEditMenu.GHOST2_X - 2, topPos + SignalEditMenu.GHOST_Y - 2,
                TabletScreen.FREQ2_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenTheme theme = theme();
        boolean shadow = theme.textShadow;
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 5,
                theme.textPrimary, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.monitor.probe"),
                8, 48, theme.textMuted, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.probe_edit.hint"),
                8, 90, theme.textFaint, shadow);
        graphics.drawString(font, Component.translatable("gui.linktablet.picker.inventory"),
                8, 134, theme.textMuted, shadow);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int slot = 0; slot < 2; slot++) {
            if (staged(slot).isEmpty()) {
                Rect2i area = frequencySlotArea(slot);
                ScreenTips.add(area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                        "gui.linktablet.tip.freq.item");
            }
        }
        if (picker.isOpen()) {
            picker.render(graphics, mouseX, mouseY, partialTick, width, height, theme());
        }
        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    // ---- Input (picker overlay first, then the container) --------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picker.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientHooks.returnToProgram(Program.MONITOR, view());
    }
}
