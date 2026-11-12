package com.usama.dailytracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Home-screen widget: balance, spent this month, and quick buttons to add a record or open habits. */
public class BalanceWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, views(c));
    }

    private static PendingIntent open(Context c, int code, String what) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction("pp.WIDGET." + what);
        i.putExtra("open", what);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews views(Context c) {
        SharedPreferences p = c.getSharedPreferences("pp_widget", Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_balance);
        v.setTextViewText(R.id.w_balance, p.getString("balance", "Open the app once"));
        v.setTextViewText(R.id.w_sub, p.getString("sub", ""));
        v.setOnClickPendingIntent(R.id.w_root, open(c, 9100, "money"));
        v.setOnClickPendingIntent(R.id.w_exp, open(c, 9101, "expense"));
        v.setOnClickPendingIntent(R.id.w_inc, open(c, 9102, "income"));
        v.setOnClickPendingIntent(R.id.w_hab, open(c, 9103, "habits"));
        return v;
    }

    static void refresh(Context c, String balance, String sub) {
        c.getSharedPreferences("pp_widget", Context.MODE_PRIVATE).edit().putString("balance", balance).putString("sub", sub).apply();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, BalanceWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(c));
    }
}
