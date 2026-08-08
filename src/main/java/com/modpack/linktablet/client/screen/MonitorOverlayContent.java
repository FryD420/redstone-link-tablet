package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.client.ClientMonitorSnapshot;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.MonitorChannels;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The pinned Frequency Monitor body (1.11.0): one compact line per
 * channel — small freq icons, transmitter count, effective power bar —
 * live on the HUD. No member detail (that stays in the full
 * {@link MonitorScreen}); right-click the window opens it. Skeleton
 * rows from {@link MonitorChannels#channelsOf} carry the layout before
 * the first live snapshot arrives, same as the full screen.
 *
 * <p>Subscription lifecycle deliberately does NOT mirror MonitorScreen's
 * init/removed: overlays outlive screens and reconstruct across relogs,
 * so acquiring in the constructor would leak a phantom consumer the
 * server never sees released. Instead the first {@link #render} lazily
 * acquires (tracked by {@link #acquired}), {@link #defocus()} releases —
 * and render re-acquires if a relog wiped the client store's consumers
 * out from under a still-pinned window (defocus can be followed by more
 * renders while the pin itself lives on; that path re-acquires
 * naturally next frame).
 */
public class MonitorOverlayContent implements OverlayContent {

    private static final int ROW_H = 20;
    private static final int ICON_SIZE = 8;
    private static final int BAR_W = 28;

    private final Supplier<SignalView> view;
    private boolean acquired = false;

    public MonitorOverlayContent(Supplier<SignalView> view) {
        this.view = view;
    }

    private List<ModNetworking.MonitorChannel> channels() {
        List<ModNetworking.MonitorChannel> live = ClientMonitorSnapshot.channels();
        if (!live.isEmpty()) return live;
        SignalView v = view.get();
        List<Frequency> known = MonitorChannels.channelsOf(v.signals(), v.gauges(), v.monitorProbes());
        if (known.isEmpty()) return List.of();
        List<ModNetworking.MonitorChannel> skeleton = new ArrayList<>(known.size());
        for (Frequency freq : known) {
            skeleton.add(new ModNetworking.MonitorChannel(freq, List.of()));
        }
        return skeleton;
    }

    @Override
    public int height(int rowWidth) {
        return Math.max(1, channels().size()) * ROW_H - 2;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        if (!acquired) {
            ClientMonitorSnapshot.acquire(view.get().target());
            acquired = true;
        } else if (!ClientMonitorSnapshot.hasTarget()) {
            // A relog zeroed the store's consumers/target out from under
            // this still-pinned window (defocus() already ran, but the
            // pin survives) — re-subscribe without double-counting since
            // we never flipped `acquired` back off.
            ClientMonitorSnapshot.acquire(view.get().target());
        }

        List<ModNetworking.MonitorChannel> channels = channels();
        if (channels.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.monitor.empty");
            String shown = TextFit.ellipsize(font, hint.getString(), rowWidth);
            graphics.drawString(font, shown, x + (rowWidth - font.width(shown)) / 2,
                    top + 6, theme.textFaint, theme.textShadow);
            return;
        }
        for (int i = 0; i < channels.size(); i++) {
            int ry = top + i * ROW_H;
            if (ry + ROW_H < clipTop || ry > clipBottom) continue;
            renderRow(graphics, font, theme, channels.get(i), x, ry, rowWidth);
        }
    }

    private void renderRow(GuiGraphics graphics, Font font, ScreenTheme theme,
                           ModNetworking.MonitorChannel channel, int x, int ry, int rowWidth) {
        graphics.fill(x, ry, x + rowWidth, ry + ROW_H - 2, theme.rowBg);

        Frequency freq = channel.frequency();
        int icon1X = x + 3;
        int icon2X = icon1X + ICON_SIZE + 1;
        int iconY = ry + (ROW_H - 2 - ICON_SIZE) / 2;
        renderMiniIcon(graphics, freq.icon1(), icon1X, iconY);
        renderMiniIcon(graphics, freq.icon2(), icon2X, iconY);

        int transmitting = 0;
        int power = 0;
        for (ModNetworking.MonitorMember member : channel.members()) {
            if (member.strength() > 0 && member.inRange()) {
                transmitting++;
                power = Math.max(power, member.strength());
            }
        }

        String level = String.valueOf(power);
        int levelW = font.width(level);
        int barX1 = x + rowWidth - 3 - levelW - 4;
        int barX0 = barX1 - BAR_W;
        int barY = ry + (ROW_H - 2 - 4) / 2;
        graphics.drawString(font, level, x + rowWidth - 3 - levelW, ry + (ROW_H - 2 - 8) / 2,
                power > 0 ? theme.textPrimary : theme.textMuted, theme.textShadow);
        graphics.fill(barX0, barY, barX1, barY + 4, theme.switchOff);
        if (power > 0 && barX1 > barX0) {
            int fillX = barX0 + Math.round((barX1 - barX0) * (power / 15F));
            graphics.fill(barX0, barY, fillX, barY + 4, theme.accentDim);
        }

        int textX = icon2X + ICON_SIZE + 5;
        int textBudget = Math.max(0, barX0 - 4 - textX);
        Component summary = Component.translatable("gui.linktablet.monitor.transmitters", transmitting);
        String summaryText = TextFit.ellipsize(font, summary.getString(), textBudget);
        graphics.drawString(font, summaryText, textX, ry + (ROW_H - 2 - 8) / 2,
                transmitting > 0 ? theme.textPrimary : theme.textMuted, theme.textShadow);
    }

    /** Item icon scaled to {@link #ICON_SIZE}px (the full-screen rows use
     * vanilla's native 16px via {@code graphics.renderItem}). */
    private static void renderMiniIcon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(ICON_SIZE / 16F, ICON_SIZE / 16F, 1F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth) {
        return false; // read-only pane; the window handles right-click-to-open
    }

    @Override
    public void defocus() {
        if (acquired) {
            ClientMonitorSnapshot.release();
            acquired = false;
        }
    }
}
