package org.nolphin.handy;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;

/** Teilen-Menü: schickt die geteilten Dateien an den PC und schließt sich sofort. */
@SuppressWarnings("deprecation")
public class ShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) {
                uris.add(u);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(in.getAction())) {
            ArrayList<Uri> list = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) {
                uris.addAll(list);
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "Nichts zum Senden", Toast.LENGTH_SHORT).show();
        } else if (!Conn.connected(this)) {
            Toast.makeText(this, "Erst den QR-Code vom PC scannen", Toast.LENGTH_LONG).show();
        } else {
            ClipData clip = ClipData.newUri(getContentResolver(), "Dateien", uris.get(0));
            for (int i = 1; i < uris.size(); i++) {
                clip.addItem(new ClipData.Item(uris.get(i)));
            }
            Intent svc = new Intent(this, SyncService.class);
            svc.setAction(SyncService.ACTION_UPLOAD);
            svc.setClipData(clip);
            svc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startForegroundService(svc);
            Toast.makeText(this, "Wird an den PC gesendet", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
