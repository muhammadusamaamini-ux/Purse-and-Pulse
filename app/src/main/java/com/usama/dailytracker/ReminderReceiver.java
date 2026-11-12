package com.usama.dailytracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Shows a reminder notification. Tapping it opens the app on the right screen. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.TIME_SET".equals(action) || "android.intent.action.TIMEZONE_CHANGED".equals(action)) {
            Reminders.scheduleAll(c);
            return;
        }
        int code = intent.getIntExtra("code", 0);
        if (intent.getBooleanExtra("daily", false)) Reminders.rescheduleDaily(c, code);
        show(c, code, intent.getStringExtra("title"), intent.getStringExtra("text"), intent.getStringExtra("open"));
    }

    static void show(Context c, int code, String title, String text, String open) {
        Reminders.ensureChannel(c);
        Intent tap = new Intent(c, MainActivity.class);
        tap.setAction("pp.OPEN." + code);
        tap.putExtra("open", open == null ? "" : open);
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, code, tap, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, Reminders.CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title == null || title.isEmpty() ? "Purse & Pulse" : title)
                .setContentText(text == null ? "" : text)
                .setStyle(new Notification.BigTextStyle().bigText(text == null ? "" : text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try { nm.notify(code, b.build()); } catch (SecurityException ignored) { }
    }
}
