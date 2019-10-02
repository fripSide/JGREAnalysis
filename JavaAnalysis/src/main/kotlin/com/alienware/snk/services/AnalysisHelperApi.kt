package com.alienware.snk.services

import com.alienware.snk.utils.SootTool
import soot.SootClass
import soot.SootMethod
import soot.SootMethodRef
import soot.jimple.InterfaceInvokeExpr
import soot.jimple.Stmt

class AnalysisHelperApi(var entryMtd: SootMethod, var exitPoints: HashSet<SootMethod>) {

    var called: SootMethod? = null

    fun findVulIPCCall(): SootMethod? {
        callGraphAnalysis(entryMtd)
        return called
    }

    private fun callGraphAnalysis(mtd: SootMethod, lev: Int = 3) {
        if (lev <= 0) return
        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            val stmt = u as Stmt
//            println(stmt)
            if (stmt.containsInvokeExpr()) {
                val inv = stmt.invokeExpr

                if (inv !is InterfaceInvokeExpr) {
                    callGraphAnalysis(inv.method, lev - 1)
                    if (called != null) return
                } else {
                    val cls = inv.methodRef.declaringClass
                    if (SootTool.isIInterface(cls)) {
                        findCallInCurrentMethod(inv.methodRef)
                        if (called != null) return
                    }
                }
            }
        }
    }

    private fun findCallInCurrentMethod(sm: SootMethodRef) {
        println("findCallInCurrentMethod check $sm")
        exitPoints.forEach { mtd ->
            if (mtd.subSignature == sm.subSignature.string) {
                called = mtd
                return
            }
        }
    }

}