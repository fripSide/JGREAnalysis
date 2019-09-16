package com.alienware.snk.services

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
        if (vul != null) {
            vul.callChain = path
            return vul
        }

        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            val stmt = u as Stmt
            if (stmt.containsInvokeExpr()) {
                val inv = stmt.invokeExpr
                val np = LinkedList(path)
                if (inv !is InterfaceInvokeExpr) {
                    val vul = callGraphAnalysis(inv.method, np, searchLev - 1)
                    if (vul != null) return vul
                }
            }
        }

        return null
    }

    private fun checkOneMethod(mtd: SootMethod): VulnerableApiDesc? {
        val valueTypes = parseMethodBinderParameters(mtd)
        val exitPoints = ExitPointChecker(mtd)
        return exitPoints.containBinderList(valueTypes)
    }

    private fun parseMethodBinderParameters(mtd: SootMethod): HashMap<Local, SootClass> {
        val valueTypes = HashMap<Local, SootClass>()
        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            if (u is IdentityStmt) {
                val right = u.rightOp
                if (right is ParameterRef) {
                    val left = u.leftOp
//                    println("$left ${left.javaClass} ${right.javaClass} ${right.type} ${right.type.javaClass}")
                    if (left is Local && right.type is RefType) {
                        val ref = right.type as RefType
                        if (isBinderParam(ref)) {
                            valueTypes[left] = ref.sootClass
                        }
//                        println("is ref type: ${ref.sootClass}")
                    }
                }
            }
        }
        return valueTypes
    }

    private fun isBinderParam(ref: RefType): Boolean {
        return ServiceImplExtractor.isBinderClass(ref.sootClass)
    }

}