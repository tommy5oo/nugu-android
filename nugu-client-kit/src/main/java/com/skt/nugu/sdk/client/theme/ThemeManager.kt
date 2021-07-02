/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.client.theme

import com.skt.nugu.sdk.core.utils.Logger
import java.util.*

class ThemeManager : ThemeManagerInterface {
    companion object {
        private const val TAG = "ThemeManager"
        val DEFAULT_THEME = ThemeManagerInterface.THEME.LIGHT
    }

    override var theme: ThemeManagerInterface.THEME = DEFAULT_THEME
        set(value) {
            field = value

            listeners.forEach {
                it.onThemeChange(field)
            }
        }

    private val listeners by lazy { Collections.synchronizedList(arrayListOf<ThemeManagerInterface.ThemeListener>()) }

    override fun addListener(listener: ThemeManagerInterface.ThemeListener) {
        if (!listeners.contains(listener)) {
            Logger.d(TAG, "addListener $listener")
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: ThemeManagerInterface.ThemeListener) {
        Logger.d(TAG, "removeListener $listener")
        listeners.remove(listener)
    }
}