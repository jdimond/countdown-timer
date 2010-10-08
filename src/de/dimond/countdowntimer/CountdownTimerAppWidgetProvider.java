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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

public class CountdownTimerAppWidgetProvider extends AppWidgetProvider {

    public static final String INTENT_DATA_WIDGET_ID = "WIDGET_ID";
    public static final String INTENT_ALARM_ALERT = "de.dimond.countdowntimer.INTENT_ALARM_ALERT";

    private static final String TAG = "CountdownTimerAppWidgetProvider";

    private static final String VIBRATE_KEY = "VIBRATE";
    private static final String INSISTENT_KEY = "INSISTENT";

    private static final boolean LOGD = false;

    private static AppWidgetManager m_widgetManager;
    private static Context m_context;
    private static Map<Integer, RemoteViews> m_remoteViews = new HashMap<Integer, RemoteViews>();
    private static Map<Integer, CountdownTask> m_countdownTasks = new HashMap<Integer, CountdownTask>();
    private static final Map<Integer, Long> m_alarms = new HashMap<Integer, Long>();
    private static Integer m_currentAlarmWidgetId = -1;

    private static final Intent ALARM_INTENT = new Intent(INTENT_ALARM_ALERT);

    private static <K, V> Map.Entry<K, V> smallestValue(Map<K, V> map) {

        if (map.size() == 0) {
            return null;
        }

        List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @SuppressWarnings("unchecked")
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                return ((Comparable<V>) o1.getValue()).compareTo(o2.getValue());
            }
        });

        return list.get(0);
    }

    private class CountdownTask extends TimerTask {

        private final AppWidgetManager m_appWidgetManager;
        private final RemoteViews m_views;
        private final int m_widgetId;
        private final long m_duration;
        private long m_startTime;
        private final boolean m_isSilent;

        private final Timer m_timer;

        private int m_lastRemainingTime = -1;

        public CountdownTask(AppWidgetManager manager, RemoteViews views, int widgetId, int duration, boolean silent) {
            m_appWidgetManager = manager;
            m_views = views;
            m_widgetId = widgetId;
            /* Add 500msecs to compensate delay from alarm */
            m_duration = duration * 1000 + 500;
            m_isSilent = silent;

            m_timer = new Timer();
        }

        public void start() {
            m_timer.scheduleAtFixedRate(this, 0, 200);
            m_startTime = SystemClock.elapsedRealtime();
        }

        public boolean isSilent() {
            return m_isSilent;
        }

        public void reset() {
            m_timer.cancel();
            m_views.setTextViewText(R.id.timer_text, m_context.getText(R.string.timer_uninitialised));
            m_appWidgetManager.updateAppWidget(m_widgetId, m_views);
        }

        @Override
        public void run() {
            int remainingTime = (int) ((m_duration - (SystemClock.elapsedRealtime() - m_startTime)) / 1000);
            if (remainingTime <= 0) {
                m_timer.cancel();
                remainingTime = 0;
            }

            /* only update the view if anything has changed */
            if (m_lastRemainingTime == remainingTime) {
                return;
            }
            m_lastRemainingTime = remainingTime;

            int seconds = remainingTime % 60;
            int minutes = (remainingTime / 60) % 60;
            int hours = remainingTime / 3600;

            String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            m_views.setTextViewText(R.id.timer_text, time);
            m_appWidgetManager.updateAppWidget(m_widgetId, m_views);

        }

    }

    public void showNotification(int id, boolean isSilent, boolean vibrate, boolean insistent) {
        Notification n = new Notification(R.drawable.stat_notify_alarm, m_context.getString(R.string.timer_expired),
                System.currentTimeMillis());

        n.defaults = Notification.DEFAULT_LIGHTS;
        if (!isSilent) {
            n.defaults |= Notification.DEFAULT_SOUND;
        }
        if (vibrate) {
            n.vibrate = new long[] { 0, 500, 200, 500, 200, 750 };
        }

        n.flags = Notification.FLAG_AUTO_CANCEL;
        if (insistent) {
            n.flags |= Notification.FLAG_INSISTENT;
        }

        n.setLatestEventInfo(m_context, m_context.getString(R.string.timer_expired),
                m_context.getString(R.string.click_to_remove),
                PendingIntent.getBroadcast(m_context, 0, new Intent(), 0));

        NotificationManager manager = (NotificationManager) m_context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(id);
        manager.notify(id, n);
    }

    @Override
    public void onUpdate(Context context, final AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int n = appWidgetIds.length;

        m_widgetManager = appWidgetManager;

        m_context = context;

        for (int i = 0; i < n; i++) {
            int appWidgetId = appWidgetIds[i];

            Intent intent = new Intent(context, NewTimerActivity.class);
            /*
             * The Android system currently reuses Intents if they match,
             * ignoring extras. To ensure that we have an unique intent for each
             * widget we add a pseudo data field with an unique id
             */
            intent.setData(Uri.parse("unique://" + appWidgetId));
            intent.putExtra(INTENT_DATA_WIDGET_ID, appWidgetId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.countdown_timer_widget);
            views.setOnClickPendingIntent(R.id.timer_text, pendingIntent);

            if (LOGD)
                Log.d(TAG, "Putting RemoteViews into HashMap with ID " + appWidgetId + "!");
            m_remoteViews.put(appWidgetId, views);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        final int n = appWidgetIds.length;
        
        for (int i = 0; i < n; i++) {
            int appWidgetId = appWidgetIds[i];

            if(m_alarms.remove(appWidgetId)!=null) {
                scheduleAlarm();
            }
            if(m_countdownTasks.containsKey(appWidgetIds)) {
                m_countdownTasks.get(appWidgetId).cancel();
            }
        }
    }

    private void scheduleAlarm() {
        Map.Entry<Integer, Long> nextAlarm = smallestValue(m_alarms);
        AlarmManager manager = (AlarmManager) m_context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(m_context, 0, ALARM_INTENT,
                PendingIntent.FLAG_CANCEL_CURRENT);

        /* No alarm left so cancel any remaining Intent */
        if (nextAlarm == null) {
            manager.cancel(pendingIntent);
            m_currentAlarmWidgetId = -1;
            return;
        }

        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextAlarm.getValue(), pendingIntent);
        m_currentAlarmWidgetId = nextAlarm.getKey();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOGD)
            Log.d(TAG, "Received Intent: " + intent.getAction());
        if (intent.getAction().equals(NewTimerActivity.INTENT_NEW_TIMER)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID, -1);
                int duration = extras.getInt(NewTimerActivity.INTENT_DATA_DURATION, -1);
                boolean silent = extras.getBoolean(NewTimerActivity.INTENT_DATA_SILENT, false);
                if (widgetId == -1 || duration == -1) {
                    Log.w(TAG, "Received invalid intent!");
                    return;
                }

                if (LOGD)
                    Log.d(TAG, "Received Intent with widgetId=" + widgetId + " and duration=" + duration);

                if (m_countdownTasks.containsKey(widgetId)) {
                    if (LOGD)
                        Log.d(TAG, "Stopping old timer with ID " + widgetId);
                    m_countdownTasks.get(widgetId).cancel();
                    m_countdownTasks.remove(widgetId);
                }
                RemoteViews views = m_remoteViews.get(widgetId);
                if (views == null) {
                    Log.e(TAG, "RemoteView not in HashMap!");
                    return;
                }
                CountdownTask countdownTask = new CountdownTask(m_widgetManager, views, widgetId, duration, silent);
                m_countdownTasks.put(widgetId, countdownTask);
                countdownTask.start();
                m_alarms.put(widgetId, SystemClock.elapsedRealtime() + duration * 1000);
                scheduleAlarm();
            }
        } else if (intent.getAction().equals(NewTimerActivity.INTENT_CANCEL_TIMER)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID, -1);
                if (widgetId == -1) {
                    Log.w(TAG, "Received invalid intent!");
                    return;
                }
                if (m_countdownTasks.containsKey(widgetId)) {
                    m_countdownTasks.get(widgetId).reset();
                    m_countdownTasks.remove(widgetId);
                    if (m_alarms.remove(widgetId) == null) {
                        Log.w(TAG, "Inconsistent data! No Alarm with widgetId " + widgetId + " in m_alarms!");
                    }
                    scheduleAlarm();
                }
            }
        } else if (intent.getAction().equals(INTENT_ALARM_ALERT)) {
            if (m_currentAlarmWidgetId == -1) {
                Log.w(TAG, "Invalid Intent received! m_currentAlarmWidgetId was -1!");
                return;
            }

            boolean isSilent = m_countdownTasks.get(m_currentAlarmWidgetId).isSilent();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(m_context);
            boolean vibrate = preferences.getBoolean(VIBRATE_KEY, true);
            boolean insistent = preferences.getBoolean(INSISTENT_KEY, false);
            showNotification(m_currentAlarmWidgetId, isSilent, vibrate, insistent);

            if (m_alarms.remove(m_currentAlarmWidgetId) == null) {
                Log.w(TAG, "Inconsistent data! No Alarm with widgetId " + m_currentAlarmWidgetId + " in m_alarms!");
            }

            if (m_countdownTasks.containsKey(m_currentAlarmWidgetId)) {
                m_countdownTasks.get(m_currentAlarmWidgetId).reset();
            }

            scheduleAlarm();
        } else {
            /* Only call if this intent is not from us */
            super.onReceive(context, intent);
        }
    }
}
