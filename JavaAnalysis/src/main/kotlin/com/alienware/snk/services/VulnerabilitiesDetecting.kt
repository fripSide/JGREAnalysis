package com.alienware.snk.services

import ServiceApiList
import com.alienware.snk.CONFIG
import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.Scene
import soot.SootClass
import soot.SootMethod

object VulnerabilitiesDetecting {

    private val vulSet = HashSet<SootMethod>()

    private val vulMap = HashMap<SootMethod, VulnerableApiDesc>()

    /*
    1. appos
    2. accessibility manager
     */
    fun detectingVul(apiList: ServiceApiList) {
        for (api in apiList.allApi) {
            var targetCls: SootClass? = api.helperClass
            val focus = "android.view.accessibility.IAccessibilityManager"
            val targetName = api.inf?.name ?: ""
            if (targetName != focus) continue

            if (api.isHelperClass()) {
                targetCls = api.helperClass
            } else {
                analysisImplClass(api.implClass!!, api.inf)
            }
        }
    }

    fun analysisImplClass(sc: SootClass, inf: SootClass?) {
        LogNow.info("Start to analysis $sc")
        val methods = getPublicApis(inf!!)
//        methods.forEach { mtd ->
//            println(mtd)
//        }
        sc.methods.forEach { mtd ->
            if (mtd.subSignature in methods) {
                if (analysisOneFunction(mtd)) {
                    vulSet.add(mtd)
                }
            } else {
//                println("not in ${mtd.subSignature} $methods")
//                if (mtd.name == "addAccessibilityInteractionConnection") {
//                    DebugTool.exitHere()
//                }
            }
        }
    }

    fun addVul(mtd: SootMethod, vul: VulnerableApiDesc) {
        vulSet.add(mtd)
        vulMap[mtd] = vul
    }

    fun dumpVul() {
        vulSet.forEach { println("Find Vul: ${vulMap[it]}") }
        println("Total Size: ${vulSet.size}")
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
        val cg = CallGraphAnalysis()
        val vul = cg.analysisEntryPoint(mtd)
        if (vul != null) {
            LogNow.info("Find Vulnerable Method: $mtd")
            vulMap[mtd] = vul
            return true
        }
        return false
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

 */
fun quickAnalysis() {
    var clsPath = CONFIG.ANDROID_JAR
    clsPath = "cls/"
    SootTool.initSootSimply("", clsPath)

    // TaskChangeNotificationController
    run {
        val focusCls = "com.android.server.wifi.WifiServiceImpl"
        val cls = Scene.v().getSootClass(focusCls)
//        val focusMtd = "listenForSubscriber"
//        val mtd = cls.getMethodByName(focusMtd)
//        val cg = CallGraphAnalysis(3)
//        val vul = cg.analysisEntryPoint(mtd)
//        if (vul != null) {
//            println("Vul Detect: $vul")
//        }
        analysisOneCls(cls)
    }

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
            "com.android.server.wifi.WifiServiceImpl",
            "")

//    for (clsName in targetCls) {
//        val cls = Scene.v().getSootClass(clsName)
//        val inf = SootTool.getInfForImpl(cls)
//        println("$cls $inf")
//
//        if (cls == inf) {
//            cls.methods.forEach { mtd ->
//                val cg = CallGraphAnalysis(1)
//                val vul = cg.analysisEntryPoint(mtd)
//                if (vul != null) {
//                    println("Vul Detect: $vul")
//                }
//            }
//        } else {
//            VulnerabilitiesDetecting.analysisImplClass(cls, inf)
//        }
//    }
//    VulnerabilitiesDetecting.dumpVul()
}

fun runDetecting(apiList: ServiceApiList) {
    VulnerabilitiesDetecting.detectingVul(apiList)
}