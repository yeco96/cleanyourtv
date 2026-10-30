package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The memory button (memory_button): closes the apps that were left running in the background, as Android itself
 * does when memory runs short, but when the owner asks. A 1.4 GB TV that has had Netflix, YouTube and a player open
 * keeps their processes, and the next app then opens slowly.
 *
 * <p>Only apps the owner can open (they have a launcher entry) and that are not part of the system: the user's own
 * apps, and system apps that were updated from the Play Store, such as YouTube. Never the launcher, and never an
 * app that has an accessibility service switched on, since the remote's helpers and the sleep timer live in those.
 * {@code killBackgroundProcesses} cannot touch what is on screen, a foreground service or a persistent process, so
 * nothing the TV needs to run goes down with it.
 */
final class MemoryCleaner {

    /** Free memory in MB before and after, and how many apps were asked to close. */
    static final class Result {
        final int freeBeforeMb;
        final int freeAfterMb;
        final int apps;

        Result(int freeBeforeMb, int freeAfterMb, int apps) {
            this.freeBeforeMb = freeBeforeMb;
            this.freeAfterMb = freeAfterMb;
            this.apps = apps;
        }
    }

    private MemoryCleaner() { }

    /**
     * Whether an app may be closed: one the owner opens ({@code launchable}), that is not a system app unless the
     * store updated it, and that is neither this launcher nor the owner of an accessibility service.
     */
    static boolean killable(boolean launchable, boolean system, boolean updatedSystem, boolean self,
                            boolean hasAccessibilityService) {
        return launchable && !self && !hasAccessibilityService && (!system || updatedSystem);
    }

    /** Closes what can be closed and measures. Waits a moment for the memory to come back, so never on the main thread. */
    static Result run(Context context) {
        PackageManager pm = context.getPackageManager();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int before = freeMb(am);
        Set<String> accessibility = new HashSet<>();
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
            for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (info.getResolveInfo() != null) accessibility.add(info.getResolveInfo().serviceInfo.packageName);
            }
        }
        Set<String> opened = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo info : found) opened.add(info.activityInfo.packageName);
        }
        int closed = 0;
        for (String pkg : opened) {
            try {
                ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
                boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean updated = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (killable(true, system, updated, pkg.equals(context.getPackageName()), accessibility.contains(pkg))) {
                    am.killBackgroundProcesses(pkg);
                    closed++;
                }
            } catch (PackageManager.NameNotFoundException gone) {
                // uninstalled while we looked
            }
        }
        try {
            Thread.sleep(900);   // the kernel gives the memory back a moment after the processes go
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        int after = freeMb(am);
        Log.i("TvHome.Memory", "asked " + closed + " apps to close; free " + before + " -> " + after + " MB");
        return new Result(before, after, closed);
    }

    private static int freeMb(ActivityManager am) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return (int) (info.availMem / (1024 * 1024));
    }
}
