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
    static int bg(Context c)       { return dark(c) ? 0xFF161815 : 0xFFF4F6F2; }
    static int surface(Context c)  { return dark(c) ? 0xFF1F211D : 0xFFFFFFFF; }
    static int text(Context c)     { return dark(c) ? 0xFFF2F5EE : 0xFF1C211A; }
    static int dim(Context c)      { return dark(c) ? 0xFFB9C4B2 : 0xFF566052; }
    static int primary(Context c)  { return dark(c) ? 0xFF7ED648 : 0xFF3F8A21; }
    static int accent(Context c)   { return primary(c); }
    static int danger(Context c)   { return dark(c) ? 0xFFE57373 : 0xFFC0392B; }
    static int bar(Context c)      { return dark(c) ? 0xFF2E7D18 : 0xFF3F8A21; }
    /** Leiste bei Auswahl: etwas kräftigeres Grün. */
    static int selBar(Context c)   { return dark(c) ? 0xFF245F12 : 0xFF2F6E18; }
    static int barEnd(Context c)   { return bar(c); }
    static int line(Context c)     { return dark(c) ? 0xFF33382F : 0xFFD9DDD5; }
    /** Hinterlegung für Auswahl und kleine Schaltflächen. */
    static int chip(Context c)     { return dark(c) ? 0xFF24421A : 0xFFE3EFD9; }
    /** Einheitliche Farbe für Symbole (keine Regenbogenfarben). */
    static int iconTint(Context c) { return dark(c) ? 0xFFD0DAC8 : 0xFF4D5649; }
    /** Ordner: eine ruhige Farbe für alle Ordner. */
    static int folder(Context c)   { return dark(c) ? 0xFF86C854 : 0xFF6AA84F; }

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

    /** Kleiner Knopf für Leisten: immer gleiche Höhe, Form und Mindestbreite; gefüllt (Hauptaktion) oder umrandet. */
    static Button actionButton(Context c, String text, boolean filled, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setSingleLine(true);
        b.setMinWidth(dp(c, 112));
        b.setMinimumWidth(dp(c, 112));
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 8));
        if (filled) {
            g.setColor(primary(c));
            b.setTextColor(dark(c) ? 0xFF10200A : Color.WHITE);
        } else {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(c, 1) + dp(c, 1) / 2, primary(c));
            b.setTextColor(primary(c));
        }
        b.setBackground(g);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 44)));
        return b;
    }

    /** Schalter im Mint-Grün der App statt in der Systemfarbe. */
    static void tintSwitch(Context c, android.widget.Switch sw) {
        int on = primary(c);
        int off = dark(c) ? 0xFF8A9484 : 0xFF7A8575;
        int[][] states = { { android.R.attr.state_checked }, {} };
        sw.setThumbTintList(new android.content.res.ColorStateList(states, new int[] { on, off }));
        sw.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[] { (on & 0x00FFFFFF) | 0x66000000, (off & 0x00FFFFFF) | 0x55000000 }));
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
        ld.setLayerInsetStart(1, dp(c, 64));
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

    /** Ordnersymbol: einfarbig in Mint-Grün. */
    static android.graphics.drawable.Drawable folderIcon(Context c) {
        return tinted(c, R.drawable.ic_folder, folder(c));
    }

    /** Dateisymbol: einfarbiges Linien-Symbol nach Dateiart (die Endung steht im Namen). */
    static android.graphics.drawable.Drawable typeIcon(Context c, String ext) {
        int res;
        switch (ext) {
            case "pdf":
                res = R.drawable.ic_pdf;
                break;
            case "doc": case "docx": case "odt": case "rtf": case "txt": case "md": case "odp": case "ppt": case "pptx":
                res = R.drawable.ic_doc;
                break;
            case "xls": case "xlsx": case "ods": case "csv":
                res = R.drawable.ic_table;
                break;
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "heic": case "bmp": case "svg":
                res = R.drawable.ic_image;
                break;
            case "mp4": case "mkv": case "webm": case "3gp": case "mov": case "avi":
                res = R.drawable.ic_video;
                break;
            case "mp3": case "m4a": case "wav": case "ogg": case "flac": case "aac": case "opus":
                res = R.drawable.ic_audio;
                break;
            case "zip": case "rar": case "7z": case "gz": case "tar": case "xz":
                res = R.drawable.ic_zip;
                break;
            case "step": case "stp": case "stl": case "obj": case "3mf": case "dxf": case "dwg": case "iges": case "igs":
                res = R.drawable.ic_box;
                break;
            case "apk":
                res = R.drawable.ic_apps;
                break;
            default:
                res = R.drawable.ic_file;
        }
        return tinted(c, res, iconTint(c));
    }

    private static android.graphics.drawable.Drawable tinted(Context c, int res, int color) {
        android.graphics.drawable.Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    // ---- Menü ---------------------------------------------------------------------------------

    interface MenuPick {
        void on(int index);
    }

    /** Menü im Stil der App (statt des Standard-Menüfensters des Systems): Karte mit Rand, große Zeilen, am Knopf ausgerichtet. */
    static void popupMenu(final Context c, View anchor, String[] items, final MenuPick pick) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(outlined(c, surface(c)));
        box.setPadding(0, dp(c, 6), 0, dp(c, 6));
        final android.widget.PopupWindow pw = new android.widget.PopupWindow(box, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(c, 8));
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = label(c, items[i], 16, text(c), false);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinHeight(dp(c, 48));
            t.setMinWidth(dp(c, 200));
            t.setPadding(dp(c, 20), 0, dp(c, 24), 0);
            t.setBackground(pressable(c));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pw.dismiss();
                    pick.on(idx);
                }
            });
            box.addView(t);
        }
        pw.showAsDropDown(anchor, 0, -dp(c, 4), Gravity.END);
    }
}
