package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.client.screen.chrome.ChromeEditBox;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Timezone search picker (1.10.0 Clock app): the {@link PickerOverlay}
 * pattern — modal, z-lifted, swallows input while open — as text rows
 * over {@link ZoneId#getAvailableZoneIds()} (~600 entries, hence the
 * search box). Each row shows the zone id and its current wall time so
 * you can pick by "what time is it there" without knowing the name.
 */
public class ZonePickerOverlay {

    private static final int ROW_H = 14;
    private static final int PANEL_W = 190;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Font font;
    private final List<String> allZones;
    private List<String> filtered;

    private EditBox searchBox;
    private Consumer<ZoneId> onPick;
    private boolean open;
    private double scroll;

    private int x, y, w, h;

    public ZonePickerOverlay(Font font) {
        this.font = font;
        this.allZones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        this.allZones.sort(String::compareTo);
        this.filtered = allZones;
    }

    public boolean isOpen() {
        return open;
    }

    public void open(int screenWidth, int screenHeight, Consumer<ZoneId> onPick) {
        this.onPick = onPick;
        this.w = PANEL_W;
        this.h = Mth.clamp(screenHeight - 60, 140, 220);
        this.x = (screenWidth - w) / 2;
        this.y = (screenHeight - h) / 2;
        this.scroll = 0;
        // ChromeEditBox inset rule: painted ink-well matches the old
        // bordered-EditBox rect (see PickerOverlay)
        this.searchBox = new ChromeEditBox(font, x + 12, y + 13, w - 24, 8,
                Component.translatable("gui.linktablet.clock.zone_search"));
        this.searchBox.setHint(Component.translatable("gui.linktablet.clock.zone_search"));
        this.searchBox.setResponder(this::applyFilter);
        this.searchBox.setFocused(true);
        applyFilter("");
        this.open = true;
    }

    public void close() {
        open = false;
        onPick = null;
        searchBox = null;
    }

    private void applyFilter(String query) {
        scroll = 0;
        if (query == null || query.isBlank()) {
            filtered = allZones;
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String id : allZones) {
            if (id.toLowerCase(Locale.ROOT).contains(q)) {
                result.add(id);
            }
        }
        filtered = result;
    }

    private int listLeft() {
        return x + 8;
    }

    private int listTop() {
        return y + 30;
    }

    private int listBottom() {
        return y + h - 8;
    }

    /** Draw the overlay (call after everything else; z-lifted). */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, ScreenTheme theme) {
        if (!open) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        graphics.fill(0, 0, screenWidth, screenHeight, 0xB0000000);
        Chrome.panel(graphics, x - 2, y - 2, w + 4, h + 4, theme);

        searchBox.render(graphics, mouseX, mouseY, partialTick);

        int left = listLeft();
        int right = x + w - 8;
        graphics.enableScissor(left, listTop(), right, listBottom());
        for (int i = 0; i < filtered.size(); i++) {
            int ry = listTop() + i * ROW_H - (int) scroll;
            if (ry + ROW_H < listTop() || ry > listBottom()) continue;
            boolean hovered = mouseX >= left && mouseX < right
                    && mouseY >= ry && mouseY < ry + ROW_H
                    && mouseY >= listTop() && mouseY < listBottom();
            if (hovered) {
                graphics.fill(left, ry, right, ry + ROW_H, theme.rowBgHover);
            }
            String id = filtered.get(i);
            String time;
            try {
                time = ZonedDateTime.now(ZoneId.of(id)).format(TIME);
            } catch (Exception e) {
                time = "--:--";
            }
            int timeW = font.width(time);
            int idBudget = right - left - timeW - 8;
            String shown = font.width(id) > idBudget
                    ? font.plainSubstrByWidth(id, idBudget - font.width("…")) + "…" : id;
            graphics.drawString(font, shown, left + 2, ry + 3, theme.textPrimary, theme.textShadow);
            graphics.drawString(font, time, right - timeW - 2, ry + 3, theme.textMuted, theme.textShadow);
        }
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    /** Returns true while open (the overlay swallows all clicks). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && mouseY >= listTop() && mouseY < listBottom()
                && mouseX >= listLeft() && mouseX < x + w - 8) {
            int row = (int) ((mouseY - listTop() + scroll) / ROW_H);
            if (row >= 0 && row < filtered.size()) {
                ZoneId picked;
                try {
                    picked = ZoneId.of(filtered.get(row));
                } catch (Exception e) {
                    return true;
                }
                Consumer<ZoneId> callback = onPick;
                close();
                callback.accept(picked);
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
        int maxScroll = Math.max(0, filtered.size() * ROW_H - (listBottom() - listTop()));
        scroll = Mth.clamp(scroll - scrollY * ROW_H, 0, maxScroll);
        return true;
    }

    /** Returns true if the overlay consumed the key (it eats everything but ESC). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == 256) { // ESC closes just the overlay
            close();
            return true;
        }
        searchBox.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!open) return false;
        searchBox.charTyped(codePoint, modifiers);
        return true;
    }
}
