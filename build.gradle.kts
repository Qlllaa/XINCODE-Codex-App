buildscript {
    ext {
        compose_ui_version = "1.7.5"
        material3_version = "1.3.0"
        activity_compose_version = "1.9.3"
        coroutine_version = "1.8.1"
        okhttp_version = "4.12.0"
        work_runtime_version = "2.9.1"
        rhino_version = "1.7.14"
        libsu_version = "5.2.2"
        maven_url = "https://jitpack.io"
    }
}

plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.android.library") version "8.6.1" apply false
}