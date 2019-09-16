package com.alienware.snk.services

import ServiceApiList
import com.alienware.snk.utils.LogNow
import soot.SootClass
import soot.SootMethod

object VulnerabilitiesDetecting {

    private val vulSet = HashSet<SootMethod>()

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
                LogNow.info("Analysis native method: ${mtd.subSignature}")
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
        val cg = CallGraphAnalysis()
        cg.analysisEntryPoint(mtd)
        return cg.findExitPoint
    }



}

fun runDetecting(apiList: ServiceApiList) {
    VulnerabilitiesDetecting.detectingVul(apiList)
}