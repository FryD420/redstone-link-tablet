package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.LinkTabletMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame hover-tooltip collector (1.12.1). Screens register the rect
 * they ALREADY computed for drawing a control; one {@link #draw} call at
 * the end of render paints the last-registered rect under the cursor and
 * clears the list.
 *
 * <p>Last-registered wins, so a popup or modal drawn over a header glyph
 * takes priority with no z-index bookkeeping, and a control that stops
 * being drawn automatically stops tipping.
 *
 * <p>Click handling is deliberately NOT involved: this class never sees
 * mouseClicked, so adding a tooltip can never change what a control does.
 *
 * <p><b>Frame boundary:</b> {@code draw}/{@code drawWindows} clear
 * {@link #TIPS} after painting, but nothing forces a registering screen's
 * {@code render} to actually reach that call every frame (an early return
 * between a screen's {@code add} calls and its {@code draw} call would
 * otherwise leave that frame's entries stranded — a stale tooltip, or a
 * rect from one screen bleeding into whatever screen renders next). To
 * make that structurally impossible, {@link #onRenderPre} clears
 * {@link #TIPS} on every {@link ScreenEvent.Render.Pre}, which NeoForge
 * fires once per screen render, immediately BEFORE {@code Screen#render}
 * runs (verified against {@code neoforge-21.1.233-universal.jar} and
 * already relied on for its {@code Render.Post} sibling by
 * {@link NoteWindows}). So every frame starts with an empty list before
 * any screen's {@code add}/{@code glyph} calls run, regardless of whether
 * the previous frame's {@code draw} call happened — a screen that forgets
 * to call {@code draw} loses only its OWN tooltips that frame, it can
 * never leak stale rects into a later one.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
final class ScreenTips {

    /** The header-row glyph square — MODE_BTN_SIZE on every screen. */
    private static final int GLYPH = 12;

    private record Tip(int x, int y, int w, int h, Component text) {
        boolean hit(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final List<Tip> TIPS = new ArrayList<>();

    /**
     * Frame boundary (see class javadoc): fires once per screen render,
     * before that screen's {@code render} body runs, so this always
     * clears any entries a previous frame failed to consume via
     * {@link #draw}/{@link #drawWindows}.
     */
    @SubscribeEvent
    static void onRenderPre(ScreenEvent.Render.Pre event) {
        TIPS.clear();
    }

    static void add(int x, int y, int w, int h, String key) {
        add(x, y, w, h, Component.translatable(key));
    }

    static void add(int x, int y, int w, int h, Component text) {
        TIPS.add(new Tip(x, y, w, h, text));
    }

    /** A header-row glyph at the standard 12x12 mode-button rect. */
    static void glyph(int x, int y, String key) {
        add(x, y, GLYPH, GLYPH, key);
    }

    /**
     * Screen pass. Yields to the floating windows: they draw ABOVE every
     * screen through NoteWindows' ScreenEvent.Render.Post, which is a
     * SECOND draw call in the same frame — without this guard both passes
     * could paint, leaving a screen tooltip stranded under a window.
     */
    static void draw(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (NoteWindows.anyContains(mouseX, mouseY)) {
            TIPS.clear();
            return;
        }
        paint(g, font, mouseX, mouseY);
    }

    /** Window pass — already on top, so no yielding. */
    static void drawWindows(GuiGraphics g, Font font, int mouseX, int mouseY) {
        paint(g, font, mouseX, mouseY);
    }

    private static void paint(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Component hit = null;
        for (Tip tip : TIPS) {
            if (tip.hit(mouseX, mouseY)) {
                hit = tip.text(); // last registered wins
            }
        }
        TIPS.clear();
        if (hit != null) {
            g.renderTooltip(font, hit, mouseX, mouseY);
        }
    }

    /** Widget-path equivalent, so ChromeButtons read identically. */
    static Tooltip tooltip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    private ScreenTips() {
    }
}
