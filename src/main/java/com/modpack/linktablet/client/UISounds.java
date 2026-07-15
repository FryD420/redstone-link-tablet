package com.modpack.linktablet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Client-side UI feedback sounds for the tablet screens. Repurposes
 * vanilla sounds (amethyst for the glassy "device" feel, buttons for
 * clicks) so no custom audio assets are needed; swap the constants here
 * if we ever record custom sounds.
 */
public class UISounds {

    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** Tablet GUI opened — soft glassy "unlock". */
    public static void open() {
        play(SoundEvents.AMETHYST_BLOCK_HIT, 1.4F, 0.5F);
    }

    /** Tablet GUI closed. */
    public static void close() {
        play(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 0.4F);
    }

    /** App toggled on/off. */
    public static void toggle(boolean nowActive) {
        if (nowActive) {
            play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.6F, 0.5F);
        } else {
            play(SoundEvents.STONE_BUTTON_CLICK_OFF, 1.3F, 0.5F);
        }
    }

    /** Navigated to the edit screen (edit or new app). */
    public static void page() {
        play(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.6F);
    }

    /** App saved. */
    public static void confirm() {
        play(SoundEvents.AMETHYST_BLOCK_PLACE, 1.3F, 0.6F);
    }

    /** App removed. */
    public static void delete() {
        play(SoundEvents.AMETHYST_BLOCK_BREAK, 0.9F, 0.5F);
    }

    /** Small interactions: view-mode switch, chip add/remove. */
    public static void tick(float pitch) {
        play(SoundEvents.STONE_BUTTON_CLICK_ON, pitch, 0.25F);
    }
}
