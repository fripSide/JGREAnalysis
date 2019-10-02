package com.alienware.snk.services

import ServiceApiList
import com.alienware.snk.CONFIG
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import com.alienware.snk.utils.Statistics
import soot.Scene
import soot.SootClass
import soot.SootMethod
import ServiceApiClass
import com.alienware.snk.utils.DebugTool
import com.beust.klaxon.Klaxon
import java.lang.StringBuilder

object VulnerabilitiesDetecting {

    val callGraphLev = 4

    val checkExitPointExtractly = true

    private val helperVulSet = HashSet<SootMethod>()

    private val vulSet = HashSet<SootMethod>()
    private val vulNameSet = HashSet<String>()

    private val vulMap = HashMap<SootMethod, VulnerableApiDesc>()

    /*
    1. appos
    2. accessibility manager
     */
    fun detectingVul(apiList: HashSet<ServiceApiClass>) {
        // analysis system service class
        for (api in apiList) {
            var targetCls: SootClass? = api.helperClass
            val focus = "android.view.accessibility.IAccessibilityManager"
            val targetName = api.inf?.name ?: ""
//            if (targetName != focus) continue

            if (!api.isHelperClass()) {
                analysisImplClass(api.implClass!!, api.inf)
            }
        }
        // analysis helper class
        for (api in apiList) {
            if (api.isHelperClass()) {
                analysisHelperClass(api.helperClass!!)
            }
        }

        dumpVul()
    }

    fun analysisImplClass(sc: SootClass, inf: SootClass?) {
        LogNow.info("Start to analysis $sc")
        var methods = getPublicApis(sc)
        if (inf != null) {
            methods = getPublicApis(inf)
        }
//        methods.forEach { mtd ->
//            println(mtd)
//        }
        sc.methods.forEach { mtd ->
            if (mtd.subSignature in methods) {
                if (analysisOneFunction(mtd)) {
                    vulSet.add(mtd)
                }
            }
        }
    }


    fun testOneHelperMethod(sm: SootMethod) {
        val vul = AnalysisHelperApi(sm, vulSet).findVulIPCCall()
        if (vul != null) {
            DebugTool.exitHere()
        }
    }


    private fun analysisHelperClass(sc: SootClass) {
//        val focus = "AppOpsManager"
//        if (!sc.name.endsWith(focus)) return
        sc.methods.forEach {mtd ->
            if (mtd.isPublic && mtd.isConcrete) {
//                LogNow.info("$mtd")
                val vul = AnalysisHelperApi(mtd, vulSet).findVulIPCCall()
                if (vul != null) {
                    helperVulSet.add(mtd)
//                    DebugTool.exitHere()
                }
            }
        }
    }

    private fun setVulInf() {
        vulSet.forEach { vul ->
            vulNameSet.add(vul.subSignature)
        }
    }

    fun addVul(mtd: SootMethod, vul: VulnerableApiDesc) {
        vulSet.add(mtd)
        vulMap[mtd] = vul
    }



    fun dumpVul() {
        vulSet.forEach { println("Find Vul: ${vulMap[it]}") }

        LogNow.show("Vul in helper: ${helperVulSet.size}")
        helperVulSet.forEach { mtd ->
            LogNow.show("$mtd")
        }

        saveSummary()

        saveDetail()

        Statistics.simpleReport()
        LogNow.show("Total Vul Size: ${vulSet.size}")
    }

    private fun getPublicApis(sc: SootClass): HashSet<String> {
        val methods = HashSet<String>()
        sc.methods.forEach { mtd ->
            if (mtd.isPublic) {
                methods.add(mtd.subSignature)
            }
        }
        return methods
    }

    private fun analysisOneFunction(mtd: SootMethod): Boolean {
//        val focus = "addClient"
//        if (mtd.name != focus) return false
        LogNow.info("Analysis IPC method: ${mtd.subSignature}")
        val cg = CallGraphAnalysis(callGraphLev)
        cg.containExitPoint = !checkExitPointExtractly
        val vul = cg.analysisEntryPoint(mtd)
        if (vul != null) {
            LogNow.info("Find Vulnerable Method: $mtd")
            vulMap[mtd] = vul
            return true
        }
        return false
    }

    private fun saveSummary() {
        val data = StringBuilder()
        val results = HashMap<SootClass, HashSet<String>>()
        vulMap.values.forEach { vul ->
            val mtd = vul.entryPoint!!
            val clsName = mtd.declaringClass.name
            val cls = mtd.declaringClass
            if (cls !in results) {
                results[cls] = HashSet<String>()
            }
            results[cls]!!.add(mtd.signature)
        }

        results.forEach { t, u ->
            val api = ServiceApiList.getServiceApiForImpl(t)
            if (api != null) {
                data.append("${api.serviceName} ${api.inf} (${u.size})\n")
            } else {
                data.append("${t.name} (${u.size})\n")
            }

            u.forEach { it ->
                data.append("\t$it\n")
            }
        }

        data.append("Vulnerabilities in Helper Class:\n")
        helperVulSet.forEach { vul ->
            data.append("\t${vul.signature}\n")
        }
        Statistics.saveResult(data.toString(), Statistics.RESULTS_TXT)
    }

