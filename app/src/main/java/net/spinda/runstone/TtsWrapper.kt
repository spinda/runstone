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

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TtsWrapper(private val context: Context) {
    companion object {
        @JvmStatic
        val TAG = TtsWrapper::class.java.simpleName!!
    }

    private enum class State {
        INITIALIZING,
        FAILED,
        READY
    }

    private var state = State.INITIALIZING

    private var pendingText: String? = null

    private val onInitListener = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.ENGLISH

            state = State.READY

            val pendingText = pendingText
            this.pendingText = null
            if (pendingText != null) {
                speak(pendingText)
            }
        } else {
            state = State.FAILED
            pendingText = null

            Log.e(TAG, "TTS initialization failed")
        }
    }

    private var tts: TextToSpeech

    init {
        tts = TextToSpeech(context, onInitListener)
    }

    fun speak(text: String, interrupt: Boolean = true) {
        when (state) {
            State.INITIALIZING -> {
                pendingText = if (interrupt) {
                    text
                } else {
                    pendingText + "\n" + text
                }
            }
            State.FAILED -> {
                state = State.INITIALIZING
                pendingText = text
                tts = TextToSpeech(context, onInitListener)
            }
            State.READY -> {
                val queueMode = if (interrupt) {
                    tts.stop()
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                }
                tts.speak(text, queueMode, null, null)
            }
        }
    }
}