import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws a RetroForge player app's launcher icon foreground: the same neon sign as RetroForge's own icon
 * (tools/NeonIcon.java), with a small "RF" on top and the console underneath, its first part orange and the rest
 * cyan ("PS" + "3", "SWI" + "TCH"). Usage: java PlayerIcon.java <font.ttf> <out.png> <size> <orange part> <cyan part> [plain]
 */
public class PlayerIcon {
    static final Color ORANGE = NeonIcon.ORANGE;
    static final Color CYAN = NeonIcon.CYAN;

    public static void main(String[] args) throws Exception {
        Font base = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]));
        int size = Integer.parseInt(args[2]);
        String warm = args[3], cool = args[4];
        boolean plain = args.length > 5; // monochrome mask: no glow, one colour
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        FontRenderContext frc = g.getFontRenderContext();

        double u = size / 108.0; // adaptive icon units; keep inside the 66 dp safe zone (21..87)
        // The console's name as large as fits 50 units across; the "RF" above it stays small.
        double maxWidth = 50 * u;
        float labelSize = (float) (24 * u);
        Font label = base.deriveFont(Font.BOLD, labelSize);
        double width = text(label, frc, warm + cool).getWidth();
        if (width > maxWidth) label = base.deriveFont(Font.BOLD, (float) (labelSize * maxWidth / width));
        Font small = base.deriveFont(Font.BOLD, (float) (11 * u));

        Rectangle2D lb = text(label, frc, warm + cool);
        Rectangle2D sb = text(small, frc, "RF");
        double gap = 5 * u;
        double blockHeight = sb.getHeight() + gap + lb.getHeight();
        double top = (size - blockHeight) / 2;

        Shape[] rf = line(small, frc, "R", "F", size, top + sb.getHeight(), 2 * u);
        Shape[] name = line(label, frc, warm, cool, size, top + sb.getHeight() + gap + lb.getHeight(), 1.5 * u);

        double fw = Math.max(lb.getWidth(), sb.getWidth()) + 16 * u, fh = blockHeight + 14 * u;
        RoundRectangle2D frame = new RoundRectangle2D.Double((size - fw) / 2, (size - fh) / 2, fw, fh, 9 * u, 9 * u);

        float tube = (float) (2.0 * u);
        if (plain) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(tube, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(frame);
            for (Shape s : rf) g.draw(s);
            for (Shape s : name) g.draw(s);
        } else {
            Shape left = new Rectangle2D.Double(0, 0, size / 2.0, size);
            Shape right = new Rectangle2D.Double(size / 2.0, 0, size / 2.0, size);
            NeonIcon.neon(g, frame, ORANGE, tube * 0.8f, u, left);
            NeonIcon.neon(g, frame, CYAN, tube * 0.8f, u, right);
            NeonIcon.neon(g, rf[0], ORANGE, tube * 0.7f, u, null);
            NeonIcon.neon(g, rf[1], CYAN, tube * 0.7f, u, null);
            NeonIcon.neon(g, name[0], ORANGE, tube, u, null);
            NeonIcon.neon(g, name[1], CYAN, tube, u, null);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(args[1]));
    }

    static Rectangle2D text(Font font, FontRenderContext frc, String s) {
        return font.createGlyphVector(frc, s).getVisualBounds();
    }

    /** [warm] then [cool] on one line centred across [size], sitting on [baseline]; each as one joined outline. */
    static Shape[] line(Font font, FontRenderContext frc, String warm, String cool, int size, double baseline, double gap) {
        GlyphVector wv = font.createGlyphVector(frc, warm);
        GlyphVector cv = font.createGlyphVector(frc, cool);
        Rectangle2D wb = wv.getVisualBounds(), cb = cv.getVisualBounds();
        double total = wb.getWidth() + gap + cb.getWidth();
        double x0 = (size - total) / 2;
        // Orbitron builds some letters from overlapping pieces; joined, only each letter's outer edge is drawn.
        Shape w = new Area(AffineTransform.getTranslateInstance(x0 - wb.getX(), baseline).createTransformedShape(wv.getOutline()));
        Shape c = new Area(AffineTransform.getTranslateInstance(x0 + wb.getWidth() + gap - cb.getX(), baseline).createTransformedShape(cv.getOutline()));
        return new Shape[] {w, c};
    }
}
