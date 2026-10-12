package org.nolphin.handy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stapelumbenennung mit Vorschau (nur Java, ohne Android). Erst wird ein {@link Plan} berechnet, der alle neuen Namen
 * und alle Probleme zeigt. Umbenannt wird nur, wenn der Plan keine Probleme hat; Tauschfälle (a wird b, b wird a)
 * werden über Zwischennamen gelöst, und bei einem Fehler wird alles Bisherige zurückgenommen.
 */
final class BatchRename {
    private BatchRename() {}

    enum Mode { REPLACE, NUMBER, AFFIX, CASE }

    enum CaseMode { LOWER, UPPER, FIRST }

    /** Die Einstellungen des Nutzers; nicht benutzte Felder bleiben leer. */
    static final class Options {
        Mode mode = Mode.REPLACE;
        String find = "";
        String replacement = "";
        String prefix = "";        // bei NUMBER der feste Anfang, bei AFFIX der Text vorn
        String suffix = "";        // bei AFFIX der Text hinten (vor der Endung)
        int start = 1;
        int digits = 3;
        CaseMode caseMode = CaseMode.LOWER;
    }

    /** Eine Zeile der Vorschau. */
    static final class Item {
        final File file;
        final String newName;
        String problem;   // null: in Ordnung

        Item(File file, String newName) {
            this.file = file;
            this.newName = newName;
        }

        boolean changes() {
            return !file.getName().equals(newName);
        }
    }

    static final class Plan {
        final List<Item> items = new ArrayList<>();

        boolean hasProblem() {
            for (Item i : items) {
                if (i.problem != null) {
                    return true;
                }
            }
            return false;
        }

        int changes() {
            int n = 0;
            for (Item i : items) {
                if (i.changes()) {
                    n++;
                }
            }
            return n;
        }

        /** Gibt es etwas Sinnvolles zu tun? (kein Problem und mindestens ein neuer Name) */
        boolean ready() {
            return !hasProblem() && changes() > 0;
        }

        /** Das erste Problem als Text für die Anzeige, oder null. */
        String firstProblem() {
            for (Item i : items) {
                if (i.problem != null) {
                    return i.problem;
                }
            }
            return null;
        }
    }

