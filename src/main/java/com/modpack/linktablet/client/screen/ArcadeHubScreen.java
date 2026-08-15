package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Arcade hub (1.11.0): one cabinet shelving all 19 formerly-secret
 * games, replacing the 1.10.0 one-app-per-game roster (user decision,
 * 2026-08-08 — reverses that decision by request). Pattern-matches
 * {@link StoreScreen} (panel/header/scroll/scissor shape) but every row
 * is its own tap target — there's no Get/Remove, no search, and no pin
 * (v1 limit): the whole shelf exists to launch a game, so the row IS the
 * button. Best-score line replaces the store's description.
 */
public class ArcadeHubScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int HEADER = 34;
    private static final int ROW_H = 32;
    private static final int ROW_GAP = 4;
    /** Fixed shelf viewport — the cabinet scrolls (19 games). */
    private static final int CONTENT_H = 160;
    private static final int BOTTOM_PAD = 8;
    private static final int MODE_BTN_SIZE = 12;

    private final SignalView view;
    private double scroll = 0;
    /** Built once — the game roster never changes mid-session. */
    private final List<Program> games = games();

    public ArcadeHubScreen(SignalView view) {
        super(Program.ARCADE.displayName());
        this.view = view;
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    private static List<Program> games() {
        List<Program> games = new ArrayList<>();
        for (Program program : Program.values()) {
            if (program.gameId() != null) games.add(program);
        }
        return games;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int contentHeight() {
        return games.isEmpty() ? 0 : games.size() * (ROW_H + ROW_GAP) - ROW_GAP;
    }

    private int bodyHeight() {
        return HEADER + Math.min(contentHeight(), CONTENT_H) + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int listTop() {
        return bodyTop() + HEADER;
    }

    private int listBottom() {
        return bodyTop() + bodyHeight() - BOTTOM_PAD;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - (listBottom() - listTop()));
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

    private int rowY(int i) {
        return listTop() + i * (ROW_H + ROW_GAP) - (int) scroll;
    }

    private int homeBtnX() {
        return panelLeft() + 4;
    }

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    private boolean overHomeBtn(double mouseX, double mouseY) {
        return mouseX >= homeBtnX() && mouseX < homeBtnX() + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    private boolean overRow(double mouseX, double mouseY, int i) {
        return mouseY >= listTop() && mouseY < listBottom()
                && mouseX >= rowX() && mouseX < rowX() + rowWidth()
                && mouseY >= rowY(i) && mouseY < rowY(i) + ROW_H;
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
                overHomeBtn(mouseX, mouseY) ? theme.glyphHover : theme.textFaint);
        ScreenTips.glyph(homeBtnX(), modeBtnY(), "gui.linktablet.home");
        Chrome.railH(graphics, left - 4, top + HEADER - 8, PANEL_W + 8, theme.bodyOuter);

        scroll = Mth.clamp(scroll, 0, maxScroll());
        graphics.enableScissor(rowX(), listTop(), rowX() + rowWidth(), listBottom());
        for (int i = 0; i < games.size(); i++) {
            Program program = games.get(i);
            int x = rowX();
            int y = rowY(i);
            if (y + ROW_H < listTop() || y > listBottom()) continue;
            boolean hovered = overRow(mouseX, mouseY, i);
            Chrome.plaque(graphics, x, y, rowWidth(), ROW_H, hovered ? theme.rowBgHover : theme.rowBg);
            // Icon chip
            graphics.fill(x + 5, y + 5, x + ROW_H - 5, y + ROW_H - 5,
                    program.chipColor() | 0xFF000000);
            ResourceLocation iconId = program.iconItem();
            if (iconId != null) {
                ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(iconId));
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, x + (ROW_H - 16) / 2, y + (ROW_H - 16) / 2);
                }
            }
            // Name + best score
            int textX = x + ROW_H + 4;
            graphics.drawString(font, program.displayName(),
                    textX, y + 6, theme.textPrimary, theme.textShadow);
            int best = ClientPrefs.gameBest(program.key());
            if (best > 0) {
                Component line = Component.translatable("gui.linktablet.arcade.best", best);
                graphics.drawString(font, line, textX, y + 18, theme.textMuted, theme.textShadow);
            }
        }
        graphics.disableScissor();

        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overHomeBtn(mouseX, mouseY)) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < games.size(); i++) {
                if (!overRow(mouseX, mouseY, i)) continue;
                Program program = games.get(i);
                UISounds.page();
                if (view instanceof SignalView.Block) {
                    // Kiosk-nav restore (final-review finding 2): mirrors
                    // ClientHooks.showProgram's block branch, which the
                    // consolidation into this hub dropped — a block-bound
                    // launch should still point the wall screen at the
                    // launched game, like every other block-bound nav does.
                    PacketDistributor.sendToServer(new ModNetworking.SetProgramPayload(
                            view.target(), program.key()));
                }
                Minecraft.getInstance().setScreen(
                        SecretGames.createApp(program.key(), view, Program.ARCADE));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * (ROW_H + ROW_GAP), 0, maxScroll());
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
