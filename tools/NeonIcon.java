import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws RetroForge's "RF" launcher icon foreground in the key art's neon-sign style: outlined letters (orange R,
 * cyan F) with a glow, inside a rounded two-tone frame. Usage: java NeonIcon.java <font.ttf> <out.png> <size> [plain]
 */
public class NeonIcon {
    static final Color ORANGE = new Color(0xFF, 0x8A, 0x2A);
    static final Color CYAN = new Color(0x3E, 0xD6, 0xFF);

    public static void main(String[] args) throws Exception {
        Font base = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]));
        int size = Integer.parseInt(args[2]);
        boolean plain = args.length > 3; // monochrome mask: no glow, one colour
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double u = size / 108.0; // adaptive icon units; keep inside the 66 dp safe zone (21..87)
        Font font = base.deriveFont(Font.BOLD, (float) (30 * u));
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector rv = font.createGlyphVector(frc, "R");
        GlyphVector fv = font.createGlyphVector(frc, "F");
        Rectangle2D rb = rv.getVisualBounds();
        Rectangle2D fb = fv.getVisualBounds();
        double gap = 4 * u;
        double total = rb.getWidth() + gap + fb.getWidth();
        double x0 = (size - total) / 2;
        double baseline = size / 2.0 + rb.getHeight() / 2;
        Shape r = AffineTransform.getTranslateInstance(x0 - rb.getX(), baseline).createTransformedShape(rv.getOutline());
        Shape f = AffineTransform.getTranslateInstance(x0 + rb.getWidth() + gap - fb.getX(), baseline).createTransformedShape(fv.getOutline());
        // Orbitron builds some letters from overlapping pieces (the R's leg crosses its bowl); join them so only the
        // letter's outer edge is drawn as a tube.
        r = new Area(r);
        f = new Area(f);

        // The sign's frame: a rounded rectangle, orange on the left half and cyan on the right.
        double fw = total + 16 * u, fh = rb.getHeight() + 14 * u;
        RoundRectangle2D frame = new RoundRectangle2D.Double((size - fw) / 2, (size - fh) / 2, fw, fh, 9 * u, 9 * u);

        float tube = (float) (2.2 * u);
        if (plain) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(tube, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(frame);
            g.draw(r);
            g.draw(f);
        } else {
            Shape left = new Rectangle2D.Double(0, 0, size / 2.0, size);
            Shape right = new Rectangle2D.Double(size / 2.0, 0, size / 2.0, size);
            neon(g, frame, ORANGE, tube * 0.8f, u, left);
            neon(g, frame, CYAN, tube * 0.8f, u, right);
            neon(g, r, ORANGE, tube, u, null);
            neon(g, f, CYAN, tube, u, null);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(args[1]));
    }

    /** A neon tube: wide faint glow, narrower brighter glow, the coloured tube, and a pale hot core. */
    static void neon(Graphics2D g, Shape shape, Color c, float w, double u, Shape clip) {
        Shape old = g.getClip();
        if (clip != null) g.setClip(clip);
        // Many thin, faint layers from wide to narrow: a smooth glow without visible steps.
        for (int i = 0; i < 16; i++) {
            float width = w * (6.0f - i * 0.3f);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 14));
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        Color core = new Color(Math.min(255, c.getRed() + 90), Math.min(255, c.getGreen() + 90), Math.min(255, c.getBlue() + 90));
        g.setColor(core);
        g.setStroke(new BasicStroke(w * 0.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setClip(old);
    }
}
