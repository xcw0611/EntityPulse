import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines
    implementation(libs.kotlinx.coroutines.swing)

    // Network & JSON
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.json.org)

    // SQLite JDBC for Desktop JVM
    implementation(libs.sqlite.jdbc)
}

compose.desktop {
    application {
        mainClass = "com.entitypulse.desktop.MainKt"
        nativeDistributions {
            packageName = "EntityPulse"
            packageVersion = "1.0.0"
            description = "EntityPulse - 全网社媒与动态聚合 (PC桌面端)"
            copyright = "© 2026 EntityPulse"
            vendor = "EntityPulse"
        }
    }
}

tasks.register<Zip>("packagePortableZip") {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("EntityPulse-Windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
