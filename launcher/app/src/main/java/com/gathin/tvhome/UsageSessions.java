package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns Android's usage events into spans of one app in front (usage_stats). No Android types, so it is tested on
 * the JVM.
 *
 * <p>Use is an app on screen: from the event that brings one of its activities to the front until another app takes
 * its place, the screen goes off or the TV shuts down. The launcher, the screensaver and the system interface are
 * "ignored": they still end the app before them, but no span is made for them. A span that has no closing event
 * (an app left on) is cut at {@code maxMs}.
 *
 * <p>Run after run, the state an unfinished span leaves ({@link State}) is carried in, so a span that lasts through
 * several runs is counted once, a piece each time.
 */
final class UsageSessions {
    /** Android's UsageEvents.Event types this reads; literals, as some need an API level newer than minSdk. */
    static final int RESUMED = 1, PAUSED = 2, SCREEN_OFF = 16, SHUTDOWN = 26;

    private UsageSessions() { }

    static final class Event {
        final long time;
        final int type;
        final String pkg;
        final String cls;

        Event(long time, int type, String pkg, String cls) {
            this.time = time;
            this.type = type;
            this.pkg = pkg;
            this.cls = cls;
        }
    }

    /** One app in front from {@code start} to {@code end}, in epoch milliseconds. */
    static final class Span {
        final String pkg;
        final long start;
        final long end;

        Span(String pkg, long start, long end) {
            this.pkg = pkg;
            this.start = start;
            this.end = end;
        }

        long seconds() {
            return Math.max(0, (end - start) / 1000);
        }
    }

    /** The span still open after the last run: the app, when it came to the front, and up to where it is counted. */
    static final class State {
        String pkg;
        String cls;
        long openedAt;
        long countedUntil;

        boolean isOpen() {
            return pkg != null;
        }

        void clear() {
            pkg = null;
            cls = null;
            openedAt = 0;
            countedUntil = 0;
        }
    }

    /**
     * Folds {@code events} (oldest first) into spans, updating {@code state}. What is still open at {@code now} is
     * counted up to {@code now}, once, and left open in the state.
     */
    static List<Span> fold(State state, List<Event> events, long now, Set<String> ignored, long maxMs) {
        List<Span> out = new ArrayList<>();
        for (Event e : events) {
            switch (e.type) {
                case RESUMED:
                    if (state.isOpen() && state.pkg.equals(e.pkg)) {
                        state.cls = e.cls;   // another screen of the same app: the span goes on
                    } else {
                        close(state, e.time, ignored, maxMs, out);
                        state.pkg = e.pkg;
                        state.cls = e.cls;
                        state.openedAt = e.time;
                        state.countedUntil = e.time;
                    }
                    break;
                case PAUSED:
                    // Only the screen on top ends the span: a pause of a screen that was already replaced does not.
                    if (state.isOpen() && state.pkg.equals(e.pkg) && (state.cls == null || state.cls.equals(e.cls))) {
                        close(state, e.time, ignored, maxMs, out);
                    }
                    break;
                case SCREEN_OFF:
                case SHUTDOWN:
                    close(state, e.time, ignored, maxMs, out);
                    break;
                default:
                    break;
            }
        }
        if (state.isOpen()) {
            long end = Math.min(now, state.openedAt + maxMs);
            if (end > state.countedUntil) {
                if (!ignored.contains(state.pkg)) out.add(new Span(state.pkg, state.countedUntil, end));
                state.countedUntil = end;
            }
            if (end >= state.openedAt + maxMs) state.clear();   // left on for too long: not counted past the cap
        }
        return out;
    }

    private static void close(State state, long at, Set<String> ignored, long maxMs, List<Span> out) {
        if (!state.isOpen()) return;
        long end = Math.min(at, state.openedAt + maxMs);
        if (end > state.countedUntil && !ignored.contains(state.pkg)) out.add(new Span(state.pkg, state.countedUntil, end));
        state.clear();
    }
}
