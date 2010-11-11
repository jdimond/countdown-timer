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

import java.io.Serializable;

public class Timer implements Serializable {
    private static final long serialVersionUID = 9045684051965285109L;

    private final int m_hours;
    private final int m_minutes;
    private final int m_seconds;
    private final String m_description;
    private final boolean m_isSilent;

    public Timer(int hours, int minutes, int seconds, String description, boolean isSilent) {
        this.m_hours = hours;
        this.m_minutes = minutes;
        this.m_seconds = seconds;
        this.m_description = description;
        this.m_isSilent = isSilent;
    }

    public int getSeconds() {
        return m_seconds;
    }

    public int getMinutes() {
        return m_minutes;
    }

    public int getHours() {
        return m_hours;
    }

    public String getDescription() {
        return m_description;
    }

    public boolean isSilent() {
        return m_isSilent;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((m_description == null) ? 0 : m_description.hashCode());
        result = prime * result + m_hours;
        result = prime * result + m_minutes;
        result = prime * result + m_seconds;
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
        Timer other = (Timer) obj;
        if (m_description == null) {
            if (other.m_description != null)
                return false;
        } else if (!m_description.equals(other.m_description))
            return false;
        if (m_hours != other.m_hours)
            return false;
        if (m_minutes != other.m_minutes)
            return false;
        if (m_seconds != other.m_seconds)
            return false;
        return true;
    }

    @Override
    public String toString() {
        if (m_description != null) {
            return String.format("%02d:%02d:%02d (%s)", m_hours, m_minutes, m_seconds, m_description);
        } else {
            return String.format("%02d:%02d:%02d", m_hours, m_minutes, m_seconds);
        }
    }
}
