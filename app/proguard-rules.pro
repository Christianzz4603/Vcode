# Compose type-safe navigation
-keep @kotlinx.serialization.Serializable class * {
    *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepnames class * {
    @kotlinx.serialization.SerialName <fields>;
}

# TextMate
-keep class org.eclipse.tm4e.** { *; }

# JGit
-keep class org.eclipse.jgit.** { *; }
-dontwarn javax.management.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn org.eclipse.jgit.util.Monitoring

# BouncyCastle
-dontwarn com.sun.jna.Library
-dontwarn com.sun.jna.Memory
-dontwarn com.sun.jna.Native
-dontwarn com.sun.jna.Platform
-dontwarn com.sun.jna.Pointer
-dontwarn com.sun.jna.Structure
-dontwarn com.sun.jna.platform.win32.BaseTSD$ULONG_PTR
-dontwarn com.sun.jna.platform.win32.WinDef$LPARAM
-dontwarn com.sun.jna.platform.win32.WinDef$LRESULT
-dontwarn com.sun.jna.platform.win32.WinUser$COPYDATASTRUCT
-dontwarn com.sun.jna.platform.win32.Kernel32
-dontwarn com.sun.jna.platform.win32.User32
-dontwarn com.sun.jna.platform.win32.WinBase$SECURITY_ATTRIBUTES
-dontwarn com.sun.jna.platform.win32.WinBase
-dontwarn com.sun.jna.platform.win32.WinDef$HWND
-dontwarn com.sun.jna.platform.win32.WinDef$WPARAM
-dontwarn com.sun.jna.platform.win32.WinNT$HANDLE

# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.sun.jna.win32.W32APIOptions
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.MessageProp
-dontwarn org.ietf.jgss.Oid
-dontwarn org.newsclub.net.unix.AFUNIXServerSocketChannel
-dontwarn org.newsclub.net.unix.AFUNIXSocketAddress
-dontwarn org.newsclub.net.unix.AFUNIXSocketChannel
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory

# Vcode: JavaScript bridge used by Run
-keepclassmembers class com.vcode.studio.feature.editor.ui.editor.panel.CodeRunner$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Vcode: smali and baksmali are used to edit DEX files
-keep class com.android.tools.smali.** { *; }
-keep class org.antlr.runtime.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.antlr.stringtemplate.**
-dontwarn com.beust.jcommander.**

# Vcode: the library viewer calls into Rust
-keep class com.vcode.studio.core.disasm.NativeDisassembler { *; }