    static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    /** Der neue Name zu einem alten; Endungen bleiben bei allen Arten außer "Ersetzen" erhalten. */
    static String newName(String old, Options o, int index) {
        switch (o.mode) {
            case NUMBER: {
                StringBuilder num = new StringBuilder(Integer.toString(o.start + index));
                while (num.length() < Math.max(1, o.digits)) {
                    num.insert(0, '0');
                }
                return o.prefix + num + ext(old);
            }
            case AFFIX:
                return o.prefix + stem(old) + o.suffix + ext(old);
            case CASE: {
                String s = stem(old);
                String e = ext(old);
                switch (o.caseMode) {
                    case UPPER:
                        return s.toUpperCase(Locale.ROOT) + e;
                    case FIRST:
                        return s.isEmpty() ? old : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT) + e;
                    default:
                        return s.toLowerCase(Locale.ROOT) + e;
                }
            }
            default:
                return o.find.isEmpty() ? old : old.replace(o.find, o.replacement);
        }
    }

    /** Gibt es im Ordner schon einen Eintrag mit diesem Namen, ohne Rücksicht auf Groß- und Kleinschreibung (wie auf FAT/exFAT)? */
    private static boolean existsIgnoreCase(File dir, String name) {
        if (Files.exists(new File(dir, name).toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        String[] names = dir.list();
        if (names != null) {
            for (String n : names) {
                if (n.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ob ein Name als Dateiname taugt; null, wenn ja, sonst der Grund. */
    static String invalidReason(String name) {
        if (name.isEmpty()) {
            return "Der Name wäre leer";
        }
        if (name.equals(".") || name.equals("..")) {
            return "„" + name + "“ ist kein gültiger Name";
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) {
            return "Der Name enthält ein ungültiges Zeichen";
        }
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255) {
            return "Der Name ist zu lang";
        }
        return null;
    }

    /**
     * Berechnet die Vorschau. Probleme: ungültiger Name, zwei Dateien mit gleichem neuen Namen (ohne Rücksicht auf
     * Groß- und Kleinschreibung, wegen FAT und exFAT auf SD-Karten), oder ein Name, den es im Ordner schon gibt und
     * der nicht zu einer der gewählten Dateien gehört.
     */
    static Plan plan(List<File> files, Options o) {
        Plan plan = new Plan();
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            plan.items.add(new Item(f, newName(f.getName(), o, i)));
        }
        Set<String> selectedNames = new HashSet<>();
        for (File f : files) {
            selectedNames.add(f.getParentFile() + "\u0000" + f.getName().toLowerCase(Locale.ROOT));
        }
        Set<String> seen = new HashSet<>();
        for (Item it : plan.items) {
            String bad = invalidReason(it.newName);
            if (bad != null) {
                it.problem = it.file.getName() + ": " + bad;
                continue;
            }
            String key = it.file.getParentFile() + "\u0000" + it.newName.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                it.problem = "Zwei Dateien würden „" + it.newName + "“ heißen";
                continue;
            }
            if (it.changes() && !selectedNames.contains(key) && existsIgnoreCase(it.file.getParentFile(), it.newName)) {
                it.problem = "„" + it.newName + "“ gibt es hier schon";
            }
        }
        return plan;
    }

    /**
     * Führt einen Plan aus. Zuerst bekommen alle betroffenen Dateien Zwischennamen, dann die endgültigen. Geht etwas
     * schief, wird alles zurückgenommen, und das Ergebnis nennt den Grund.
     */
    static FileOps.Result apply(Plan plan, FileOps.Progress progress) {
        FileOps.Result r = new FileOps.Result();
        if (plan.hasProblem()) {
            r.failed.add(plan.firstProblem());
            return r;
        }
        List<Item> todo = new ArrayList<>();
        for (Item i : plan.items) {
            if (i.changes()) {
                todo.add(i);
            } else {
                r.skipped.add(i.file.getName() + ": Name bleibt gleich");
            }
        }
        List<File> temps = new ArrayList<>();
        // Phase 1: alle auf Zwischennamen
        int done1 = 0;
        try {
            for (Item i : todo) {
                File tmp = new File(i.file.getParentFile(), ".nolphin-umbenennen-" + System.nanoTime() + "-" + done1 + ".tmp");
                Files.move(i.file.toPath(), tmp.toPath());
                temps.add(tmp);
                done1++;
            }
        } catch (IOException e) {
            rollback(todo, temps, done1);
            r.failed.add("Umbenennen nicht möglich (" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    + "). Alles wurde zurückgenommen.");
            return r;
        }
        // Phase 2: Zwischennamen zu den endgültigen Namen
        int done2 = 0;
        try {
            for (int k = 0; k < todo.size(); k++) {
                Item i = todo.get(k);
                File dst = new File(i.file.getParentFile(), i.newName);
                Files.move(temps.get(k).toPath(), dst.toPath());
                done2++;
                r.touch(dst);
                r.touch(i.file);
                r.done.add(i.file.getName() + " → " + i.newName);
                if (progress != null) {
                    progress.update(i.newName, done2, todo.size(), done2, todo.size());
                }
            }
        } catch (IOException e) {
            // endgültige Namen wieder auf Zwischennamen, dann alles auf die alten Namen
            try {
                for (int k = done2 - 1; k >= 0; k--) {
                    Files.move(new File(todo.get(k).file.getParentFile(), todo.get(k).newName).toPath(), temps.get(k).toPath());
                }
            } catch (IOException ignored) {
            }
            rollback(todo, temps, todo.size());
            r.done.clear();
            r.touched.clear();
            r.failed.add("Umbenennen nicht möglich (" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    + "). Alles wurde zurückgenommen.");
            return r;
        }
        return r;
    }

    private static void rollback(List<Item> todo, List<File> temps, int count) {
        for (int k = 0; k < count && k < temps.size(); k++) {
            try {
                Files.move(temps.get(k).toPath(), todo.get(k).file.toPath());
            } catch (IOException ignored) {
            }
        }
    }
}
