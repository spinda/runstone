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

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Switch

class MainActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        val TAG = MainActivity::class.java.simpleName!!
    }

    private lateinit var stopwatchSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stopwatchSwitch = findViewById(R.id.stopwatchSwitch)
        stopwatchSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!CommunicationService.isServiceActive) {
                    CommunicationService.startService(applicationContext)
                }
            } else {
                if (CommunicationService.isServiceActive) {
                    CommunicationService.stopService(applicationContext)
                }
            }
        }

        stopwatchSwitch.isChecked = CommunicationService.isServiceActive
    }
}