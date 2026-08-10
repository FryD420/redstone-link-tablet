package com.modpack.linktablet.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * THE emote tokenizer (1.11.0 emotes) — the one place chat text becomes
 * text/emote segments and wrapped lines. GUI, overlay, and kiosk face
 * all lay out through here (the TabletScreenMath one-source rule):
 * never fork matching or wrapping into a renderer. Tokenization is
 * memoized per ChatMessage (weak keys ride the ring buffer's lifetime)
 * and invalidated by TwitchEmotes.generation() when a set lands, or by
 * the toggle flipping.
 */
public final class EmoteText {

    public sealed interface Segment permits TextSeg, EmoteSeg {}
    public record TextSeg(String text) implements Segment {}
    public record EmoteSeg(TwitchEmotes.Emote emote) implements Segment {}

    /** One placed run on a wrapped line; text is null for emote runs. */
    public record Run(int x, int width, Segment seg, String text) {}
    public record Line(List<Run> runs) {}

    private record Memo(int generation, boolean emotesOn, String channel, List<Segment> segments) {}
    private static final Map<TwitchChatService.ChatMessage, Memo> MEMO = new WeakHashMap<>();

    // ------------------------------------------------------------------
    // Tokenization (memoized)
    // ------------------------------------------------------------------

    public static List<Segment> segments(TwitchChatService.ChatMessage m, String channel) {
        boolean on = ClientPrefs.twitchEmotes();
        int gen = TwitchEmotes.generation(channel);
        Memo memo = MEMO.get(m);
        if (memo != null && memo.generation() == gen && memo.emotesOn() == on && memo.channel().equals(channel)) {
            return memo.segments();
        }
        List<Segment> segs = on ? build(m, channel) : List.of(new TextSeg(m.text()));
        MEMO.put(m, new Memo(gen, on, channel, segs));
        return segs;
    }

    private static List<Segment> build(TwitchChatService.ChatMessage m, String channel) {
        List<Segment> out = new ArrayList<>();
        String text = m.text();
        try {
            // Native spans are INCLUSIVE CODE-POINT ranges (see EmoteSpan)
            int cpCursor = 0;
            int charCursor = 0;
            for (TwitchChatService.EmoteSpan span : m.emotes()) {
                if (span.from() < cpCursor) continue; // overlapping/bad tag
                int start = text.offsetByCodePoints(charCursor, span.from() - cpCursor);
                int end = text.offsetByCodePoints(start, span.to() - span.from() + 1);
                if (start > charCursor) words(text.substring(charCursor, start), channel, out);
                out.add(new EmoteSeg(TwitchEmotes.nativeEmote(span.id(), text.substring(start, end))));
                charCursor = end;
                cpCursor = span.to() + 1;
            }
            if (charCursor < text.length()) words(text.substring(charCursor), channel, out);
        } catch (IndexOutOfBoundsException e) {
            return List.of(new TextSeg(text)); // malformed ranges: text wins
        }
        return out.isEmpty() ? List.of(new TextSeg("")) : out;
    }

    /** Word-boundary third-party matching; unmatched text coalesces
     * into single TextSegs (fewer segments, fewer draw calls). */
    private static void words(String text, String channel, List<Segment> out) {
        StringBuilder pending = new StringBuilder();
        for (String token : text.split("(?<= )")) { // split AFTER spaces, keeps them
            // Literal-trailing-space-only trim: strip() also eats Unicode
            // whitespace (em-space, tabs) that the literal-space split above
            // never isolates, which could drop leading exotic whitespace.
            int end = token.length();
            while (end > 0 && token.charAt(end - 1) == ' ') end--;
            String bare = token.substring(0, end);
            TwitchEmotes.Emote e = bare.isEmpty() ? null : TwitchEmotes.resolve(bare, channel);
            if (e == null) {
                pending.append(token);
                continue;
            }
            if (!pending.isEmpty()) {
                out.add(new TextSeg(pending.toString()));
                pending.setLength(0);
            }
            out.add(new EmoteSeg(e));
            pending.append(token, end, token.length()); // trailing space
        }
        if (!pending.isEmpty()) out.add(new TextSeg(pending.toString()));
    }

    // ------------------------------------------------------------------
    // Wrapping (GUI + overlay; the face is single-line and self-clips)
    // ------------------------------------------------------------------

    public static List<Line> wrap(Font font, List<Segment> segments, int maxWidth, int emoteH) {
        maxWidth = Math.max(maxWidth, 8); // degenerate-caller guard: never narrower than one glyph
        List<Line> lines = new ArrayList<>();
        List<Run> current = new ArrayList<>();
        int x = 0;
        for (Segment seg : segments) {
            if (seg instanceof EmoteSeg es) {
                int w = Math.min(emoteWidth(es.emote(), font, emoteH), maxWidth);
                if (x > 0 && x + w > maxWidth) {
                    lines.add(new Line(current));
                    current = new ArrayList<>();
                    x = 0;
                }
                current.add(new Run(x, w, seg, null));
                x += w;
            } else if (seg instanceof TextSeg ts) {
                for (String word : ts.text().split("(?<= )")) {
                    // Hard-break single words wider than the row
                    while (font.width(word) > maxWidth) {
                        String head = font.plainSubstrByWidth(word, maxWidth - Math.min(x, maxWidth - 1));
                        if (head.isEmpty()) {
                            lines.add(new Line(current));
                            current = new ArrayList<>();
                            x = 0;
                            head = font.plainSubstrByWidth(word, maxWidth);
                            if (head.isEmpty()) break; // unrenderable word: discard, guarantee termination
                        }
                        x = appendText(current, lines, font, x, maxWidth, head);
                        word = word.substring(head.length());
                    }
                    if (word.isEmpty()) continue;
                    int w = font.width(word);
                    if (x > 0 && x + w > maxWidth) {
                        lines.add(new Line(current));
                        current = new ArrayList<>();
                        x = 0;
                        if (word.isBlank()) continue; // no leading wrap-space
                    }
                    x = appendText(current, lines, font, x, maxWidth, word);
                }
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) lines.add(new Line(current));
        return lines;
    }

    /** Appends text to the current line, merging into a trailing text
     * run when possible. Returns the new x cursor. */
    private static int appendText(List<Run> current, List<Line> lines, Font font,
                                  int x, int maxWidth, String text) {
        int w = font.width(text);
        if (!current.isEmpty()) {
            Run last = current.get(current.size() - 1);
            if (last.seg() instanceof TextSeg && last.x() + last.width() == x) {
                current.set(current.size() - 1,
                        new Run(last.x(), last.width() + w, last.seg(), last.text() + text));
                return x + w;
            }
        }
        current.add(new Run(x, w, new TextSeg(text), text));
        return x + w;
    }

    /** Loaded emotes are aspect-scaled to emoteH; unloaded/FAILED render
     * as their text name (chat never blocks on the network). */
    public static int emoteWidth(TwitchEmotes.Emote e, Font font, int emoteH) {
        EmoteTextures.Sprite s = EmoteTextures.get(e);
        return s == null ? font.width(e.name())
                : Mth.clamp(emoteH * s.frameW() / Math.max(1, s.frameH()), 1, emoteH * 4);
    }

    // ------------------------------------------------------------------
    // GUI draw (screen + overlay; NOT the world face)
    // ------------------------------------------------------------------

    public static void drawGui(GuiGraphics g, Font font, Line line, int x, int y,
                               int emoteH, int textColor, boolean shadow) {
        for (Run run : line.runs()) {
            if (run.seg() instanceof EmoteSeg es) {
                EmoteTextures.Sprite s = EmoteTextures.get(es.emote());
                if (s == null) {
                    g.drawString(font, es.emote().name(), x + run.x(), y, textColor, shadow);
                    continue;
                }
                int frame = EmoteTextures.frameAt(s, Util.getMillis());
                g.blit(s.texture(), x + run.x(), y, run.width(), emoteH,
                        0, frame * s.frameH(), s.frameW(), s.frameH(),
                        s.frameW(), s.frameH() * s.frameCount());
            } else {
                g.drawString(font, run.text(), x + run.x(), y, textColor, shadow);
            }
        }
    }

    private EmoteText() {
    }
}
