package com.alienware.snk.utils

import soot.Scene
import java.io.File
import java.lang.Exception

object Statistics {
    private val SAVE_DIR = "results"

    val SAVE_SERVICE_API = "service_api.json"

    val RESULTS_TXT = "results.txt"
    val RESULTS_JSON = "results.json"

    /*
    Ground truth from dynamic verification for vulnerability results statistic in different Android Version.
     */
    val groundTruth = mutableListOf<Pair<String, String>>(
            Pair("", "")
    )

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

    fun simpleReport() {
        val apiList = ServiceApiList.allApi
        var helperCnt = 0
        var implCnt = 0
        var helperApiNum = 0
        var implApiNum = 0
        for (api in apiList) {
            if (api.isHelperClass()) {
                helperCnt++
                var cur = 0
                for (mtd in api.helperClass!!.methods) {
                    if (mtd.isPublic && mtd.isConcrete) {
                        cur++
                    }
                }
                helperApiNum += cur
            } else {
                implCnt++
                var cur = 0
                if (api.inf == null) {
                    api.inf = api.implClass
                }
                for (mtd in api.inf!!.methods) {
                    if (mtd.isAbstract) {
                        cur++
                    }
                }
                implApiNum += cur
            }
        }
        val allClsCnt = Scene.v().applicationClasses.size
        LogNow.show("Service Helper Classes: $helperCnt Api: $helperApiNum")
        LogNow.show("System Service Classes: $implCnt Api: $implApiNum")
        LogNow.show("All Classes: $allClsCnt")
    }
}