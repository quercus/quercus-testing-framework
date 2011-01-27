/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * This file is part of Quercus(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Quercus Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Quercus Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Quercus Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 * @author Dominik Dorn
 */


package com.caucho.util;

public class TestAlarm {
    public static void clear() {
        Alarm.testClear();
    }

    public static void setTime(long time) {
        Alarm.setTestTime(time);
    }

    public static void addTime(long delta) {
        Alarm.setTestTime(Alarm.getCurrentTime() + delta);
    }

    public static void setNanoDelta(long delta) {
        Alarm.setTestNanoDelta(delta);
    }
}


