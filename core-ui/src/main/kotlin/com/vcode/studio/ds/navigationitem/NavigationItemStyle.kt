/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vcode.studio.ds.navigationitem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.vcode.studio.ds.VcodeTheme

@Immutable
data class NavigationItemStyle(
    val selectionColor: Color,
    val iconColorSelected: Color,
    val iconColorUnselected: Color,
    val textColorSelected: Color,
    val textColorUnselected: Color,
    val disabledIconColor: Color,
    val disabledTextColor: Color,
)

object NavigationItemStyleDefaults {

    val Default: NavigationItemStyle
        @Composable
        @ReadOnlyComposable
        get() = NavigationItemStyle(
            selectionColor = VcodeTheme.colors.colorBackgroundTertiary,
            iconColorSelected = VcodeTheme.colors.colorTextAndIconPrimary,
            iconColorUnselected = VcodeTheme.colors.colorTextAndIconSecondary,
            textColorSelected = VcodeTheme.colors.colorTextAndIconPrimary,
            textColorUnselected = VcodeTheme.colors.colorTextAndIconSecondary,
            disabledIconColor = VcodeTheme.colors.colorTextAndIconDisabled,
            disabledTextColor = VcodeTheme.colors.colorTextAndIconDisabled,
        )
}