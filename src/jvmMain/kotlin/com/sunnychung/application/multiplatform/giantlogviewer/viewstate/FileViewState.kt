package com.sunnychung.application.multiplatform.giantlogviewer.viewstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class FileViewState(file: File) {

    var fileLength by mutableStateOf(file.length())
}
