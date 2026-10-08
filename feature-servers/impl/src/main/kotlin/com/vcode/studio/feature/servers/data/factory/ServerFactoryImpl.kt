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

package com.vcode.studio.feature.servers.data.factory

import com.vcode.studio.feature.servers.api.factory.ServerFactory
import com.vcode.studio.filesystem.base.Filesystem
import com.vcode.studio.filesystem.base.model.ServerConfig
import com.vcode.studio.filesystem.base.model.ServerType
import com.vcode.studio.filesystem.ftp.FTPFilesystem
import com.vcode.studio.filesystem.ftps.FTPSFilesystem
import com.vcode.studio.filesystem.sftp.SFTPFilesystem
import java.io.File

internal class ServerFactoryImpl(
    private val cacheDir: File,
    private val keysDir: File,
) : ServerFactory {

    override fun create(serverConfig: ServerConfig): Filesystem {
        return when (serverConfig.scheme) {
            ServerType.FTP -> FTPFilesystem(serverConfig, cacheDir)
            ServerType.FTPS -> FTPSFilesystem(serverConfig, cacheDir, isImplicit = true)
            ServerType.FTPES -> FTPSFilesystem(serverConfig, cacheDir, isImplicit = false)
            ServerType.SFTP -> SFTPFilesystem(serverConfig, cacheDir, keysDir)
        }
    }
}