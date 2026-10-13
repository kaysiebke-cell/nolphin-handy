package org.nolphin.handy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

/**
 * Gibt einzelne Dateien des Handy-Speichers als content://-Adresse an andere Apps heraus („Öffnen mit …“,
 * „Bearbeiten mit …“). Der Provider ist nicht exportiert: Eine andere App kommt nur an Dateien, für die die App
 * ausdrücklich eine Berechtigung (grantUriPermission) erteilt hat. Zusätzlich werden nur Dateien unterhalb von /storage
 * herausgegeben (siehe {@link FileUris#isAllowed}).
 */
public final class FilesProvider extends ContentProvider {
    static final String AUTHORITY = "org.nolphin.handy.files";

    /** content://…/f/<Pfad>; der Dateiname steht am Ende, damit die andere App den echten Namen und die Endung sieht. */
    static Uri uriFor(File file) {
        Uri.Builder b = new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("f");
        for (String part : file.getPath().split("/")) {
            if (!part.isEmpty()) {
                b.appendPath(part);
            }
        }
        return b.build();
    }

    static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (m != null) {
                return m;
            }
        }
        return "application/octet-stream";
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || !path.startsWith("/f/")) {
            throw new FileNotFoundException("unbekannte Adresse");
        }
        try {
            File f = new File(path.substring(2)).getCanonicalFile();
            if (!FileUris.isAllowed(f.getPath(), Collections.singletonList("/storage")) || !f.isFile()) {
                throw new FileNotFoundException("nicht freigegeben");
            }
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException("nicht lesbar");
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            row[i] = OpenableColumns.DISPLAY_NAME.equals(cols[i]) ? f.getName()
                    : OpenableColumns.SIZE.equals(cols[i]) ? (Object) f.length() : null;
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String last = uri.getLastPathSegment();
        return last == null ? "application/octet-stream" : mimeOf(last);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
