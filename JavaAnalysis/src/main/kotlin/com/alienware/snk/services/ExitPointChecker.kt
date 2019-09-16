package com.alienware.snk.services

import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.AssignStmt
import soot.jimple.InterfaceInvokeExpr
import soot.jimple.InvokeExpr
import soot.jimple.Stmt
import soot.jimple.internal.JInstanceFieldRef
import soot.jimple.internal.JInterfaceInvokeExpr
import soot.jimple.internal.JNewExpr
import soot.jimple.internal.JimpleLocal
import java.util.*
import kotlin.collections.HashMap

class ExitPoint(var mtd: SootMethod) {
    var isNative: Boolean =  false
    var isBinder: Boolean = false
}

class VulnerableApiDesc(var binderCls: SootClass) {
    var entryPoint: SootMethod? = null
    var callChain = LinkedList<SootMethod>()
    var listTag: String = ""

    override fun toString(): String {
        return "$entryPoint Calls: $callChain By $listTag -> $binderCls"
    }
}

class ExitPointChecker(var entryMtd: SootMethod) {

    private val kRemoteCallback = "android.os.RemoteCallbackList"
    private val kRemoteListRegisterName = "register"

    private val listClassTypes = hashSetOf("android.util.SparseArray", "java.util.HashMap", "android.util.ArrayMap")
    private val listAddMethods = hashSetOf("add", "put")

    private val kIBinderInf = "android.os.IBinder"
    private val kLinkToDeathCls = "android.os.IBinder\$DeathRecipient"
    private val kAsBinder = "asBinder"

    val valueDefines = HashMap<Local, SootClass>()

    val pointToSet = HashMap<Local, Local>()

    val vulDetail = StringBuilder()

    fun containBinderList(params: HashMap<Local, SootClass>): VulnerableApiDesc? {
//        println("containBinderList: $entryMtd")
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

//        println(valueDefines)
//        println(pointToSet)

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
//        LogNow.debug("findValueDefines ${u.rightOp.javaClass} $u")
        val left = u.leftOp
        val right = u.rightOp
        if (left is Local) {
            if (right is JInstanceFieldRef) {
                val filed = right.field
                // is Class
                if (filed.type is RefType) {
                    val ref = filed.type as RefType
                    valueDefines[left] = ref.sootClass
                }
//                println(" right.field $filed ${filed.javaClass} ${filed.declaringClass} ${filed.type}")
            }

            if (right is JimpleLocal) {
                pointToSet[left] = right
            }

            if (right is JNewExpr) {
                val jn = right.type as RefType
//                addLocalDefine(left, jn.sootClass)
                valueDefines[left] = jn.sootClass
            }

            if (right is JInterfaceInvokeExpr) {
                val cls = resolveInterfaceInvoke(right)
                if (cls != null) {
                    valueDefines[left] = cls
                }
            }
        }
    }

    private fun detectAddToList(invoke: InvokeExpr): VulnerableApiDesc? {
        if (invoke is InterfaceInvokeExpr) {
            return null
        }
        val cur = invoke.method
        val clsName = cur.declaringClass.name
        if (clsName == kRemoteCallback && cur.name == kRemoteListRegisterName) {
            val vul = VulnerableApiDesc(entryMtd.declaringClass)
            vul.listTag = clsName
            LogNow.info("Contain RemoteList: $invoke ${invoke.method.signature}")
            return vul
        }
        if (clsName in listClassTypes && cur.name in listAddMethods) {
            for (arg in invoke.args) {
                if (arg is Local) {
                    val valCls = getLocalDefine(arg)
                    if (valCls != null && isExitClass(valCls)) {
                        LogNow.debug("Local isBinderInterface: $invoke $arg ${valueDefines[arg]}")
                        val vul = VulnerableApiDesc(valCls)
                        vul.listTag = clsName
                        return vul
                    }
                    LogNow.debug("Local value: $arg ${valueDefines[arg]}")
                }
            }


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

    private fun getLocalPointTo(loc: Local): Local {
        var cur: Local? = loc
        while (cur != null && cur in pointToSet) {
            val nxt = pointToSet[cur]
            if (nxt == null) return cur
            cur = nxt
        }
        return loc
    }

    private fun getLocalDefine(loc: Local): SootClass? {
        val key = getLocalPointTo(loc)
        return valueDefines[key]
    }

    private fun resolveInterfaceInvoke(inv: JInterfaceInvokeExpr): SootClass? {
        val mRef = inv.methodRef
        if (mRef.name == kAsBinder) {
            return mRef.declaringClass
        }
        val ref = mRef.returnType
        if (ref is RefType) {
            return ref.sootClass
        }
        return null
    }

    private fun checkHoldBinderInList() {

    }
}