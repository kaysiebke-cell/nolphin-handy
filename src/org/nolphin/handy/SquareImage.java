package org.nolphin.handy;

import android.content.Context;
import android.widget.ImageView;

/** Bildfläche, die immer so hoch wie breit ist: Kacheln füllen so die ganze Breite mit dem Symbol oder Vorschaubild. */
final class SquareImage extends ImageView {
    SquareImage(Context c) {
        super(c);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
        int w = getMeasuredWidth();
        setMeasuredDimension(w, w);
    }
}
