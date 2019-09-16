package com.alienware.snk.services

import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.AssignStmt
import soot.jimple.InterfaceInvokeExpr
import soot.jimple.InvokeExpr
import soot.jimple.Stmt
import soot.jimple.internal.JInstanceFieldRef
import java.util.*

class ExitPoint(var mtd: SootMethod) {
    var isNative: Boolean =  false
    var isBinder: Boolean = false
}

class VulnerableApiDesc(var binderCls: SootClass) {
    var entryPoint: SootMethod? = null
    var callChain = LinkedList<SootMethod>()

    override fun toString(): String {
        return "$entryPoint Calls: $callChain By cls: $binderCls"
    }
}

class ExitPointChecker(var entryMtd: SootMethod) {

    private val kRemoteCallback = "android.os.RemoteCallbackList"

    private val listClassTypes = hashSetOf("android.util.SparseArray")



    val valueDefines = HashMap<Local, SootClass>()

    val vulDetail = StringBuilder()

    fun containBinderList(params: HashMap<Local, SootClass>): VulnerableApiDesc? {
        params.forEach { t, u ->
            valueDefines[t] = u
        }
        val body = SootTool.tryGetMethodBody(entryMtd)
        // find value defines
        body?.units?.forEach { u ->
            if (u is AssignStmt) {
                findValueDefines(u)
            }
        }

        println(valueDefines)

        // detect Binder list
        body?.units?.forEach { u ->
            val stmt = u as Stmt
            if (stmt.containsInvokeExpr()) {
                val invoke = stmt.invokeExpr
                val vul = detectAddToList(invoke)
                if (vul != null) return vul
            }
        }

        return null
    }

    private fun findValueDefines(u: AssignStmt) {
        val left = u.leftOp
        val right = u.rightOp
        if (left is Local) {
            if (right is JInstanceFieldRef) {
                val filed = right.field
                // is Class
                if (filed.type is RefType) {
                    val ref = filed.type as RefType
                    valueDefines[left] = ref.sootClass
//                    println("=========ref $left = $ref ${ref.sootClass}")
                }
//                println(" right.field $filed ${filed.javaClass} ${filed.declaringClass} ${filed.type}")
            }
        }
    }

    private fun detectAddToList(invoke: InvokeExpr): VulnerableApiDesc? {
        if (invoke is InterfaceInvokeExpr) {
            return null
        }
        val cur = invoke.method
        val clsName = cur.declaringClass.name
        if (clsName == kRemoteCallback) {
            return VulnerableApiDesc(entryMtd.declaringClass)
        }
        if (clsName in listClassTypes) {
            for (arg in invoke.args) {
                println(arg)
                backwardResolveValueType(entryMtd, arg)
                if (arg is Local) {
                    val valCls = valueDefines[arg]
                    if (valCls != null && ServiceImplExtractor.isBinderInterface(valCls)) {
                        println("Local isBinderInterface: $arg ${valueDefines[arg]}")
                    }
                    println("Local value: $arg ${valueDefines[arg]}")
                }
            }
            println(invoke)
            println(valueDefines)
            DebugTool.exitHere("Contain Exit list in $entryMtd -> $cur")
        }

        return null
    }

    private fun backwardResolveValueType(mtd: SootMethod, v: Value): SootClass? {
        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            println("${u.javaClass} $u")
        }
        return null
    }

    private fun checkHoldBinderInList() {

    }
}