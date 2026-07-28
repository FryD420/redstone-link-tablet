package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Signal-link editor (1.10.0): the {@link ZonePickerOverlay} modal
 * pattern as rows over the tablet's OTHER link-able signals (toggles
 * and timers — momentary/slider can't be targets, v1 scope). Clicking
 * a row cycles its mode none → ON → OFF → FOLLOW; the overlay mutates
 * the edit screen's staged link list in place, and Save commits it
 * with the rest of the signal. Rows for signals that predate links and
 * were never re-saved (linkId 0) are inert with a "save it once first"
 * hint — one save round mints their ids.
 */
public class LinkPickerOverlay {

    /** One pickable target row. */
    public record Candidate(String name, int linkId, boolean timer) {
    }

    private static final int ROW_H = 14;
    private static final int PANEL_W = 190;

    private final Font font;

    private List<Candidate> candidates = List.of();
    private List<Signal.Link> links;
    private boolean open;
    private double scroll;

    private int x, y, w, h;

    public LinkPickerOverlay(Font font) {
        this.font = font;
    }

    public boolean isOpen() {
        return open;
    }

    /** Opens over the given candidates, editing {@code links} IN PLACE. */
    public void open(int screenWidth, int screenHeight,
                     List<Candidate> candidates, List<Signal.Link> links) {
        this.candidates = candidates;
        this.links = links;
        this.w = PANEL_W;
        this.h = Mth.clamp(30 + candidates.size() * ROW_H + 8, 70, 220);
        this.x = (screenWidth - w) / 2;
        this.y = (screenHeight - h) / 2;
        this.scroll = 0;
        this.open = true;
    }

    public void close() {
        open = false;
        candidates = List.of();
        links = null;
    }

    private int listLeft() {
        return x + 8;
    }

    private int listTop() {
        return y + 26;
    }

    private int listBottom() {
        return y + h - 8;
    }

    private Signal.Link.Mode modeFor(int targetId) {
        for (Signal.Link link : links) {
            if (link.targetId() == targetId) return link.mode();
        }
        return null;
    }

    /** none → ON → OFF → FOLLOW → none, capped at {@link Signal#MAX_LINKS}. */
    private void cycle(Candidate candidate) {
        Signal.Link.Mode current = modeFor(candidate.linkId());
        links.removeIf(link -> link.targetId() == candidate.linkId());
        Signal.Link.Mode next = current == null ? Signal.Link.Mode.ON
                : current == Signal.Link.Mode.ON ? Signal.Link.Mode.OFF
                : current == Signal.Link.Mode.OFF ? Signal.Link.Mode.FOLLOW
                : null;
        if (next != null) {
            if (current == null && links.size() >= Signal.MAX_LINKS) {
                UISounds.tick(0.7F); // full — the cap holds the door
                return;
            }
            links.add(new Signal.Link(candidate.linkId(), next));
            UISounds.tick(1.2F + next.ordinal() * 0.15F);
        } else {
            UISounds.tick(0.9F);
        }
    }

    private static Component modeLabel(Signal.Link.Mode mode) {
        String key = mode == null ? "none" : switch (mode) {
            case ON -> "on";
            case OFF -> "off";
            case FOLLOW -> "follow";
        };
        return Component.translatable("gui.linktablet.links.mode." + key);
    }

    /** Draw the overlay (call after everything else; z-lifted). */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, ScreenTheme theme) {
        if (!open) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        graphics.fill(0, 0, screenWidth, screenHeight, 0xB0000000);
        Chrome.panel(graphics, x - 2, y - 2, w + 4, h + 4, theme);
        graphics.drawString(font, Component.translatable("gui.linktablet.links.title"),
                x + 8, y + 8, theme.textPrimary, theme.textShadow);

        int left = listLeft();
        int right = x + w - 8;
        if (candidates.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.linktablet.links.empty"),
                    left, listTop() + 4, theme.textFaint, theme.textShadow);
        }
        graphics.enableScissor(left, listTop(), right, listBottom());
        for (int i = 0; i < candidates.size(); i++) {
            int ry = listTop() + i * ROW_H - (int) scroll;
            if (ry + ROW_H < listTop() || ry > listBottom()) continue;
            Candidate candidate = candidates.get(i);
            boolean eligible = candidate.linkId() != 0;
            boolean hovered = eligible && mouseX >= left && mouseX < right
                    && mouseY >= ry && mouseY < ry + ROW_H
                    && mouseY >= listTop() && mouseY < listBottom();
            if (hovered) {
                graphics.fill(left, ry, right, ry + ROW_H, theme.rowBgHover);
            }
            Signal.Link.Mode mode = eligible ? modeFor(candidate.linkId()) : null;
            Component tag = eligible ? modeLabel(mode)
                    : Component.translatable("gui.linktablet.links.unsaved");
            int tagW = font.width(tag);
            String name = candidate.name() + (candidate.timer()
                    ? " " + Component.translatable("gui.linktablet.links.timer_tag").getString() : "");
            int nameBudget = right - left - tagW - 8;
            String shown = font.width(name) > nameBudget
                    ? font.plainSubstrByWidth(name, nameBudget - font.width("…")) + "…" : name;
            int nameColor = eligible ? theme.textPrimary : theme.textFaint;
            int tagColor = !eligible ? theme.textFaint
                    : mode == null ? theme.textMuted : theme.accent;
            graphics.drawString(font, shown, left + 2, ry + 3, nameColor, theme.textShadow);
            graphics.drawString(font, tag, right - tagW - 2, ry + 3, tagColor, theme.textShadow);
        }
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    /** Returns true while open (the overlay swallows all clicks). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button == 0 && mouseY >= listTop() && mouseY < listBottom()
                && mouseX >= listLeft() && mouseX < x + w - 8) {
            int row = (int) ((mouseY - listTop() + scroll) / ROW_H);
            if (row >= 0 && row < candidates.size()) {
                Candidate candidate = candidates.get(row);
                if (candidate.linkId() != 0) {
                    cycle(candidate);
                }
                return true;
            }
        }
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            close();
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!open) return false;
        int maxScroll = Math.max(0, candidates.size() * ROW_H - (listBottom() - listTop()));
        scroll = Mth.clamp(scroll - scrollY * ROW_H, 0, maxScroll);
        return true;
    }

    /** Returns true if the overlay consumed the key (ESC closes it). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == 256) {
            close();
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return open;
    }

    /** The edit screen's candidate builder: every OTHER toggle/timer
     * signal on the tablet, in list order. */
    public static List<Candidate> candidates(List<Signal> signals, int selfIndex) {
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < signals.size(); i++) {
            if (i == selfIndex) continue;
            Signal signal = signals.get(i);
            if (signal.momentary() || signal.slider()) continue;
            result.add(new Candidate(signal.name(), signal.linkId(), signal.timed()));
        }
        return result;
    }
}
