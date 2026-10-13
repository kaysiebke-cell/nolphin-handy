package org.nolphin.handy;

/** Rechenregel für die Darstellungsgröße der App (ohne Android, damit sie sich testen lässt). */
final class UiScale {
    private UiScale() {}

    /** Bezugsbreite in dp, für die die App gestaltet ist (Bilder der README, Abstände, Schrift). */
    static final int REFERENCE_DP = 360;
    static final float MAX = 1.25f;

    /**
     * Faktor, mit dem die App vergrößert wird: Handys, die schmaler als die Bezugsbreite sind, bleiben unverändert;
     * breitere bekommen Schrift, Symbole und Abstände im gleichen Verhältnis größer, damit die App überall gleich
     * aussieht. Begrenzt auf {@link #MAX}.
     */
    static float factor(int smallestWidthDp) {
        if (smallestWidthDp <= REFERENCE_DP) {
            return 1f;
        }
        return Math.min(MAX, smallestWidthDp / (float) REFERENCE_DP);
    }

    /** Neue Bildschirmdichte (dpi) für die Vergrößerung. */
    static int densityDpi(int densityDpi, int smallestWidthDp) {
        return Math.round(densityDpi * factor(smallestWidthDp));
    }
}
