/*
 * Copyright (C) 2010 Jonathan Dimond
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dimond.countdowntimer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class CountdownTimerAppWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, final AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int n = appWidgetIds.length;

        for (int i = 0; i < n; i++) {
            int appWidgetId = appWidgetIds[i];

            Intent intent = new Intent(context, CountdownTimerService.class);
            intent.setAction(CountdownTimerService.INTENT_ADD_WIDGET);
            intent.putExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, appWidgetId);
            context.startService(intent);

            RemoteViews views = buildRemoteView(context, appWidgetId, null);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onEnabled(Context context) {
        /* First boot send reset Intent to Service */
        Intent intent = new Intent(context, CountdownTimerService.class);
        intent.setAction(CountdownTimerService.INTENT_RESET_ALARMS);
        context.startService(intent);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        final int n = appWidgetIds.length;

        for (int i = 0; i < n; i++) {
            int appWidgetId = appWidgetIds[i];

            Intent intent = new Intent(context, CountdownTimerService.class);
            intent.setAction(CountdownTimerService.INTENT_REMOVE_WIDGET);
            intent.putExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, appWidgetId);
            context.startService(intent);
        }
    }

    public static RemoteViews buildRemoteView(Context context, int widgetId, String description) {
        Intent intent = new Intent(context, NewTimerActivity.class);
        intent.setData(Uri.parse("widget://" + widgetId));

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, widgetId);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews views;
        if (description == null) {
            views = new RemoteViews(context.getPackageName(), R.layout.countdown_timer_widget);
        } else {
            views = new RemoteViews(context.getPackageName(), R.layout.countdown_timer_widget_desc);
            views.setTextViewText(R.id.description_text, description);
        }
        views.setOnClickPendingIntent(R.id.timer_text, pendingIntent);

        return views;
    }
}
