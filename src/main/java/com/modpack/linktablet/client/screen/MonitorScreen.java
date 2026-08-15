package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.ClientMonitorSnapshot;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.MonitorChannels;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Frequency Monitor program (1.11.0): a read-only, live view of
 * every Redstone Link channel this tablet cares about — every signal's
 * frequency, every gauge's frequency, and an optional user-set "probe"
 * channel — with every member (link block, placed tablet, carried
 * tablet) currently transmitting or listening on it. Snapshots stream
 * in from the server ({@link ClientMonitorSnapshot}) while this screen
 * (or the overlay pin) is open; before the first snapshot arrives, rows
 * render skeleton-only from {@link MonitorChannels#channelsOf}.
 */
public class MonitorScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int HEADER = 34;
    private static final int MODE_BTN_SIZE = 12;
    private static final int BOTTOM_PAD = 8;

    private static final int PROBE_ROW_H = 34;
    private static final int CHANNEL_HEADER_H = 34;
    private static final int MEMBER_ROW_H = 12;
    private static final int CHANNEL_GAP = 4;
    /** Fixed scrolling viewport for the channel list. */
    private static final int CONTENT_H = 130;

    private final SignalView view;

    private double scroll = 0;
    private boolean subscribed = false;

    public MonitorScreen(SignalView view) {
        super(Component.translatable("program.linktablet.monitor"));
        this.view = view;
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    @Override
    protected void init() {
        super.init();
        // Vanilla Screen.resize() re-calls init() WITHOUT calling removed()
        // first — guard so a window resize while open doesn't bump the
        // consumer count with no matching release (orphaned subscription).
        if (!subscribed) {
            ClientMonitorSnapshot.acquire(view.target());
            subscribed = true;
        }
    }

    @Override
    public void removed() {
        // Guard against any future double-removed path bumping the shared
        // consumer count's release below zero — release only matches an
        // actual acquire.
        if (subscribed) {
            ClientMonitorSnapshot.release();
            subscribed = false;
        }
        super.removed();
    }

    // ------------------------------------------------------------------
    // Channel data (live snapshot, or a skeleton while it's in flight)
    // ------------------------------------------------------------------

    private List<ModNetworking.MonitorChannel> channels() {
        List<ModNetworking.MonitorChannel> live = ClientMonitorSnapshot.channels();
        if (!live.isEmpty()) return live;
        List<Frequency> known = MonitorChannels.channelsOf(view.signals(), view.gauges(), view.monitorProbes());
        if (known.isEmpty()) return List.of();
        List<ModNetworking.MonitorChannel> skeleton = new ArrayList<>(known.size());
        for (Frequency freq : known) {
            skeleton.add(new ModNetworking.MonitorChannel(freq, List.of()));
        }
        return skeleton;
    }

    private static int channelHeight(ModNetworking.MonitorChannel channel) {
        return CHANNEL_HEADER_H + channel.members().size() * MEMBER_ROW_H;
    }

    private int contentHeight() {
        List<ModNetworking.MonitorChannel> channels = channels();
        if (channels.isEmpty()) return 0;
        int h = -CHANNEL_GAP;
        for (ModNetworking.MonitorChannel channel : channels) {
            h += channelHeight(channel) + CHANNEL_GAP;
        }
        return h;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int bodyHeight() {
        return HEADER + PROBE_ROW_H + Math.min(Math.max(contentHeight(), 1), CONTENT_H) + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private int rowX() {
        return panelLeft() + 6;
    }

    private int rowWidth() {
        return PANEL_W - 12;
    }

    private int probeTop() {
        return bodyTop() + HEADER;
    }

    private int addBtnX() {
        return rowX() + 4;
    }

    private int addBtnY() {
        return probeTop() + 12;
    }

    private int listTop() {
        return probeTop() + PROBE_ROW_H;
    }

    private int listBottom() {
        return bodyTop() + bodyHeight() - BOTTOM_PAD;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - (listBottom() - listTop()));
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

    private static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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
        ScreenTips.glyph(homeBtnX(), modeBtnY(), "gui.linktablet.home");
        boolean pinned = OverlayPin.isPinned(view, Program.MONITOR);
        HeaderGlyphs.pin(graphics, pinBtnX(), modeBtnY(),
                pinned ? theme.accent
                        : overBtn(mouseX, mouseY, pinBtnX()) ? theme.glyphHover : theme.textFaint);
        ScreenTips.glyph(pinBtnX(), modeBtnY(), pinned
                ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
        Chrome.railH(graphics, left - 4, top + HEADER - 8, PANEL_W + 8, theme.bodyOuter);

        renderProbeRow(graphics, theme);
        Chrome.railH(graphics, left - 4, listTop() - 4, PANEL_W + 8, theme.bodyOuter);

        scroll = Mth.clamp(scroll, 0, maxScroll());
        List<ModNetworking.MonitorChannel> channels = channels();
        if (channels.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.monitor.empty");
            graphics.drawString(font, hint, width / 2 - font.width(hint) / 2,
                    (listTop() + listBottom()) / 2 - 4, theme.textMuted, theme.textShadow);
        } else {
            graphics.enableScissor(rowX(), listTop(), rowX() + rowWidth(), listBottom());
            int y = listTop() - (int) scroll;
            for (ModNetworking.MonitorChannel channel : channels) {
                int h = channelHeight(channel);
                if (y + h >= listTop() && y <= listBottom()) {
                    renderChannel(graphics, theme, channel, rowX(), y, rowWidth());
                }
                y += h + CHANNEL_GAP;
            }
            graphics.disableScissor();
        }

        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    /** Whether another probe can be added (the cap gate; dupes are
     * rejected by the editor itself). */
    private boolean canAddProbe() {
        return view.monitorProbes().size() < MonitorChannels.MAX_PROBES;
    }

    private void renderProbeRow(GuiGraphics graphics, ScreenTheme theme) {
        int x = rowX();
        int top = probeTop();
        Chrome.plaque(graphics, x, top, rowWidth(), PROBE_ROW_H - 2, theme.rowBg);
        Component label = Component.translatable("gui.linktablet.monitor.probe");
        graphics.drawString(font, label, x + 4, top + 3, theme.textMuted, theme.textShadow);

        // Plus chip — opens the add-probe container menu (the signal
        // editor experience: inventory slots + JEI/EMI panels)
        boolean addable = canAddProbe();
        int addX = addBtnX();
        int addY = addBtnY();
        Chrome.slot(graphics, addX, addY, addable ? theme.rowBgHover : theme.surfaceLo);
        int plusColor = addable ? theme.accent : theme.textFaint;
        graphics.fill(addX + 4, addY + 8, addX + 14, addY + 10, plusColor);
        graphics.fill(addX + 8, addY + 4, addX + 10, addY + 14, plusColor);
        ScreenTips.add(addX, addY, 18, 18, "gui.linktablet.tip.probe.add");

        Component hint = Component.translatable("gui.linktablet.monitor.probe.hint");
        int hintX = addX + 18 + 6;
        int budget = x + rowWidth() - 4 - hintX;
        String shown = TextFit.ellipsize(font, hint.getString(), Math.max(0, budget));
        graphics.drawString(font, shown, hintX, addY + 5, theme.textFaint, theme.textShadow);
    }

    private void renderChannel(GuiGraphics graphics, ScreenTheme theme,
                               ModNetworking.MonitorChannel channel, int x, int y, int w) {
        Frequency freq = channel.frequency();
        Chrome.plaque(graphics, x, y, w, CHANNEL_HEADER_H - 2, theme.rowBgHover);

        int icon1X = x + 4;
        int icon2X = icon1X + 18;
        int iconY = y + 3;
        graphics.renderItem(freq.icon1(), icon1X, iconY);
        graphics.renderItem(freq.icon2(), icon2X, iconY);
        graphics.fill(icon1X, iconY + 16, icon1X + 16, iconY + 18, TabletScreen.FREQ1_COLOR);
        graphics.fill(icon2X, iconY + 16, icon2X + 16, iconY + 18, TabletScreen.FREQ2_COLOR);

        int transmitting = 0;
        int power = 0;
        for (ModNetworking.MonitorMember member : channel.members()) {
            if (member.strength() > 0 && member.inRange()) {
                transmitting++;
                power = Math.max(power, member.strength());
            }
        }

        // Probe rows carry a remove cross top-right (the picker flow's
        // other half — probes leave from the row they created)
        boolean probeRow = view.monitorProbes().contains(freq);
        int removeX = x + w - 12;
        if (probeRow) {
            int crossColor = theme.textFaint;
            for (int d = 0; d < 6; d++) {
                graphics.fill(removeX + d, y + 3 + d, removeX + d + 1, y + 4 + d, crossColor);
                graphics.fill(removeX + 5 - d, y + 3 + d, removeX + 6 - d, y + 4 + d, crossColor);
            }
            ScreenTips.add(removeX, y + 3, 9, 9, "gui.linktablet.tip.probe.remove");
        }

        int textX = icon2X + 16 + 6;
        int textBudget = (probeRow ? removeX - 4 : x + w - 4) - textX;
        Component summary = Component.translatable("gui.linktablet.monitor.transmitters", transmitting);
        String summaryText = TextFit.ellipsize(font, summary.getString(), Math.max(0, textBudget));
        graphics.drawString(font, summaryText, textX, y + 4,
                transmitting > 0 ? theme.textPrimary : theme.textMuted, theme.textShadow);

        // Effective power: slider-bar look, 0-15, procedural fill (matches
        // SignalRowPainter's slider readout).
        String level = String.valueOf(power);
        int levelW = font.width(level);
        int barY = y + 16;
        int barX1 = x + w - 4 - levelW - 4;
        graphics.drawString(font, level, x + w - 4 - levelW, barY - 2,
                power > 0 ? theme.textPrimary : theme.textMuted, theme.textShadow);
        graphics.fill(textX, barY, barX1, barY + 4, theme.switchOff);
        if (power > 0 && barX1 > textX) {
            int fillX = textX + Math.round((barX1 - textX) * (power / 15F));
            graphics.fill(textX, barY, fillX, barY + 4, theme.accentDim);
        }

        int memberY = y + CHANNEL_HEADER_H;
        for (ModNetworking.MonitorMember member : channel.members()) {
            renderMember(graphics, theme, member, x, memberY, w);
            memberY += MEMBER_ROW_H;
        }
    }

    private void renderMember(GuiGraphics graphics, ScreenTheme theme,
                              ModNetworking.MonitorMember member, int x, int y, int w) {
        boolean inRange = member.inRange();
        boolean active = member.strength() > 0;
        int color = !inRange ? theme.textFaint : active ? theme.textPrimary : theme.textMuted;
        int badgeColor = !inRange ? theme.textFaint : member.listening() ? theme.accent : theme.textMuted;

        int badgeX = x + 4;
        int badgeY = y + (MEMBER_ROW_H - 6) / 2;
        if (member.listening()) {
            // Hollow ring: a receiver, listening for the channel
            graphics.fill(badgeX, badgeY, badgeX + 6, badgeY + 1, badgeColor);
            graphics.fill(badgeX, badgeY + 5, badgeX + 6, badgeY + 6, badgeColor);
            graphics.fill(badgeX, badgeY, badgeX + 1, badgeY + 6, badgeColor);
            graphics.fill(badgeX + 5, badgeY, badgeX + 6, badgeY + 6, badgeColor);
        } else {
            // Solid square: actively broadcasting
            graphics.fill(badgeX + 1, badgeY + 1, badgeX + 5, badgeY + 5, badgeColor);
        }

        String coords = member.pos().getX() + " " + member.pos().getY() + " " + member.pos().getZ();
        int coordsW = font.width(coords);
        int coordsX = x + w - 4 - coordsW;

        String strength = String.valueOf(member.strength());
        int strengthW = font.width(strength);
        int strengthX = coordsX - 6 - strengthW;

        int labelX = badgeX + 10;
        int labelBudget = Math.max(0, strengthX - 4 - labelX);
        String labelText = member.label().getString();
        if (!inRange) {
            labelText = labelText + " (" + Component.translatable(
                    "gui.linktablet.monitor.out_of_range").getString() + ")";
        }
        String shown = TextFit.ellipsize(font, labelText, labelBudget);
        int textY = y + (MEMBER_ROW_H - 8) / 2;
        graphics.drawString(font, shown, labelX, textY, color, theme.textShadow);
        graphics.drawString(font, strength, strengthX, textY, color, theme.textShadow);
        graphics.drawString(font, coords, coordsX, textY, theme.textFaint, theme.textShadow);
    }

    // ------------------------------------------------------------------
    // Probe input
    // ------------------------------------------------------------------

    private void sendProbes(List<Frequency> probes) {
        PacketDistributor.sendToServer(new ModNetworking.SetProbePayload(
                view.target(), probes));
    }

    /** Opens the add-probe container menu server-side (ProbeEditScreen
     * binds to it — a REAL menu, so the JEI/EMI panels actually show;
     * plain screens can't host them). */
    private void openProbeEditor() {
        if (!canAddProbe()) {
            UISounds.tick(0.7F);
            return;
        }
        UISounds.page();
        PacketDistributor.sendToServer(new ModNetworking.OpenProbeMenuPayload(view.target()));
    }

    private void removeProbe(Frequency freq) {
        List<Frequency> probes = new ArrayList<>(view.monitorProbes());
        if (!probes.remove(freq)) return;
        sendProbes(probes);
        UISounds.delete();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBtn(mouseX, mouseY, homeBtnX())) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0 && overBtn(mouseX, mouseY, pinBtnX())) {
            if (OverlayPin.isPinned(view, Program.MONITOR)) {
                OverlayPin.unpin();
                UISounds.tick(1.0F);
            } else {
                OverlayPin.pin(view, Program.MONITOR);
                UISounds.tick(1.5F);
            }
            return true;
        }
        if (button == 0 && over(mouseX, mouseY, addBtnX(), addBtnY(), 18, 18)) {
            openProbeEditor();
            return true;
        }
        // Probe-row remove crosses live inside the scrolled viewport —
        // walk rows exactly like render does, honoring the scissor bounds
        if (button == 0 && mouseY >= listTop() && mouseY < listBottom()) {
            List<Frequency> probes = view.monitorProbes();
            int y = listTop() - (int) scroll;
            for (ModNetworking.MonitorChannel channel : channels()) {
                int h = channelHeight(channel);
                if (probes.contains(channel.frequency())
                        && over(mouseX, mouseY, rowX() + rowWidth() - 12, y + 3, 9, 9)) {
                    removeProbe(channel.frequency());
                    return true;
                }
                y += h + CHANNEL_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * MEMBER_ROW_H * 2, 0, maxScroll());
        return true;
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
