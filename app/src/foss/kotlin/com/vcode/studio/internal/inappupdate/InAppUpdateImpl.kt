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

package com.vcode.studio.internal.inappupdate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vcode.studio.internal.provider.inappupdate.InAppUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Looks at the latest build on the GitHub releases of the app. A new build is announced with the update dialog, and
 * Install downloads the APK and opens the installer of Android.
 */
internal class InAppUpdateImpl(private val activity: Activity) : InAppUpdate {

    @Volatile
    private var apkUrl: String? = null

    override fun checkForUpdates(onUpdateAvailable: () -> Unit) {
        Thread {
            try {
                val connection = URL(RELEASE_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT
                connection.readTimeout = TIMEOUT
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@Thread
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latest = VERSION_CODE.find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@Thread
                if (latest <= installedVersionCode()) return@Thread
                val assets = release.optJSONArray("assets") ?: return@Thread
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrEmpty()) return@Thread
                activity.runOnUiThread(onUpdateAvailable)
            } catch (e: Exception) {
                // No network or no release: no dialog
            }
        }.apply { isDaemon = true }.start()
    }

    override fun installUpdate() {
        val url = apkUrl ?: return
        toast("Downloading the update...")
        Thread {
            try {
                val file = File(File(activity.cacheDir, "updates"), "Toolix.apk")
                file.parentFile?.mkdirs()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT
                connection.readTimeout = DOWNLOAD_TIMEOUT
                connection.instanceFollowRedirects = true
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                activity.runOnUiThread { install(file) }
            } catch (e: Exception) {
                toast("The update could not be downloaded: " + (e.message ?: e.javaClass.simpleName))
            }
        }.apply { isDaemon = true }.start()
    }

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            toast("Allow this app to install apps, then open it again to install the update")
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)),
            )
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    private fun installedVersionCode(): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun toast(text: String) {
        activity.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val RELEASE_URL = "https://api.github.com/repos/Christianzz4603/Vcode/releases/tags/foss-latest"
        const val TIMEOUT = 10_000
        const val DOWNLOAD_TIMEOUT = 60_000
        val VERSION_CODE = Regex("versionCode:\\s*(\\d+)")
    }
}
