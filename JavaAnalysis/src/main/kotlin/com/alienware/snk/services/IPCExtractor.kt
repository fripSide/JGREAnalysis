package com.alienware.snk.services

import com.alienware.snk.CONFIG
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import ServiceApiList
import ApiData
import com.alienware.snk.utils.Statistics
import com.beust.klaxon.Klaxon
import soot.PackManager
import soot.Scene
import soot.SootMethod
import soot.options.Options

class IPCExtractor {

    val apiList = ServiceApiList()

    fun extractIPCMethods() {
        var cls = CONFIG.ANDROID_JAR
//        cls = "E:\\PaperWork\\JGRE\\AndroidApiExtract-master\\cls"
        SootTool.initSootSimply("", cls)
//        SootTool.printSootClasses()

//        PackManager.v().writeOutput()
        serviceHelperExtractor()
        serviceAPIExtractor()
    }

    fun saveResults() {
        val ret = HashSet<ApiData>()
        ServiceApiList.allApi.forEach { api ->
            ret.add(api.getDataClass())
        }
        val data =  Klaxon().toJsonString(ret)
        Statistics.saveResult(data, Statistics.SAVE_SERVICE_API)
    }

    private fun serviceHelperExtractor() {
        val cls = ServiceHelperExtractor.findRegisteredServiceHelperClass()
        LogNow.info("Total Service Helper: ${cls.size}")
        apiList.addServiceApiList(cls)
    }

    /*
    Extract service API and their register name in SystemService
     */
    private fun serviceAPIExtractor() {
        val cls = ServiceImplExtractor.extractServiceImpl()
        LogNow.info("Total Service API: ${cls.size}")
        apiList.addServiceApiList(cls)
    }
}

fun runIPCExtractor(): ServiceApiList {
    val ipc = IPCExtractor()
    ipc.extractIPCMethods()
    ipc.saveResults()
    return ipc.apiList
}