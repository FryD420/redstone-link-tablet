package com.modpack.linktablet.client.screen.chrome;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Vanilla {@link EditBox} over a Chrome ink-well: unbordered, with the
 * dark recessed field painted around it. Construct it INSET by (4, 5)
 * with w-8/h-10 relative to the old bordered box's rect — the painted
 * footprint then matches pixel-for-pixel, and since an unbordered
 * EditBox draws text exactly at (x, y), cursor and click math stay
 * vanilla-correct. Light text on the fixed dark field is shadow-safe on
 * every theme (see the ink-well note in {@link Chrome}).
 */
public class ChromeEditBox extends EditBox {

    public ChromeEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setBordered(false);
        setTextColor(0xE0E0E0);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        Chrome.inkField(graphics, getX() - 4, getY() - 5, width + 8, height + 10);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
