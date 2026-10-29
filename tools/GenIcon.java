import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;

/** Draws the app icon (an app bundle box with the Android robot head peeking out) and writes PNG sizes + .ico. */
public class GenIcon {
    static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 1024.0;
        g.scale(s, s);

        // macOS-style squircle tile with margin
        RoundRectangle2D tile = new RoundRectangle2D.Double(100, 100, 824, 824, 380, 380);
        // soft shadow
        for (int i = 18; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 5));
            g.fill(new RoundRectangle2D.Double(100 - i + 0, 100 - i + 14, 824 + 2 * i, 824 + 2 * i, 380 + 2 * i, 380 + 2 * i));
        }
        g.setPaint(new GradientPaint(0, 100, new Color(0x4BE38F), 0, 924, new Color(0x0E9F6E)));
        g.fill(tile);
        // glossy highlight
        g.setClip(tile);
        g.setPaint(new GradientPaint(0, 100, new Color(255, 255, 255, 70), 0, 520, new Color(255, 255, 255, 0)));
        g.fill(new Rectangle2D.Double(100, 100, 824, 420));
        g.setClip(null);

        // isometric box (the .aab bundle)
        double cx = 512, top = 520, w = 230, h = 130, depth = 250;
        Path2D topFace = new Path2D.Double();
        topFace.moveTo(cx, top - h); topFace.lineTo(cx + w, top); topFace.lineTo(cx, top + h); topFace.lineTo(cx - w, top); topFace.closePath();
        Path2D left = new Path2D.Double();
        left.moveTo(cx - w, top); left.lineTo(cx, top + h); left.lineTo(cx, top + h + depth); left.lineTo(cx - w, top + depth); left.closePath();
        Path2D right = new Path2D.Double();
        right.moveTo(cx + w, top); right.lineTo(cx, top + h); right.lineTo(cx, top + h + depth); right.lineTo(cx + w, top + depth); right.closePath();

        g.setColor(new Color(0, 0, 0, 40));
        g.fill(AffineTransform.getTranslateInstance(0, 22).createTransformedShape(left));
        g.setColor(new Color(0xE9F7F0)); g.fill(left);
        g.setColor(new Color(0xB5E6CE)); g.fill(right);
        // dark opening on top of the box
        g.setColor(new Color(0x0A5C40)); g.fill(topFace);
        Path2D inner = new Path2D.Double();
        double k = 0.78;
        inner.moveTo(cx, top - h * k); inner.lineTo(cx + w * k, top); inner.lineTo(cx, top + h * k); inner.lineTo(cx - w * k, top); inner.closePath();
        g.setColor(new Color(0x07402C)); g.fill(inner);

        // Android robot head peeking out of the box (the extracted APK)
        double hr = 135, hcy = 430;
        Path2D headShape = new Path2D.Double();
        headShape.moveTo(cx - hr, 700);
        headShape.lineTo(cx - hr, hcy);
        headShape.append(new Arc2D.Double(cx - hr, hcy - hr, 2 * hr, 2 * hr, 180, -180, Arc2D.OPEN), true);
        headShape.lineTo(cx + hr, 700);
        headShape.closePath();
        Path2D above = new Path2D.Double();   // everything above the box's front rim
        above.moveTo(0, 0); above.lineTo(1024, 0); above.lineTo(1024, top);
        above.lineTo(cx + w, top); above.lineTo(cx, top + h); above.lineTo(cx - w, top); above.lineTo(0, top); above.closePath();
        g.setClip(above);
        g.setColor(new Color(0, 0, 0, 50)); g.fill(AffineTransform.getTranslateInstance(0, 14).createTransformedShape(headShape));
        g.setColor(Color.WHITE); g.fill(headShape);
        // eyes
        g.setColor(new Color(0x12A874));
        g.fill(new Ellipse2D.Double(cx - 58 - 17, hcy - 52 - 17, 34, 34));
        g.fill(new Ellipse2D.Double(cx + 58 - 17, hcy - 52 - 17, 34, 34));
        // antennae
        g.setStroke(new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.draw(new Line2D.Double(cx - 75, hcy - 108, cx - 112, hcy - 170));
        g.draw(new Line2D.Double(cx + 75, hcy - 108, cx + 112, hcy - 170));
        g.setClip(null);
        // front lip of the box drawn over the arrow base
        Path2D lip = new Path2D.Double();
        lip.moveTo(cx - w, top); lip.lineTo(cx, top + h); lip.lineTo(cx + w, top);
        lip.lineTo(cx + w * 0.9, top - 4); lip.lineTo(cx, top + h * 0.9); lip.lineTo(cx - w * 0.9, top - 4); lip.closePath();
        g.setColor(new Color(0x07402C)); g.fill(lip);

        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bo);
        return bo.toByteArray();
    }

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        BufferedImage big = draw(1024);
        ImageIO.write(big, "png", new File(out, "icon-1024.png"));
        ImageIO.write(draw(512), "png", new File(out, "icon.png"));
        File res = new File(out, "resources");
        res.mkdirs();
        for (int z : new int[]{16, 32, 48, 64, 128, 256, 512}) ImageIO.write(draw(z), "png", new File(res, "icon-" + z + ".png"));

        // .iconset for iconutil
        File set = new File(out, "AppIcon.iconset");
        set.mkdirs();
        int[] base = {16, 32, 128, 256, 512};
        for (int b : base) {
            ImageIO.write(draw(b), "png", new File(set, "icon_" + b + "x" + b + ".png"));
            ImageIO.write(draw(b * 2), "png", new File(set, "icon_" + b + "x" + b + "@2x.png"));
        }

        // Windows .ico with PNG-compressed entries
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) pngs[i] = png(draw(sizes[i]));
        ByteBuffer bb = ByteBuffer.allocate(6 + 16 * sizes.length + java.util.Arrays.stream(pngs).mapToInt(x -> x.length).sum()).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int sz = sizes[i] >= 256 ? 0 : sizes[i];
            bb.put((byte) sz).put((byte) sz).put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32).putInt(pngs[i].length).putInt(offset);
            offset += pngs[i].length;
        }
        for (byte[] p : pngs) bb.put(p);
        try (FileOutputStream fo = new FileOutputStream(new File(out, "icon.ico"))) { fo.write(bb.array()); }
    }
}
