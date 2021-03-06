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

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.util.Log
import com.getpebble.android.kit.Constants
import com.getpebble.android.kit.PebbleKit
import com.getpebble.android.kit.util.PebbleDictionary
import java.util.*

class MainService : JobService() {
    private enum class ServiceState {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    companion object {
        @JvmStatic
        val TAG = MainService::class.java.simpleName!!

        private var serviceState = ServiceState.STOPPED
        private val stopwatch = Stopwatch()
        private const val jobId: Int = 0

        val isServiceActive
            get() = serviceState == ServiceState.STARTING || serviceState == ServiceState.STARTED

        fun startService(context: Context) {
            serviceState = ServiceState.STARTING
            stopwatch.start()

            val timerJobServiceComponent = ComponentName(context, MainService::class.java)
            val timerJobTask = JobInfo.Builder(jobId, timerJobServiceComponent)
                    .setMinimumLatency(1)
                    .setOverrideDeadline(1)
                    .setBackoffCriteria(1, JobInfo.BACKOFF_POLICY_LINEAR)
                    .build()
            getJobScheduler(context).schedule(timerJobTask)
        }

        fun stopService(context: Context) {
            serviceState = ServiceState.STOPPING
            stopwatch.stop()

            getJobScheduler(context).cancel(jobId)
        }

        private fun getJobScheduler(context: Context): JobScheduler {
            return context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        }

        private var tts: TtsWrapper? = null

        private var lastSpokenMinutes = 0L

        private fun resetLastSpokenMinutes() {
            lastSpokenMinutes = 0L
        }

        private fun isUnspokenMinutes(minutes: Long): Boolean {
            return minutes > lastSpokenMinutes
        }
    }

    private var pebbleDataReceiver: PebbleKit.PebbleDataReceiver? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val initialServiceState = serviceState
        serviceState = ServiceState.STARTED

        Log.i(TAG, "Job start (initialServiceState = $initialServiceState)")

        if (tts == null) {
            tts = TtsWrapper(applicationContext)
        }

        if (initialServiceState == ServiceState.STARTING) {
            tts?.speak(getString(R.string.voice_started))
            resetLastSpokenMinutes()
        }

        // Start the built-in Pebble Sports app (required for the Sports API to function properly).
        PebbleKit.startAppOnPebble(applicationContext, Constants.SPORTS_UUID)

        // Start Music Boss Sport, if it's installed.
        PebbleKit.startAppOnPebble(applicationContext, UUID.fromString("10e5ae7b-9b4b-4fd0-9708-4bf9bce5033d"))

        val pebbleDataReceiver = object : PebbleKit.PebbleDataReceiver(Constants.SPORTS_UUID) {
            override fun receiveData(context: Context?, id: Int, data: PebbleDictionary?) {
                PebbleKit.sendAckToPebble(applicationContext, id)

                if (data == null) {
                    return
                }

                val state = data.getUnsignedIntegerAsLong(Constants.SPORTS_STATE_KEY).toInt()
                when (state) {
                    1 -> { // Pause signal
                        if (!stopwatch.isPaused) {
                            Log.i(TAG, "Pausing")
                            stopwatch.pause()
                            communicateTime()
                            stopPeriodicCommunicaton()
                            tts?.speak(getString(R.string.voice_paused))
                        }
                    }
                    2 -> { // Resume signal
                        if (stopwatch.isPaused) {
                            Log.i(TAG, "Resuming")
                            stopwatch.resume()
                            communicateTime()
                            startPeriodicCommunication()
                            tts?.speak(getString(R.string.voice_resumed))
                        }
                    }
                }
            }
        }
        PebbleKit.registerReceivedDataHandler(applicationContext, pebbleDataReceiver)
        this.pebbleDataReceiver = pebbleDataReceiver

        communicateTime()
        startPeriodicCommunication()

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        val initialServiceState = serviceState
        serviceState = ServiceState.STOPPED

        Log.i(TAG, "Job stop (initialServiceState = $initialServiceState)")

        val tickTimerRunnable = periodicCommunicationRunnable
        this.periodicCommunicationRunnable = null
        if (tickTimerRunnable != null) {
            stopTimer(tickTimerRunnable)
        }

        val pebbleDataReceiver = pebbleDataReceiver
        this.pebbleDataReceiver = null
        if (pebbleDataReceiver != null) {
            try {
                applicationContext.unregisterReceiver(pebbleDataReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister ${PebbleKit.PebbleDataReceiver::class.java.simpleName}", e)
            }
        }

        // Close the built-in Pebble Sports app, if open (required for the Sports API to function properly).
        PebbleKit.closeAppOnPebble(applicationContext, Constants.SPORTS_UUID);

        if (initialServiceState == ServiceState.STOPPING) {
            tts?.speak(getString(R.string.voice_stopped))
        }

        // Restart if we weren't meant to be stopping now (the Android system is shutting us down for whatever reason).
        return initialServiceState != ServiceState.STOPPED
    }

    private var periodicCommunicationRunnable: Runnable? = null

    private fun startPeriodicCommunication() {
        periodicCommunicationRunnable = startTimer(1000, Runnable {
            communicateTime()
        })
    }

    private fun stopPeriodicCommunicaton() {
        val pebbleDisplayPeriodicUpdatesRunnable = periodicCommunicationRunnable
        this.periodicCommunicationRunnable = null
        if (pebbleDisplayPeriodicUpdatesRunnable != null) {
            stopTimer(pebbleDisplayPeriodicUpdatesRunnable)
        }
    }

    private fun communicateTime() {
        val elapsedTime = stopwatch.elapsedTime

        val minutes = elapsedTime / 60L
        val seconds = elapsedTime % 60L
        val timeString =
                "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

        val msg = PebbleDictionary()
        msg.addString(Constants.SPORTS_TIME_KEY, timeString)
        msg.addString(Constants.SPORTS_DISTANCE_KEY, "0.0")
        msg.addString(Constants.SPORTS_DATA_KEY, "0.00")
        msg.addUint8(Constants.SPORTS_LABEL_KEY, Constants.SPORTS_DATA_PACE.toByte())
        msg.addUint8(Constants.SPORTS_UNITS_KEY, Constants.SPORTS_UNITS_METRIC.toByte())
        PebbleKit.sendDataToPebble(applicationContext, Constants.SPORTS_UUID, msg)

        if (isUnspokenMinutes(minutes)) {
            tts?.speak(if (minutes == 1L) {
                getString(R.string.voice_minute)
            } else {
                getString(R.string.voice_minutes_template).format(minutes)
            }, false)
            lastSpokenMinutes = minutes
        }
    }

    private val handler: Handler = Handler()

    private fun startTimer(interval: Long, runnable: Runnable): Runnable {
        val timerRunnable = object : Runnable {
            override fun run() {
                runnable.run()
                handler.postDelayed(this, interval)
            }
        }
        handler.postDelayed(timerRunnable, interval)
        return timerRunnable
    }

    private fun stopTimer(timerRunnable: Runnable) {
        handler.removeCallbacks(timerRunnable)
    }
}