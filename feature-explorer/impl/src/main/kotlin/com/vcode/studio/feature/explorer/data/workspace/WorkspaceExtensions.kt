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

package com.vcode.studio.feature.explorer.data.workspace

import android.content.Context
import android.os.Environment
import com.vcode.studio.feature.explorer.R
import com.vcode.studio.feature.explorer.domain.model.WorkspaceModel
import com.vcode.studio.feature.explorer.domain.model.WorkspaceType
import com.vcode.studio.filesystem.base.model.FileModel
import com.vcode.studio.filesystem.local.LocalFilesystem
import com.vcode.studio.filesystem.root.RootFilesystem

internal const val LOCAL_WORKSPACE_ID = "local_workspace"
internal const val ROOT_WORKSPACE_ID = "root_workspace"

internal fun Context.createLocalWorkspace(): WorkspaceModel {
    return WorkspaceModel(
        uuid = LOCAL_WORKSPACE_ID,
        name = getString(R.string.explorer_workspace_button_files),
        type = WorkspaceType.LOCAL,
        defaultLocation = FileModel(
            fileUri = LocalFilesystem.LOCAL_SCHEME +
                Environment.getExternalStorageDirectory().absolutePath,
            filesystemUuid = LocalFilesystem.LOCAL_UUID,
            isDirectory = true,
        ),
    )
}

internal fun Context.createRootWorkspace(): WorkspaceModel {
    return WorkspaceModel(
        uuid = ROOT_WORKSPACE_ID,
        name = getString(R.string.explorer_workspace_button_root),
        type = WorkspaceType.ROOT,
        defaultLocation = FileModel(
            fileUri = RootFilesystem.ROOT_SCHEME,
            filesystemUuid = RootFilesystem.ROOT_UUID,
            isDirectory = true,
        ),
    )
}