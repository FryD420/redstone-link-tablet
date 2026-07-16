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

    // Tablet palette (matches the mod)
    private static final Color CASE = new Color(0x383C45);
    private static final Color CASE_BORDER = new Color(0x1A1D23);
    private static final Color CASE_SIDE = new Color(0x101318);
    private static final Color SCREEN_BG = new Color(0x223044);
    private static final Color ROW_TRACK = new Color(0x2A2E37);
    private static final Color BUTTON = new Color(0x565D6B);
    private static final Color NAME_ON = new Color(0xE2, 0xE5, 0xEB, 190);
    private static final Color NAME_OFF = new Color(0x9A, 0xA0, 0xAC, 160);
    private static final Color SWITCH_ON = new Color(0x2F855A);
    private static final Color SWITCH_OFF = new Color(0x444955);
    private static final Color KNOB_ON = new Color(0x4ADE80);
    private static final Color KNOB_OFF = new Color(0x9AA0AC);
    private static final Color[] CHIPS = {
            new Color(0xF9801D), new Color(0x3AB3DA), new Color(0xB07CFF)};
    private static final boolean[] ROW_ON = {true, false, true};

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

        // Three list rows, centered vertically in the screen
        double rowW = 348, rowH = 92, gap = 22;
        double x = -rowW / 2;
        double y = -266 + (500 - (3 * rowH + 2 * gap)) / 2;
        for (int i = 0; i < 3; i++, y += rowH + gap) {
            boolean on = ROW_ON[i];
            g.setColor(ROW_TRACK);
            g.fill(new RoundRectangle2D.Double(x, y, rowW, rowH, 18, 18));

            g.setColor(CHIPS[i]);
            g.fill(new RoundRectangle2D.Double(x + 10, y + 10, 72, 72, 14, 14));

            g.setColor(on ? NAME_ON : NAME_OFF);
            g.fill(new RoundRectangle2D.Double(x + 100, y + 34, 122, 24, 12, 12));

            double sw = 96, sh = 38;
            double sx = x + rowW - sw - 12, sy = y + (rowH - sh) / 2;
            g.setColor(on ? SWITCH_ON : SWITCH_OFF);
            g.fill(new RoundRectangle2D.Double(sx, sy, sw, sh, sh, sh));
            g.setColor(on ? KNOB_ON : KNOB_OFF);
            double knobX = on ? sx + sw - 34 : sx + 4;
            g.fill(new Ellipse2D.Double(knobX, sy + 4, 30, 30));
        }

        g.setTransform(old);
    }

    /** The case outline, optionally offset (shadow/side) or inset (border). */
    private static RoundRectangle2D caseShape(double dx, double dy, double inset) {
        return new RoundRectangle2D.Double(-240 + dx + inset, -310 + dy + inset,
                480 - 2 * inset, 620 - 2 * inset, 70 - inset, 70 - inset);
    }
}
