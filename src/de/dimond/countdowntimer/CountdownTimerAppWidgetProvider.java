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

import java.util.Arrays;
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
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;

public class CountdownTimerAppWidgetProvider extends AppWidgetProvider {

    public static final String INTENT_DATA_WIDGET_ID = "WIDGET_ID";
    public static final String INTENT_DATA_IS_SILENT = "IS_SILENT";
    public static final String INTENT_ALARM_ALERT = "de.dimond.countdowntimer.INTENT_ALARM_ALERT";
    public static final String INTENT_WAKEUP = "de.dimond.countdowntimer.INTENT_WAKEUP";

    /* periodically wake up to make sure our countdown timers are running */
    public static final long WAKEUP_INTERVAL = 15000;

    private static final String TAG = "CountdownTimerAppWidgetProvider";

    private static final String VIBRATE_KEY = "CTW_VIBRATE";
    private static final String INSISTENT_KEY = "CTW_INSISTENT";
    private static final String RINGTONE_KEY = "CTW_RINGTONE";
    private static final String SCHEDULED_ALARMS_KEY = "CTW_SCHEDULED_ALARMS";

    private static final boolean LOGD = false;

    private static Map<Integer, CountdownTask> m_countdownTasks;
    private static final Map<Integer, RemoteViews> m_remoteViews = new HashMap<Integer, RemoteViews>();

    private static Map<Integer, Alarm> m_alarms;

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

    private class Alarm implements Comparable<Alarm> {
        public final long m_when;
        public final boolean m_isSilent;

