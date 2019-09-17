package com.alienware.snk.services

import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.IdentityStmt
import soot.jimple.InterfaceInvokeExpr
import soot.jimple.ParameterRef
import soot.jimple.Stmt
import java.util.*
import kotlin.collections.HashSet

/*
Trace binder object.
*/
class CallGraphAnalysis(var lev: Int = 2) {

    val visitSet = HashSet<SootMethod>()

    fun analysisEntryPoint(mtd: SootMethod): VulnerableApiDesc? {
        val initial = LinkedList<SootMethod>()
        val vul = callGraphAnalysis(mtd, initial, lev)
        vul?.entryPoint = mtd
        return vul
    }

    private fun callGraphAnalysis(mtd: SootMethod, path: LinkedList<SootMethod>, searchLev: Int = 2): VulnerableApiDesc? {
        if (searchLev <= 0) return null
        if (visitSet.contains(mtd)) return null
        visitSet.add(mtd)
        path.add(mtd)
        val vul = checkOneMethod(mtd)
//        LogNow.info("Analysis calls: $mtd")
        if (vul != null) {
            vul.callChain = path
            return vul
        }

        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
//            println("${u.javaClass} $u")
            val stmt = u as Stmt
            if (stmt.containsInvokeExpr()) {
                val inv = stmt.invokeExpr
                if (inv !is InterfaceInvokeExpr) {
                    val methods = HashSet<SootMethod>()
                    // Resolve Implicit call
                    if (ImplicitCallResolver.isImplicitCall(inv)) {
                        val mtds = ImplicitCallResolver().resolve(inv)
                        methods.addAll(mtds)
                    } else {
                        methods.add(inv.method)
                    }

                    for (m in methods) {
                        val np = LinkedList(path)
                        val v2 = callGraphAnalysis(m, np, searchLev - 1)
                        if (v2 != null) return v2
                    }
                }
            }
        }

        return null
    }

    private fun checkOneMethod(mtd: SootMethod): VulnerableApiDesc? {
        val exitPoints = ExitPointChecker(mtd)
        return exitPoints.containBinderList()
    }

}