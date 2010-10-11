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

import java.util.Timer;
import java.util.TimerTask;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

public class CountdownTask {

    private final Context m_context;
    private final RemoteViews m_views;
    private final int m_widgetId;
    private final long m_when;

    private Timer m_timer;

    private int m_lastRemainingTime = -1;

    private static final String TAG = "CountdownTask";
    private static final boolean LOGD = false;

    private class CountdownTimerTask extends TimerTask {

        @Override
        public void run() {
            refresh();
        }

    }

    public CountdownTask(Context context, RemoteViews views, int widgetId, long when) {
        m_context = context;
        m_views = views;
        m_widgetId = widgetId;
        m_when = when;
    }

    public void start(int interval) {
        stopTimer();
        m_timer = new Timer();
        if (interval == 1) {
            interval = 200;
        } else {
            interval = interval * 1000;
        }
        m_timer.scheduleAtFixedRate(new CountdownTimerTask(), 0, interval);
    }

    public void refresh() {
        int remainingTime = (int) ((m_when - SystemClock.elapsedRealtime()) / 1000);
        if (remainingTime <= 0) {
            remainingTime = 0;
            stopTimer();
        }

        /* only update the view if anything has changed */
        if (m_lastRemainingTime == remainingTime) {
            if (LOGD)
                Log.d(TAG, "No update!");
            return;
        }

        m_lastRemainingTime = remainingTime;

        int seconds = remainingTime % 60;
        int minutes = (remainingTime / 60) % 60;
        int hours = remainingTime / 3600;

        String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        if (LOGD)
            Log.d(TAG, "Update: " + time);

        m_views.setTextViewText(R.id.timer_text, time);
        AppWidgetManager.getInstance(m_context).updateAppWidget(m_widgetId, m_views);
    }

    public void reset() {
        stopTimer();
        m_views.setTextViewText(R.id.timer_text, m_context.getText(R.string.timer_uninitialised));
        AppWidgetManager.getInstance(m_context).updateAppWidget(m_widgetId, m_views);
    }

    public void stop() {
        stopTimer();
    }

    private void stopTimer() {
        if (m_timer == null) {
            return;
        }
        m_timer.cancel();
        m_timer = null;
    }

}