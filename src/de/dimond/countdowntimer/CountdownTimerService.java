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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;

public class CountdownTimerService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "CountdownTimerService";

    public static final String INTENT_ADD_WIDGET = "de.dimond.countdowntimer.intent.ACTION_SERVICE_ADD_WIDGET";
    public static final String INTENT_REMOVE_WIDGET = "de.dimond.countdowntimer.intent.ACTION_SERVICE_REMOVE_WIDGET";
    public static final String INTENT_RESET_ALARMS = "de.dimond.countdowntimer.intent.ACTION_SERVICE_RESET_ALARMS";
    public static final String INTENT_ALARM_ALERT = "de.dimond.countdowntimer.intent.ACTION_ALARM_ALERT";

    public static final String INTENT_DATA_WIDGET_ID = "WIDGET_ID";
    public static final String INTENT_DATA_IS_SILENT = "IS_SILENT";
    private static final String VIBRATE_KEY = "CTW_VIBRATE";
    private static final String INSISTENT_KEY = "CTW_INSISTENT";
    private static final String RINGTONE_KEY = "CTW_RINGTONE";
    private static final String REFRESH_INTERVAL_KEY = "CTW_REFRESH_INTERVAL";

    private static final String SCHEDULED_ALARMS_KEY = "CTW_SCHEDULED_ALARMS";

    private Map<Integer, RemoteViews> m_remoteViews;
    private Map<Integer, CountdownTask> m_countdownTasks;
    private Map<Integer, Alarm> m_alarms;

    private SharedPreferences m_preferences;
    private BroadcastReceiver m_receiver;

    private static final boolean LOGD = false;

    @Override
    public void onCreate() {
        if (LOGD)
            Log.d(TAG, "Service created!");
        m_remoteViews = new HashMap<Integer, RemoteViews>();
        m_preferences = PreferenceManager.getDefaultSharedPreferences(this);
        m_preferences.registerOnSharedPreferenceChangeListener(this);
        loadAlarms();
        scheduleAlarm();
        startAllCountdownTasks();

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        m_receiver = new ScreenBroadcastReceiver();
        registerReceiver(m_receiver, filter);
    }

    @Override
    public void onDestroy() {
        m_preferences.unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(m_receiver);
    }

    private int getIntentWidgetId(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.w(TAG, "Received invalid Intent!");
            return -1;
        }

        if (!extras.containsKey(INTENT_DATA_WIDGET_ID)) {
            Log.w(TAG, "Received invalid Intent!");
            return -1;
        }

        int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID);
        return widgetId;
    }
    
    /* This is for any pre-2.0 platform */
    @Override
    public void onStart(Intent intent, int startId) {
        onStartCommand(intent, 0, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (LOGD)
            Log.d(TAG, "Received Intent: " + intent);
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }
        if (intent.getAction().equals(INTENT_ADD_WIDGET)) {
            int widgetId = getIntentWidgetId(intent);
            if (widgetId == -1) {
                return START_STICKY;
            }

            RemoteViews views = CountdownTimerAppWidgetProvider.buildRemoteView(this, widgetId);
            m_remoteViews.put(widgetId, views);
        } else if (intent.getAction().equals(INTENT_REMOVE_WIDGET)) {
            int widgetId = getIntentWidgetId(intent);
            if (widgetId == -1) {
                return START_STICKY;
            }

            m_remoteViews.remove(widgetId);
            cancelAlarmAndTask(widgetId);
        } else if (intent.getAction().equals(INTENT_RESET_ALARMS)) {
            deleteAllAlarms();
        } else if (intent.getAction().equals(NewTimerActivity.INTENT_NEW_TIMER)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID, -1);
                int duration = extras.getInt(NewTimerActivity.INTENT_DATA_DURATION, -1);
                boolean silent = extras.getBoolean(NewTimerActivity.INTENT_DATA_SILENT, false);
                if (widgetId == -1 || duration == -1) {
                    Log.w(TAG, "Received invalid intent!");
                    return START_STICKY;
                }

                if (LOGD)
                    Log.d(TAG, "Received Intent with widgetId=" + widgetId + " and duration=" + duration);

                if (m_countdownTasks.containsKey(widgetId)) {
                    if (LOGD)
                        Log.d(TAG, "Stopping old timer with ID " + widgetId);
                    m_countdownTasks.get(widgetId).stop();
                    m_countdownTasks.remove(widgetId);
                }

                RemoteViews views = getViewsOrBuild(widgetId);

                long when = SystemClock.elapsedRealtime() + duration * 1000;

                CountdownTask countdownTask = new CountdownTask(this, views, widgetId, when);
                m_countdownTasks.put(widgetId, countdownTask);
                int interval = Integer.parseInt(m_preferences.getString(REFRESH_INTERVAL_KEY, "1"));
                countdownTask.start(interval);

                addAlarm(widgetId, when, silent);
            }
        } else if (intent.getAction().equals(NewTimerActivity.INTENT_CANCEL_TIMER)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID, -1);
                if (widgetId == -1) {
                    Log.w(TAG, "Received invalid intent!");
                    return START_STICKY;
                }

                cancelAlarmAndTask(widgetId);
            }
        } else if (intent.getAction().equals(INTENT_ALARM_ALERT)) {
            if (LOGD)
                Log.d(TAG, "Received alarm intent: " + intent);

            Bundle extras = intent.getExtras();
            if (extras == null) {
                Log.w(TAG, "Received invalid intent, Extras was null!");
                return START_STICKY;
            }

            int widgetId = extras.getInt(INTENT_DATA_WIDGET_ID, -1);
            if (widgetId == -1) {
                Log.w(TAG, "Received invalid intent!");
                return START_STICKY;
            }

            boolean isSilent = extras.getBoolean(INTENT_DATA_IS_SILENT, false);

            boolean vibrate = m_preferences.getBoolean(VIBRATE_KEY, true);
            boolean insistent = m_preferences.getBoolean(INSISTENT_KEY, false);

            Uri sound;
            if (isSilent) {
                sound = Uri.EMPTY;
            } else {
                sound = Uri.parse(m_preferences.getString(RINGTONE_KEY,
                        Settings.System.DEFAULT_NOTIFICATION_URI.toString()));
            }
            showNotification(widgetId, sound, vibrate, insistent);

            removeAlarm(widgetId);

            if (m_countdownTasks.containsKey(widgetId)) {
                m_countdownTasks.get(widgetId).refresh();
                m_countdownTasks.remove(widgetId);
            }

            /* No alarms left stopp service */
            if (m_alarms.size() == 0) {
                if (LOGD)
                    Log.d(TAG, "Stopping service!");
                stopSelf();
            }
        }

        return START_STICKY;
    }

    private RemoteViews getViewsOrBuild(int widgetId) {
        if (m_remoteViews.containsKey(widgetId)) {
            return m_remoteViews.get(widgetId);
        } else {
            RemoteViews views = CountdownTimerAppWidgetProvider.buildRemoteView(this, widgetId);
            m_remoteViews.put(widgetId, views);
            return views;
        }
    }

    public void showNotification(int id, Uri sound, boolean vibrate, boolean insistent) {
        Notification n = new Notification(R.drawable.stat_notify_alarm, getString(R.string.timer_expired),
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

        n.setLatestEventInfo(this, getString(R.string.timer_expired), getString(R.string.click_to_remove),
                PendingIntent.getBroadcast(this, 0, new Intent(), 0));

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(id);
        manager.notify(id, n);
    }

    private void loadAlarms() {
        String alarmsString = m_preferences.getString(SCHEDULED_ALARMS_KEY, "");

        if (LOGD)
            Log.d(TAG, "Recoverd Alarms: " + alarmsString);

        m_alarms = new HashMap<Integer, Alarm>();
        m_countdownTasks = new HashMap<Integer, CountdownTask>();

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

                CountdownTask task = new CountdownTask(this, getViewsOrBuild(widgetId), widgetId, when);
                m_countdownTasks.put(widgetId, task);
            } catch (NumberFormatException e) {
                Log.w(TAG, "NumberFormatException: Invalid Alarm found: " + alarm);
            }
        }
    }

    private void startAllCountdownTasks() {
        int interval = Integer.parseInt(m_preferences.getString(REFRESH_INTERVAL_KEY, "1"));
        for (CountdownTask task : m_countdownTasks.values()) {
            task.start(interval);
        }
    }

    private void stopAllCountdownTasks() {
        for (CountdownTask task : m_countdownTasks.values()) {
            task.stop();
        }
    }

    private void cancelAlarmAndTask(int widgetId) {
        if (m_countdownTasks.containsKey(widgetId)) {
            m_countdownTasks.get(widgetId).reset();
            m_countdownTasks.remove(widgetId);
        }
        removeAlarm(widgetId);
    }

    private void saveAlarms() {
        Editor editor = m_preferences.edit();
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

    private void removeAlarm(int widgetId) {
        if (LOGD)
            Log.d(TAG, "Removing Alarm with ID " + widgetId);
        /* No change occurred */
        if (m_alarms.remove(widgetId) == null) {
            return;
        }
        saveAlarms();
        scheduleAlarm();
    }

    private void addAlarm(int widgetId, long when, boolean isSilent) {
        m_alarms.put(widgetId, new Alarm(when, isSilent));
        saveAlarms();
        scheduleAlarm();
    }

    private void deleteAllAlarms() {
        m_alarms = new HashMap<Integer, Alarm>();
        saveAlarms();
        scheduleAlarm();
    }

    private void scheduleAlarm() {
        long now = SystemClock.elapsedRealtime();

        if (LOGD)
            Log.d(TAG, "Scheduling Alarms: " + m_alarms.values().toString());

        Map.Entry<Integer, Alarm> nextAlarm = smallestValue(m_alarms);

        while (nextAlarm != null && nextAlarm.getValue().m_when < (now - 2000)) {
            m_alarms.remove(nextAlarm.getKey());
            Log.w(TAG, "Removing too old alarm!");
            nextAlarm = smallestValue(m_alarms);
        }

        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (nextAlarm == null) {
            Intent intent = new Intent(INTENT_ALARM_ALERT);
            intent.setComponent(new ComponentName(this, CountdownTimerService.class));
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            manager.cancel(pendingIntent);
            return;
        }

        long when = nextAlarm.getValue().m_when;

        Intent intent = new Intent(INTENT_ALARM_ALERT);
        intent.setComponent(new ComponentName(this, CountdownTimerService.class));
        intent.putExtra(INTENT_DATA_WIDGET_ID, nextAlarm.getKey());
        intent.putExtra(INTENT_DATA_IS_SILENT, nextAlarm.getValue().m_isSilent);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        Log.i(TAG, "Setting alarm in " + (when - now) / 1000 + " seconds!");
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pendingIntent);
    }

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

    @Override
    public IBinder onBind(Intent intent) {
        throw new IllegalStateException("This service cannot be bound!");
    }

    private class ScreenBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (LOGD)
                    Log.d(TAG, "Starting all CountdownTasks!");
                startAllCountdownTasks();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (LOGD)
                    Log.d(TAG, "Stopping all CountdownTasks!");
                stopAllCountdownTasks();
            }
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(REFRESH_INTERVAL_KEY)) {
            stopAllCountdownTasks();
            startAllCountdownTasks();
        }
    }

}
