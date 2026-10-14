package org.nolphin.handy;

/**
 * Was die Verbindungskarte der Startseite zeigt (ohne Android, damit sich die Regeln testen lassen): ein Umschalter
 * WLAN | Kabel, der den Weg anzeigt, ein Hauptknopf und eine kleine Zeile für den Link. Der Umschalter springt von selbst
 * auf „Kabel“, sobald ein Kabelnetz da ist oder die Verbindung darüber läuft.
 */
final class ConnCard {
    private ConnCard() {}

    enum Way { WIFI, CABLE }

    enum Main { SCAN, TETHERING, DISCONNECT }

    static final class View {
        final Way way;
        final boolean connected;
        final boolean problem;
        final Main main;
        final boolean linkLine;
        final boolean steps;
        final String subtitle;

        View(Way way, boolean connected, boolean problem, Main main, boolean linkLine, boolean steps, String subtitle) {
            this.way = way;
            this.connected = connected;
            this.problem = problem;
            this.main = main;
            this.linkLine = linkLine;
            this.steps = steps;
            this.subtitle = subtitle;
        }
    }

    /**
     * @param cable     Zustand des Kabelnetzes (siehe {@link CableNet})
     * @param connected gespeicherte Verbindung vorhanden
     * @param problem   der Dienst meldet „PC nicht erreichbar“ oder „Schlüssel ungültig“
     * @param pcName    Name des PCs oder leer
     */
    static View of(CableNet.State cable, boolean connected, boolean problem, String pcName) {
        boolean viaCable = cable == CableNet.State.CONNECTED_VIA_CABLE;
        boolean cableWay = viaCable || (!connected && (cable == CableNet.State.CABLE_READY
                || cable == CableNet.State.CABLE_PLUGGED_NO_NET));
        Way way = cableWay ? Way.CABLE : Way.WIFI;
        if (connected) {
            String pc = pcName == null || pcName.isEmpty() ? "PC" : pcName;
            return new View(way, true, problem, Main.DISCONNECT, false, false, problem ? "PC nicht erreichbar" : "PC: " + pc);
        }
        if (cable == CableNet.State.CABLE_PLUGGED_NO_NET) {
            return new View(way, false, false, Main.TETHERING, true, true, "Kabel steckt, Tethering ist aus");
        }
        if (cable == CableNet.State.CABLE_READY) {
            return new View(way, false, false, Main.SCAN, true, true, "USB-Netz zum PC steht bereit");
        }
        return new View(way, false, false, Main.SCAN, true, true, "Mit dem PC verbinden");
    }

    /** Beschriftung des Hauptknopfs. */
    static String mainLabel(Main m) {
        switch (m) {
            case TETHERING:
                return "USB-Tethering einschalten";
            case DISCONNECT:
                return "Verbindung trennen";
            default:
                return "QR-Code scannen";
        }
    }
}