    private fun saveDetail() {
        val vulList = mutableListOf<VulData>()
        val results = HashMap<SootClass, HashSet<VulData>>()
        vulMap.values.forEach { vul ->
            val mtd = vul.entryPoint!!
            val clsName = mtd.declaringClass.name
            val cls = mtd.declaringClass
            if (cls !in results) {
                results[cls] = HashSet<VulData>()
            }
            results[cls]!!.add(vul.getData())
        }
        results.forEach{ cls, data ->
            val api = ServiceApiList.getServiceApiForImpl(cls)
            if (api != null) {
                data.forEach { it ->
                    it.serviceName = api.serviceName ?: ""
                    vulList.add(it)
                }
            }
        }

        val data = Klaxon().toJsonString(vulList)
        Statistics.saveResult(data, Statistics.RESULTS_JSON)
    }
}

private fun analysisOneCls(cls: SootClass) {
    val inf = SootTool.getInfForImpl(cls)
    println("$cls $inf")
    if (cls == inf) {
        cls.methods.forEach { mtd ->
            if (mtd.isPublic) {
                println("all method: $mtd")
                val cg = CallGraphAnalysis(3)
                val vul = cg.analysisEntryPoint(mtd)
                if (vul != null) {
//                    println("Vul Detect: $vul")
                    VulnerabilitiesDetecting.addVul(mtd, vul)
                }
            }
        }
    } else {
        VulnerabilitiesDetecting.analysisImplClass(cls, inf)
    }
    VulnerabilitiesDetecting.dumpVul()
}

/*
TODO:
False Positive:
<com.android.server.midi.MidiService: void unregisterListener -> get and add before delete
<com.android.server.wallpaper.WallpaperManagerService: getWallpaper

FN:
"com.android.server.fingerprint.FingerprintService\$FingerprintServiceWrapper": Handler Implicit Call
MidiService.registerDeviceServer, exceed max level

com.android.server.print.PrintManagerService$PrintManagerImpl->addPrintJobStateChangeListener: Implicit call


Is not service API:
MediaSessionRecord.registerCallbackListener
MediaSessionService.createSession

Level 3:
com.android.server.TelephonyRegistry
listenForSubscriber

TODO:
1.Wallpaper extract
2. wifi

 */
fun quickAnalysis() {
    var clsPath = CONFIG.ANDROID_JAR
//    clsPath = "cls/"
    SootTool.initSootSimply("", clsPath)

    run {
        val focusCls = "android.app.AppOpsManager"
        val cls = Scene.v().getSootClass(focusCls)
        val focusMtd = "startWatchingMode"
        for (mtd in cls.methods) {
            if (mtd.name == focusMtd)
                VulnerabilitiesDetecting.testOneHelperMethod(mtd)
        }
    }


    // TaskChangeNotificationController
//    run {
//        val focusCls = "com.android.server.fingerprint.FingerprintService\$FingerprintServiceWrapper"
//        val cls = Scene.v().getSootClass(focusCls)
//        val focusMtd = "addLockoutResetCallback"
//        val mtd = cls.getMethodByName(focusMtd)
//        val cg = CallGraphAnalysis(3)
//        val vul = cg.analysisEntryPoint(mtd)
//        if (vul != null) {
//            println("Vul Detect: $vul")
//        }
//        analysisOneCls(cls)
//    }

    val targetCls = listOf("com.android.server.accessibility.AccessibilityManagerService",
            "com.android.server.am.ActivityManagerService",
            "com.android.server.AppOpsService",
            "com.android.server.audio.AudioService",
            "com.android.server.autofill.AutofillManagerService\$AutoFillManagerServiceStub",
            "com.android.server.clipboard.ClipboardService\$ClipboardImpl",
            "com.android.server.CountryDetectorService",
            "com.android.server.DeviceIdleController\$BinderService",
            "com.android.server.ethernet.EthernetServiceImpl",
            "com.android.server.fingerprint.FingerprintService\$FingerprintServiceWrapper",
            "com.android.server.input.InputManagerService",
            "com.android.server.media.MediaRouterService",
            "com.android.server.media.MediaSessionService\$SessionManagerImpl",
            "com.android.server.midi.MidiService",
            "com.android.server.NetworkManagementService",
            "com.android.server.print.PrintManagerService\$PrintManagerImpl",
            "com.android.server.TelephonyRegistry",
            "com.android.server.wallpaper.WallpaperManagerService",
            "com.android.server.wifi.WifiServiceImpl")

//    for (clsName in targetCls) {
//        val cls = Scene.v().getSootClass(clsName)
//        val inf = SootTool.getInfForImpl(cls)
//        println("$cls $inf")
//
//        if (cls == inf) {
//            cls.methods.forEach { mtd ->
//                val cg = CallGraphAnalysis(3)
//                val vul = cg.analysisEntryPoint(mtd)
//                if (vul != null) {
//                    println("Vul Detect: $vul")
//                }
//            }
//        } else {
//            VulnerabilitiesDetecting.analysisImplClass(cls, inf)
//        }
//    }
    VulnerabilitiesDetecting.dumpVul()
}

fun runDetecting() {
    VulnerabilitiesDetecting.detectingVul(ServiceApiList.allApi)

}