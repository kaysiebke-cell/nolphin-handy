package org.nolphin.handy;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Verbindet die App mit dem PC: Link lesen, Adresse und Schlüssel am PC prüfen, erst dann speichern. */
final class Connector {
    private Connector() {}

    interface Listener {
        void onSuccess(Link link);

        void onFailure(String message);
    }

    /** Läuft im Hintergrund; die Rückmeldung kommt im Hauptthread. */
    static void connect(final Context context, final String text, final Listener listener) {
        final Handler main = new Handler(Looper.getMainLooper());
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String failure = null;
                Link link = null;
                try {
                    link = Link.parse(text, System.currentTimeMillis() / 1000);
                    PcCheck.Result r = PcCheck.check(link);
                    if (!r.ok) {
                        failure = r.message;
                    }
                } catch (Link.Invalid e) {
                    failure = e.getMessage();
                }
                final String msg = failure;
                final Link ok = link;
                if (msg == null) {
                    Conn.save(app, ok);
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (msg == null) {
                            listener.onSuccess(ok);
                        } else {
                            listener.onFailure(msg);
                        }
                    }
                });
            }
        }, "nolphin-connect").start();
    }
}
