package com.atlas.ultra.core.model

enum class Platform(val os: String, val arch: String, val classifier: String) {
    LINUX_ARM64("linux", "arm64", "natives-linux-arm64"),
    LINUX_X64("linux", "x64", "natives-linux-x64"),
    WINDOWS_X64("windows", "x64", "natives-windows-x64"),
    MACOS_X64("osx", "x64", "natives-osx-x64"),
    MACOS_ARM64("osx", "arm64", "natives-osx-arm64");

    companion object {
        fun fromTarget(target: String): Platform = when (target.lowercase()) {
            "linux-arm64" -> LINUX_ARM64
            "linux-x64" -> LINUX_X64
            "windows-x64" -> WINDOWS_X64
            "macos-x64" -> MACOS_X64
            "macos-arm64" -> MACOS_ARM64
            else -> throw IllegalArgumentException("Unknown platform: $target")
        }
    }
}