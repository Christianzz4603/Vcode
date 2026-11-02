/*
 * Copyright Vcode contributors.
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

package com.vcode.studio.application.toolz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vcode.studio.R
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.feature.settings.api.navigation.HeaderListRoute
import com.vcode.studio.navigation.api.Navigator
import java.io.File

internal enum class ToolzTab(val label: String, val icon: Int) {
    Files("Files", R.drawable.toolz_ic_folder),
    Vcode("Vcode", R.drawable.toolz_ic_code),
    Disassembler("Disassembler", R.drawable.toolz_ic_chip),
    Decompiler("Decompiler", R.drawable.toolz_ic_doc),
    Settings("Settings", R.drawable.toolz_ic_settings),
}

/**
 * The screen of Toolix: a bottom bar with the file manager, the Vcode editor, the disassembler, the decompiler
 * and the settings (the same settings screens as in Vcode, shown on the [navigator] back stack). [vcodeContent] is the whole Vcode app, it stays in the composition so that open files are kept.
 */
@Composable
internal fun ToolzShell(
    navigator: Navigator,
    onOpenInEditor: (File) -> Unit,
    vcodeContent: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(ToolzTab.Files.ordinal) }
    var directory by rememberSaveable { mutableStateOf<String?>(null) }
    var disassemblePath by rememberSaveable { mutableStateOf<String?>(null) }
    var decompilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsBase by rememberSaveable { mutableIntStateOf(0) }
    val colors = VcodeTheme.colors
    val isNavTab = tab == ToolzTab.Vcode.ordinal || tab == ToolzTab.Settings.ordinal

    BackHandler(enabled = !isNavTab && tab != ToolzTab.Files.ordinal) {
        tab = ToolzTab.Files.ordinal
    }

    // The settings were closed with the back button of their toolbar: go back to the editor tab
    val stackSize = navigator.backStack.size
    LaunchedEffect(tab, stackSize) {
        if (tab == ToolzTab.Settings.ordinal && stackSize <= settingsBase) {
            tab = ToolzTab.Vcode.ordinal
        }
    }

    fun select(item: ToolzTab) {
        if (item == ToolzTab.Settings) {
            if (tab != item.ordinal) {
                settingsBase = navigator.backStack.size
                navigator.navigate(HeaderListRoute)
                tab = item.ordinal
            }
            return
        }
        if (tab == ToolzTab.Settings.ordinal) {
            while (navigator.backStack.size > settingsBase && navigator.backStack.size > 1) {
                navigator.goBack()
            }
        }
        tab = item.ordinal
    }

    Scaffold(
        backgroundColor = colors.colorBackgroundPrimary,
        bottomBar = {
            BottomNavigation(
                backgroundColor = colors.colorBackgroundSecondary,
                contentColor = colors.colorPrimary,
            ) {
                ToolzTab.entries.forEach { item ->
                    BottomNavigationItem(
                        selected = tab == item.ordinal,
                        onClick = { select(item) },
                        icon = {
                            Icon(
                                painter = painterResource(item.icon),
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = VcodeTheme.typography.text12Regular,
                                maxLines = 1,
                            )
                        },
                        selectedContentColor = colors.colorPrimary,
                        unselectedContentColor = colors.colorTextAndIconSecondary,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            Box(if (isNavTab) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                vcodeContent()
            }
            when (ToolzTab.entries[tab]) {
                ToolzTab.Files -> FileManagerScreen(
                    directory = directory,
                    onDirectoryChange = { directory = it },
                    onOpenInEditor = {
                        onOpenInEditor(it)
                        tab = ToolzTab.Vcode.ordinal
                    },
                    onDisassemble = {
                        disassemblePath = it.absolutePath
                        tab = ToolzTab.Disassembler.ordinal
                    },
                    onDecompile = {
                        decompilePath = it.absolutePath
                        tab = ToolzTab.Decompiler.ordinal
                    },
                )
                ToolzTab.Disassembler -> LibraryToolScreen(ToolMode.Disassemble, disassemblePath)
                ToolzTab.Decompiler -> LibraryToolScreen(ToolMode.Decompile, decompilePath)
                ToolzTab.Vcode, ToolzTab.Settings -> Unit
            }
        }
    }
}
