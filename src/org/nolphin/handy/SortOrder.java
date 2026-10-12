package org.nolphin.handy;

import java.util.Comparator;
import java.util.Locale;

/** Sortierreihenfolge für Dateilisten: Schlüssel, Richtung und "Ordner zuerst" (ohne Android-Abhängigkeit, damit sie sich testen lässt). */
final class SortOrder {
    static final int NAME = 0, SIZE = 1, DATE = 2, TYPE = 3;
    static final String[] LABELS = { "Name", "Größe", "Änderungsdatum", "Dateityp" };

    /** Was die Sortierung von einem Listeneintrag wissen muss. */
    interface Sortable {
        String sortName();

        boolean sortIsDir();

        long sortSize();

        long sortModified();
    }

    final int key;
    final boolean ascending;
    final boolean foldersFirst;

    SortOrder(int key, boolean ascending, boolean foldersFirst) {
        this.key = key >= NAME && key <= TYPE ? key : NAME;
        this.ascending = ascending;
        this.foldersFirst = foldersFirst;
    }

    static SortOrder standard() {
        return new SortOrder(NAME, true, true);
    }

    /** Text für den Hinweis unter der Pfadleiste, zum Beispiel "Größe ↓". */
    String describe() {
        return LABELS[key] + (ascending ? " ↑" : " ↓");
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Ordner stehen vorn (wenn gewählt), unabhängig von der Richtung. Gleiche Werte werden immer nach Namen
     * aufsteigend geordnet, damit die Reihenfolge stabil und vorhersehbar bleibt.
     */
    Comparator<Sortable> comparator() {
        final SortOrder o = this;
        return new Comparator<Sortable>() {
            @Override
            public int compare(Sortable a, Sortable b) {
                if (o.foldersFirst && a.sortIsDir() != b.sortIsDir()) {
                    return a.sortIsDir() ? -1 : 1;
                }
                int c = 0;
                switch (o.key) {
                    case SIZE:
                        c = Long.compare(a.sortSize(), b.sortSize());
                        break;
                    case DATE:
                        c = Long.compare(a.sortModified(), b.sortModified());
                        break;
                    case TYPE:
                        c = extension(a.sortName()).compareTo(extension(b.sortName()));
                        break;
                    default:
                        c = a.sortName().compareToIgnoreCase(b.sortName());
                }
                if (!o.ascending) {
                    c = -c;
                }
                if (c != 0) {
                    return c;
                }
                return a.sortName().compareToIgnoreCase(b.sortName());
            }
        };
    }
}
