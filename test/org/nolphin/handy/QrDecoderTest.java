package org.nolphin.handy;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Prüft den QR-Leser auf dem PC: Die Matrizen kommen vom C-Encoder von Nolphin
 * (src/nolphin-qr.c), sie werden gedreht, verzerrt, unscharf und verrauscht
 * gezeichnet und wieder gelesen. Aufruf: android/test.sh
 */
public class QrDecoderTest {
    static int fails = 0, runs = 0;

    static boolean[][] matrix(String driver, String text) throws Exception {
        Process p = new ProcessBuilder(driver, text, "-1").redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String l;
            while ((l = r.readLine()) != null) {
                lines.add(l.trim());
            }
        }
        boolean[][] m = new boolean[lines.size()][lines.size()];
        for (int y = 0; y < lines.size(); y++) {
            for (int x = 0; x < lines.size(); x++) {
                m[y][x] = lines.get(y).charAt(x) == '1';
            }
        }
        return m;
    }

    /** Zeichnet den Code: scale = Pixel je Modul, persp = Perspektive, blur = Unschärfe in Pixeln. */
    static byte[] render(boolean[][] m, int W, int H, double scale, double angleDeg, double persp,
                         double blur, double noise, double gradient, long seed) {
        int n = m.length;
        Random rnd = new Random(seed);
        double a = Math.toRadians(angleDeg);
        double ca = Math.cos(a), sa = Math.sin(a);
        double[] img = new double[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double acc = 0;
                for (int sy = 0; sy < 2; sy++) {
                    for (int sx = 0; sx < 2; sx++) {
                        double px = x + 0.25 + 0.5 * sx - W / 2.0;
                        double py = y + 0.25 + 0.5 * sy - H / 2.0;
                        double xr = ca * px + sa * py;
                        double yr = -sa * px + ca * py;
                        double den = 1 + persp * xr / (n * scale);
                        double mx = xr / scale / den + n / 2.0;
                        double my = yr / scale / den + n / 2.0;
                        boolean dark = false;
                        int ix = (int) Math.floor(mx), iy = (int) Math.floor(my);
                        if (ix >= 0 && iy >= 0 && ix < n && iy < n) {
                            dark = m[iy][ix];
                        }
                        acc += dark ? 45 : 215;
                    }
                }
                img[y * W + x] = acc / 4 + gradient * (x - W / 2.0) / W * 60;
            }
        }
        if (blur > 0) {
            int r = (int) Math.ceil(blur);
            double[] out = new double[W * H];
            double sigma = blur;
            double[] k = new double[2 * r + 1];
            double sum = 0;
            for (int i = -r; i <= r; i++) {
                k[i + r] = Math.exp(-i * i / (2 * sigma * sigma));
                sum += k[i + r];
            }
            double[] tmp = new double[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    double v = 0;
                    for (int i = -r; i <= r; i++) {
                        int xx = Math.min(W - 1, Math.max(0, x + i));
                        v += img[y * W + xx] * k[i + r];
                    }
                    tmp[y * W + x] = v / sum;
                }
            }
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    double v = 0;
                    for (int i = -r; i <= r; i++) {
                        int yy = Math.min(H - 1, Math.max(0, y + i));
                        v += tmp[yy * W + x] * k[i + r];
                    }
                    out[y * W + x] = v / sum;
                }
            }
            img = out;
        }
        byte[] g = new byte[W * H];
        for (int i = 0; i < g.length; i++) {
            double v = img[i] + (noise > 0 ? rnd.nextGaussian() * noise : 0);
            g[i] = (byte) Math.max(0, Math.min(255, Math.round(v)));
        }
        return g;
    }

    static void check(String what, byte[] g, int W, int H, String expect) {
        runs++;
        String got = QrDecoder.decode(g, W, H);
        if (!expect.equals(got)) {
            fails++;
            System.out.println("FAIL: " + what + " -> " + (got == null ? "nichts gelesen" : "falscher Text " + got));
        }
    }

    public static void main(String[] args) throws Exception {
        String driver = args[0];
        String[] texts = {
            "A",
            "nolphin://connect?host=192.168.178.20:41841&t=f334a57a2c0d4a7e9b6c1d2e3f405162",
            "http://192.168.178.20:41841/?t=f334a57a2c0d4a7e9b6c1d2e3f405162",
            "http://10.0.0.5:8080/?t=00112233445566778899aabbccddeeff",
            "123456789012345",
        };
        double[] scales = { 4, 5.5, 8 };
        double[] angles = { 0, 90, 180, 270, 12, 37, 200 };
        for (String text : texts) {
            boolean[][] m = matrix(driver, text);
            for (double scale : scales) {
                for (double angle : angles) {
                    int W = 640, H = 480;
                    if (m.length * scale > H - 20) {
                        continue;
                    }
                    check("sauber " + m.length + " s=" + scale + " w=" + angle, render(m, W, H, scale, angle, 0, 0, 0, 0, 1), W, H, text);
                }
            }
            // Verzerrt, unscharf, verrauscht, ungleich beleuchtet (mittlere Bedingungen)
            double[][] hard = {
                // scale, angle, persp, blur, noise, gradient
                { 7, 8, 0.10, 0.8, 6, 0.4 },
                { 7, -15, 0.15, 1.0, 8, 0.6 },
                { 6, 30, 0.12, 0.9, 5, 0.3 },
                { 8, 100, 0.08, 1.2, 10, 0.5 },
                { 6.5, 0, 0.20, 0.7, 4, 0.0 },
            };
            for (double[] hset : hard) {
                int W = 640, H = 480;
                if (m.length * hset[0] > H - 20 || m.length < 33) {
                    continue;   // Nolphin-Links sind immer Version 4 bis 6
                }
                for (long seed = 1; seed <= 3; seed++) {
                    check("verzerrt " + m.length + " " + java.util.Arrays.toString(hset), render(m, W, H, hset[0], hset[1], hset[2], hset[3], hset[4], hset[5], seed), W, H, text);
                }
            }
        }
        // Große Fotos (Kamera-App, 12 Megapixel und mehr): große Module, nur mit Verkleinerung lesbar
        {
            String text = "http://192.168.178.20:43913/?t=4d6d1633ec8ca225aaeb5ddfbc5a1b6d&v=1&e=1791769130";
            boolean[][] m = matrix(driver, text);
            int[][] sizes = { { 4000, 3000 }, { 3264, 2448 }, { 2560, 1920 } };
            double[] scalesBig = { 26, 18, 14 };
            for (int i = 0; i < sizes.length; i++) {
                for (double angle : new double[] { 0, 15, 100 }) {
                    int W = sizes[i][0], H = sizes[i][1];
                    byte[] g = render(m, W, H, scalesBig[i], angle, 0.08, 1.5, 4, 0.3, 7);
                    runs++;
                    long t0 = System.nanoTime();
                    String got = QrDecoder.decodeMultiScale(g, W, H);
                    long ms = (System.nanoTime() - t0) / 1000000;
                    if (!text.equals(got)) {
                        fails++;
                        System.out.println("FAIL: großes Foto " + W + "x" + H + " Winkel " + angle + " -> " + got);
                    } else if (angle == 0) {
                        System.out.println("Foto " + W + "x" + H + " gelesen in " + ms + " ms");
                    }
                }
            }
        }
        // Falscher Inhalt darf nichts liefern
        {
            byte[] noiseImg = new byte[640 * 480];
            new Random(5).nextBytes(noiseImg);
            runs++;
            if (QrDecoder.decode(noiseImg, 640, 480) != null) {
                fails++;
                System.out.println("FAIL: Rauschen wurde als Code gelesen");
            }
            byte[] blank = new byte[640 * 480];
            java.util.Arrays.fill(blank, (byte) 200);
            runs++;
            if (QrDecoder.decode(blank, 640, 480) != null) {
                fails++;
                System.out.println("FAIL: leeres Bild wurde als Code gelesen");
            }
        }
        // Echter Bildschirmfoto-Ausschnitt aus der Dokumentation (QR-Code im Panel)
        if (args.length > 1 && new File(args[1]).exists()) {
            BufferedImage bi = ImageIO.read(new File(args[1]));
            int W = bi.getWidth(), H = bi.getHeight();
            byte[] g = new byte[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int rgb = bi.getRGB(x, y);
                    g[y * W + x] = (byte) (((rgb >> 16 & 255) * 3 + (rgb >> 8 & 255) * 6 + (rgb & 255)) / 10);
                }
            }
            runs++;
            String got = QrDecoder.decode(g, W, H);
            if (got == null || !got.startsWith("http://192.168.178.20:") || !got.contains("/?t=")) {
                fails++;
                System.out.println("FAIL: Screenshot -> " + got);
            } else {
                System.out.println("Screenshot gelesen: " + got.replaceAll("t=.*", "t=…"));
            }
        }
        // Echtes Handyfoto (dunkel, unscharf, schief) des QR-Codes vom Bildschirm, Ausschnitt aus dem Xiaomi-Scanner
        File real = new File(args.length > 2 ? args[2] : "android/test/resources/echtes-foto.png");
        if (real.exists()) {
            BufferedImage bi = ImageIO.read(real);
            int W = bi.getWidth(), H = bi.getHeight();
            byte[] g = new byte[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    g[y * W + x] = (byte) (bi.getRGB(x, y) & 255);
                }
            }
            runs++;
            String got = QrDecoder.decode(g, W, H);
            if (!"http://192.168.178.20:39369/?t=4d6d1633ec8ca225aaeb5ddfbc5a1b6d".equals(got)) {
                fails++;
                System.out.println("FAIL: echtes Foto -> " + got);
            } else {
                System.out.println("Echtes Handyfoto gelesen: " + got.replaceAll("t=.*", "t=…"));
            }
        }
        System.out.println(runs - fails + " von " + runs + " Prüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
