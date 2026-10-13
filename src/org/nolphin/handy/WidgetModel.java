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
        String lastLine = "";     // letzter Eintrag aus dem Verlauf, schon formatiert
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
                return "Verbindung zu " + pc + " gespeichert, Dienst gestoppt";
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

    /** Anzahl weiterer Dateien, die nicht im Widget stehen (für „Alle anzeigen (n)“). */
    static int hiddenFiles(Input in, int rows) {
        return mode(in) == Mode.CONNECTED ? Math.max(0, in.files.size() - rows) : 0;
    }
}
