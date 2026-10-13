package org.nolphin.handy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Nimmt die Schaltflächen des Widgets entgegen, die den Dienst steuern (Trennen, Abbrechen, Laden). Nicht exportiert:
 * Nur das Widget selbst kann sie über seine PendingIntents auslösen, keine andere App.
 */
public final class WidgetActions extends BroadcastReceiver {
    static final String ACTION_DISCONNECT = "org.nolphin.handy.widget.DISCONNECT";
    static final String ACTION_CANCEL = "org.nolphin.handy.widget.CANCEL";
    static final String ACTION_FETCH = "org.nolphin.handy.widget.FETCH";

    @Override
    public void onReceive(Context c, Intent in) {
        String a = in == null ? null : in.getAction();
        if (a == null) {
            return;
        }
        try {
            if (ACTION_DISCONNECT.equals(a)) {
                Conn.clear(c);   // aktualisiert das Widget
                if (SyncService.running) {
                    c.startService(new Intent(c, SyncService.class).setAction(SyncService.ACTION_STOP));
                }
            } else if (ACTION_CANCEL.equals(a)) {
                if (SyncService.running) {
                    c.startService(new Intent(c, SyncService.class).setAction(SyncService.ACTION_CANCEL));
                }
            } else if (ACTION_FETCH.equals(a)) {
                String name = in.getStringExtra("name");
                if (name == null || !Conn.connected(c) || !SyncService.running) {
                    Toast.makeText(c, "Nicht verbunden. Bitte erst Nolphin Handy öffnen.", Toast.LENGTH_LONG).show();
                    return;
                }
                c.startService(new Intent(c, SyncService.class).setAction(SyncService.ACTION_FETCH)
                        .putExtra("name", name).putExtra("size", in.getLongExtra("size", -1))
                        .putExtra("dir", in.getStringExtra("dir")));
            }
        } catch (RuntimeException e) {
            Toast.makeText(c, "Das geht gerade nicht. Bitte Nolphin Handy öffnen.", Toast.LENGTH_LONG).show();
        }
        NolphinWidget.refresh(c);
    }
}
