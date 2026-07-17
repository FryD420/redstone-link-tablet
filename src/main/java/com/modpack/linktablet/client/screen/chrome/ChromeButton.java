package com.modpack.linktablet.client.screen.chrome;

import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Vanilla {@link Button} in banner-plaque clothing: only
 * {@link #renderWidget} is overridden — click, keyboard, focus, and
 * narration behavior stay vanilla. The label is drawn manually so the
 * theme's {@code textShadow} rule applies (vanilla centered text always
 * shadows).
 */
public class ChromeButton extends Button {

    private final Supplier<ScreenTheme> theme;

    public ChromeButton(int x, int y, int width, int height, Component message,
                        OnPress onPress, Supplier<ScreenTheme> theme) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.theme = theme;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenTheme t = theme.get();
        Chrome.ButtonState state = !active ? Chrome.ButtonState.DISABLED
                : isHoveredOrFocused() ? Chrome.ButtonState.HOVER
                : Chrome.ButtonState.NORMAL;
        Chrome.bannerButton(graphics, getX(), getY(), width, height, state,
                state == Chrome.ButtonState.HOVER ? t.rowBgHover : t.rowBg);

        Font font = Minecraft.getInstance().font;
        int textColor = active ? t.textPrimary : t.textFaint;
        int tx = getX() + (width - font.width(getMessage())) / 2;
        int ty = getY() + (height - 8) / 2;
        graphics.drawString(font, getMessage(), tx, ty, textColor, t.textShadow);

        if (isFocused()) {
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, t.accent);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, t.accent);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, t.accent);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, t.accent);
        }
    }
}
