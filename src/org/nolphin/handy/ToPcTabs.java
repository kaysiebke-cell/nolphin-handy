package org.nolphin.handy;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Der Reiter "Zum PC": zeigt ein Fenster ({@link ToPcScreen}) oder, wenn der Zwei-Fenster-Modus in den
 * Einstellungen angeboten und eingeschaltet ist, zwei Fenster ({@link DualScreen}).
 */
final class ToPcTabs implements Screen, ToPcScreen.DualSwitch {
    private final Context c;
    private final Host host;
    private final FrameLayout frame;
    private final ToPcScreen single;
    private DualScreen dual;
    private boolean dualOn;

    ToPcTabs(Context c, Host host) {
        this.c = c;
        this.host = host;
        frame = new FrameLayout(c);
        single = new ToPcScreen(c, host);
        single.setDualSwitch(this);
        frame.addView(single.view());
    }

    private Screen current() {
        return dualOn && dual != null ? dual : single;
    }

    private void show(boolean wantDual) {
        if (wantDual && dual == null) {
            dual = new DualScreen(c, host, this);
        }
        if (wantDual == dualOn && frame.getChildCount() > 0) {
            return;
        }
        dualOn = wantDual;
        frame.removeAllViews();
        frame.addView(current().view());
    }

    @Override
    public void setDual(boolean on) {
        Conn.setDualActive(c, on);
        show(on && Conn.dualAvailable(c));
        current().onShow();
    }

    @Override
    public View view() {
        return frame;
    }

    @Override
    public void onShow() {
        show(Conn.dualActive(c));   // folgt den Einstellungen
        current().onShow();
    }

    @Override
    public void refresh() {
        current().refresh();
    }

    @Override
    public boolean onBack() {
        return current().onBack();
    }
}
