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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

import com.android.example.NumberPicker;

public class NewTimerActivity extends Activity implements OnClickListener {

    public static final String INTENT_NEW_TIMER = "de.dimond.countdowntimer.intent.ACTION_NEW_TIMER";
    public static final String INTENT_CANCEL_TIMER = "de.dimond.countdowntimer.intent.ACTION_CANCEL_TIMER";
    public static final String INTENT_DATA_DURATION = "DURATION";
    public static final String INTENT_DATA_SILENT = "SILENT";

    public static final String HOURS_KEY = "CTW_HOURS";
    public static final String MINUTES_KEY = "CTW_MINUTES";
    public static final String SECONDS_KEY = "CTW_SECONDS";
    public static final String SILENT_KEY = "CTW_SILENT";

    private static final String TAG = "NewTimerActivity";
    private static final boolean LOGD = false;

    private int m_widgetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.new_timer);

        NumberPicker pickerHours = (NumberPicker) findViewById(R.id.hours);
        NumberPicker pickerMinutes = (NumberPicker) findViewById(R.id.minutes);
        NumberPicker pickerSeconds = (NumberPicker) findViewById(R.id.seconds);

        SharedPreferences prefrences = PreferenceManager.getDefaultSharedPreferences(this);

        pickerHours.setRange(0, 23);
        pickerHours.setCurrent(prefrences.getInt(HOURS_KEY, 0));

        pickerMinutes.setRange(0, 59);
        pickerMinutes.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
        pickerMinutes.setCurrent(prefrences.getInt(MINUTES_KEY, 1));

        pickerSeconds.setRange(0, 59);
        pickerSeconds.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
        pickerSeconds.setCurrent(prefrences.getInt(SECONDS_KEY, 0));

        CheckBox checkBox = (CheckBox) findViewById(R.id.silent);
        checkBox.setChecked(prefrences.getBoolean(SILENT_KEY, false));

        Button startButton = (Button) findViewById(R.id.start_button);
        startButton.setOnClickListener(this);

        Button cancelButton = (Button) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(this);

        Intent intent = getIntent();
        m_widgetId = intent.getIntExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, -1);
        if (LOGD)
            Log.d(TAG, "Received Intent with Widget ID=" + m_widgetId);
        if (m_widgetId == -1) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        /* Close because user doesn't want to schedule alarm */
        finish();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            Intent i = new Intent();
            i.setClass(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if (v.equals(findViewById(R.id.start_button))) {
            NumberPicker pickerHours = (NumberPicker) findViewById(R.id.hours);
            NumberPicker pickerMinutes = (NumberPicker) findViewById(R.id.minutes);
            NumberPicker pickerSeconds = (NumberPicker) findViewById(R.id.seconds);

            CheckBox checkBox = (CheckBox) findViewById(R.id.silent);

            int hours = pickerHours.getCurrent();
            int minutes = pickerMinutes.getCurrent();
            int seconds = pickerSeconds.getCurrent();

            boolean silent = checkBox.isChecked();

            saveState(hours, minutes, seconds, silent);

            Intent intent = new Intent(INTENT_NEW_TIMER);
            intent.putExtra(INTENT_DATA_DURATION, hours * 3600 + minutes * 60 + seconds);
            intent.putExtra(INTENT_DATA_SILENT, silent);
            intent.putExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, m_widgetId);

            startService(intent);

            finish();
        } else if (v.equals(findViewById(R.id.cancel_button))) {
            Intent intent = new Intent(INTENT_CANCEL_TIMER);
            intent.putExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, m_widgetId);

            startService(intent);

            finish();
        }
    }

    private void saveState(int hours, int minutes, int seconds, boolean silent) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putInt(HOURS_KEY, hours);
        editor.putInt(MINUTES_KEY, minutes);
        editor.putInt(SECONDS_KEY, seconds);
        editor.putBoolean(SILENT_KEY, silent);

        editor.commit();
    }

}
