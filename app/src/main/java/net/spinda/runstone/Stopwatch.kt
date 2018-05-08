/* This file is part of RunStone, a simple run timer app for use with Android
 * and the Pebble smartwatch.
 *
 * Copyright (C) 2018 Michael Smith <michael@spinda.net>
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package net.spinda.runstone

import android.os.SystemClock

class Stopwatch {
    companion object {
        val instance = Stopwatch()
    }

    private var startTimestamp: Long = 0
    private var pauseTimestamp: Long = 0

    val isStarted: Boolean
        get() = startTimestamp != 0L

    val isPaused: Boolean
        get() = pauseTimestamp != 0L

    val elapsedTime: Long
        get() {
            if (!isStarted) {
                return 0
            }

            val now = timestampInSeconds()
            var result = now - startTimestamp
            if (isPaused) {
                result -= now - pauseTimestamp
            }
            return result
        }

    fun start() {
        startTimestamp = timestampInSeconds()
    }

    fun pause() {
        pauseTimestamp = timestampInSeconds()
    }

    fun resume() {
        startTimestamp += timestampInSeconds() - pauseTimestamp
        pauseTimestamp = 0
    }

    fun stop() {
        startTimestamp = 0
        pauseTimestamp = 0
    }

    private fun timestampInSeconds(): Long {
        return SystemClock.elapsedRealtime() / 1000
    }
}
