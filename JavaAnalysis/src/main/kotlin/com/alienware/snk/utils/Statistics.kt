package com.alienware.snk.utils

import java.io.File
import java.lang.Exception

object Statistics {
    private val SAVE_DIR = "results"

    val SAVE_SERVICE_API = "service_api.json"

    init {
        if (!File(SAVE_DIR).exists()) {
            File(SAVE_DIR).mkdir()
        }
    }

    fun saveResult(json: String, path: String) {
        try {
            File("$SAVE_DIR/$path").writeText(json)
        } catch (ex: Exception) {
            DebugTool.panic(ex)
        }
    }
}