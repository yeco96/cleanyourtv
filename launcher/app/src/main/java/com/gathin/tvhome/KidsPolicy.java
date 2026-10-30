package com.gathin.tvhome;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * What kids mode lets through and how it slows down guessing the PIN (usage_limit). No Android types; tested on the JVM.
 * The ideas (an allowed-apps screen, a guard that sends other apps back, a wait after wrong PINs) come from
 * github.com/maxhayim/kids-mode (MIT); this is written for this launcher.
 */
final class KidsPolicy {
    static final int FREE_TRIES = 5;
    static final long FIRST_WAIT_MS = 30_000L;
    static final long MAX_WAIT_MS = 5 * 60_000L;

    /** Package or app-name fragments of children's apps: they start ticked in the allowed-apps list. */
    private static final String[] KIDS_HINTS = {
            "youtube.tvkids", "pbskids", "pbs kids", "nickjr", "nick jr", "disneyjunior", "disney junior", "kidoodle",
            "noggin", "khanacademykids", "sesame", "toca", "babyfirst", "hopster", "boomerang", "cartoonito", "kids",
            "infantil", "junior"
    };

    private KidsPolicy() { }

    /** How long to wait before the next PIN try, after this many wrong ones in a row: none for five, then 30 s doubling to 5 min. */
    static long waitAfter(int wrong) {
        if (wrong < FREE_TRIES) return 0;
        return Math.min(MAX_WAIT_MS, FIRST_WAIT_MS << Math.min(10, wrong - FREE_TRIES));
    }

    /** True for an app that starts ticked: YouTube Kids or a known children's app, never the full YouTube. */
    static boolean kidsApp(String pkg, String label) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (p.equals("com.google.android.youtube.tv")) return false;
        for (String hint : KIDS_HINTS) if (p.contains(hint) || l.contains(hint)) return true;
        return false;
    }

    /**
     * True when the guard must send the TV back to the kids' screen: the guard is on and the app in front is neither
     * this launcher, an allowed app, an on-screen keyboard, nor the system's own volume and dialog windows.
     */
    static boolean bounce(boolean guarding, String pkg, String self, Set<String> allowed, Collection<String> keyboards) {
        if (!guarding || pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals(self) || allowed.contains(pkg) || keyboards.contains(pkg)) return false;
        return !pkg.equals("com.android.systemui") && !pkg.equals("android");
    }
}
