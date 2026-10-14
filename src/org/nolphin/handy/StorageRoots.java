package org.nolphin.handy;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Die Wurzeln der Speicher des Handys: interner Speicher und eingelegte SD-Karten (für „Anzeige nach Art“). */
final class StorageRoots {
    private StorageRoots() {}

    static List<String> paths() {
        List<String> out = new ArrayList<>();
        out.add(Environment.getExternalStorageDirectory().getPath());
        File[] vols = new File("/storage").listFiles();
        if (vols != null) {
            for (File f : vols) {
                String n = f.getName();
                if (!n.equals("emulated") && !n.equals("self") && f.isDirectory() && f.canRead()) {
                    out.add(f.getPath());
                }
            }
        }
        return out;
    }
}
