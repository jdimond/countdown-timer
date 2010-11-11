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

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class TimerSpinnerAdapter extends BaseAdapter {

    private final List<Timer> m_timers;
    private final LayoutInflater m_inflater;

    public TimerSpinnerAdapter(Context context, List<Timer> timers) {
        m_timers = timers;
        m_inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return m_timers.size();
    }

    @Override
    public Object getItem(int position) {
        return m_timers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView text;
        if (convertView == null) {
            text = (TextView) m_inflater.inflate(android.R.layout.simple_spinner_item, null);
        } else {
            text = (TextView) convertView;
        }

        text.setText(R.string.recently_used);
        return text;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return generateFromResource(R.layout.spinner_dropdown_view, true, m_timers.get(position), convertView, parent);
    }

    private View generateFromResource(int resourceId, boolean icon, Timer timer, View convertView, ViewGroup parent) {
        View layout;
        if (convertView == null) {
            layout = m_inflater.inflate(resourceId, null);
        } else {
            layout = convertView;
        }
        TextView timeText = (TextView) layout.findViewById(R.id.spinner_time);
        if (icon) {
            int which = (timer.isSilent()) ? R.drawable.speaker_mute : R.drawable.speaker;
            timeText.setCompoundDrawablesWithIntrinsicBounds(which, 0, 0, 0);
        }
        TextView descriptionText = (TextView) layout.findViewById(R.id.spinner_description);

        String timeString = String.format("%02d:%02d:%02d", timer.getHours(), timer.getMinutes(), timer.getSeconds());
        timeText.setText(timeString);
        descriptionText.setText(timer.getDescription());
        return layout;
    }

}