        public Alarm(long when, boolean isSilent) {
            this.m_when = when;
            this.m_isSilent = isSilent;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + (m_isSilent ? 1231 : 1237);
            result = prime * result + (int) (m_when ^ (m_when >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Alarm other = (Alarm) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (m_isSilent != other.m_isSilent)
                return false;
            if (m_when != other.m_when)
                return false;
            return true;
        }

        private CountdownTimerAppWidgetProvider getOuterType() {
            return CountdownTimerAppWidgetProvider.this;
        }

        @Override
        public String toString() {
            return "Alarm [m_when=" + m_when + ", m_isSilent=" + m_isSilent + "]";
        }

        @Override
        public int compareTo(Alarm another) {
            return Long.signum(m_when - another.m_when);
        }

    }

    private class CountdownTask extends TimerTask {

        private final Context m_context;
        private final RemoteViews m_views;
        private final int m_widgetId;
        private final long m_duration;
        private long m_startTime;

        private final Timer m_timer;

        private int m_lastRemainingTime = -1;

        public CountdownTask(Context context, RemoteViews views, int widgetId, long when) {
            m_context = context;
            m_views = views;
            m_widgetId = widgetId;
            /* Add 500msecs to compensate delay from alarm */
            long now = SystemClock.elapsedRealtime();
            long duration = (when - now) + 500;
            if (duration < 0) {
                m_duration = 0;
            } else {
                m_duration = duration;
            }

            m_timer = new Timer();
        }

        public void start() {
            m_timer.scheduleAtFixedRate(this, 0, 200);
            m_startTime = SystemClock.elapsedRealtime();
        }

        public void reset() {
            m_timer.cancel();
            m_views.setTextViewText(R.id.timer_text, m_context.getText(R.string.timer_uninitialised));
            AppWidgetManager.getInstance(m_context).updateAppWidget(m_widgetId, m_views);
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
            AppWidgetManager.getInstance(m_context).updateAppWidget(m_widgetId, m_views);
        }

    }

    public void showNotification(Context context, int id, Uri sound, boolean vibrate, boolean insistent) {
        Notification n = new Notification(R.drawable.stat_notify_alarm, context.getString(R.string.timer_expired),
                System.currentTimeMillis());

        n.defaults = Notification.DEFAULT_LIGHTS;
        if (!sound.equals(Uri.EMPTY)) {
            n.sound = sound;
        }
        if (vibrate) {
            n.vibrate = new long[] { 0, 500, 200, 500, 200, 750 };
        }

        n.flags = Notification.FLAG_AUTO_CANCEL;
        if (insistent) {
            n.flags |= Notification.FLAG_INSISTENT;
        }

        n.setLatestEventInfo(context, context.getString(R.string.timer_expired),
                context.getString(R.string.click_to_remove), PendingIntent.getBroadcast(context, 0, new Intent(), 0));

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(id);
        manager.notify(id, n);
    }

    @Override
    public void onUpdate(Context context, final AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int n = appWidgetIds.length;

        for (int i = 0; i < n; i++) {
            int appWidgetId = appWidgetIds[i];

            RemoteViews views = getViewsOrBuild(context, appWidgetId);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onEnabled(Context context) {
        /*
         * Delete all old alarms from another boot
         */
        if (LOGD)
            Log.d(TAG, "Deleting all old alarms!");
        deleteAllAlarms(context);
    }

    private RemoteViews getViewsOrBuild(Context context, int widgetId) {
        if (m_remoteViews.containsKey(widgetId)) {
            return m_remoteViews.get(widgetId);
        } else {
            RemoteViews views = buildRemoteView(context, widgetId);
            m_remoteViews.put(widgetId, views);
            return views;
        }
    }

    private RemoteViews buildRemoteView(Context context, int widgetId) {
        Intent intent = new Intent(context, NewTimerActivity.class);
        intent.setData(Uri.parse("widget://" + widgetId));

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(INTENT_DATA_WIDGET_ID, widgetId);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.countdown_timer_widget);
        views.setOnClickPendingIntent(R.id.timer_text, pendingIntent);

        return views;
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        final int n = appWidgetIds.length;

        for (int i = 0; i < n; i++) {
            int appWidgetId = appWidgetIds[i];

            removeAlarm(context, appWidgetId);

            if (m_countdownTasks.containsKey(appWidgetIds)) {
                m_countdownTasks.get(appWidgetId).cancel();
            }
        }
    }

    private void recoverAlarms(Context context) {
        if (m_alarms != null) {
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String alarmsString = preferences.getString(SCHEDULED_ALARMS_KEY, "");

        if (LOGD)
            Log.d(TAG, "Recoverd Alarms: " + alarmsString);

        m_alarms = new HashMap<Integer, Alarm>();

        String[] alarms = alarmsString.split(",");

        for (String alarm : alarms) {
            if (alarm.length() == 0) {
                continue;
            }

            String[] entry = alarm.substring(1, alarm.length() - 1).split(":");

            if (LOGD)
                Log.d(TAG, "Got alarm: " + Arrays.toString(entry));

            if (entry.length != 3) {
                Log.w(TAG, "Invalid Alarm found: " + alarm);
                continue;
            }

            try {
                int widgetId = Integer.parseInt(entry[0]);
                long when = Long.parseLong(entry[1]);
                boolean isSilent = entry[2].equals("1") ? true : false;

                m_alarms.put(widgetId, new Alarm(when, isSilent));
            } catch (NumberFormatException e) {
                Log.w(TAG, "NumberFormatException: Invalid Alarm found: " + alarm);
            }
        }

    }

    private void saveAlarms(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = preferences.edit();
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, Alarm> entry : m_alarms.entrySet()) {
            builder.append("(");
            builder.append(entry.getKey());
            builder.append(":");
            builder.append(entry.getValue().m_when);
            builder.append(":");
            builder.append(entry.getValue().m_isSilent ? "1" : "0");
            builder.append(")");
            builder.append(",");
        }
        String alarms = "";
        if (builder.length() > 0) {
            alarms = builder.substring(0, builder.length() - 1);
        }
        if (LOGD)
            Log.d(TAG, "Saving Alarms: " + alarms);
        editor.putString(SCHEDULED_ALARMS_KEY, alarms);
        editor.commit();
    }

    private void removeAlarm(Context context, int widgetId) {
        recoverAlarms(context);
        if (LOGD)
            Log.d(TAG, "Removing Alarm with ID " + widgetId);
        /* No change occurred */
        if (m_alarms.remove(widgetId) == null) {
            return;
        }
        saveAlarms(context);
        scheduleAlarm(context);
    }

    private void addAlarm(Context context, int widgetId, long when, boolean isSilent) {
        recoverAlarms(context);
        m_alarms.put(widgetId, new Alarm(when, isSilent));
        saveAlarms(context);
        scheduleAlarm(context);
    }

    private void deleteAllAlarms(Context context) {
        m_alarms = new HashMap<Integer, CountdownTimerAppWidgetProvider.Alarm>();
        saveAlarms(context);
    }

    private void scheduleWakeup(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        long now = SystemClock.elapsedRealtime();
        long wakeup = now + WAKEUP_INTERVAL;

        Intent wakeupIntent = new Intent(INTENT_WAKEUP);
        PendingIntent wakeupPendingIntent = PendingIntent.getBroadcast(context, 0, wakeupIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        if (LOGD)
            Log.d(TAG, "Setting wakup alarm in " + (wakeup - now) / 1000 + " seconds!");
        manager.set(AlarmManager.ELAPSED_REALTIME, wakeup, wakeupPendingIntent);
    }

    private void scheduleAlarm(Context context) {
        recoverAlarms(context);

        long now = SystemClock.elapsedRealtime();

        Map.Entry<Integer, Alarm> nextAlarm = smallestValue(m_alarms);

        boolean changed = false;

        while (nextAlarm != null && nextAlarm.getValue().m_when < (now - 2000)) {
            m_alarms.remove(nextAlarm.getKey());
            Log.w(TAG, "Removing too old alarm!");
            nextAlarm = smallestValue(m_alarms);
            changed = true;
        }

        if (changed) {
            saveAlarms(context);
        }

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (nextAlarm == null) {
            Intent intent = new Intent(INTENT_ALARM_ALERT);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            manager.cancel(pendingIntent);
            Log.i(TAG, "Cancelled alarm!");
            return;
        }

        long when = nextAlarm.getValue().m_when;

        Intent intent = new Intent(INTENT_ALARM_ALERT);
        intent.putExtra(INTENT_DATA_WIDGET_ID, nextAlarm.getKey());
        intent.putExtra(INTENT_DATA_IS_SILENT, nextAlarm.getValue().m_isSilent);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        Log.i(TAG, "Setting alarm in " + (when - now) / 1000 + " seconds!");
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pendingIntent);

        scheduleWakeup(context);
    }

    private void checkCountdownTasks(Context context) {
        if (m_countdownTasks != null) {
            return;
        }

        recoverAlarms(context);
        m_countdownTasks = new HashMap<Integer, CountdownTask>();
        for (Map.Entry<Integer, Alarm> entry : m_alarms.entrySet()) {
            int widgetId = entry.getKey();
            CountdownTask task = new CountdownTask(context, getViewsOrBuild(context, widgetId), widgetId,
                    entry.getValue().m_when);
            m_countdownTasks.put(widgetId, task);
            task.start();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOGD)
            Log.d(TAG, "Received Intent: " + intent.getAction());

        /* Check if we we're killed in the mean time */
        checkCountdownTasks(context);

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

                RemoteViews views = getViewsOrBuild(context, widgetId);

                long when = SystemClock.elapsedRealtime() + duration * 1000;

                CountdownTask countdownTask = new CountdownTask(context, views, widgetId, when);
                m_countdownTasks.put(widgetId, countdownTask);
                countdownTask.start();

                addAlarm(context, widgetId, when, silent);
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
                }
                removeAlarm(context, widgetId);
            }
        } else if (intent.getAction().equals(INTENT_ALARM_ALERT)) {

            if (LOGD)
                Log.d(TAG, "Received alarm intent: " + intent);

            Bundle extras = intent.getExtras();
            if (extras == null) {
                Log.w(TAG, "Received invalid intent, Extras was null!");
                return;
            }

            int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID, -1);
            if (widgetId == -1) {
                Log.w(TAG, "Received invalid intent!");
                return;
            }

            boolean isSilent = extras.getBoolean(INTENT_DATA_IS_SILENT, false);

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

            boolean vibrate = preferences.getBoolean(VIBRATE_KEY, true);
            boolean insistent = preferences.getBoolean(INSISTENT_KEY, false);

            Uri sound;
            if (isSilent) {
                sound = Uri.EMPTY;
            } else {
                sound = Uri.parse(preferences.getString(RINGTONE_KEY,
                        Settings.System.DEFAULT_NOTIFICATION_URI.toString()));
            }
            showNotification(context, widgetId, sound, vibrate, insistent);

            removeAlarm(context, widgetId);

            m_countdownTasks.remove(widgetId);
        } else if (intent.getAction().equals(INTENT_WAKEUP)) {
            if (LOGD)
                Log.d(TAG, "Woke up!");
            recoverAlarms(context);
            if (m_alarms.size() > 0) {
                scheduleWakeup(context);
            }
        } else {
            /* Only call if this intent is not from us */
            super.onReceive(context, intent);
        }
    }

}
