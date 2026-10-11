package org.nolphin.handy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Eigener QR-Leser ohne Bibliothek. Liest die QR-Codes, die Nolphin erzeugt
 * (Byte-Modus, Fehlerkorrektur M, Version 1 bis 6), auch gedreht, verzerrt und
 * leicht unscharf. Reines Java ohne Android-Klassen, damit er auch auf dem PC
 * getestet werden kann.
 */
final class QrDecoder {
    private QrDecoder() {}

    /** Anzahl der Suchmuster im zuletzt gelesenen Bild; für die Anzeige im Scanner ("Code erkannt"). */
    static volatile int lastFinderCount;
    /** Wurde im zuletzt gelesenen Bild ein stimmiges Dreieck aus drei Suchmustern gefunden (der Code ist im Bild)? */
    static volatile boolean lastTriple;

    /**
     * Wie decode (), versucht es bei Misserfolg mit auf die Hälfte und ein Viertel verkleinertem Bild.
     * So werden auch sehr große Fotos mit großen Modulen gelesen.
     */
    static String decodeMultiScale(byte[] gray, int w, int h) {
        String text = decode(gray, w, h);
        int best = lastFinderCount;
        boolean triple = lastTriple;
        for (int f = 2; text == null && f <= 4; f *= 2) {
            int nw = w / f, nh = h / f;
            if (nw < 80 || nh < 80) {
                break;
            }
            text = decode(downscale(gray, w, h, f), nw, nh);
            best = Math.max(best, lastFinderCount);
            triple |= lastTriple;
        }
        lastFinderCount = best;
        lastTriple = triple;
        return text;
    }

