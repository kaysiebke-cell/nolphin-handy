package org.nolphin.handy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Entscheidet, was das Startbildschirm-Widget anzeigt (ohne Android, damit sich die Regeln testen lassen).
 * Wichtigster Grundsatz: Eine Verbindung gilt nur dann als aktiv, wenn gespeicherte Verbindungsdaten vorhanden sind
 * <b>und</b> der Synchronisationsdienst in diesem Prozess tatsächlich läuft; Übertragungen werden nur gezeigt, solange
 * ihr Zustand frisch ist. Nach einem Prozessende bleibt so nichts fälschlich „verbunden“ oder „lädt“.
 */
final class WidgetModel {
    private WidgetModel() {}

    enum Mode {
        /** keine Verbindungsdaten gespeichert */
        NOT_CONNECTED,
        /** Verbindungsdaten gespeichert, der Dienst läuft aber nicht (zum Beispiel nach einem Neustart des Handys) */
        STOPPED,
        /** Dienst läuft, der PC antwortet nicht oder der Schlüssel ist ungültig */
        ERROR,
        /** Dienst läuft und der PC ist erreichbar */
        CONNECTED
    }

    /** Größtes Alter in Millisekunden, bis eine Übertragung als „nicht mehr aktuell“ gilt. */
    static final long TRANSFER_FRESH_MS = 20_000;

    /** Ein Eintrag aus dem Verlauf, schon für die Anzeige aufbereitet. */
    static final class Hist {
        final String name;
        final boolean down;
        final boolean ok;
        final String when;

        Hist(String name, boolean down, boolean ok, String when) {
            this.name = name;
            this.down = down;
            this.ok = ok;
            this.when = when;
        }
    }

    static final class Input {
        boolean saved;            // Verbindungsdaten vorhanden (Conn.connected)
        boolean running;          // Synchronisationsdienst läuft in diesem Prozess
        String status = "";       // SyncService.status
        String pcName = "";
        String transferName = ""; // leer = keine Übertragung
        boolean transferDown;
        long transferDone;
        long transferTotal;       // 0 = unbekannt
        long stateTime;           // wann der Dienst den Zustand zuletzt geändert hat
        long now;
        List<RemoteFile> files = new ArrayList<>();
        List<Hist> history = new ArrayList<>();   // neueste zuerst
    }

    static boolean isErrorStatus(String status) {
        return status != null && (status.startsWith("PC nicht erreichbar") || status.startsWith("Schlüssel"));
    }

    static Mode mode(Input in) {
        if (!in.saved) {
            return Mode.NOT_CONNECTED;
        }
        if (!in.running) {
            return Mode.STOPPED;
        }
        return isErrorStatus(in.status) ? Mode.ERROR : Mode.CONNECTED;
    }

    /** Läuft gerade eine Übertragung, deren Zustand noch aktuell ist? */
    static boolean transferActive(Input in) {
        if (!in.running || in.transferName == null || in.transferName.isEmpty()) {
            return false;
        }
        return in.now - in.stateTime <= TRANSFER_FRESH_MS;
    }

