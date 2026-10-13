package org.nolphin.handy;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Startbildschirm-Widget: zeigt Verbindung, Übertragung und Dateien vom PC und startet die vorhandenen Funktionen
 * (App öffnen, QR-Scanner, Trennen, Laden, Abbrechen, Senden). Es öffnet selbst keine Netzwerkverbindung: Es liest
 * nur den Zustand des {@link SyncService} und die gespeicherten Daten. Die Aktionen laufen über {@link WidgetActions}.
 */
public class NolphinWidget extends AppWidgetProvider {
    static final String ACTION_PICK = "org.nolphin.handy.action.PICK";
    static final String ACTION_FROM_PC = "org.nolphin.handy.action.FROM_PC";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            update(c, m, id);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        update(c, m, id);   // Größe geändert: Layout neu wählen
    }

    /** Aktualisiert alle Widgets (kompakt und erweitert) aus dem vorhandenen Zustand (schnell, ohne Netzwerk). */
    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (Class<?> k : new Class<?>[] { NolphinWidget.class, NolphinWidgetLarge.class }) {
                for (int id : m.getAppWidgetIds(new ComponentName(c, k))) {
                    update(c, m, id);
                }
            }
        } catch (RuntimeException ignored) {
            // das Widget darf nie die App oder den Dienst zum Absturz bringen
        }
    }

    private static WidgetModel.Input input(Context c) {
        WidgetModel.Input in = new WidgetModel.Input();
        in.saved = Conn.connected(c);
        in.running = SyncService.running;
        in.status = SyncService.status;
        in.pcName = Conn.pcName(c);
        in.transferName = SyncService.transferName;
        in.transferDown = SyncService.transferDown;
        in.transferDone = SyncService.transferDone;
        in.transferTotal = SyncService.transferTotal;
        in.stateTime = SyncService.stateTime;
        in.now = System.currentTimeMillis();
        RemoteFile[] files = SyncService.files;
        for (RemoteFile f : files) {
            in.files.add(f);
        }
        List<Store.Entry> h = Store.history(c);
        for (int i = 0; i < h.size() && i < 3; i++) {
            Store.Entry e = h.get(i);
            in.history.add(new WidgetModel.Hist(e.name, e.down, e.ok, e.ok ? Ui.time(e.time) : "fehlgeschlagen"));
        }
        return in;
    }

    private static void update(Context c, AppWidgetManager m, int id) {
        try {
            Bundle o = m.getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            android.appwidget.AppWidgetProviderInfo info = m.getAppWidgetInfo(id);
            boolean large = info != null && info.provider != null
                    && info.provider.getClassName().endsWith("NolphinWidgetLarge");
            m.updateAppWidget(id, build(c, input(c), w, h, large));
        } catch (RuntimeException ignored) {
        }
    }

    private static PendingIntent activity(Context c, int code, String action) {
        Intent i = new Intent(c, MainActivity.class).setAction(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent broadcast(Context c, int code, String action, RemoteFile f) {
        Intent i = new Intent(c, WidgetActions.class).setAction(action);
        if (f != null) {
            i.putExtra("name", f.name).putExtra("size", f.size).putExtra("dir", f.dir);
        }
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static final int[] BTN = { R.id.w_b1, R.id.w_b2, R.id.w_b3 };
    private static final int[] BTN_I = { R.id.w_b1_i, R.id.w_b2_i, R.id.w_b3_i };
    private static final int[] BTN_T = { R.id.w_b1_t, R.id.w_b2_t, R.id.w_b3_t };
    private static final int[] ROW = { R.id.w_f1, R.id.w_f2, R.id.w_f3 };
    private static final int[] ROW_I = { R.id.w_f1_i, R.id.w_f2_i, R.id.w_f3_i };
    private static final int[] ROW_N = { R.id.w_f1_n, R.id.w_f2_n, R.id.w_f3_n };
    private static final int[] ROW_S = { R.id.w_f1_s, R.id.w_f2_s, R.id.w_f3_s };
    private static final int[] ROW_L = { R.id.w_f1_l, R.id.w_f2_l, R.id.w_f3_l };
    private static final int[] HROW = { R.id.w_h1, R.id.w_h2, R.id.w_h3 };
    private static final int[] HROW_I = { R.id.w_h1_i, R.id.w_h2_i, R.id.w_h3_i };
    private static final int[] HROW_N = { R.id.w_h1_n, R.id.w_h2_n, R.id.w_h3_n };
    private static final int[] HROW_W = { R.id.w_h1_w, R.id.w_h2_w, R.id.w_h3_w };

    private static void button(Context c, RemoteViews v, int slot, boolean show, boolean primary, int icon, String text, PendingIntent pi) {
        v.setViewVisibility(BTN[slot], show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!show) {
            return;
        }
        int fg = primary ? c.getColor(R.color.widget_onacc) : c.getColor(R.color.widget_acc);
        v.setInt(BTN[slot], "setBackgroundResource", primary ? R.drawable.widget_btn_fill : R.drawable.widget_btn);
        v.setViewVisibility(BTN_I[slot], icon == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
        if (icon != 0) {
            v.setImageViewResource(BTN_I[slot], icon);
            v.setInt(BTN_I[slot], "setColorFilter", fg);
        }
        v.setTextViewText(BTN_T[slot], text);
        v.setTextColor(BTN_T[slot], fg);
        v.setOnClickPendingIntent(BTN[slot], pi);
    }

    /** @param large true für das erweiterte Widget (Übertragung, Dateien vom PC), false für das kompakte */
    static RemoteViews build(Context c, WidgetModel.Input in, int widthDp, int heightDp, boolean large) {
        RemoteViews v = new RemoteViews(c.getPackageName(), large ? R.layout.widget_large : R.layout.widget_compact);
        WidgetModel.Mode mode = WidgetModel.mode(in);
        int text = c.getColor(R.color.widget_text);
        int dim = c.getColor(R.color.widget_dim);
        int acc = c.getColor(R.color.widget_acc);
        int err = c.getColor(R.color.widget_err);

        // Kopf: Zustand
        boolean ok = mode == WidgetModel.Mode.CONNECTED;
        v.setImageViewResource(R.id.w_icon, ok ? R.drawable.w_phone : R.drawable.w_phone_off);
        v.setInt(R.id.w_icon, "setColorFilter", ok ? acc : dim);
        v.setTextViewText(R.id.w_title, WidgetModel.title(in));
        v.setTextColor(R.id.w_title, mode == WidgetModel.Mode.ERROR ? err : text);
        v.setTextViewText(R.id.w_sub, WidgetModel.subtitle(in));
        v.setTextColor(R.id.w_sub, mode == WidgetModel.Mode.ERROR ? err : dim);
        v.setInt(R.id.w_dot, "setColorFilter", ok ? acc : mode == WidgetModel.Mode.STOPPED ? dim : err);
        v.setOnClickPendingIntent(R.id.w_root, activity(c, 1, Intent.ACTION_MAIN));

        // Übertragung
        boolean active = WidgetModel.transferActive(in);
        v.setViewVisibility(R.id.w_tr, active ? android.view.View.VISIBLE : android.view.View.GONE);
        if (active) {
            int pct = WidgetModel.percent(in.transferDone, in.transferTotal);
            v.setTextViewText(R.id.w_tr_name, (in.transferDown ? "Lade " : "Sende ") + in.transferName);
            v.setTextColor(R.id.w_tr_name, text);
            v.setTextViewText(R.id.w_tr_pct, pct >= 0 ? pct + " %" : "");
            v.setTextColor(R.id.w_tr_pct, text);
            v.setProgressBar(R.id.w_tr_bar, 100, Math.max(pct, 0), pct < 0);
            v.setTextViewText(R.id.w_tr_amount, WidgetModel.amount(in.transferDone, in.transferTotal));
            v.setTextColor(R.id.w_tr_amount, dim);
            v.setTextColor(R.id.w_tr_cancel, acc);
            v.setOnClickPendingIntent(R.id.w_tr_cancel, broadcast(c, 4, WidgetActions.ACTION_CANCEL, null));
        }

        // Dateien vom PC oder Verlauf (nur erweitertes Layout), wie im Entwurf auch während einer Übertragung
        if (large) {
            int rows = WidgetModel.fileRows(Math.max(heightDp, 180));
            List<RemoteFile> shown = WidgetModel.shownFiles(in, rows);
            List<WidgetModel.Hist> hist = WidgetModel.shownHistory(in, rows);
            boolean any = !shown.isEmpty() || !hist.isEmpty();
            v.setViewVisibility(R.id.w_sec, any ? android.view.View.VISIBLE : android.view.View.GONE);
            if (any) {
                v.setTextViewText(R.id.w_sec, WidgetModel.sectionTitle(in, rows));
                v.setTextColor(R.id.w_sec, dim);
            }
            for (int i = 0; i < ROW.length; i++) {
                boolean on = i < shown.size();
                v.setViewVisibility(ROW[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
                if (!on) {
                    continue;
                }
                RemoteFile f = shown.get(i);
                v.setImageViewResource(ROW_I[i], iconOf(f.name));
                v.setInt(ROW_I[i], "setColorFilter", acc);
                v.setTextViewText(ROW_N[i], f.name);
                v.setTextColor(ROW_N[i], text);
                v.setTextViewText(ROW_S[i], WidgetModel.size(f.size));
                v.setTextColor(ROW_S[i], dim);
                v.setTextColor(ROW_L[i], acc);
                v.setOnClickPendingIntent(ROW_L[i], broadcast(c, 10 + i, WidgetActions.ACTION_FETCH, f));
            }
            for (int i = 0; i < HROW.length; i++) {
                boolean on = i < hist.size();
                v.setViewVisibility(HROW[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
                if (!on) {
                    continue;
                }
                WidgetModel.Hist e = hist.get(i);
                v.setImageViewResource(HROW_I[i], e.down ? R.drawable.w_download : R.drawable.w_upload);
                v.setInt(HROW_I[i], "setColorFilter", e.ok ? acc : err);
                v.setTextViewText(HROW_N[i], e.name);
                v.setTextColor(HROW_N[i], text);
                v.setTextViewText(HROW_W[i], e.when);
                v.setTextColor(HROW_W[i], e.ok ? dim : err);
            }
        }

        // Schaltflächen je nach Zustand
        PendingIntent open = activity(c, 1, Intent.ACTION_MAIN);
        PendingIntent scan = activity(c, 2, MainActivity.ACTION_SCAN);
        PendingIntent send = activity(c, 5, ACTION_PICK);
        PendingIntent all = activity(c, 6, ACTION_FROM_PC);
        PendingIntent off = broadcast(c, 3, WidgetActions.ACTION_DISCONNECT, null);
        switch (mode) {
            case CONNECTED:
                if (large) {   // wie im Entwurf: zwei Schaltflächen ohne Symbole
                    button(c, v, 0, true, true, 0, "Alle anzeigen", all);
                    button(c, v, 1, true, false, 0, "Dateien senden", send);
                    button(c, v, 2, false, false, 0, "", open);
                } else {
                    button(c, v, 0, true, true, R.drawable.w_folder_open, "Öffnen", open);
                    button(c, v, 1, true, false, R.drawable.w_upload, "Senden", send);
                    button(c, v, 2, true, false, R.drawable.w_plug_off, "Trennen", off);
                }
                break;
            case ERROR:
                button(c, v, 0, true, true, large ? 0 : R.drawable.ic_refresh, "Verbinden", open);
                button(c, v, 1, true, false, large ? 0 : R.drawable.ic_scan, "QR-Code scannen", scan);
                button(c, v, 2, !large, false, R.drawable.w_plug_off, "Trennen", off);
                break;
            case STOPPED:
                button(c, v, 0, true, true, large ? 0 : R.drawable.ic_refresh, "Verbinden", open);
                button(c, v, 1, true, false, large ? 0 : R.drawable.ic_scan, "QR-Code scannen", scan);
                button(c, v, 2, false, false, 0, "", open);
                break;
            default:
                button(c, v, 0, true, true, large ? 0 : R.drawable.ic_scan, "QR-Code scannen", scan);
                button(c, v, 1, true, false, large ? 0 : R.drawable.w_folder_open, "Öffnen", open);
                button(c, v, 2, false, false, 0, "", open);
                break;
        }
        return v;
    }

    private static int iconOf(String name) {
        String x = Ui.ext(name);
        switch (x) {
            case "pdf": return R.drawable.ic_pdf;
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "heic": return R.drawable.ic_image;
            case "mp4": case "mkv": case "webm": case "3gp": case "mov": return R.drawable.ic_video;
            case "mp3": case "m4a": case "wav": case "ogg": case "flac": case "opus": return R.drawable.ic_audio;
            case "zip": case "rar": case "7z": case "gz": return R.drawable.ic_zip;
            case "xls": case "xlsx": case "ods": case "csv": return R.drawable.ic_table;
            case "doc": case "docx": case "odt": case "txt": case "md": case "rtf": return R.drawable.ic_doc;
            default: return R.drawable.ic_file;
        }
    }
}