    /** Verkleinert um den ganzzahligen Faktor f, indem f mal f Punkte gemittelt werden. */
    static byte[] downscale(byte[] gray, int w, int h, int f) {
        int nw = w / f, nh = h / f;
        byte[] out = new byte[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int sum = 0;
                for (int dy = 0; dy < f; dy++) {
                    int row = (y * f + dy) * w + x * f;
                    for (int dx = 0; dx < f; dx++) {
                        sum += gray[row + dx] & 0xFF;
                    }
                }
                out[y * nw + x] = (byte) (sum / (f * f));
            }
        }
        return out;
    }

    // Version 1 bis 6, Stufe M: Codewörter gesamt, Blöcke, Datencodewörter, Fehlerkorrektur je Block
    private static final int[] TOTAL = { 0, 26, 44, 70, 100, 134, 172 };
    private static final int[] BLOCKS = { 0, 1, 1, 1, 2, 2, 4 };
    private static final int[] DATA = { 0, 16, 28, 44, 64, 86, 108 };
    private static final int[] EC = { 0, 10, 16, 26, 18, 24, 16 };

    /**
     * Sucht einen QR-Code im Graustufenbild und gibt seinen Text zurück, sonst null.
     *
     * @param gray Helligkeit je Pixel (0 bis 255), Zeile für Zeile
     */
    static String decode(byte[] gray, int w, int h) {
        if (w < 40 || h < 40 || gray.length < w * h) {
            return null;
        }
        boolean[] dark = binarize(gray, w, h);
        List<double[]> finders = findFinders(dark, w, h);
        lastFinderCount = finders.size();
        lastTriple = false;
        if (finders.size() < 3) {
            return null;
        }
        finders.sort((a, b) -> Double.compare(b[3], a[3]));
        if (finders.size() > 9) {
            finders = new ArrayList<>(finders.subList(0, 9));
        }
        int n = finders.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    String text = tryTriple(dark, w, h, finders.get(i), finders.get(j), finders.get(k));
                    if (text != null) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    // ---- 1. Schwarzweiß mit örtlicher Schwelle ---------------------------------------------

    private static boolean[] binarize(byte[] gray, int w, int h) {
        int[] integral = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            int row = 0;
            for (int x = 0; x < w; x++) {
                row += gray[y * w + x] & 0xFF;
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row;
            }
        }
        int half = Math.max(8, Math.min(w, h) / 16);
        boolean[] dark = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - half);
            int y1 = Math.min(h, y + half + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - half);
                int x1 = Math.min(w, x + half + 1);
                long sum = (long) integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1]
                        - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0];
                int mean = (int) (sum / ((long) (x1 - x0) * (y1 - y0)));
                // Abstand zum örtlichen Mittel: bei hellen Bildern gröber, bei dunklen fein
                dark[y * w + x] = (gray[y * w + x] & 0xFF) < mean - Math.max(2, mean / 25);
            }
        }
        return dark;
    }

    // ---- 2. Suchmuster finden (Verhältnis 1:1:3:1:1) -----------------------------------------

    /** Kandidat: {x, y, Modulgröße, Gewicht}. */
    private static List<double[]> findFinders(boolean[] dark, int w, int h) {
        List<double[]> found = new ArrayList<>();
        int[] runs = new int[w + 2];
        int[] starts = new int[w + 2];
        for (int y = 0; y < h; y++) {
            int count = 0;
            boolean cur = dark[y * w];
            boolean firstDark = cur;
            int start = 0;
            for (int x = 1; x <= w; x++) {
                boolean v = x < w ? dark[y * w + x] : !cur;
                if (v != cur) {
                    runs[count] = x - start;
                    starts[count] = start;
                    count++;
                    start = x;
                    cur = v;
                }
            }
            for (int i = 0; i + 4 < count; i++) {
                if (((i % 2) == 0) != firstDark) {
                    continue;   // das Muster beginnt mit einem dunklen Lauf
                }
                double total = runs[i] + runs[i + 1] + runs[i + 2] + runs[i + 3] + runs[i + 4];
                double m = total / 7.0;
                if (m < 1.5 || !ratioOk(runs[i], runs[i + 1], runs[i + 2], runs[i + 3], runs[i + 4], m)) {
                    continue;
                }
                double cx = starts[i] + runs[i] + runs[i + 1] + runs[i + 2] / 2.0;
                double[] v = scan(dark, w, h, (int) cx, y, 0, 1);
                if (v == null) {
                    continue;
                }
                double cy = y + v[0];
                double[] hz = scan(dark, w, h, (int) cx, (int) Math.round(cy), 1, 0);
                if (hz == null) {
                    continue;
                }
                merge(found, cx + hz[0], cy, (m + v[1] + hz[1]) / 3.0);
            }
        }
        return found;
    }

    private static boolean ratioOk(double a, double b, double c, double d, double e, double m) {
        double tol = m * 0.7;
        return Math.abs(a - m) < tol && Math.abs(b - m) < tol && Math.abs(c - 3 * m) < 3 * tol
                && Math.abs(d - m) < tol && Math.abs(e - m) < tol;
    }

    private static boolean inside(int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /**
     * Prüft das Muster 1:1:3:1:1 entlang (dx, dy) durch (x, y), das dunkel sein muss.
     * Gibt {Verschiebung der Mitte entlang der Richtung, Modulgröße} oder null zurück.
     */
    private static double[] scan(boolean[] dark, int w, int h, int x, int y, int dx, int dy) {
        if (!inside(x, y, w, h) || !dark[y * w + x]) {
            return null;
        }
        int[] c = new int[5];
        int back2 = 0, fwd2 = 0;
        int px = x, py = y;
        while (inside(px, py, w, h) && dark[py * w + px]) { back2++; px -= dx; py -= dy; }
        if (!inside(px, py, w, h)) return null;
        while (inside(px, py, w, h) && !dark[py * w + px]) { c[1]++; px -= dx; py -= dy; }
        if (!inside(px, py, w, h)) return null;
        while (inside(px, py, w, h) && dark[py * w + px]) { c[0]++; px -= dx; py -= dy; }
        px = x + dx;
        py = y + dy;
        while (inside(px, py, w, h) && dark[py * w + px]) { fwd2++; px += dx; py += dy; }
        if (!inside(px, py, w, h)) return null;
        while (inside(px, py, w, h) && !dark[py * w + px]) { c[3]++; px += dx; py += dy; }
        if (!inside(px, py, w, h)) return null;
        while (inside(px, py, w, h) && dark[py * w + px]) { c[4]++; px += dx; py += dy; }
        c[2] = back2 + fwd2;
        double m = (c[0] + c[1] + c[2] + c[3] + c[4]) / 7.0;
        if (m < 1.5 || !ratioOk(c[0], c[1], c[2], c[3], c[4], m)) {
            return null;
        }
        return new double[] { (fwd2 - back2 + 1) / 2.0, m };
    }

    private static void merge(List<double[]> list, double x, double y, double m) {
        for (double[] f : list) {
            if (Math.abs(f[0] - x) < f[2] * 2.5 && Math.abs(f[1] - y) < f[2] * 2.5 && Math.abs(f[2] - m) < f[2] * 0.5) {
                double wgt = f[3];
                f[0] = (f[0] * wgt + x) / (wgt + 1);
                f[1] = (f[1] * wgt + y) / (wgt + 1);
                f[2] = (f[2] * wgt + m) / (wgt + 1);
                f[3] = wgt + 1;
                return;
            }
        }
        list.add(new double[] { x, y, m, 1 });
    }

    // ---- 3. Drei Suchmuster zu einem Code zusammensetzen ---------------------------------------

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static String tryTriple(boolean[] dark, int w, int h, double[] p, double[] q, double[] r) {
        double mAvg = (p[2] + q[2] + r[2]) / 3.0;
        for (double[] f : new double[][] { p, q, r }) {
            if (Math.abs(f[2] - mAvg) > mAvg * 0.5) {
                return null;
            }
        }
        double dpq = dist(p, q), dpr = dist(p, r), dqr = dist(q, r);
        double[] tl, a, b;
        double longest, s1, s2;
        if (dqr >= dpq && dqr >= dpr) { tl = p; a = q; b = r; longest = dqr; s1 = dpq; s2 = dpr; }
        else if (dpr >= dpq) { tl = q; a = p; b = r; longest = dpr; s1 = dpq; s2 = dqr; }
        else { tl = r; a = p; b = q; longest = dpq; s1 = dpr; s2 = dqr; }
        if (Math.abs(s1 - s2) > 0.3 * Math.max(s1, s2) || Math.abs(longest - Math.hypot(s1, s2)) > 0.3 * longest) {
            return null;
        }
        // Rechts oben ist die Ecke, die im Uhrzeigersinn (Bild: y nach unten) vor der linken unteren liegt.
        double cross = (a[0] - tl[0]) * (b[1] - tl[1]) - (a[1] - tl[1]) * (b[0] - tl[0]);
        lastTriple = true;
        double[] tr = cross > 0 ? a : b;
        double[] bl = cross > 0 ? b : a;

        double span = (dist(tl, tr) + dist(tl, bl)) / 2.0;
        int estimate = (int) Math.round(span / mAvg + 7);
        int version = Math.round((estimate - 17) / 4.0f);
        for (int delta : new int[] { 0, -1, 1 }) {
            int v = version + delta;
            if (v < 1 || v > 6) {
                continue;
            }
            for (int mode = 0; mode < 2; mode++) {
                String text = decodeGrid(dark, w, h, tl, tr, bl, v, mode == 0);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    // ---- 4. Raster abtasten ---------------------------------------------------------------------

    private static String decodeGrid(boolean[] dark, int w, int h, double[] tl, double[] tr, double[] bl,
                                     int version, boolean useAlignment) {
        int size = 4 * version + 17;
        double[][] src = new double[4][];
        double[][] dst = new double[4][];
        src[0] = new double[] { 3.5, 3.5 };
        dst[0] = new double[] { tl[0], tl[1] };
        src[1] = new double[] { size - 3.5, 3.5 };
        dst[1] = new double[] { tr[0], tr[1] };
        src[2] = new double[] { 3.5, size - 3.5 };
        dst[2] = new double[] { bl[0], bl[1] };
        // Vierter Punkt: Ergänzung zum Parallelogramm (Ecke rechts unten)
        double bx = tr[0] + bl[0] - tl[0];
        double by = tr[1] + bl[1] - tl[1];
        src[3] = new double[] { size - 3.5, size - 3.5 };
        dst[3] = new double[] { bx, by };

        if (useAlignment) {
            if (version < 2) {
                return null;
            }
            double ux = (tr[0] - tl[0]) / (size - 7);
            double uy = (tr[1] - tl[1]) / (size - 7);
            double vx = (bl[0] - tl[0]) / (size - 7);
            double vy = (bl[1] - tl[1]) / (size - 7);
            double a = size - 7;   // Mitte des Ausrichtungsmusters: (size-6.5, size-6.5), also 3 Module von der Ecke
            double px = tl[0] + ux * a + vx * a;
            double py = tl[1] + uy * a + vy * a;
            double[] best = null;
            double bestScore = -1;
            for (double oy = -3.5; oy <= 3.51; oy += 0.25) {
                for (double ox = -3.5; ox <= 3.51; ox += 0.25) {
                    double cx = px + ux * ox + vx * oy;
                    double cy = py + uy * ox + vy * oy;
                    double score = alignmentScore(dark, w, h, cx, cy, ux, uy, vx, vy);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new double[] { cx, cy };
                    }
                }
            }
            if (best == null || bestScore < 20) {
                return null;
            }
            src[3] = new double[] { size - 6.5, size - 6.5 };
            dst[3] = best;
        }
        double[] hom = homography(src, dst);
        if (hom == null) {
            return null;
        }
        boolean[][] grid = new boolean[size][size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int votes = 0;
                double[][] offs = { { 0.5, 0.5 }, { 0.25, 0.5 }, { 0.75, 0.5 }, { 0.5, 0.25 }, { 0.5, 0.75 } };
                for (double[] o : offs) {
                    double[] pt = apply(hom, i + o[0], j + o[1]);
                    int xx = (int) Math.floor(pt[0]);
                    int yy = (int) Math.floor(pt[1]);
                    if (inside(xx, yy, w, h) && dark[yy * w + xx]) {
                        votes++;
                    }
                }
                grid[j][i] = votes >= 3;
            }
        }
        return readGrid(grid, version);
    }

    private static double alignmentScore(boolean[] dark, int w, int h, double cx, double cy,
                                         double ux, double uy, double vx, double vy) {
        int score = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = (int) Math.round(cx + ux * dx + vx * dy);
                int y = (int) Math.round(cy + uy * dx + vy * dy);
                if (!inside(x, y, w, h)) {
                    continue;
                }
                boolean expectDark = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                if (dark[y * w + x] == expectDark) {
                    score++;
                }
            }
        }
        return score;
    }

    /** Löst die Abbildung (a, b) -> (x, y) aus vier Punktpaaren. */
    private static double[] homography(double[][] s, double[][] d) {
        double[][] m = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double a = s[i][0], b = s[i][1], x = d[i][0], y = d[i][1];
            m[2 * i] = new double[] { a, b, 1, 0, 0, 0, -a * x, -b * x, x };
            m[2 * i + 1] = new double[] { 0, 0, 0, a, b, 1, -a * y, -b * y, y };
        }
        for (int col = 0; col < 8; col++) {
            int pivot = col;
            for (int r = col + 1; r < 8; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(m[pivot][col]) < 1e-12) {
                return null;
            }
            double[] t = m[col]; m[col] = m[pivot]; m[pivot] = t;
            for (int r = 0; r < 8; r++) {
                if (r != col) {
                    double f = m[r][col] / m[col][col];
                    for (int c = col; c < 9; c++) {
                        m[r][c] -= f * m[col][c];
                    }
                }
            }
        }
        double[] hm = new double[8];
        for (int i = 0; i < 8; i++) {
            hm[i] = m[i][8] / m[i][i];
        }
        return hm;
    }

    private static double[] apply(double[] h, double a, double b) {
        double den = h[6] * a + h[7] * b + 1;
        return new double[] { (h[0] * a + h[1] * b + h[2]) / den, (h[3] * a + h[4] * b + h[5]) / den };
    }

    // ---- 5. Bits lesen -----------------------------------------------------------------------------

    private static boolean[][] functionMap(int version) {
        int size = 4 * version + 17;
        boolean[][] fn = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            fn[6][i] = true;
            fn[i][6] = true;
        }
        int[][] centers = { { 3, 3 }, { size - 4, 3 }, { 3, size - 4 } };
        for (int[] c : centers) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int x = c[0] + dx, y = c[1] + dy;
                    if (x >= 0 && x < size && y >= 0 && y < size) {
                        fn[y][x] = true;
                    }
                }
            }
        }
        if (version >= 2) {
            int a = 4 * version + 10;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    fn[a + dy][a + dx] = true;
                }
            }
        }
        for (int[] p : formatPositions1(size)) {
            fn[p[1]][p[0]] = true;
        }
        for (int[] p : formatPositions2(size)) {
            fn[p[1]][p[0]] = true;
        }
        fn[size - 8][8] = true;
        return fn;
    }

    /** Orte der 15 Formatbits (Spalte, Zeile), erste Kopie, Bit 0 bis 14. */
    private static int[][] formatPositions1(int size) {
        int[][] p = new int[15][];
        for (int i = 0; i <= 5; i++) p[i] = new int[] { 8, i };
        p[6] = new int[] { 8, 7 };
        p[7] = new int[] { 8, 8 };
        p[8] = new int[] { 7, 8 };
        for (int i = 9; i < 15; i++) p[i] = new int[] { 14 - i, 8 };
        return p;
    }

    private static int[][] formatPositions2(int size) {
        int[][] p = new int[15][];
        for (int i = 0; i < 8; i++) p[i] = new int[] { size - 1 - i, 8 };
        for (int i = 8; i < 15; i++) p[i] = new int[] { 8, size - 15 + i };
        return p;
    }

    private static int formatCode(int data) {
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        }
        return ((data << 10) | rem) ^ 0x5412;
    }

    /** Liest die Formatbits; gibt die Maske (0 bis 7) zurück oder -1. Nur Fehlerkorrektur M. */
    private static int readMask(boolean[][] grid, int size) {
        int raw1 = 0, raw2 = 0;
        int[][] p1 = formatPositions1(size);
        int[][] p2 = formatPositions2(size);
        for (int i = 0; i < 15; i++) {
            if (grid[p1[i][1]][p1[i][0]]) raw1 |= 1 << i;
            if (grid[p2[i][1]][p2[i][0]]) raw2 |= 1 << i;
        }
        int best = -1, bestDist = 4;
        for (int data = 0; data < 32; data++) {
            int code = formatCode(data);
            int d = Math.min(Integer.bitCount(code ^ raw1), Integer.bitCount(code ^ raw2));
            if (d < bestDist) {
                bestDist = d;
                best = data;
            }
        }
        if (best < 0 || (best >> 3) != 0) {
            return -1;   // keine Stufe M
        }
        return best & 7;
    }

    private static boolean maskApplies(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return (x * y) % 2 + (x * y) % 3 == 0;
            case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
            default: return (((x + y) % 2) + (x * y) % 3) % 2 == 0;
        }
    }

    private static String readGrid(boolean[][] grid, int version) {
        int size = 4 * version + 17;
        int mask = readMask(grid, size);
        if (mask < 0) {
            return null;
        }
        boolean[][] fn = functionMap(version);
        int total = TOTAL[version];
        byte[] cw = new byte[total];
        int bit = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!fn[y][x] && bit < total * 8) {
                        boolean v = grid[y][x] ^ maskApplies(mask, x, y);
                        if (v) {
                            cw[bit >> 3] |= (byte) (0x80 >> (bit & 7));
                        }
                        bit++;
                    }
                }
            }
        }
        // Blöcke entflechten und korrigieren
        int blocks = BLOCKS[version];
        int dataPer = DATA[version] / blocks;
        int ec = EC[version];
        int[][] block = new int[blocks][dataPer + ec];
        int k = 0;
        for (int i = 0; i < dataPer; i++) {
            for (int b = 0; b < blocks; b++) {
                block[b][i] = cw[k++] & 0xFF;
            }
        }
        for (int i = 0; i < ec; i++) {
            for (int b = 0; b < blocks; b++) {
                block[b][dataPer + i] = cw[k++] & 0xFF;
            }
        }
        byte[] data = new byte[DATA[version]];
        int pos = 0;
        for (int b = 0; b < blocks; b++) {
            if (!Rs.correct(block[b], ec)) {
                return null;
            }
            for (int i = 0; i < dataPer; i++) {
                data[pos++] = (byte) block[b][i];
            }
        }
        return parse(data);
    }

    /** Byte-Modus: 0100, 8 Bit Länge, Bytes. */
    private static String parse(byte[] data) {
        int mode = (data[0] & 0xFF) >> 4;
        if (mode != 4) {
            return null;
        }
        int len = ((data[0] & 0x0F) << 4) | ((data[1] & 0xFF) >> 4);
        if (len == 0 || len + 2 > data.length) {
            return null;
        }
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) (((data[1 + i] & 0x0F) << 4) | ((data[2 + i] & 0xFF) >> 4));
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    // ---- 6. Reed-Solomon (GF(256), Polynom 0x11D, Wurzeln ab alpha^0) ---------------------------------

    private static final class Rs {
        private static final int[] EXP = new int[512];
        private static final int[] LOG = new int[256];

        static {
            int x = 1;
            for (int i = 0; i < 255; i++) {
                EXP[i] = x;
                LOG[x] = i;
                x <<= 1;
                if ((x & 0x100) != 0) {
                    x ^= 0x11D;
                }
            }
            for (int i = 255; i < 512; i++) {
                EXP[i] = EXP[i - 255];
            }
        }

        static int mul(int a, int b) {
            return (a == 0 || b == 0) ? 0 : EXP[LOG[a] + LOG[b]];
        }

        static int inv(int a) {
            return EXP[255 - LOG[a]];
        }

        /** Korrigiert @p word (Daten, dann Fehlerkorrektur) an Ort und Stelle; false, wenn nicht möglich. */
        static boolean correct(int[] word, int ec) {
            int len = word.length;
            int[] s = new int[ec];
            boolean clean = true;
            for (int j = 0; j < ec; j++) {
                int acc = 0;
                for (int i = 0; i < len; i++) {
                    acc = mul(acc, EXP[j]) ^ word[i];
                }
                s[j] = acc;
                if (acc != 0) {
                    clean = false;
                }
            }
            if (clean) {
                return true;
            }
            // Berlekamp-Massey
            int[] c = new int[ec + 1];
            int[] bPoly = new int[ec + 1];
            c[0] = 1;
            bPoly[0] = 1;
            int l = 0, m = 1, bb = 1;
            for (int n = 0; n < ec; n++) {
                int d = s[n];
                for (int i = 1; i <= l; i++) {
                    d ^= mul(c[i], s[n - i]);
                }
                if (d == 0) {
                    m++;
                } else if (2 * l <= n) {
                    int[] t = c.clone();
                    int coef = mul(d, inv(bb));
                    for (int i = 0; i + m <= ec; i++) {
                        c[i + m] ^= mul(coef, bPoly[i]);
                    }
                    l = n + 1 - l;
                    bPoly = t;
                    bb = d;
                    m = 1;
                } else {
                    int coef = mul(d, inv(bb));
                    for (int i = 0; i + m <= ec; i++) {
                        c[i + m] ^= mul(coef, bPoly[i]);
                    }
                    m++;
                }
            }
            if (l == 0 || l > ec / 2) {
                return false;
            }
            // Nullstellen suchen: Fehlerstelle mit Potenz i, wenn sigma(alpha^-i) == 0
            int[] errPow = new int[l];
            int found = 0;
            for (int i = 0; i < len && found <= l; i++) {
                int x = EXP[(255 - i) % 255];
                int val = 0;
                int xp = 1;
                for (int j = 0; j <= l; j++) {
                    val ^= mul(c[j], xp);
                    xp = mul(xp, x);
                }
                if (val == 0) {
                    if (found < l) {
                        errPow[found] = i;
                    }
                    found++;
                }
            }
            if (found != l) {
                return false;
            }
            // Omega = S * sigma mod x^ec
            int[] omega = new int[ec];
            for (int i = 0; i < ec; i++) {
                int acc = 0;
                for (int j = 0; j <= i && j <= l; j++) {
                    acc ^= mul(c[j], s[i - j]);
                }
                omega[i] = acc;
            }
            for (int e = 0; e < l; e++) {
                int pw = errPow[e];
                int xk = EXP[pw];
                int xinv = EXP[(255 - pw) % 255];
                int om = 0;
                int xp = 1;
                for (int j = 0; j < ec; j++) {
                    om ^= mul(omega[j], xp);
                    xp = mul(xp, xinv);
                }
                int deriv = 0;
                xp = 1;
                for (int j = 1; j <= l; j += 2) {
                    deriv ^= mul(c[j], xp);
                    xp = mul(xp, mul(xinv, xinv));
                }
                if (deriv == 0) {
                    return false;
                }
                int value = mul(xk, mul(om, inv(deriv)));
                int index = len - 1 - pw;
                if (index < 0 || index >= len) {
                    return false;
                }
                word[index] ^= value;
            }
            // Gegenprobe
            for (int j = 0; j < ec; j++) {
                int acc = 0;
                for (int i = 0; i < len; i++) {
                    acc = mul(acc, EXP[j]) ^ word[i];
                }
                if (acc != 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
