package com.usama.dailytracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Keeps the reminder schedule. The page sends two lists:
 *  daily: [{id, hour, min, title, text, open}]  repeats every day
 *  once:  [{id, at, title, text, open}]           one time (task reminders), "at" in ms
 * Alarms are rebuilt after every change, after a restart and after an app update.
 */
public final class Reminders {
    static final String CHANNEL = "reminders";
    private static final String PREFS = "pp_reminders";

    private Reminders() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, String dailyJson, String onceJson) {
        prefs(c).edit().putString("daily", dailyJson).putString("once", onceJson).apply();
        scheduleAll(c);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Daily reminders to add money and tick habits, and task reminders");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private static PendingIntent alarmIntent(Context c, int code, JSONObject item, boolean daily) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("pp.REMIND." + code);
        i.putExtra("title", item.optString("title", "Purse & Pulse"));
        i.putExtra("text", item.optString("text", ""));
        i.putExtra("open", item.optString("open", ""));
        i.putExtra("code", code);
        i.putExtra("daily", daily);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static long nextDaily(int hour, int min) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    static void setAlarm(Context c, long at, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void scheduleAll(Context c) {
        ensureChannel(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = prefs(c);
        // cancel what was set before
        String old = p.getString("codes", "");
        for (String s : old.split(",")) {
            if (s.isEmpty()) continue;
            try {
                int code = Integer.parseInt(s);
                Intent i = new Intent(c, ReminderReceiver.class);
                i.setAction("pp.REMIND." + code);
                PendingIntent pi = PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            } catch (Exception ignored) { }
        }
        StringBuilder codes = new StringBuilder();
        try {
            JSONArray daily = new JSONArray(p.getString("daily", "[]"));
            for (int k = 0; k < daily.length(); k++) {
                JSONObject o = daily.getJSONObject(k);
                int code = 1000 + k;
                setAlarm(c, nextDaily(o.optInt("hour", 21), o.optInt("min", 0)), alarmIntent(c, code, o, true));
                codes.append(code).append(',');
            }
        } catch (Exception ignored) { }
        try {
            JSONArray once = new JSONArray(p.getString("once", "[]"));
            long now = System.currentTimeMillis();
            int n = 0;
            for (int k = 0; k < once.length() && n < 400; k++) {
                JSONObject o = once.getJSONObject(k);
                long at = o.optLong("at", 0);
                if (at <= now) continue;
                int code = 5000 + n++;
                setAlarm(c, at, alarmIntent(c, code, o, false));
                codes.append(code).append(',');
            }
        } catch (Exception ignored) { }
        p.edit().putString("codes", codes.toString()).apply();
    }

    /** After a daily reminder fires, set it again for the same time tomorrow. */
    static void rescheduleDaily(Context c, int code) {
        try {
            JSONArray daily = new JSONArray(prefs(c).getString("daily", "[]"));
            int k = code - 1000;
            if (k < 0 || k >= daily.length()) return;
            JSONObject o = daily.getJSONObject(k);
            setAlarm(c, nextDaily(o.optInt("hour", 21), o.optInt("min", 0)), alarmIntent(c, code, o, true));
        } catch (Exception ignored) { }
    }
}
