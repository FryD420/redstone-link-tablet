package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.api.TabletProgram;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * The pinned Launcher body (1.10.0 user feedback: the launcher's pin
 * pins the launcher): the tablet's home roster as an app dock — icon
 * chip + name per row, one click opens that program's full screen on
 * the pinned tablet. The roster is read live, so drawer/store edits
 * reflow the dock.
 */
public class LauncherOverlayContent implements OverlayContent {

    private static final int ROW_H = 22;

    private final Supplier<SignalView> view;

    public LauncherOverlayContent(Supplier<SignalView> view) {
        this.view = view;
    }

    @Override
    public int height(int rowWidth) {
        return Math.max(1, view.get().homeApps().size()) * ROW_H - 2;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        List<TabletProgram> home = view.get().homeApps();
        for (int i = 0; i < home.size(); i++) {
            int ry = top + i * ROW_H;
            if (ry + ROW_H < clipTop || ry > clipBottom) continue;
            TabletProgram program = home.get(i);
            boolean hovered = reachable && mouseX >= x && mouseX < x + rowWidth
                    && mouseY >= ry && mouseY < ry + ROW_H - 2;
            graphics.fill(x, ry, x + rowWidth, ry + ROW_H - 2, hovered ? theme.rowBgHover : theme.rowBg);
            graphics.fill(x + 2, ry + 2, x + 18, ry + ROW_H - 4, program.chipColor() | 0xFF000000);
            ResourceLocation icon = program.iconItem();
            if (icon != null) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(icon));
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 2, ry + 2);
                }
            }
            graphics.drawString(font, program.displayName(),
                    x + 24, ry + (ROW_H - 10) / 2, theme.textPrimary, theme.textShadow);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth) {
        if (button != 0) return false;
        List<TabletProgram> home = view.get().homeApps();
        for (int i = 0; i < home.size(); i++) {
            int ry = top + i * ROW_H;
            if (mouseY >= ry && mouseY < ry + ROW_H - 2) {
                ClientHooks.openProgram(home.get(i), view.get());
                return true;
            }
        }
        return false;
    }
}
