package com.gathin.tvhome;

/**
 * How much contrast a backdrop needs, from how bright it is where the text sits (tmdb_backdrops). A dark picture
 * gets a light touch and keeps its colour; a bright one gets the full scrim and a darker picture, so the title, the
 * stars and the overview read on any of them. No Android types, so it is tested on the JVM.
 */
final class BackdropContrast {
    /** At or below this mean brightness (0 to 1) a picture needs no more help than the lightest setting. */
    static final float DARK = 0.25f;
    /** At or above this a picture is as bright as backdrops get here and takes the strongest setting. */
    static final float BRIGHT = 0.65f;
    /** Share of the scrim's strength over the darkest pictures; the brightest take all of it. */
    static final float SCRIM_MIN = 0.70f;
    /** The dim over the picture with the focus on the dock, from the darkest picture to the brightest. */
    static final float DIM_DOCK_MIN = 0.12f;
    static final float DIM_DOCK_MAX = 0.42f;

    private BackdropContrast() { }

    /** 0 for a picture that needs nothing extra, 1 for one that needs everything. */
    static float need(float luminance) {
        return Math.max(0f, Math.min(1f, (luminance - DARK) / (BRIGHT - DARK)));
    }

    /** The scrim's opacity, 0.70 to 1. */
    static float scrim(float luminance) {
        return SCRIM_MIN + (1f - SCRIM_MIN) * need(luminance);
    }

    /** The dim's opacity while the dock has the focus. */
    static float dockDim(float luminance) {
        return DIM_DOCK_MIN + (DIM_DOCK_MAX - DIM_DOCK_MIN) * need(luminance);
    }

    /** Mean brightness (0 to 1, by the usual weights of red, green and blue) of ARGB pixels; 0.4 when there are none. */
    static float luminance(int[] argb, int count) {
        if (argb == null || count <= 0) return 0.4f;
        double sum = 0;
        for (int i = 0; i < count; i++) {
            int p = argb[i];
            sum += 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF);
        }
        return (float) (sum / count / 255.0);
    }
}
