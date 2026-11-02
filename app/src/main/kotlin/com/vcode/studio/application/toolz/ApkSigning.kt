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

import android.content.Context
import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/** Signs APK files with a key that is made on the first use and kept in the files of the app. */
internal object ApkSigning {

    private const val ALIAS = "toolz"
    private const val DAY = 86_400_000L
    private val PASSWORD = "software-toolz".toCharArray()

    private class SigningKey(val privateKey: PrivateKey, val certificate: X509Certificate)

    private fun signingKey(context: Context): SigningKey {
        val store = File(context.filesDir, "toolz-signing.p12")
        val keyStore = KeyStore.getInstance("PKCS12")
        if (store.exists()) {
            store.inputStream().use { keyStore.load(it, PASSWORD) }
        } else {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair = generator.generateKeyPair()
            val name = X500Name("CN=Toolix")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                Date(now - DAY),
                Date(now + 30 * 365 * DAY),
                name,
                pair.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
            val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            keyStore.load(null, null)
            keyStore.setKeyEntry(ALIAS, pair.private, PASSWORD, arrayOf(certificate))
            store.outputStream().use { keyStore.store(it, PASSWORD) }
        }
        val privateKey = keyStore.getKey(ALIAS, PASSWORD) as PrivateKey
        val certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        return SigningKey(privateKey, certificate)
    }

    /** Writes the signed copy of [input] to [output]. */
    fun sign(context: Context, input: File, output: File) {
        val key = signingKey(context)
        val config = ApkSigner.SignerConfig.Builder("toolz", key.privateKey, listOf(key.certificate)).build()
        val temp = File(output.parentFile, output.name + ".tmp")
        try {
            ApkSigner.Builder(listOf(config))
                .setInputApk(input)
                .setOutputApk(temp)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()
            if (output.exists()) output.delete()
            if (!temp.renameTo(output)) {
                temp.copyTo(output, overwrite = true)
            }
        } finally {
            temp.delete()
        }
    }
}
