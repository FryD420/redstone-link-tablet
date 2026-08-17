package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.client.screen.chrome.ChromeEditBox;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.Gauge;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * The Gauges program (1.10.0 OS suite): a grid of live read-only dials,
 * each listening on a Redstone Link frequency ({@link Gauge}). Click a
 * dial (or the add tile) to open the EDITOR overlay — name, the two
 * frequency ghost slots (all-items picker), accent color, delete. All
 * edits go through Upsert/RemoveGaugePayload; readings arrive via the
 * player snapshot (carried) or the BE sync (kiosk) — see
 * {@link SignalView#gaugeReading}.
 */
public class GaugesScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int HEADER = 34;
    private static final int BOTTOM_PAD = 8;
    private static final int MODE_BTN_SIZE = 12;

    private static final int CELL_W = 62;
    private static final int CELL_H = 62;
    private static final int MAX_COLS = 3;

    /** The signal editor's palette — one look across the device. */
    private static final int[] COLORS = {
            0xFF3A3F4B, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF80C71F, 0xFF5E7C16, 0xFF3AB3DA, 0xFF169C9C,
            0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA,
            0xFF835432, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21
    };

    private final SignalView view;
    private final PickerOverlay picker = new PickerOverlay(
            net.minecraft.client.Minecraft.getInstance().font);

    // Editor overlay state (editIndex -1 = closed, -2 = new gauge)
    private int editIndex = -1;
    private boolean editorOpen = false;
    private EditBox nameBox;
    private ItemStack editStack1 = ItemStack.EMPTY;
    private ItemStack editStack2 = ItemStack.EMPTY;
    private int editColor = Gauge.DEFAULT_COLOR;

    public GaugesScreen(SignalView view) {
        super(Component.translatable("program.linktablet.gauges"));
        this.view = view;
    }

    // ------------------------------------------------------------------
    // JEI/EMI ghost-drag targets (compat/jei, compat/emi) — same staging
    // path as the picker, nothing consumed. Zero-area when the editor
    // modal is closed, so the handlers naturally have nowhere to drop.
    // ------------------------------------------------------------------

    /** Absolute 18×18 rect of editor frequency slot 0/1 — empty while
     * the editor modal is closed. */
    public net.minecraft.client.renderer.Rect2i frequencySlotArea(int slot) {
        if (!editorOpen) return new net.minecraft.client.renderer.Rect2i(0, 0, 0, 0);
        return new net.minecraft.client.renderer.Rect2i(
                slot == 0 ? edSlot1X() : edSlot2X(), edSlotY(), 18, 18);
    }

    /** The chrome panel's outer rect — JEI/EMI use it to position their
     * ingredient panels around this plain (non-container) screen, which
     * is what makes the drag SOURCE visible at all. */
    public net.minecraft.client.renderer.Rect2i panelBounds() {
        return new net.minecraft.client.renderer.Rect2i(
                panelLeft() - 6, bodyTop() - 2, PANEL_W + 12, bodyHeight() + 4);
    }

    /** Stages a viewer-dragged ingredient into an editor slot (count 1). */
    public void stageFrequencyItem(int slot, ItemStack stack) {
        if (!editorOpen || stack.isEmpty()) return;
        if ((slot & 1) == 0) {
            editStack1 = stack.copyWithCount(1);
        } else {
            editStack2 = stack.copyWithCount(1);
        }
        UISounds.tick(1.3F);
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    private List<Gauge> gauges() {
        return view.gauges();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int totalTiles() {
        return gauges().size() + (gauges().size() < Gauge.MAX_GAUGES ? 1 : 0);
    }

    private int columns() {
        return Mth.clamp(totalTiles(), 1, MAX_COLS);
    }

    private int rows() {
        return Mth.positiveCeilDiv(totalTiles(), columns());
    }

    private int bodyHeight() {
        return HEADER + rows() * CELL_H + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private int gridLeft() {
        return panelLeft() + (PANEL_W - columns() * CELL_W) / 2;
    }

    private int cellX(int i) {
        return gridLeft() + (i % columns()) * CELL_W;
    }

    private int cellY(int i) {
        return bodyTop() + HEADER + (i / columns()) * CELL_H;
    }

    private int tileIndexAt(double mouseX, double mouseY) {
        for (int i = 0; i < totalTiles(); i++) {
            int x = cellX(i);
            int y = cellY(i);
            if (mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H) {
                return i;
            }
        }
        return -1;
    }

    private int homeBtnX() {
        return panelLeft() + 4;
    }

    private int pinBtnX() {
        return homeBtnX() + MODE_BTN_SIZE + 4;
    }

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    private boolean overBtn(double mouseX, double mouseY, int btnX) {
        return mouseX >= btnX && mouseX < btnX + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    // ---- Editor overlay geometry -------------------------------------

    private static final int ED_W = 170;
    private static final int ED_H = 158;

    private int edX() {
        return (width - ED_W) / 2;
    }

    private int edY() {
        return (height - ED_H) / 2;
    }

    private int edSlotY() {
        return edY() + 34;
    }

    private int edSlot1X() {
        return edX() + ED_W / 2 - 26;
    }

    private int edSlot2X() {
        return edX() + ED_W / 2 + 4;
    }

    private int edSwatchY() {
        return edSlotY() + 30;
    }

    private int edSwatchX(int i) {
        return edX() + (ED_W - 8 * 14 + 2) / 2 + (i % 8) * 14;
    }

    private int edButtonY() {
        return edY() + ED_H - 24;
    }

    // ------------------------------------------------------------------
    // Editor open/close/save
    // ------------------------------------------------------------------

    private void openEditor(int index) {
        editorOpen = true;
        editIndex = index;
        Gauge gauge = index >= 0 ? gauges().get(index) : null;
        editStack1 = gauge != null ? gauge.frequency().icon1() : ItemStack.EMPTY;
        editStack2 = gauge != null ? gauge.frequency().icon2() : ItemStack.EMPTY;
        editColor = gauge != null ? gauge.color() : Gauge.DEFAULT_COLOR;
        nameBox = new ChromeEditBox(font, edX() + 12, edY() + 15, ED_W - 24, 8,
                Component.translatable("gui.linktablet.gauge.name"));
        nameBox.setMaxLength(Gauge.MAX_NAME_LENGTH);
        nameBox.setHint(Component.translatable("gui.linktablet.gauge.name"));
        nameBox.setValue(gauge != null ? gauge.name() : "");
        // Standalone EditBox: nothing else ever focuses it (vanilla's
        // mouseClicked never sets focus — that's the widget traversal's
        // job, and this box isn't registered), so focus it here like
        // PickerOverlay's search box or the name can never be typed.
        nameBox.setFocused(true);
        UISounds.page();
    }

    private void closeEditor() {
        editorOpen = false;
        editIndex = -1;
        nameBox = null;
    }

    private void saveEditor() {
        Frequency freq = Frequency.of(editStack1, editStack2);
        if (freq.isEmpty()) {
            UISounds.tick(0.7F); // a link gauge needs a channel
            return;
        }
        String name = nameBox.getValue().strip();
        Gauge gauge = new Gauge(name, Gauge.Source.LINK, freq, editColor, Optional.empty());
        PacketDistributor.sendToServer(new ModNetworking.UpsertGaugePayload(
                view.target(), editIndex >= 0 ? editIndex : -1, gauge));
        UISounds.confirm();
        closeEditor();
    }

    private void deleteEditor() {
        if (editIndex >= 0) {
            PacketDistributor.sendToServer(new ModNetworking.RemoveGaugePayload(
                    view.target(), editIndex));
            UISounds.delete();
        }
        closeEditor();
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        int left = panelLeft();
        int top = bodyTop();

        Chrome.panel(graphics, left - 6, top - 2, PANEL_W + 12, bodyHeight() + 4, theme);
        Component titleText = getTitle();
        int titleW = font.width(titleText);
        Chrome.plaque(graphics, width / 2 - titleW / 2 - 6, top + 2, titleW + 12, 18, theme.rowBg);
        graphics.drawString(font, titleText, width / 2 - titleW / 2, top + 7,
                theme.textPrimary, theme.textShadow);
        HeaderGlyphs.home(graphics, homeBtnX(), modeBtnY(),
                overBtn(mouseX, mouseY, homeBtnX()) ? theme.glyphHover : theme.textFaint);
        addBackgroundTip(homeBtnX(), modeBtnY(), MODE_BTN_SIZE, MODE_BTN_SIZE, "gui.linktablet.home");
        boolean pinned = OverlayPin.isPinned(view, Program.GAUGES);
        HeaderGlyphs.pin(graphics, pinBtnX(), modeBtnY(),
                pinned ? theme.accent
                        : overBtn(mouseX, mouseY, pinBtnX()) ? theme.glyphHover : theme.textFaint);
        addBackgroundTip(pinBtnX(), modeBtnY(), MODE_BTN_SIZE, MODE_BTN_SIZE, pinned
                ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
        Chrome.railH(graphics, left - 4, top + HEADER - 8, PANEL_W + 8, theme.bodyOuter);

        List<Gauge> gauges = gauges();
        for (int i = 0; i < totalTiles(); i++) {
            int x = cellX(i);
            int y = cellY(i);
            boolean hovered = !editorOpen && tileIndexAt(mouseX, mouseY) == i;
            if (i < gauges.size()) {
                renderGaugeTile(graphics, theme, gauges.get(i), view.gaugeReading(i), x, y, hovered);
            } else {
                Chrome.tile(graphics, x + 3, y + 3, CELL_W - 6, CELL_H - 6,
                        hovered ? theme.surfaceHi : theme.rowBg);
                graphics.drawString(font, "+", x + CELL_W / 2 - 2, y + CELL_H / 2 - 4,
                        theme.textMuted, theme.textShadow);
                addBackgroundTip(x + 3, y + 3, CELL_W - 6, CELL_H - 6, "gui.linktablet.tip.gauge.new");
            }
        }
        if (gauges.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.gauge.empty");
            graphics.drawString(font, hint, width / 2 - font.width(hint) / 2,
                    top + bodyHeight() + 6, theme.textMuted, theme.textShadow);
        }

        if (editorOpen) {
            renderEditor(graphics, mouseX, mouseY, theme);
        }
        if (picker.isOpen()) {
            picker.render(graphics, mouseX, mouseY, partialTick, width, height, theme);
        }

        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    private void renderGaugeTile(GuiGraphics graphics, ScreenTheme theme, Gauge gauge,
                                 int value, int x, int y, boolean hovered) {
        Chrome.tile(graphics, x + 3, y + 3, CELL_W - 6, CELL_H - 6,
                hovered ? theme.rowBgHover : theme.rowBg);
        int cx = x + CELL_W / 2;
        int cy = y + 34;
        GaugeDialPainter.dial(graphics, theme, gauge.color(), value, cx, cy, 20);
        // Value numeral inside the arc
        String level = String.valueOf(value);
        graphics.drawString(font, level, cx - font.width(level) / 2, cy - 14,
                value > 0 ? theme.textPrimary : theme.textFaint, theme.textShadow);
        // Name under the dial
        String name = gauge.name().isBlank()
                ? Component.translatable("program.linktablet.gauges").getString() : gauge.name();
        String shown = TextFit.ellipsize(font, name, CELL_W - 10);
        graphics.drawString(font, shown, cx - font.width(shown) / 2, y + CELL_H - 14,
                theme.textMuted, theme.textShadow);
    }

    private void renderEditor(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme theme) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(0, 0, width, height, 0xB0000000);
        Chrome.panel(graphics, edX() - 2, edY() - 2, ED_W + 4, ED_H + 4, theme);

        nameBox.render(graphics, mouseX, mouseY, 0);
        addEditorTip(edX() + 12, edY() + 15, ED_W - 24, 8, "gui.linktablet.tip.name");

        // Frequency ghost slots (18x18), Redstone-Link colored underlines
        Chrome.slot(graphics, edSlot1X(), edSlotY(), theme.surfaceLo);
        Chrome.slot(graphics, edSlot2X(), edSlotY(), theme.surfaceLo);
        if (!editStack1.isEmpty()) {
            graphics.renderItem(editStack1, edSlot1X() + 1, edSlotY() + 1);
        }
        if (!editStack2.isEmpty()) {
            graphics.renderItem(editStack2, edSlot2X() + 1, edSlotY() + 1);
        }
        graphics.fill(edSlot1X(), edSlotY() + 20, edSlot1X() + 18, edSlotY() + 22,
                TabletScreen.FREQ1_COLOR);
        graphics.fill(edSlot2X(), edSlotY() + 20, edSlot2X() + 18, edSlotY() + 22,
                TabletScreen.FREQ2_COLOR);
        addEditorTip(edSlot1X(), edSlotY(), 18, 18, "gui.linktablet.tip.freq.item");
        addEditorTip(edSlot2X(), edSlotY(), 18, 18, "gui.linktablet.tip.freq.item");

        // Color swatches, 8 × 2
        for (int i = 0; i < COLORS.length; i++) {
            int sx = edSwatchX(i);
            int sy = edSwatchY() + (i / 8) * 14;
            if (COLORS[i] == editColor) {
                graphics.fill(sx - 1, sy - 1, sx + 13, sy + 13, 0xFFFFFFFF);
            }
            graphics.fill(sx, sy, sx + 12, sy + 12, COLORS[i]);
            addEditorTip(sx, sy, 12, 12, "gui.linktablet.tip.colour");
        }

        // Save / Delete
        boolean overSave = over(mouseX, mouseY, edX() + 8, edButtonY(), 74, 18);
        Chrome.bannerButton(graphics, edX() + 8, edButtonY(), 74, 18,
                overSave ? Chrome.ButtonState.HOVER : Chrome.ButtonState.NORMAL,
                overSave ? theme.rowBgHover : theme.rowBg);
        Component save = Component.translatable("gui.linktablet.gauge.save");
        graphics.drawString(font, save, edX() + 8 + (74 - font.width(save)) / 2,
                edButtonY() + 5, theme.textPrimary, theme.textShadow);
        addEditorTip(edX() + 8, edButtonY(), 74, 18, "gui.linktablet.tip.save");

        boolean canDelete = editIndex >= 0;
        boolean overDelete = canDelete && over(mouseX, mouseY, edX() + ED_W - 82, edButtonY(), 74, 18);
        Chrome.bannerButton(graphics, edX() + ED_W - 82, edButtonY(), 74, 18,
                !canDelete ? Chrome.ButtonState.DISABLED
                        : overDelete ? Chrome.ButtonState.HOVER : Chrome.ButtonState.NORMAL,
                overDelete ? theme.rowBgHover : theme.rowBg);
        Component delete = Component.translatable("gui.linktablet.gauge.delete");
        graphics.drawString(font, delete, edX() + ED_W - 82 + (74 - font.width(delete)) / 2,
                edButtonY() + 5, canDelete ? theme.textPrimary : theme.textFaint, theme.textShadow);
        if (canDelete) {
            addEditorTip(edX() + ED_W - 82, edButtonY(), 74, 18, "gui.linktablet.tip.delete");
        }

        graphics.pose().popPose();
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Registers a tooltip for a BACKGROUND control (header glyphs, the
     * empty "new gauge" tile) — suppressed while EITHER the editor modal
     * or the picker overlay is open, since both draw after these
     * controls and would otherwise be painted over by an unsuppressed
     * tooltip. Mirrors {@code SignalEditScreen#addBackgroundTip} /
     * {@code ClockScreen#addBackgroundTip}.
     */
    private void addBackgroundTip(int x, int y, int w, int h, String key) {
        if (editorOpen || picker.isOpen()) return;
        ScreenTips.add(x, y, w, h, key);
    }

    /**
     * Registers a tooltip for one of the EDITOR MODAL's own controls
     * (name box, frequency slots, colour swatches, save, delete). These
     * only draw while {@link #renderEditor} runs, i.e. {@code editorOpen}
     * is already true, but the editor itself can be covered by the
     * picker overlay (clicking a frequency slot opens the picker on top
     * of the editor), so that still needs suppressing here.
     */
    private void addEditorTip(int x, int y, int w, int h, String key) {
        if (picker.isOpen()) return;
        ScreenTips.add(x, y, w, h, key);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker.mouseClicked(mouseX, mouseY, button)) return true;

        if (editorOpen) {
            // Click-to-focus for the standalone box (see openEditor note)
            if (nameBox.mouseClicked(mouseX, mouseY, button)) {
                nameBox.setFocused(true);
                return true;
            }
            nameBox.setFocused(false);
            if (over(mouseX, mouseY, edSlot1X(), edSlotY(), 18, 18)) {
                UISounds.tick(1.3F);
                picker.open(width, height, stack -> editStack1 = stack, true);
                return true;
            }
            if (over(mouseX, mouseY, edSlot2X(), edSlotY(), 18, 18)) {
                UISounds.tick(1.3F);
                picker.open(width, height, stack -> editStack2 = stack, true);
                return true;
            }
            for (int i = 0; i < COLORS.length; i++) {
                int sx = edSwatchX(i);
                int sy = edSwatchY() + (i / 8) * 14;
                if (over(mouseX, mouseY, sx, sy, 12, 12)) {
                    editColor = COLORS[i];
                    UISounds.tick(1.4F);
                    return true;
                }
            }
            if (over(mouseX, mouseY, edX() + 8, edButtonY(), 74, 18)) {
                saveEditor();
                return true;
            }
            if (editIndex >= 0 && over(mouseX, mouseY, edX() + ED_W - 82, edButtonY(), 74, 18)) {
                deleteEditor();
                return true;
            }
            if (mouseX < edX() || mouseX >= edX() + ED_W
                    || mouseY < edY() || mouseY >= edY() + ED_H) {
                closeEditor();
            }
            return true;
        }

        if (button == 0 && overBtn(mouseX, mouseY, homeBtnX())) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0 && overBtn(mouseX, mouseY, pinBtnX())) {
            if (OverlayPin.isPinned(view, Program.GAUGES)) {
                OverlayPin.unpin();
                UISounds.tick(1.0F);
            } else {
                OverlayPin.pin(view, Program.GAUGES);
                UISounds.tick(1.5F);
            }
            return true;
        }
        if (button == 0) {
            int index = tileIndexAt(mouseX, mouseY);
            if (index >= 0) {
                openEditor(index < gauges().size() ? index : -2);
                return true;
            }
        }
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
        if (editorOpen && nameBox.isFocused() && keyCode != 256) {
            nameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (editorOpen && keyCode == 256) { // ESC closes just the editor
            closeEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picker.charTyped(codePoint, modifiers)) return true;
        if (editorOpen && nameBox.isFocused()) {
            nameBox.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        UISounds.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
