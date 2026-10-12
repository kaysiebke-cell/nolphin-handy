package org.nolphin.handy;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Farben, Karten, Knöpfe und kleine Hilfen für das Aussehen der App (hell und dunkel). */
final class Ui {
    private Ui() {}

    static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    // Ruhige, neutrale Flächen; Grün nur als Akzent (Auswahl, aktive Reiter, Fortschritt)
    static int bg(Context c)       { return dark(c) ? 0xFF181818 : 0xFFF4F5F3; }
    static int surface(Context c)  { return dark(c) ? 0xFF242424 : 0xFFFFFFFF; }
    static int text(Context c)     { return dark(c) ? 0xFFEDEDED : 0xFF1C211A; }
    static int dim(Context c)      { return dark(c) ? 0xFFB0B0B0 : 0xFF565E52; }
    static int primary(Context c)  { return dark(c) ? 0xFF8CC152 : 0xFF3F8A21; }
    static int accent(Context c)   { return primary(c); }
    static int danger(Context c)   { return dark(c) ? 0xFFE57373 : 0xFFC0392B; }
    static int bar(Context c)      { return dark(c) ? 0xFF202020 : 0xFF3F8A21; }
    static int barEnd(Context c)   { return bar(c); }
    static int line(Context c)     { return dark(c) ? 0xFF353535 : 0xFFE2E5DF; }
    /** Hinterlegung für Auswahl und kleine Schaltflächen. */
    static int chip(Context c)     { return dark(c) ? 0xFF2F3A28 : 0xFFE6F0DD; }
    /** Einheitliche Farbe für Symbole (keine Regenbogenfarben). */
    static int iconTint(Context c) { return dark(c) ? 0xFFC4C4C4 : 0xFF4A5246; }
    /** Ordner: eine ruhige Farbe für alle Ordner. */
    static int folder(Context c)   { return dark(c) ? 0xFF8FB571 : 0xFF5E8F45; }

    static final int RADIUS = 10;

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Fläche mit dünnem Rand statt Schatten: klare Kanten, ruhiger Eindruck. */
    static GradientDrawable outlined(Context c, int color) {
        GradientDrawable g = round(c, color, RADIUS);
        g.setStroke(Math.max(1, dp(c, 1) / 2 + 1), line(c));
        return g;
    }

