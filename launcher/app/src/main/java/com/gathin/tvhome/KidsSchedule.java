package com.gathin.tvhome;

/** The daily windows in which kids mode turns itself on and off (usage_limit). Choices, not free times: OK steps through them. No Android types. */
final class KidsSchedule {
    /** Start and end hour of each choice; the first is "off". */
    private static final int[][] CHOICES = {{0, 0}, {15, 19}, {15, 20}, {16, 21}, {14, 22}};

    private KidsSchedule() { }

    static int count() {
        return CHOICES.length;
    }

    static int startHour(int choice) {
        return valid(choice) ? CHOICES[choice][0] : 0;
    }

    static int endHour(int choice) {
        return valid(choice) ? CHOICES[choice][1] : 0;
    }

    /** True when {@code minuteOfDay} (0 to 1439) is inside the choice's window; always false for "off". A window may cross midnight. */
    static boolean inWindow(int choice, int minuteOfDay) {
        if (choice <= 0 || !valid(choice)) return false;
        int start = CHOICES[choice][0] * 60, end = CHOICES[choice][1] * 60;
        return start <= end ? minuteOfDay >= start && minuteOfDay < end : minuteOfDay >= start || minuteOfDay < end;
    }

    /** The next choice, wrapping to "off" after the last. */
    static int next(int choice) {
        return (choice + 1) % CHOICES.length;
    }

    private static boolean valid(int choice) {
        return choice >= 0 && choice < CHOICES.length;
    }
}
