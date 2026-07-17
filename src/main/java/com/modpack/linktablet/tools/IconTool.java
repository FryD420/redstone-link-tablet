package com.modpack.linktablet.tools;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Dev-only listing-icon generator (excluded from the jar): the testers'
 * blueprint-blue disc art (docs/images/icon-bg.png) with a tilted 3D
 * tablet showing the list-mode screen; falls back to a drawn disc when
 * the art is missing. Rerun with
 * {@code ./gradlew iconTool --args="docs/icon.png"} (relative paths —
 * Gradle splits args on spaces). Tablet colors mirror the mod's real
 * palette (case tint, DARK theme screen, GUI switches).
 */
public final class IconTool {

    private static final int SIZE = 1000;
    private static final String DEFAULT_BG = "docs/images/icon-bg.png";

    // Blueprint disc
    private static final Color DISC_CENTER = new Color(0x5A9BD8);
    private static final Color DISC_EDGE = new Color(0x3E7CBF);
    private static final Color GRID = new Color(0x7F, 0xB8, 0xE8, 140);
    private static final Color GRID_MAJOR = new Color(0x8E, 0xC4, 0xF0, 210);
    private static final float GRID_W = 30f;
    private static final int GRID_STEP = 215;

    // Tablet palette (matches the mod — DARK ScreenTheme + GUI rows)
    private static final Color CASE = new Color(0x383C45);
    private static final Color CASE_BORDER = new Color(0x1A1D23);
    private static final Color CASE_SIDE = new Color(0x101318);
    private static final Color SCREEN_BG = new Color(0x223044);
    private static final Color ROW_BG = new Color(0x2C303A);
    private static final Color BUTTON = new Color(0x565D6B);
    private static final Color NAME_ON = new Color(0xE2, 0xE5, 0xEB, 190);
    private static final Color NAME_OFF = new Color(0x9A, 0xA0, 0xAC, 160);
    private static final Color SWITCH_ON = new Color(0x2F855A);
    private static final Color SWITCH_OFF = new Color(0x444955);
    private static final Color KNOB_ON = new Color(0x4ADE80);
    private static final Color KNOB_OFF = new Color(0x9AA0AC);
    private static final Color ACCENT = new Color(0x4ADE80);
    private static final Color[] CHIPS = {
            new Color(0xF9801D), new Color(0x3AB3DA), new Color(0xB07CFF)};

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        File background = new File(args.length > 1 ? args[1] : DEFAULT_BG);
        if (background.isFile()) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(ImageIO.read(background), 0, 0, SIZE, SIZE, null);
            System.out.println("background: " + background.getPath());
        } else {
            drawDisc(g);
            System.out.println("background: drawn fallback (" + background.getPath() + " not found)");
        }
        drawTablet(g);

        g.dispose();
        File out = new File(args[0]);
        ImageIO.write(image, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    private static void drawDisc(Graphics2D g) {
        Ellipse2D disc = new Ellipse2D.Double(30, 30, SIZE - 60, SIZE - 60);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(SIZE / 2.0, SIZE / 2.0),
                (SIZE - 60) / 2f, new float[]{0f, 1f}, new Color[]{DISC_CENTER, DISC_EDGE}));
        g.fill(disc);

        // Off-center grid, brighter pair crossing in the upper-left —
        // mirrors the reference art
        var oldClip = g.getClip();
        g.setClip(disc);
        for (int i = 0; i < 5; i++) {
            float pos = 33 + i * GRID_STEP;
            g.setColor(i == 1 ? GRID_MAJOR : GRID);
            g.fill(new RoundRectangle2D.Float(pos, 0, GRID_W, SIZE, 0, 0));
            g.fill(new RoundRectangle2D.Float(0, pos, SIZE, GRID_W, 0, 0));
        }
        g.setClip(oldClip);
    }

    private static void drawTablet(Graphics2D g) {
        // Tilted-card pose: rotate + shear around the disc center
        AffineTransform pose = new AffineTransform();
        pose.translate(SIZE / 2.0, SIZE / 2.0 + 8);
        pose.rotate(Math.toRadians(-8));
        pose.shear(-0.12, 0);

        AffineTransform old = g.getTransform();

        // Soft drop shadow on the disc (two translucent layers)
        g.transform(pose);
        g.setColor(new Color(0, 0, 0, 45));
        g.fill(caseShape(34, 44, 30));
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(caseShape(24, 32, 12));

        // Extruded side = thickness, then the face on top
        g.setColor(CASE_SIDE);
        g.fill(caseShape(15, 19, 0));
        g.setColor(CASE);
        g.fill(caseShape(0, 0, 0));
        g.setColor(CASE_BORDER);
        g.setStroke(new BasicStroke(13f));
        g.draw(caseShape(0, 0, -7));

        // Screen (lit DARK theme) and home button on the bottom bezel
        g.setColor(SCREEN_BG);
        g.fill(new RoundRectangle2D.Double(-196, -266, 392, 500, 30, 30));
        g.setColor(BUTTON);
        g.fill(new RoundRectangle2D.Double(-22, 254, 44, 28, 12, 12));

        // Three list rows, centered vertically in the screen — 1.5.x
        // plaque+chip look, one row per app kind (toggle / slider /
        // momentary button; mirrors TabletScreenRenderer's raisedBevel
        // and the GUI's control shapes).
        double rowW = 348, rowH = 92, gap = 22;
        double x = -rowW / 2;
        double y = -266 + (500 - (3 * rowH + 2 * gap)) / 2;
        for (int i = 0; i < 3; i++, y += rowH + gap) {
            // Toggle ON, slider transmitting, momentary idle
            boolean on = i != 2;

            plaque(g, x, y, rowW, rowH, ROW_BG, 6);
            if (on) {
                g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 110));
                g.setStroke(new BasicStroke(4f));
                g.draw(new RoundRectangle2D.Double(x - 2, y - 2, rowW + 4, rowH + 4, 20, 20));
            }

            // Chip: recessed well with the app color inset behind the icon
            chip(g, x + 10, y + 10, 72, CHIPS[i]);

            g.setColor(on ? NAME_ON : NAME_OFF);
            g.fill(new RoundRectangle2D.Double(x + 100, y + 34, 122, 24, 12, 12));

            switch (i) {
                case 0 -> { // toggle switch, ON
                    double sw = 96, sh = 38;
                    double sx = x + rowW - sw - 12, sy = y + (rowH - sh) / 2;
                    g.setColor(SWITCH_ON);
                    g.fill(new RoundRectangle2D.Double(sx, sy, sw, sh, sh, sh));
                    g.setColor(KNOB_ON);
                    g.fill(new Ellipse2D.Double(sx + sw - 34, sy + 4, 30, 30));
                }
                case 1 -> { // recessed slider groove + fill + knob
                    double gw = 96, gh = 26;
                    double gx = x + rowW - gw - 12, gy = y + (rowH - gh) / 2;
                    g.setColor(shade(SCREEN_BG, 0.75f));
                    g.fill(new RoundRectangle2D.Double(gx, gy, gw, gh, gh, gh));
                    g.setColor(shade(SCREEN_BG, 0.45f));
                    g.fill(new RoundRectangle2D.Double(gx, gy, gw, 6, 6, 6));
                    g.setColor(mixWhite(SCREEN_BG, 0.15f));
                    g.fill(new RoundRectangle2D.Double(gx, gy + gh - 5, gw, 5, 5, 5));
                    g.setColor(CHIPS[i]);
                    g.fill(new RoundRectangle2D.Double(gx + 3, gy + 3, gw * 0.62, gh - 6, gh - 6, gh - 6));
                    g.setColor(NAME_ON);
                    g.fill(new Ellipse2D.Double(gx + gw * 0.62 - 8, gy - 4, gh + 8, gh + 8));
                }
                case 2 -> { // momentary push button, idle (center dot)
                    double sw = 76, sh = 44;
                    double sx = x + rowW - sw - 22, sy = y + (rowH - sh) / 2;
                    g.setColor(SWITCH_OFF);
                    g.fill(new RoundRectangle2D.Double(sx, sy, sw, sh, 12, 12));
                    g.setColor(shade(SWITCH_OFF, 0.55f));
                    g.setStroke(new BasicStroke(3f));
                    g.draw(new RoundRectangle2D.Double(sx, sy, sw, sh, 12, 12));
                    g.setColor(KNOB_OFF);
                    g.fill(new Ellipse2D.Double(sx + sw / 2 - 7, sy + sh / 2 - 7, 14, 14));
                }
            }
        }

        g.setTransform(old);
    }

    /**
     * Raised plaque: base fill, thin dark outline, highlight along
     * top+left, shadow along bottom+right — the renderer's raisedBevel
     * (outline shade 0.35, hi 15% toward white, lo shade 0.55).
     */
    private static void plaque(Graphics2D g, double x, double y, double w, double h,
                               Color base, double bw) {
        g.setColor(base);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
        g.setColor(shade(base, 0.35f));
        g.setStroke(new BasicStroke((float) (bw * 0.5)));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
        g.setColor(mixWhite(base, 0.15f));
        g.fill(new RoundRectangle2D.Double(x + bw * 0.5, y + bw * 0.5, w - bw, bw, 8, 8));
        g.fill(new RoundRectangle2D.Double(x + bw * 0.5, y + bw * 0.5, bw, h - bw, 8, 8));
        g.setColor(shade(base, 0.55f));
        g.fill(new RoundRectangle2D.Double(x + bw * 0.5, y + h - bw * 1.5, w - bw, bw, 8, 8));
        g.fill(new RoundRectangle2D.Double(x + w - bw * 1.5, y + bw * 0.5, bw, h - bw, 8, 8));
    }

    /** Recessed chip well with the app color inset (1.5.2 tile look). */
    private static void chip(Graphics2D g, double x, double y, double size, Color color) {
        g.setColor(shade(ROW_BG, 0.55f));
        g.fill(new RoundRectangle2D.Double(x, y, size, size, 14, 14));
        g.setColor(color);
        g.fill(new RoundRectangle2D.Double(x + 5, y + 5, size - 10, size - 10, 10, 10));
        g.setColor(shade(color, 0.55f));
        g.fill(new RoundRectangle2D.Double(x + 5, y + 5, size - 10, 5, 5, 5));
        g.setColor(mixWhite(color, 0.15f));
        g.fill(new RoundRectangle2D.Double(x + 5, y + size - 10, size - 10, 5, 5, 5));
    }

    private static Color shade(Color c, float f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    private static Color mixWhite(Color c, float f) {
        return new Color((int) (c.getRed() + (255 - c.getRed()) * f),
                (int) (c.getGreen() + (255 - c.getGreen()) * f),
                (int) (c.getBlue() + (255 - c.getBlue()) * f));
    }

    /** The case outline, optionally offset (shadow/side) or inset (border). */
    private static RoundRectangle2D caseShape(double dx, double dy, double inset) {
        return new RoundRectangle2D.Double(-240 + dx + inset, -310 + dy + inset,
                480 - 2 * inset, 620 - 2 * inset, 70 - inset, 70 - inset);
    }
}
