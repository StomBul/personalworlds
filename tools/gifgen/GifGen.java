import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Генератор анимированных GIF для сайта в стиле 2007 года.
 * Запуск из корня проекта: java tools/gifgen/GifGen.java docs/assets/img
 * Всё рисуется кодом, без сглаживания — как настоящие гифки той эпохи.
 */
public class GifGen {
    static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "docs/assets/img");
        out.mkdirs();
        write(new File(out, "block.gif"), block(), 12);
        write(new File(out, "new.gif"), badge("NEW!", new Color[] {new Color(0xE00000), new Color(0xFFD800)},
                new Color[] {Color.WHITE, new Color(0xE00000)}), 35);
        write(new File(out, "hot.gif"), hot(), 12);
        write(new File(out, "construction.gif"), construction(), 10);
        write(new File(out, "divider.gif"), divider(), 8);
        write(new File(out, "torch.gif"), torch(), 14);
        write(new File(out, "star.gif"), star(), 10);
        write(new File(out, "arrow.gif"), arrow(), 12);
        write(new File(out, "diamond.gif"), diamond(), 9);
        write(new File(out, "bullet.gif"), bullet(), 15);
        write(new File(out, "pickaxe.gif"), pickaxe(), 9);
        System.out.println("GIF готовы: " + out.getAbsolutePath());
    }

    // ------------------------------------------------------------ кадры

    static BufferedImage frame(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        return g;
    }

    static void px(Graphics2D g, int x, int y, int s, Color c) {
        g.setColor(c);
        g.fillRect(x * s, y * s, s, s);
    }

    /** Блок травы, который подпрыгивает, и блик бежит по верху. */
    static List<BufferedImage> block() {
        List<BufferedImage> fr = new ArrayList<>();
        int n = 12;
        for (int i = 0; i < n; i++) {
            BufferedImage img = frame(72, 76);
            Graphics2D g = g(img);
            int dy = (int) Math.round(-Math.abs(Math.sin(Math.PI * i / n)) * 8);
            int ox = 4, oy = 10 + dy;
            // тень
            int sw = 40 + dy * 2;
            g.setColor(new Color(0, 0, 0, 255));
            g.setColor(new Color(0x1B4F8A));
            g.fillOval(36 - sw / 2, 68, sw, 6);
            Polygon top = poly(ox + 32, oy, ox + 64, oy + 16, ox + 32, oy + 32, ox, oy + 16);
            Polygon left = poly(ox, oy + 16, ox + 32, oy + 32, ox + 32, oy + 64, ox, oy + 48);
            Polygon right = poly(ox + 32, oy + 32, ox + 64, oy + 16, ox + 64, oy + 48, ox + 32, oy + 64);
            g.setColor(new Color(0x6CBF3F)); g.fill(top);
            g.setColor(new Color(0x8B5A2B)); g.fill(left);
            g.setColor(new Color(0x6B4320)); g.fill(right);
            // травяная кромка
            g.setColor(new Color(0x4F9A2A));
            g.fill(poly(ox, oy + 16, ox + 32, oy + 32, ox + 32, oy + 39, ox, oy + 23));
            g.setColor(new Color(0x3D7D1F));
            g.fill(poly(ox + 32, oy + 32, ox + 64, oy + 16, ox + 64, oy + 23, ox + 32, oy + 39));
            // «пиксели» земли
            g.setColor(new Color(0xA0703A));
            int[][] dots = {{8, 30}, {16, 40}, {10, 46}, {22, 50}, {26, 36}};
            for (int[] d : dots) g.fillRect(ox + d[0], oy + d[1], 4, 4);
            g.setColor(new Color(0x553417));
            int[][] dots2 = {{40, 40}, {50, 34}, {56, 44}, {44, 52}};
            for (int[] d : dots2) g.fillRect(ox + d[0], oy + d[1], 4, 4);
            // блик по верхней грани
            int t = (i * 80 / n) - 16;
            g.setClip(top);
            g.setColor(new Color(0xB8F08A));
            g.fill(poly(ox + t, oy, ox + t + 10, oy, ox + t - 6, oy + 32, ox + t - 16, oy + 32));
            g.setClip(null);
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    static Polygon poly(int... xy) {
        Polygon p = new Polygon();
        for (int i = 0; i < xy.length; i += 2) p.addPoint(xy[i], xy[i + 1]);
        return p;
    }

    /** Мигающая плашка с текстом (NEW!). */
    static List<BufferedImage> badge(String text, Color[] bg, Color[] fg) {
        List<BufferedImage> fr = new ArrayList<>();
        for (int i = 0; i < bg.length; i++) {
            BufferedImage img = frame(36, 15);
            Graphics2D g = g(img);
            g.setColor(bg[i]);
            g.fillRect(0, 0, 36, 15);
            g.setColor(fg[i]);
            g.drawRect(0, 0, 35, 14);
            g.setFont(new Font("Verdana", Font.BOLD, 10));
            centered(g, text, 36, 12);
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    static void centered(Graphics2D g, String text, int w, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (w - fm.stringWidth(text)) / 2, baseline);
    }

    /** HOT! с языками пламени. */
    static List<BufferedImage> hot() {
        List<BufferedImage> fr = new ArrayList<>();
        Color[] fire = {new Color(0xFF2200), new Color(0xFF7A00), new Color(0xFFC400), new Color(0xFF7A00)};
        for (int i = 0; i < 4; i++) {
            BufferedImage img = frame(36, 20);
            Graphics2D g = g(img);
            for (int x = 1; x < 35; x += 3) {
                int h = 3 + (int) ((Math.sin(x * 1.7 + i * 1.6) + 1) * 2.2);
                g.setColor(fire[(x / 3 + i) % 4]);
                g.fillRect(x, 6 - h + 3, 2, h);
            }
            g.setColor(new Color(0x990000));
            g.fillRect(0, 6, 36, 14);
            g.setColor(fire[i]);
            g.setFont(new Font("Verdana", Font.BOLD, 10));
            centered(g, "HOT!", 36, 17);
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** «Сайт строится» — бегущие жёлто-чёрные полосы и мигающий текст. */
    static List<BufferedImage> construction() {
        List<BufferedImage> fr = new ArrayList<>();
        int w = 330, h = 46, n = 8;
        for (int i = 0; i < n; i++) {
            BufferedImage img = frame(w, h);
            Graphics2D g = g(img);
            g.setColor(new Color(0xFFD400));
            g.fillRect(0, 0, w, h);
            g.setColor(Color.BLACK);
            for (int band : new int[] {0, h - 8}) {
                g.setClip(0, band, w, 8);
                for (int x = -16 + i * 2; x < w + 16; x += 16) g.fill(poly(x, band, x + 8, band, x + 16, band + 8, x + 8, band + 8));
                g.setClip(null);
            }
            g.setColor(i % 4 < 2 ? Color.BLACK : new Color(0xC00000));
            g.setFont(new Font("Verdana", Font.BOLD, 13));
            centered(g, "!!! САЙТ ПОСТОЯННО СТРОИТСЯ !!!", w, 28);
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Радужная бегущая полоска-разделитель. */
    static List<BufferedImage> divider() {
        List<BufferedImage> fr = new ArrayList<>();
        int w = 400, h = 4, n = 16;
        for (int i = 0; i < n; i++) {
            BufferedImage img = frame(w, h);
            Graphics2D g = g(img);
            for (int x = 0; x < w; x += 4) {
                float hue = ((x / (float) w) * 2 - i / (float) n) % 1f;
                if (hue < 0) hue += 1;
                hue = Math.round(hue * 24) / 24f; // 24 оттенка, без лишних цветов
                g.setColor(Color.getHSBColor(hue, 0.9f, 1f));
                g.fillRect(x, 0, 4, h);
            }
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Факел из Майнкрафта с мерцающим огнём. */
    static List<BufferedImage> torch() {
        List<BufferedImage> fr = new ArrayList<>();
        int s = 3;
        Color wood = new Color(0x6E4A25), woodD = new Color(0x4F3418);
        Color[] f = {new Color(0xFFF3A0), new Color(0xFFC21A), new Color(0xFF7A00), new Color(0xD03A00)};
        int[][][] flames = {
                {{1, 0, 3}, {2, 0, 2}, {0, 1, 2}, {1, 1, 1}, {2, 1, 0}, {3, 1, 2}, {0, 2, 1}, {1, 2, 0}, {2, 2, 0}, {3, 2, 1}},
                {{2, 0, 3}, {1, 0, 2}, {0, 1, 1}, {1, 1, 0}, {2, 1, 1}, {3, 1, 2}, {0, 2, 2}, {1, 2, 0}, {2, 2, 0}, {3, 2, 1}},
                {{1, 0, 2}, {0, 1, 2}, {1, 1, 0}, {2, 1, 1}, {3, 1, 3}, {0, 2, 1}, {1, 2, 0}, {2, 2, 1}, {3, 2, 2}},
                {{2, 0, 2}, {3, 0, 3}, {0, 1, 2}, {1, 1, 1}, {2, 1, 0}, {3, 1, 1}, {0, 2, 1}, {1, 2, 0}, {2, 2, 0}, {3, 2, 2}},
        };
        for (int[][] flame : flames) {
            BufferedImage img = frame(6 * s, 14 * s);
            Graphics2D g = g(img);
            for (int[] p : flame) px(g, p[0] + 1, p[1] + 1, s, f[p[2]]);
            px(g, 2, 4, s, f[1]);
            px(g, 3, 4, s, f[2]);
            for (int y = 5; y < 14; y++) {
                px(g, 2, y, s, wood);
                px(g, 3, y, s, woodD);
            }
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Мерцающая звёздочка. */
    static List<BufferedImage> star() {
        List<BufferedImage> fr = new ArrayList<>();
        int[] sizes = {2, 4, 6, 7, 6, 4};
        for (int r : sizes) {
            BufferedImage img = frame(15, 15);
            Graphics2D g = g(img);
            g.setColor(new Color(0xFFC800));
            g.fillRect(7 - r, 7, r * 2 + 1, 1);
            g.fillRect(7, 7 - r, 1, r * 2 + 1);
            if (r > 3) {
                g.fillRect(6, 6, 3, 3);
                g.setColor(new Color(0xFFF7C0));
                g.fillRect(7, 7, 1, 1);
                g.setColor(new Color(0xFFE066));
                g.fillRect(5, 5, 1, 1); g.fillRect(9, 5, 1, 1); g.fillRect(5, 9, 1, 1); g.fillRect(9, 9, 1, 1);
            }
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Бегающая стрелочка для меню. */
    static List<BufferedImage> arrow() {
        List<BufferedImage> fr = new ArrayList<>();
        int[] off = {0, 1, 2, 3, 2, 1};
        for (int o : off) {
            BufferedImage img = frame(12, 9);
            Graphics2D g = g(img);
            g.setColor(new Color(0xF07800));
            for (int i = 0; i < 5; i++) g.fillRect(o + i, i, 2, 9 - i * 2);
            g.setColor(new Color(0xFFC060));
            g.fillRect(o, 2, 1, 5);
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Вращающийся алмаз для заголовков. */
    static List<BufferedImage> diamond() {
        List<BufferedImage> fr = new ArrayList<>();
        int n = 10;
        for (int i = 0; i < n; i++) {
            BufferedImage img = frame(18, 18);
            Graphics2D g = g(img);
            double c = Math.cos(2 * Math.PI * i / n);
            int hw = Math.max(1, (int) Math.round(Math.abs(c) * 8));
            Color face = c >= 0 ? new Color(0x4FE8E0) : new Color(0x2AB0C8);
            g.setColor(face);
            g.fill(poly(9, 1, 9 + hw, 7, 9, 17, 9 - hw, 7));
            g.setColor(new Color(0xB8FFFA));
            g.fill(poly(9, 1, 9 + hw, 7, 9, 7));
            g.setColor(new Color(0x14788A));
            g.setStroke(new BasicStroke(1));
            g.draw(poly(9, 1, 9 + hw, 7, 9, 17, 9 - hw, 7));
            if (i == 0) {
                g.setColor(Color.WHITE);
                g.fillRect(6, 4, 2, 2);
            }
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Пульсирующий маркер списка. */
    static List<BufferedImage> bullet() {
        List<BufferedImage> fr = new ArrayList<>();
        Color[] cs = {new Color(0xF07800), new Color(0xFF9A20), new Color(0xFFC040), new Color(0xFF9A20)};
        for (Color c : cs) {
            BufferedImage img = frame(9, 9);
            Graphics2D g = g(img);
            g.setColor(c);
            g.fillRect(2, 1, 5, 7);
            g.fillRect(1, 2, 7, 5);
            g.setColor(new Color(0xFFF0C0));
            g.fillRect(3, 2, 2, 2);
            g.dispose();
            fr.add(img);
        }
        return fr;
    }

    /** Кирка, которая копает. */
    static List<BufferedImage> pickaxe() {
        List<BufferedImage> fr = new ArrayList<>();
        double[] angles = {-50, -35, -15, 10, 25, 10, -15, -35};
        for (double a : angles) {
            BufferedImage img = frame(48, 48);
            Graphics2D g = g(img);
            g.translate(20, 40);
            g.rotate(Math.toRadians(a));
            g.setColor(new Color(0x6E4A25));
            g.fillRect(-2, -26, 4, 28);
            g.setColor(new Color(0x4F3418));
            g.fillRect(1, -26, 1, 28);
            g.setColor(new Color(0x3FD7D0));
            g.fill(poly(-14, -24, -6, -30, 6, -30, 16, -22, 12, -20, 4, -25, -4, -25, -10, -20));
            g.setColor(new Color(0x1C8C94));
            g.draw(poly(-14, -24, -6, -30, 6, -30, 16, -22, 12, -20, 4, -25, -4, -25, -10, -20));
            g.dispose();
            if (a == 25) { // искры при ударе
                Graphics2D s = g(img);
                s.setColor(new Color(0xFFE066));
                s.fillRect(40, 38, 2, 2); s.fillRect(43, 33, 2, 2); s.fillRect(37, 43, 2, 2);
                s.dispose();
            }
            fr.add(img);
        }
        return fr;
    }

    // ------------------------------------------------------------ запись GIF

    static void write(File file, List<BufferedImage> frames, int delayCs) throws Exception {
        int shift = 0;
        Map<Integer, Integer> palette;
        boolean transparent;
        while (true) {
            palette = new LinkedHashMap<>();
            transparent = false;
            int mask = (0xFF << shift) & 0xFF;
            for (BufferedImage f : frames) {
                for (int y = 0; y < f.getHeight(); y++) {
                    for (int x = 0; x < f.getWidth(); x++) {
                        int argb = f.getRGB(x, y);
                        if ((argb >>> 24) < 128) { transparent = true; continue; }
                        int rgb = (((argb >> 16) & mask) << 16) | (((argb >> 8) & mask) << 8) | (argb & mask);
                        palette.putIfAbsent(rgb, 0);
                    }
                }
            }
            if (palette.size() <= (transparent ? 255 : 256)) break;
            shift++;
        }
        int size = palette.size() + (transparent ? 1 : 0);
        int bits = 8;
        int len = 256;
        byte[] r = new byte[len], gg = new byte[len], b = new byte[len];
        int idx = transparent ? 1 : 0;
        for (Map.Entry<Integer, Integer> e : palette.entrySet()) {
            int rgb = e.getKey();
            r[idx] = (byte) (rgb >> 16); gg[idx] = (byte) (rgb >> 8); b[idx] = (byte) rgb;
            e.setValue(idx++);
        }
        IndexColorModel cm = transparent ? new IndexColorModel(bits, len, r, gg, b, 0) : new IndexColorModel(bits, len, r, gg, b);
        int mask = (0xFF << shift) & 0xFF;

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        file.delete();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            boolean first = true;
            for (BufferedImage f : frames) {
                BufferedImage ind = new BufferedImage(cm, cm.createCompatibleWritableRaster(f.getWidth(), f.getHeight()), false, null);
                WritableRaster ras = ind.getRaster();
                for (int y = 0; y < f.getHeight(); y++) {
                    for (int x = 0; x < f.getWidth(); x++) {
                        int argb = f.getRGB(x, y);
                        int v;
                        if ((argb >>> 24) < 128) {
                            v = 0;
                        } else {
                            int rgb = (((argb >> 16) & mask) << 16) | (((argb >> 8) & mask) << 8) | (argb & mask);
                            v = palette.get(rgb);
                        }
                        ras.setSample(x, y, 0, v);
                    }
                }
                IIOMetadata md = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(ind), null);
                String fmt = md.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(fmt);
                IIOMetadataNode gce = node(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", transparent ? "TRUE" : "FALSE");
                gce.setAttribute("transparentColorIndex", "0");
                gce.setAttribute("delayTime", String.valueOf(delayCs));
                // Своя таблица цветов у каждого кадра, иначе ImageIO подставит стандартную веб-палитру.
                IIOMetadataNode lct = node(root, "LocalColorTable");
                while (lct.getLength() > 0) lct.removeChild(lct.getFirstChild());
                lct.setAttribute("sizeOfLocalColorTable", String.valueOf(len));
                lct.setAttribute("sortFlag", "FALSE");
                for (int c = 0; c < len; c++) {
                    IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                    entry.setAttribute("index", String.valueOf(c));
                    entry.setAttribute("red", String.valueOf(r[c] & 0xFF));
                    entry.setAttribute("green", String.valueOf(gg[c] & 0xFF));
                    entry.setAttribute("blue", String.valueOf(b[c] & 0xFF));
                    lct.appendChild(entry);
                }
                IIOMetadataNode desc = node(root, "ImageDescriptor");
                desc.setAttribute("interlaceFlag", "FALSE");
                if (first) {
                    IIOMetadataNode apps = node(root, "ApplicationExtensions");
                    IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0}); // бесконечный повтор
                    apps.appendChild(app);
                    first = false;
                }
                md.setFromTree(fmt, root);
                writer.writeToSequence(new IIOImage(ind, null, md), null);
            }
            writer.endWriteSequence();
        }
        System.out.printf("%-18s %2d кадров, %3d цветов%n", file.getName(), frames.size(), size);
    }

    static IIOMetadataNode node(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) root.item(i);
        }
        IIOMetadataNode n = new IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }
}
