package org.nolphin.handy;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Die Wurzeln der Speicher des Handys: interner Speicher und eingelegte SD-Karten (für „Anzeige nach Art“). */
final class StorageRoots {
    private StorageRoots() {}

    static List<String> paths(Context c) {
        List<String> out = new ArrayList<>();
        out.add(Environment.getExternalStorageDirectory().getPath());
        try {
            StorageManager sm = c.getSystemService(StorageManager.class);
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (v.isPrimary() || !Environment.MEDIA_MOUNTED.equals(v.getState())) {
                    continue;
                }
                File d = null;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    d = v.getDirectory();
                } else {
                    for (File f : c.getExternalFilesDirs(null)) {
                        if (f != null && f.getPath().contains("/Android/") && !f.getPath().startsWith(out.get(0))) {
                            d = new File(f.getPath().substring(0, f.getPath().indexOf("/Android/")));
                        }
                    }
                }
                if (d != null && !out.contains(d.getPath())) {
                    out.add(d.getPath());
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }
}
