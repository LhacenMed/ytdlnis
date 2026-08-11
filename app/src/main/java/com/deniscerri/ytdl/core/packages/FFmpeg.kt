package com.deniscerri.ytdl.core.packages

object FFmpeg : PackageBase() {
    override val executableName: String get() = "ffmpeg"
    override val packageFolderName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    // Not shipped in the APK anymore. See Python for why this is null rather than a version.
    override val bundledVersion: String? get() = null
    override val canUninstall: Boolean = false
    override val githubRepo: String  get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  get() = "ffmpeg"
    override val apkPackage: String get() = "com.deniscerri.ytdl.ffmpeg"
}
