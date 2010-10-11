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

public class Alarm implements Comparable<Alarm> {
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
        if (m_isSilent != other.m_isSilent)
            return false;
        if (m_when != other.m_when)
            return false;
        return true;
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