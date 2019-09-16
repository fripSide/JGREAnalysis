package com.alienware.snk.services

import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.AssignStmt
import soot.jimple.InterfaceInvokeExpr
import soot.jimple.InvokeExpr
import soot.jimple.Stmt
import soot.jimple.internal.JInstanceFieldRef
import soot.jimple.internal.JNewExpr
import java.util.*
import kotlin.collections.HashMap

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

    private val kIBinderInf = "android.os.IBinder"
    private val kLinkToDeathCls = "android.os.IBinder\$DeathRecipient"

    val valueDefines = HashMap<Local, SootClass>()

    val pointToSet = HashMap<Local, Local>()

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
        println("findValueDefines ${u.rightOp.javaClass} $u")
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

            if (right is JNewExpr) {
                val jn = right.type as RefType
                addLocalDefine(left, jn.sootClass)
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
                    if (valCls != null && isExitClass(valCls)) {
                        println("Local isBinderInterface: $arg ${valueDefines[arg]}")

                        return VulnerableApiDesc(valCls)
                    }
                    println("Local value: $arg ${valueDefines[arg]}")
                    if (valCls != null) {
                        println(ServiceImplExtractor.isBinderClass(valCls))
                        println(valCls.interfaces)

                    }

                }
            }

            println(invoke)
            println(valueDefines)
//            DebugTool.exitHere("Contain Exit list in $entryMtd -> $cur")
        }

        return null
    }

    private fun isExitClass(sc: SootClass): Boolean {
        if (ServiceImplExtractor.isBinderClass(sc)) return true

        sc.interfaces.forEach { inf ->
            if (inf.name.startsWith(kIBinderInf)) return true
        }
        return false
    }

    private fun backwardResolveValueType(mtd: SootMethod, v: Value): SootClass? {
        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            if (u is AssignStmt) {
                val asi = u as AssignStmt

            }

        }
        return null
    }

    private fun getLocalPointTo(loc: Local): Local {
        var cur: Local? = loc
        while (cur != null && cur in pointToSet) {
            val nxt = pointToSet[cur]
            if (nxt == null) return cur
            cur = nxt
        }
        return loc
    }

    private fun addLocalDefine(loc: Local, sc: SootClass) {
        val key = getLocalPointTo(loc)
        valueDefines[key] = sc
    }

    private fun checkHoldBinderInList() {

    }
}