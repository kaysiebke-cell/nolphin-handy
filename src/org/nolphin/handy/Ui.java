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

    static int bg(Context c)       { return dark(c) ? 0xFF1E1E1E : 0xFFF2F5F0; }
    static int surface(Context c)  { return dark(c) ? 0xFF2B2B2B : 0xFFFFFFFF; }
    static int text(Context c)     { return dark(c) ? 0xFFEAEAEA : 0xFF1F2A1B; }
    static int dim(Context c)      { return dark(c) ? 0xFFA3A3A3 : 0xFF5C6B55; }
    static int primary(Context c)  { return dark(c) ? 0xFF87CF3E : 0xFF3F8A21; }
    static int accent(Context c)   { return dark(c) ? 0xFF87CF3E : 0xFF3F8A21; }
    static int danger(Context c)   { return dark(c) ? 0xFFE57373 : 0xFFC0392B; }
    static int bar(Context c)      { return dark(c) ? 0xFF262626 : 0xFF3F8A21; }
    static int barEnd(Context c)   { return bar(c); }
    static int line(Context c)     { return dark(c) ? 0xFF3A3A3A : 0xFFE0E7DB; }
    static int chip(Context c)     { return dark(c) ? 0xFF33402A : 0xFFE4F0D8; }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Weiße, abgerundete Karte; Kinder werden senkrecht angeordnet. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, surface(c), 16));
        l.setElevation(dp(c, 2));
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
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
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
        g.setCornerRadius(dp(c, 24));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 48));
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

    /** Runde Fläche mit Symbol darin. */
    static LinearLayout badge(Context c, int res, int bgColor, int iconColor, int sizeDp) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bgColor);
        l.setBackground(g);
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
        t.setPadding(dp(c, 4), dp(c, 14), 0, dp(c, 6));
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
        card.setBackground(round(c, surface(c), 14));
        card.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        card.addView(badge(c, icon, chip(c), color, 44));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(c, 10), 0, 0, 0);
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
}
