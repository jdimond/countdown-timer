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

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.android.example.NumberPicker;

public class NewTimerActivity extends Activity implements OnClickListener, OnItemSelectedListener {

    public static final String INTENT_NEW_TIMER = "de.dimond.countdowntimer.intent.ACTION_NEW_TIMER";
    public static final String INTENT_CANCEL_TIMER = "de.dimond.countdowntimer.intent.ACTION_CANCEL_TIMER";
    public static final String INTENT_DATA_DURATION = "DURATION";
    public static final String INTENT_DATA_SILENT = "SILENT";
    public static final String INTENT_DATA_DESCRIPTION = "DESCRIPTION";

    private static final String RECENT_TIMERS_FILE = "recent_timers";

    private static final Timer DEFAULT_TIMER = new Timer(0, 1, 0, null, false);
    private static final int MAX_RECENT_TIMERS = 7;

    private static final String TAG = "NewTimerActivity";
    private static final boolean LOGD = false;

    private int m_widgetId;
    private List<Timer> m_recentTimers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.new_timer);

        NumberPicker pickerHours = (NumberPicker) findViewById(R.id.hours);
        NumberPicker pickerMinutes = (NumberPicker) findViewById(R.id.minutes);
        NumberPicker pickerSeconds = (NumberPicker) findViewById(R.id.seconds);

        Spinner recentTimers = (Spinner) findViewById(R.id.recent_timers);

        m_recentTimers = readState();

        TimerSpinnerAdapter adapter = new TimerSpinnerAdapter(this, m_recentTimers);

        recentTimers.setAdapter(adapter);
        recentTimers.setOnItemSelectedListener(this);

        pickerHours.setRange(0, 23);

        pickerMinutes.setRange(0, 59);
        pickerMinutes.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);

        pickerSeconds.setRange(0, 59);
        pickerSeconds.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);

        Button startButton = (Button) findViewById(R.id.start_button);
        startButton.setOnClickListener(this);

        Button cancelButton = (Button) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(this);

        Timer lastTimer = m_recentTimers.get(0);
        setTimer(lastTimer);

        Intent intent = getIntent();
        m_widgetId = intent.getIntExtra(CountdownTimerService.INTENT_DATA_WIDGET_ID, -1);
        if (LOGD)
            Log.d(TAG, "Received Intent with Widget ID=" + m_widgetId);
        if (m_widgetId == -1) {
            finish();
        }
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

    public void setTimer(Timer timer) {
        NumberPicker pickerHours = (NumberPicker) findViewById(R.id.hours);
        NumberPicker pickerMinutes = (NumberPicker) findViewById(R.id.minutes);
        NumberPicker pickerSeconds = (NumberPicker) findViewById(R.id.seconds);

        EditText description = (EditText) findViewById(R.id.description);

        String descStr = timer.getDescription();
        if (descStr != null) {
            description.setText(descStr);
        } else {
            description.setText("");
        }

        pickerHours.setCurrent(timer.getHours());
        pickerMinutes.setCurrent(timer.getMinutes());
        pickerSeconds.setCurrent(timer.getSeconds());

        CheckBox checkBox = (CheckBox) findViewById(R.id.silent);
        checkBox.setChecked(timer.isSilent());
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Timer timer = m_recentTimers.get(position);
        setTimer(timer);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        /* Do nothing */
    }

    @Override
    public void onClick(View v) {
        if (v.equals(findViewById(R.id.start_button))) {
            NumberPicker pickerHours = (NumberPicker) findViewById(R.id.hours);
            NumberPicker pickerMinutes = (NumberPicker) findViewById(R.id.minutes);
            NumberPicker pickerSeconds = (NumberPicker) findViewById(R.id.seconds);

            EditText description = (EditText) findViewById(R.id.description);

            CheckBox checkBox = (CheckBox) findViewById(R.id.silent);

            int hours = pickerHours.getCurrent();
            int minutes = pickerMinutes.getCurrent();
            int seconds = pickerSeconds.getCurrent();

            String descStr = description.getText().toString();

            if (descStr.equals("")) {
                descStr = null;
            }

            boolean silent = checkBox.isChecked();

            saveState(new Timer(hours, minutes, seconds, descStr, silent));

            Intent intent = new Intent(INTENT_NEW_TIMER);
            intent.putExtra(INTENT_DATA_DURATION, hours * 3600 + minutes * 60 + seconds);
            intent.putExtra(INTENT_DATA_SILENT, silent);
            intent.putExtra(INTENT_DATA_DESCRIPTION, descStr);
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

    private List<Timer> readState() {
        List<Timer> recentList = new ArrayList<Timer>(MAX_RECENT_TIMERS + 1);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(openFileInput(RECENT_TIMERS_FILE));
            while (true) {
                try {
                    Object object = ois.readObject();
                    if (object == null) {
                        break;
                    }
                    if (object instanceof Timer) {
                        recentList.add((Timer) object);
                    } else {
                        Log.w(TAG, "Object was not of class Timer!");
                    }
                } catch (ClassNotFoundException e) {
                    /* This should not happen, if it does just silently retry */
                    Log.w(TAG, e);
                }
            }
        } catch (FileNotFoundException e) {
            /* Thats ok, just use default timer */
        } catch (EOFException e) {
            /* Thats ok too, this is normal */
        } catch (IOException e) {
            /* Use default timer, if recentList is empty */
            Log.w(TAG, e);
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Log.w(TAG, e);
                }
            }
        }

        /* If our list happens to be empty, just add the default list */
        if (recentList.size() == 0) {
            recentList.add(DEFAULT_TIMER);
        }

        return recentList;
    }

    private void saveState(Timer newTimer) {
        m_recentTimers.remove(newTimer);
        m_recentTimers.add(0, newTimer);
        while (m_recentTimers.size() > MAX_RECENT_TIMERS) {
            m_recentTimers.remove(MAX_RECENT_TIMERS);
        }

        try {
            ObjectOutputStream oos = new ObjectOutputStream(openFileOutput(RECENT_TIMERS_FILE, MODE_PRIVATE));
            for (Timer t : m_recentTimers) {
                oos.writeObject(t);
            }
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
        } catch (IOException e) {
            /* Well just tough luck */
            Log.w(TAG, e);
        }
    }

}
