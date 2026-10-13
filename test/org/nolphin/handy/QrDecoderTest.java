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

    /** Löst die Abbildung aus vier Punktpaaren (a,b)->(x,y); Ergebnis: acht Werte einer Perspektivabbildung. */
    static double[] homography(double[][] s, double[][] d) {
        double[][] m = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double a = s[i][0], b = s[i][1], x = d[i][0], y = d[i][1];
            m[2 * i] = new double[] { a, b, 1, 0, 0, 0, -a * x, -b * x, x };
            m[2 * i + 1] = new double[] { 0, 0, 0, a, b, 1, -a * y, -b * y, y };
        }
        for (int col = 0; col < 8; col++) {
            int piv = col;
            for (int r = col + 1; r < 8; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[piv][col])) {
                    piv = r;
                }
            }
            double[] t = m[col]; m[col] = m[piv]; m[piv] = t;
            for (int r = 0; r < 8; r++) {
                if (r != col) {
                    double f = m[r][col] / m[col][col];
                    for (int c = col; c < 9; c++) {
                        m[r][c] -= f * m[col][c];
                    }
                }
            }
        }
        double[] h = new double[8];
        for (int i = 0; i < 8; i++) {
            h[i] = m[i][8] / m[i][i];
        }
        return h;
    }

    /**
     * Zeichnet den Code wie ein schräg gehaltenes Handy ihn sieht: das Papier (Code mit Rand) füllt das Viereck
     * quad (links oben, rechts oben, rechts unten, links unten, in Bildpunkten); dazu Unschärfe, Schatten, Rauschen
     * und auf Wunsch ein weißer Mauszeiger auf dem Code nahe dem Ausrichtungsmuster.
     */
    static byte[] renderQuad(boolean[][] m, int W, int H, double[][] quad, double blur, double noise, double gradient,
                             boolean cursor, long seed) {
        int n = m.length;
        double q = 4;   // Ruhezone in Modulen
        double[][] mod = { { -q, -q }, { n + q, -q }, { n + q, n + q }, { -q, n + q } };
        double[] h = homography(quad, mod);   // Bildpunkt -> Modulkoordinate
        Random rnd = new Random(seed);
        double[] img = new double[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double acc = 0;
                for (int sy = 0; sy < 2; sy++) {
                    for (int sx = 0; sx < 2; sx++) {
                        double px = x + 0.25 + 0.5 * sx, py = y + 0.25 + 0.5 * sy;
                        double den = h[6] * px + h[7] * py + 1;
                        double mx = (h[0] * px + h[1] * py + h[2]) / den;
                        double my = (h[3] * px + h[4] * py + h[5]) / den;
                        double v = 110;   // Hintergrund
                        if (mx >= -q && mx < n + q && my >= -q && my < n + q) {
                            v = 235;      // Papier
                            int ix = (int) Math.floor(mx), iy = (int) Math.floor(my);
                            if (ix >= 0 && iy >= 0 && ix < n && iy < n && m[iy][ix]) {
                                v = 70;
                            }
                            if (cursor) {
                                // Pfeil, Spitze bei Modul (29, 30): oben links in (0,0), Maße in Modulen
                                double ax = mx - 29, ay = my - 30;
                                boolean in = ax >= 0 && ay >= 0 && ax <= 3.2 && ay <= 4.8
                                        && ay >= ax * 1.5 - 0.2 && ay <= 4.6 - ax * 0.6 + (ax > 1.4 ? 0 : 3);
                                if (in) {
                                    v = 250;
                                }
                            }
                        }
                        acc += v;
                    }
                }
                img[y * W + x] = acc / 4;
            }
        }
        int r = Math.max(0, (int) Math.round(blur));
        if (r > 0) {
            double[] tmp = new double[W * H];
            for (int pass = 0; pass < 2; pass++) {
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        double sum = 0; int c = 0;
                        for (int k = -r; k <= r; k++) {
                            int xx = x + k;
                            if (xx >= 0 && xx < W) { sum += img[y * W + xx]; c++; }
                        }
                        tmp[y * W + x] = sum / c;
                    }
                }
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        double sum = 0; int c = 0;
                        for (int k = -r; k <= r; k++) {
                            int yy = y + k;
                            if (yy >= 0 && yy < H) { sum += tmp[yy * W + x]; c++; }
                        }
                        img[y * W + x] = sum / c;
                    }
                }
            }
        }
        byte[] out = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double k = 1 - gradient * 0.5 + gradient * 0.5 * (x / (double) W) - 0.1 * gradient * (y / (double) H);
                double v = img[y * W + x] * k + rnd.nextGaussian() * noise;
                out[y * W + x] = (byte) Math.max(0, Math.min(255, (int) Math.round(v)));
            }
        }
        return out;
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
            "nolphin://connect?host=192.168.1.10:40000&t=0123456789abcdef0123456789abcdef",
            "http://192.168.1.10:40000/?t=0123456789abcdef0123456789abcdef",
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
            String text = "nolphin://192.168.1.10:40000/?t=0123456789abcdef0123456789abcdef&v=2&f=QULDtffIw8Ed6DmETVJ7zA";
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
        // Starke Schräglage (Handy schräg vor dem Bildschirm), Mauszeiger auf dem Code, Schatten, Rauschen, Unschärfe:
        // genau die Fälle, in denen die Ausrichtungsmarke weit neben der aus den Ecken errechneten Stelle liegt.
        {
            String text = "nolphin://192.168.1.10:40000/?t=0123456789abcdef0123456789abcdef&v=2&f=QULDtffIw8Ed6DmETVJ7zA";
            boolean[][] m = matrix(driver, text);
            double[][][] quads = {
                { { 120, 60 }, { 430, 80 }, { 420, 400 }, { 100, 420 } },    // leicht
                { { 110, 50 }, { 450, 40 }, { 405, 410 }, { 95, 430 } },     // rechts schmaler
                { { 150, 60 }, { 420, 70 }, { 470, 410 }, { 90, 400 } },     // oben schmaler
                { { 160, 40 }, { 470, 120 }, { 430, 420 }, { 110, 330 } },   // gedreht und schräg
                { { 100, 80 }, { 470, 50 }, { 430, 440 }, { 130, 400 } },    // stark schräg
                { { 90, 40 }, { 400, 90 }, { 450, 430 }, { 120, 380 } },     // gegenläufig
            };
            for (int qi = 0; qi < quads.length; qi++) {
                for (int variant = 0; variant < 3; variant++) {
                    boolean cursor = variant != 1;
                    double blur = variant == 2 ? 2 : 1;
                    int W = 640, H = 480;
                    byte[] g = renderQuad(m, W, H, quads[qi], blur, 3 + variant, 0.5, cursor, 100 + qi * 10 + variant);
                    check("schräg " + qi + (cursor ? " mit Zeiger" : " ohne Zeiger") + " Unschärfe " + blur, g, W, H, text);
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
            if (got == null || !got.startsWith("nolphin://192.168.1.10:40000/?t=") || !got.contains("&v=2&f=")) {
                fails++;
                System.out.println("FAIL: Screenshot -> " + got);
            } else {
                System.out.println("Screenshot gelesen: " + got.replaceAll("t=.*", "t=…"));
            }
        }
        // Künstlich verwischtes Testbild (dunkel, wenig Kontrast, unscharf, schief, mit Rauschen) mit erfundenem Link.
        // Ein echtes Foto wird nicht mehr benutzt: Es enthielt den echten Link einer Sitzung.
        File real = new File(args.length > 2 ? args[2] : "test/resources/testfoto-verwischt.png");
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
            if (!"nolphin://192.168.1.10:40000/?t=0123456789abcdef0123456789abcdef&v=2&f=QULDtffIw8Ed6DmETVJ7zA".equals(got)) {
                fails++;
                System.out.println("FAIL: verwischtes Testbild -> " + got);
            } else {
                System.out.println("Verwischtes Testbild gelesen: " + got.replaceAll("t=.*", "t=…"));
            }
        }
        System.out.println(runs - fails + " von " + runs + " Prüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
