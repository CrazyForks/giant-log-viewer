import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    kotlin("multiplatform") version "2.0.20"
    id("org.jetbrains.compose") version "1.6.11"
//    kotlin("jvm") version "2.0.20"
    kotlin("plugin.compose") version "2.0.20"
}

group = "com.sunnychung.application"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

//dependencies {
////    testImplementation(kotlin("test"))
//
//}
//
//tasks.test {
//    useJUnitPlatform()
//}

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.github.sunny-chung:bigtext-ui-composable:2.1.0")
                implementation("io.github.sunny-chung:kdatetime-multiplatform:1.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        val distributionVersion = "^(\\d+\\.\\d+\\.\\d+).*".toRegex()
            .matchEntire(project.version.toString())!!
            .groupValues[1]

        mainClass = "com.sunnychung.application.multiplatform.giantlogviewer.MainKt"
        jvmArgs += "-Xmx80m"
        jvmArgs += "-XX:MaxMetaspaceSize=40m"
        jvmArgs += "-XX:+UseStringDeduplication"
//        jvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5025" // to enable debugger for debug use only
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Giant Log Viewer"
            vendor = "Sunny Chung"
            copyright = "© 2025 Sunny Chung"
            packageVersion = distributionVersion
        }
    }
}

// BEGIN Create build info properties

fun getGitCommitHash(): String {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}

tasks.create("createBuildProperties") {
    doFirst {
        val file = File("$buildDir/resources/build.properties")
        file.parentFile.mkdirs()
        file.writer().use { writer ->
            val p = Properties()
            p["version"] = project.version.toString()
//            p["git.commit"] = getGitCommitHash() // FIXME
            p.store(writer, null)
        }
    }
}

tasks.getByName("jvmProcessResources") {
    dependsOn("createBuildProperties")
}

// END Create build info properties

// BEGIN Test

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-Xmx100m")
}

// END Test