    /** Fortschritt in Prozent (0 bis 100) oder -1, wenn die Gesamtgröße unbekannt ist. */
    static int percent(long done, long total) {
        if (total <= 0) {
            return -1;
        }
        long p = done * 100 / total;
        return (int) Math.max(0, Math.min(100, p));
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

    /** "0,5 MB von 1,1 MB" oder, wenn die Gesamtgröße unbekannt ist, nur die bisherige Menge. */
    static String amount(long done, long total) {
        return total > 0 ? size(done) + " von " + size(total) : size(done);
    }

    static String title(Input in) {
        switch (mode(in)) {
            case CONNECTED:
                return "Verbunden";
            case ERROR:
                return "Verbindung gestört";
            case STOPPED:
                return "Nicht aktiv";
            default:
                return "Nicht verbunden";
        }
    }

    /** Zweite Zeile: PC-Name, Fehler oder Hinweis. */
    static String subtitle(Input in) {
        String pc = in.pcName == null || in.pcName.isEmpty() ? "PC" : in.pcName;
        switch (mode(in)) {
            case CONNECTED:
                return "PC: " + pc;
            case ERROR:
                return in.status;
            case STOPPED:
                return "Verbindung gespeichert, Dienst aus";
            default:
                return "Mit dem PC verbinden";
        }
    }

    /** Wie viele Dateien das Widget je nach Höhe (in dp) zeigt: kompakt keine, sonst bis zu drei. */
    static int fileRows(int heightDp) {
        if (heightDp < 180) {
            return 0;
        }
        return heightDp < 250 ? 2 : 3;
    }

    /** Ob das erweiterte Layout benutzt wird. */
    static boolean large(int heightDp, int widthDp) {
        return heightDp >= 180 && widthDp >= 180;
    }

    /** Die Dateien, die im Widget stehen: nur wenn verbunden, höchstens {@code rows}. */
    static List<RemoteFile> shownFiles(Input in, int rows) {
        List<RemoteFile> out = new ArrayList<>();
        if (mode(in) != Mode.CONNECTED) {
            return out;
        }
        for (RemoteFile f : in.files) {
            if (out.size() >= rows) {
                break;
            }
            out.add(f);
        }
        return out;
    }

    /**
     * Wie im Entwurf: Stehen Dateien vom PC bereit, zeigt das erweiterte Widget sie (auch während einer Übertragung);
     * sonst die letzten Einträge aus dem Verlauf unter „Zuletzt“.
     */
    static boolean showFiles(Input in, int rows) {
        return !shownFiles(in, rows).isEmpty();
    }

    static List<Hist> shownHistory(Input in, int rows) {
        List<Hist> out = new ArrayList<>();
        if (showFiles(in, rows)) {
            return out;
        }
        for (Hist h : in.history) {
            if (out.size() >= rows) {
                break;
            }
            out.add(h);
        }
        return out;
    }

    /** Überschrift über der Liste: „Bereit am PC (n)“ oder „Zuletzt“. */
    static String sectionTitle(Input in, int rows) {
        return showFiles(in, rows) ? "Bereit am PC (" + in.files.size() + ")" : "Zuletzt";
    }

    /** Rechte Spalte eines Verlaufseintrags: die Uhrzeit, sonst der gespeicherte Grund („abgebrochen“, „fehlgeschlagen“). */
    static String when(boolean ok, String note, String time) {
        if (ok) {
            return time;
        }
        return note == null || note.trim().isEmpty() ? "fehlgeschlagen" : note.trim();
    }

    /** Welche Schaltfläche das kleine Widget zeigt. */
    enum SmallAction { SEND, SCAN, CANCEL, CONNECT }

    static SmallAction smallAction(Input in) {
        if (transferActive(in)) {
            return SmallAction.CANCEL;
        }
        switch (mode(in)) {
            case CONNECTED:
                return SmallAction.SEND;
            case NOT_CONNECTED:
                return SmallAction.SCAN;
            default:
                return SmallAction.CONNECT;   // gestoppt oder gestört: die App öffnen, sie verbindet neu
        }
    }

    /** Erste Zeile des kleinen Widgets: bei einer Übertragung Name und Prozent, sonst der Zustand. */
    static String smallTitle(Input in) {
        if (!transferActive(in)) {
            return title(in);
        }
        int pct = percent(in.transferDone, in.transferTotal);
        return (in.transferDown ? "Lade " : "Sende ") + in.transferName + (pct >= 0 ? " · " + pct + " %" : "");
    }

    /** Zweite Zeile: bei einer Übertragung die Menge, sonst PC-Name oder Hinweis. */
    static String smallSubtitle(Input in) {
        if (transferActive(in)) {
            return amount(in.transferDone, in.transferTotal);
        }
        return mode(in) == Mode.STOPPED ? "Dienst aus" : subtitle(in);   // die kleine Zeile teilt sich den Platz mit der Schaltfläche
    }

    /** Anzahl weiterer Dateien, die nicht im Widget stehen (für „Alle anzeigen (n)“). */
    static int hiddenFiles(Input in, int rows) {
        return mode(in) == Mode.CONNECTED ? Math.max(0, in.files.size() - rows) : 0;
    }
}