    /** Weiße, abgerundete Karte; Kinder werden senkrecht angeordnet. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(outlined(c, surface(c)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView label(Context c, String text, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        // Klare serifenlose Schrift; „medium“ statt fett, das bleibt ruhiger und besser lesbar
        t.setTypeface(android.graphics.Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                android.graphics.Typeface.NORMAL));
        // Etwas mehr Buchstaben- und Zeilenabstand: leichter zu lesen, besonders bei Legasthenie
        t.setLetterSpacing(0.02f);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    /** Abgerundeter Knopf: gefüllt (Hauptaktion) oder umrandet. */
    static Button button(Context c, String text, boolean filled, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 8));
        if (filled) {
            g.setColor(primary(c));
            b.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(c, 1), primary(c));
            b.setTextColor(primary(c));
        }
        b.setBackground(g);
        b.setOnClickListener(l);
        // Mindesthöhe statt fester Höhe: lange Texte brechen um, ohne unten abgeschnitten zu werden
        b.setMinHeight(dp(c, 52));
        b.setMinimumHeight(dp(c, 52));
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        b.setLayoutParams(lp);
        return b;
    }

    static ImageView icon(Context c, int res, int tint, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Quadratische Fläche mit leicht runden Ecken und Symbol darin. */
    static LinearLayout badge(Context c, int res, int bgColor, int iconColor, int sizeDp) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(round(c, bgColor, 8));
        l.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        l.addView(icon(c, res, iconColor, sizeDp / 2));
        return l;
    }

    static String size(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024L * 1024) {
            return (b / 1024) + " KB";
        }
        if (b < 1024L * 1024 * 1024) {
            return String.format(Locale.GERMANY, "%.1f MB", b / 1048576.0);
        }
        return String.format(Locale.GERMANY, "%.2f GB", b / 1073741824.0);
    }

    /** "14:32" für heute, sonst "11.10. 14:32". */
    static String time(long ms) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ms);
        boolean today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(today ? "HH:mm" : "dd.MM. HH:mm", Locale.GERMANY).format(new Date(ms));
    }

    /** Kleine Abschnittsüberschrift. */
    static TextView heading(Context c, String text) {
        TextView t = label(c, text, 13, dim(c), true);
        t.setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8));
        return t;
    }

    /** Zwei gleich breite Karten nebeneinander. */
    static LinearLayout pair(Context c, View a, View b) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        l.rightMargin = dp(c, 4);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        r.leftMargin = dp(c, 4);
        row.addView(a, l);
        row.addView(b, r);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(c, 8);
        row.setLayoutParams(rp);
        return row;
    }

    /** Kompakte Karte: Symbol links, Name und Angabe rechts. */
    static View shortcut(Context c, int icon, int color, String name, String sub, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(outlined(c, surface(c)));
        card.setPadding(dp(c, 14), dp(c, 12), dp(c, 12), dp(c, 12));
        card.addView(icon(c, icon, color, 26));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(c, 14), 0, 0, 0);
        TextView n = label(c, name, 15, text(c), true);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(n);
        TextView s = label(c, sub, 12, dim(c), false);
        s.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(s);
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.setOnClickListener(l);
        return card;
    }

    // ---- Listen in Gruppen ------------------------------------------------------------------

    /** Eine Gruppe von Zeilen auf einer Fläche, durch dünne Linien getrennt (wie in den Systemeinstellungen). */
    static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(outlined(c, surface(c)));
        g.setClipToOutline(true);
        g.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 4);
        g.setLayoutParams(lp);
        return g;
    }

    /** Hängt eine Zeile an die Gruppe; ab der zweiten Zeile mit Trennlinie (eingerückt bis zum Text). */
    static void addRow(Context c, LinearLayout group, View row) {
        if (group.getChildCount() > 0) {
            View div = new View(c);
            div.setBackgroundColor(line(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.max(1, dp(c, 1) / 2 + 1));
            lp.leftMargin = dp(c, 56);
            group.addView(div, lp);
        }
        group.addView(row);
    }

    /** Zeile: Symbol links, Name (und Angabe) daneben, rechts ein Pfeil oder eine Zahl. */
    static LinearLayout row(Context c, int icon, int iconColor, String name, String sub, String trail,
                            View.OnClickListener l) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, 60));
        r.setPadding(dp(c, 16), dp(c, 10), dp(c, 12), dp(c, 10));
        r.setBackground(pressable(c));
        r.addView(icon(c, icon, iconColor, 24));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(c, 16), 0, dp(c, 8), 0);
        TextView n = label(c, name, 15, text(c), true);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        if (sub != null && !sub.isEmpty()) {
            TextView s = label(c, sub, 12, dim(c), false);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(0, dp(c, 2), 0, 0);
            text.addView(s);
        }
        r.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (trail != null && !trail.isEmpty()) {
            r.addView(label(c, trail, 12, dim(c), false));
        }
        if (l != null) {
            ImageView chev = icon(c, R.drawable.ic_chevron, dim(c), 20);
            LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) chev.getLayoutParams();
            cl.leftMargin = dp(c, 4);
            r.addView(chev);
            r.setOnClickListener(l);
        }
        return r;
    }

    /** Hintergrund einer Listenzeile: Fläche (bei Auswahl grün hinterlegt) mit dünner Linie unten, eingerückt bis zum Text. */
    static android.graphics.drawable.Drawable listRowBg(Context c, boolean picked) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(picked ? chip(c) : surface(c));
        GradientDrawable ln = new GradientDrawable();
        ln.setColor(line(c));
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] { fill, ln });
        ld.setLayerHeight(1, Math.max(1, dp(c, 1) / 2 + 1));
        ld.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        ld.setLayerInsetStart(1, dp(c, 76));
        android.graphics.drawable.Drawable press = pressable(c);
        if (press == null) {
            return ld;
        }
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] { ld, press });
    }

    /** Hintergrund, der beim Antippen kurz aufhellt. */
    static android.graphics.drawable.Drawable pressable(Context c) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0) {
            return c.getDrawable(tv.resourceId);
        }
        return null;
    }

    // ---- Dateitypen ---------------------------------------------------------------------------

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Gedeckte Farbe des Etiketts je Dateiart (PDF, Text, Tabelle, Präsentation, Bild, Video, Audio, Archiv, App). */
    static int typeColor(String e) {
        switch (e) {
            case "pdf":
                return 0xFFB8483F;
            case "doc": case "docx": case "odt": case "rtf": case "txt": case "md":
                return 0xFF3F6FAE;
            case "xls": case "xlsx": case "ods": case "csv":
                return 0xFF2F7D4F;
            case "ppt": case "pptx": case "odp":
                return 0xFFBF6A2E;
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "heic": case "bmp": case "svg":
                return 0xFF7A5C9E;
            case "mp4": case "mkv": case "webm": case "3gp": case "mov": case "avi":
                return 0xFF9E4565;
            case "mp3": case "m4a": case "wav": case "ogg": case "flac": case "aac": case "opus":
                return 0xFF2C7F7B;
            case "zip": case "rar": case "7z": case "gz": case "tar": case "xz":
                return 0xFF7D6E48;
            case "apk":
                return 0xFF5E8A33;
            default:
                return 0xFF6B7178;
        }
    }

    /** Ordnersymbol: gleiche Kachelform wie die Dateisymbole, damit die Liste ruhig und einheitlich wirkt. */
    static android.graphics.drawable.Drawable folderIcon(Context c) {
        return new TypeIcon(c, null);
    }

    /** Dateisymbol: klare Kachel mit der Endung in großen Buchstaben (PDF, DOCX …), Farbe nach Dateiart. */
    static android.graphics.drawable.Drawable typeIcon(Context c, String ext) {
        return new TypeIcon(c, ext);
    }

    private static final class TypeIcon extends android.graphics.drawable.Drawable {
        private final String label;   // null = Ordner
        private final int accent;
        private final int fill;
        private final float density;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path glyph;

        TypeIcon(Context c, String ext) {
            boolean night = dark(c);
            int base;
            if (ext == null) {
                label = null;
                base = folder(c);
                glyph = new android.graphics.Path();
                // Ordner (24er-Raster): Reiter links oben, Körper darunter
                glyph.moveTo(3, 7); glyph.lineTo(3, 18); glyph.lineTo(21, 18); glyph.lineTo(21, 9);
                glyph.lineTo(11.5f, 9); glyph.lineTo(9.5f, 6); glyph.lineTo(4, 6); glyph.close();
            } else {
                String e = ext.toUpperCase(Locale.ROOT);
                label = e.isEmpty() ? "?" : e.length() > 4 ? e.substring(0, 4) : e;
                base = typeColor(ext);
                glyph = null;
            }
            // Schrift und Rand in kräftiger Farbe, Fläche nur ganz leicht getönt (hoher Kontrast)
            accent = night ? blend(base, 0xFFFFFFFF, 0.35f) : blend(base, 0xFF000000, 0.15f);
            fill = blend(surface(c), base, night ? 0.22f : 0.12f);
            density = c.getResources().getDisplayMetrics().density;
        }

        private static int blend(int a, int b, float t) {
            int r = Math.round(Color.red(a) * (1 - t) + Color.red(b) * t);
            int g = Math.round(Color.green(a) * (1 - t) + Color.green(b) * t);
            int bl = Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t);
            return Color.rgb(r, g, bl);
        }

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            float side = Math.min(b.width(), b.height());
            float x = b.left + (b.width() - side) / 2f;
            float y = b.top + (b.height() - side) / 2f;
            float stroke = Math.max(1.5f, 1.5f * density);
            float r = side * 0.16f;
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(fill);
            cv.drawRoundRect(x, y, x + side, y + side, r, r, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(accent);
            float h = stroke / 2f;
            cv.drawRoundRect(x + h, y + h, x + side - h, y + side - h, r, r, paint);
            if (glyph != null) {
                cv.save();
                cv.translate(x + side * 0.16f, y + side * 0.16f);
                float k = side * 0.68f / 24f;
                cv.scale(k, k);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(accent);
                cv.drawPath(glyph, paint);
                cv.restore();
                return;
            }
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(accent);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            float ts = side * (label.length() <= 3 ? 0.34f : 0.27f);
            paint.setTextSize(ts);
            float tw = paint.measureText(label);
            if (tw > side * 0.84f) {
                paint.setTextSize(ts * side * 0.84f / tw);
            }
            android.graphics.Paint.FontMetrics fm = paint.getFontMetrics();
            cv.drawText(label, x + side / 2f, y + side / 2f - (fm.ascent + fm.descent) / 2f, paint);
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
            paint.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
