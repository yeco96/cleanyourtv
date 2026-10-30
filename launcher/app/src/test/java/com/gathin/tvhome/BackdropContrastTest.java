package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackdropContrastTest {
    private static final float EPS = 0.001f;

    @Test
    public void aDarkPictureGetsTheLightestSetting() {
        assertEquals(BackdropContrast.SCRIM_MIN, BackdropContrast.scrim(0.05f), EPS);
        assertEquals(BackdropContrast.DIM_DOCK_MIN, BackdropContrast.dockDim(0.05f), EPS);
    }

    @Test
    public void aBrightPictureGetsTheStrongest() {
        assertEquals(1f, BackdropContrast.scrim(0.9f), EPS);
        assertEquals(BackdropContrast.DIM_DOCK_MAX, BackdropContrast.dockDim(0.9f), EPS);
    }

    @Test
    public void theHelpGrowsWithTheBrightness() {
        float last = -1;
        for (float l = 0f; l <= 1f; l += 0.05f) {
            float dim = BackdropContrast.dockDim(l);
            assertTrue(dim >= last);
            last = dim;
        }
    }

    @Test
    public void luminanceUsesTheWeightsOfRedGreenAndBlue() {
        assertEquals(1f, BackdropContrast.luminance(new int[]{0xFFFFFFFF}, 1), EPS);
        assertEquals(0f, BackdropContrast.luminance(new int[]{0xFF000000}, 1), EPS);
        assertEquals(0.299f, BackdropContrast.luminance(new int[]{0xFFFF0000}, 1), EPS);
        assertEquals(0.587f, BackdropContrast.luminance(new int[]{0xFF00FF00}, 1), EPS);
        assertEquals(0.5f, BackdropContrast.luminance(new int[]{0xFFFFFFFF, 0xFF000000}, 2), EPS);
    }

    @Test
    public void noPixelsMeansAnAverageBackdrop() {
        assertEquals(0.4f, BackdropContrast.luminance(null, 0), EPS);
        assertEquals(0.4f, BackdropContrast.luminance(new int[0], 0), EPS);
    }
}
