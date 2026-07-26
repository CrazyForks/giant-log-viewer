package com.sunnychung.application.multiplatform.giantlogviewer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sunnychung.application.giantlogviewer.generated.resources.Res
import com.sunnychung.application.giantlogviewer.generated.resources.appicon
import com.sunnychung.application.multiplatform.giantlogviewer.extension.setMinimumSize
import com.sunnychung.application.multiplatform.giantlogviewer.manager.AppContext
import com.sunnychung.application.multiplatform.giantlogviewer.ux.App
import com.sunnychung.application.multiplatform.giantlogviewer.util.LogLevel
import com.sunnychung.application.multiplatform.giantlogviewer.util.log
import kotlinx.coroutines.runBlocking
import net.harawata.appdirs.AppDirsFactory
import org.jetbrains.compose.resources.painterResource
import java.io.File

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.appearance", "system")

    // Parse log level from command line arguments before initializing app
    val initialToastMessage = parseLogLevel(args)

    val appDir = AppDirsFactory.getInstance().getUserDataDir("Giant Log Viewer", null, null)
    AppContext.instance.dataDir = File(appDir)
    runBlocking {
        AppContext.instance.ResourceManager.loadAllResources()
    }

    // Parse command line arguments for initial file path
    val initialFilePath = parseInitialFilePath(args)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Giant Log Viewer",
            icon = painterResource(Res.drawable.appicon),
        ) {
            setMinimumSize(250.dp, 150.dp)
            App(
                onExitApplication = ::exitApplication,
                initialFilePath = initialFilePath,
                initialToastMessage = initialToastMessage
            )
        }
    }
}

/**
 * Parse log level from command line arguments.
 * Supports: --logLevel=LEVEL or --logLevel LEVEL (case-insensitive)
 * Valid levels: VERBOSE, DEBUG, INFO, WARN, ERROR
 * Returns error message if level is invalid, null otherwise.
 */
fun parseLogLevel(args: Array<String>): String? {
    for (i in args.indices) {
        val arg = args[i]
        // Check for --logLevel=VALUE format
        if (arg.startsWith("--logLevel=", ignoreCase = true)) {
            val levelStr = arg.substringAfter("=")
            val parsedLevel = LogLevel.parseFrom(levelStr)
            if (parsedLevel != null) {
                log.logLevel = parsedLevel
                return null
            } else {
                return "Invalid log level: '$levelStr'. Valid levels are: VERBOSE, DEBUG, INFO, WARN, ERROR. Continuing with current log level."
            }
        }
        // Check for --logLevel VALUE format
        if (arg.equals("--logLevel", ignoreCase = true) && i + 1 < args.size) {
            val levelStr = args[i + 1]
            val parsedLevel = LogLevel.parseFrom(levelStr)
            if (parsedLevel != null) {
                log.logLevel = parsedLevel
                return null
            } else {
                return "Invalid log level: '$levelStr'. Valid levels are: VERBOSE, DEBUG, INFO, WARN, ERROR. Continuing with current log level."
            }
        }
    }
    return null
}

/**
 * Parse command line arguments to extract the initial file path.
 * Supports: "Giant Log Viewer.exe" "C:\path\to\file.log"
 * Skips --logLevel arguments when looking for file path.
 */
fun parseInitialFilePath(args: Array<String>): String? {
    if (args.isEmpty()) return null

    var isSkippingLogLevelValue = false
    val filePath = args.firstOrNull { arg ->
        if (isSkippingLogLevelValue) {
            isSkippingLogLevelValue = false
            false
        } else if (arg.equals("--logLevel", ignoreCase = true)) {
            isSkippingLogLevelValue = true
            false
        } else {
            arg.isNotBlank() && !arg.startsWith("--logLevel=", ignoreCase = true)
        }
    } ?: return null

    val file = File(filePath)
    if (!file.exists() || !file.isFile || !file.canRead()) {
        return null
    }

    return filePath
}
