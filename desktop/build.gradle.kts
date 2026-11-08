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
            description = "EntityPulse - Social Media & Feed Aggregator (PC Desktop)"
            copyright = "Copyright (c) 2026 EntityPulse"
            vendor = "EntityPulse"

            modules("java.base", "java.desktop", "java.sql", "java.naming", "java.management", "java.security.jgss", "java.instrument", "jdk.unsupported")
        }
    }
}

tasks.register<Zip>("packagePortableZip") {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("EntityPulse-Windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
