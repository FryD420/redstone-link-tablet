package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Programs;
import com.modpack.linktablet.api.TabletProgram;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.client.screen.chrome.ChromeEditBox;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The App Store (1.10.0, user decision from the first test pass —
 * replaced the app drawer): every program as a shelf card with a
 * description and a Get/Remove button. "Getting" an app puts its tile
 * on this tablet's Home screen; underneath it's still the
 * {@code home_apps} roster and SetHomeAppsPayload. The store lives
 * behind the launcher's permanent last tile, and the ADDON API — other
 * mods shelving their own programs here — is a planned follow-up
 * (roadmap), which is why the shelf reads like a catalog and not a
 * settings list.
 */
public class StoreScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int HEADER = 34;
    private static final int ROW_H = 32;
    private static final int ROW_GAP = 4;
    /** Fixed shelf viewport — the catalog scrolls (24 programs). */
    private static final int CONTENT_H = 160;
    private static final int BOTTOM_PAD = 8;
    private static final int MODE_BTN_SIZE = 12;
    private static final int BTN_W = 48;
    private static final int BTN_H = 16;

    /** Search row height (1.11.0, tester request — 25-program catalog). */
    private static final int SEARCH_H = 22;

    private final SignalView view;
    private double scroll = 0;
    private EditBox searchBox;
    /** Survives init() re-runs (window resize recreates the box). */
    private String query = "";

    public StoreScreen(SignalView view) {
        super(Component.translatable("program.linktablet.store"));
        this.view = view;
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    @Override
    protected void init() {
        super.init();
        // Inset by (4,5)/(w-8,h-10): the painted ink-well footprint matches
        // the old bordered box's rect (ChromeEditBox rule)
        searchBox = new ChromeEditBox(font, panelLeft() + 12, searchTop() + 6,
                PANEL_W - 24, 8, Component.translatable("gui.linktablet.store.search"));
        searchBox.setHint(Component.translatable("gui.linktablet.store.search"));
        searchBox.setValue(query);
        searchBox.setResponder(q -> {
            query = q;
            scroll = 0;
        });
    }

    /** The shelf, filtered by the search query (name, description, key). */
    private List<TabletProgram> apps() {
        List<TabletProgram> catalog = Programs.catalog();
        if (query.isBlank()) return catalog;
        String q = query.toLowerCase(Locale.ROOT);
        List<TabletProgram> filtered = new ArrayList<>();
        for (TabletProgram program : catalog) {
            if (program.displayName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || program.storeDescription().getString().toLowerCase(Locale.ROOT).contains(q)
                    || program.key().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(program);
            }
        }
        return filtered;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int contentHeight() {
        List<TabletProgram> apps = apps();
        return apps.isEmpty() ? 0 : apps.size() * (ROW_H + ROW_GAP) - ROW_GAP;
    }

    private int bodyHeight() {
        // Sized by the FULL catalog so the panel doesn't jump while a
        // search filter shrinks the shelf
        int full = Programs.catalog().size() * (ROW_H + ROW_GAP) - ROW_GAP;
        return HEADER + SEARCH_H + Math.min(full, CONTENT_H) + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int searchTop() {
        return bodyTop() + HEADER;
    }

    private int listTop() {
        return searchTop() + SEARCH_H;
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

    private int btnX() {
        return rowX() + rowWidth() - BTN_W - 4;
    }

    private int btnY(int i) {
        return rowY(i) + (ROW_H - BTN_H) / 2;
    }

    private boolean overBtn(double mouseX, double mouseY, int i) {
        return mouseY >= listTop() && mouseY < listBottom()
                && mouseX >= btnX() && mouseX < btnX() + BTN_W
                && mouseY >= btnY(i) && mouseY < btnY(i) + BTN_H;
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
        Chrome.railH(graphics, left - 4, top + HEADER - 8, PANEL_W + 8, theme.bodyOuter);

        searchBox.render(graphics, mouseX, mouseY, partialTick);

        scroll = net.minecraft.util.Mth.clamp(scroll, 0, maxScroll());
        List<TabletProgram> home = view.homeApps();
        List<TabletProgram> apps = apps();
        if (apps.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.store.no_matches");
            graphics.drawString(font, hint, width / 2 - font.width(hint) / 2,
                    (listTop() + listBottom()) / 2 - 4, theme.textMuted, theme.textShadow);
        }
        graphics.enableScissor(rowX(), listTop(), rowX() + rowWidth(), listBottom());
        for (int i = 0; i < apps.size(); i++) {
            TabletProgram program = apps.get(i);
            int x = rowX();
            int y = rowY(i);
            if (y + ROW_H < listTop() || y > listBottom()) continue;
            boolean onHome = home.contains(program);
            Chrome.plaque(graphics, x, y, rowWidth(), ROW_H, theme.rowBg);
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
            // Name + description, budgeted against the button
            int textX = x + ROW_H + 4;
            graphics.drawString(font, program.displayName(),
                    textX, y + 6, theme.textPrimary, theme.textShadow);
            String desc = program.storeDescription().getString();
            int descBudget = btnX() - textX - 4;
            if (font.width(desc) > descBudget) {
                desc = font.plainSubstrByWidth(desc, descBudget - font.width("…")) + "…";
            }
            graphics.drawString(font, desc, textX, y + 18, theme.textMuted, theme.textShadow);
            // Get / Remove
            boolean lastApp = onHome && home.size() == 1;
            boolean hovered = !lastApp && overBtn(mouseX, mouseY, i);
            Chrome.bannerButton(graphics, btnX(), btnY(i), BTN_W, BTN_H,
                    lastApp ? Chrome.ButtonState.DISABLED
                            : hovered ? Chrome.ButtonState.HOVER : Chrome.ButtonState.NORMAL,
                    hovered ? theme.rowBgHover : theme.rowBg);
            Component action = Component.translatable(onHome
                    ? "gui.linktablet.store.remove" : "gui.linktablet.store.get");
            int color = lastApp ? theme.textFaint : onHome ? theme.textMuted : theme.accent;
            graphics.drawString(font, action, btnX() + (BTN_W - font.width(action)) / 2,
                    btnY(i) + (BTN_H - 8) / 2, color, theme.textShadow);
        }
        graphics.disableScissor();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void sendHome(List<TabletProgram> home) {
        PacketDistributor.sendToServer(new ModNetworking.SetHomeAppsPayload(
                view.target(), Programs.keys(home)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Standalone EditBox (no widget registration): vanilla's
        // mouseClicked repositions the cursor but NEVER focuses — focus
        // normally comes from Screen's widget traversal, so a manual box
        // must set it on the click path itself or typing stays gated off
        // (canConsumeInput requires isFocused).
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            searchBox.setFocused(true);
            return true;
        }
        searchBox.setFocused(false);
        if (button == 0 && overHomeBtn(mouseX, mouseY)) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0) {
            List<TabletProgram> apps = apps();
            for (int i = 0; i < apps.size(); i++) {
                if (!overBtn(mouseX, mouseY, i)) continue;
                TabletProgram program = apps.get(i);
                List<TabletProgram> home = new java.util.ArrayList<>(view.homeApps());
                if (home.contains(program)) {
                    if (home.size() == 1) {
                        UISounds.tick(0.7F); // the last app holds the door
                    } else {
                        home.remove(program);
                        UISounds.delete();
                        sendHome(home);
                    }
                } else {
                    home.add(program);
                    UISounds.confirm();
                    sendHome(home);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = net.minecraft.util.Mth.clamp(scroll - scrollY * (ROW_H + ROW_GAP), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return super.keyPressed(keyCode, scanCode, modifiers); // ESC closes
        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        // While typing, keep the inventory key from closing the screen
        // (the GaugesScreen nameBox rule)
        if (searchBox.isFocused()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.charTyped(codePoint, modifiers)) return true;
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
