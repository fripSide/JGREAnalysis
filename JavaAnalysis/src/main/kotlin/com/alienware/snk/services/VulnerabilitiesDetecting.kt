package com.alienware.snk.services

import ServiceApiList
import com.alienware.snk.CONFIG
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

    private fun analysisImplClass(sc: SootClass, inf: SootClass?) {
        LogNow.info("Start to analysis $sc")
        val methods = getPublicApis(inf!!)
        methods.forEach { mtd ->
            println(mtd)
        }
        sc.methods.forEach { mtd ->
            if (mtd.subSignature in methods) {
                if (analysisOneFunction(mtd)) {
                    vulSet.add(mtd)
                }
            }
        }
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
        val focus = "addClient"
        if (mtd.name != focus) return false
        LogNow.info("Analysis native method: ${mtd.subSignature}")
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

fun quickAnalysis() {
    var clsPath = CONFIG.ANDROID_JAR
    clsPath = "cls/"
    SootTool.initSootSimply("", clsPath)
    val focusCls = "com.android.server.accessibility.AccessibilityManagerService"
    val cls = Scene.v().getSootClass(focusCls)
    val mtd = cls.getMethodByName("addAccessibilityInteractionConnection")
    val cg = CallGraphAnalysis()
    val vul = cg.analysisEntryPoint(mtd)
    println(vul)
}

fun runDetecting(apiList: ServiceApiList) {
    VulnerabilitiesDetecting.detectingVul(apiList)
}