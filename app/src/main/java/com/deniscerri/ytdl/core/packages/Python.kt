package com.deniscerri.ytdl.core.packages

object Python : PackageBase() {
    override val executableName: String get() = "python"
    override val packageFolderName: String get() = "python"
    override val bundledZipName: String get() = "libpython.zip.so"
    // Not shipped in the APK anymore: null keeps every "is this bundled?" check honest, so the
    // packages screen reads "not installed" and auto-update stops nagging about a missing package.
    override val bundledVersion: String? get() = null
    // Nothing runs without the interpreter, so a missing Python is what the gate prompts for.
    override val isRequired: Boolean get() = true
    override val canUninstall: Boolean = false
    override val githubRepo: String  get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  get() = "python"
    override val apkPackage: String get() = "com.deniscerri.ytdl.python"
}
