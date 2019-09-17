package com.alienware.snk.services

import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.*
import soot.jimple.internal.JInstanceFieldRef
import soot.jimple.internal.JInterfaceInvokeExpr
import soot.jimple.internal.JNewExpr
import soot.jimple.internal.JimpleLocal
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

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

    private val listClassTypes = hashSetOf("android.util.SparseArray", "java.util.HashMap", "android.util.ArrayMap",
            "java.util.ArrayList", "java.util.Map", "java.util.List")
    private val listAddMethods = hashSetOf("add", "put")

    private val kIBinderInf = "android.os.IBinder"
    private val kLinkToDeathCls = "android.os.IBinder\$DeathRecipient"
    private val kAsBinder = "asBinder"

    private val pointToAnalysis = PointToAnalysis(entryMtd)

    fun containBinderList(): VulnerableApiDesc? {
        val body = SootTool.tryGetMethodBody(entryMtd)
        pointToAnalysis.run()

        // detect Binder list
        body?.units?.forEach { u ->
//            println("${u.javaClass} $u")
            val stmt = u as Stmt
            if (stmt.containsInvokeExpr()) {
                val invoke = stmt.invokeExpr
                val vul = detectAddToList(invoke)
                if (vul != null) return vul
            }
        }

        return null
    }

    private fun detectAddToList(invoke: InvokeExpr): VulnerableApiDesc? {
        if (invoke is InterfaceInvokeExpr) {
            return null
        }
//        println("detectAddToList $invoke")
        val cur = invoke.method
        val clsName = cur.declaringClass.name
        if (clsName == kRemoteCallback && cur.name == kRemoteListRegisterName) {
            val vul = VulnerableApiDesc(entryMtd.declaringClass)
            vul.listTag = clsName
            LogNow.info("Contain RemoteList: $invoke ${invoke.method.signature}")
            return vul
        }
        if (clsName in listClassTypes && cur.name in listAddMethods) {
            val loc = SootTool.getInvokeLocalFiled(invoke)
            if (loc != null && pointToAnalysis.isLocalValue(loc)) {
                LogNow.info("$clsName $loc is local List.")
                return null
            }
            for (arg in invoke.args) {
                if (arg is Local) {
                    val valCls = pointToAnalysis.getLocalDefine(arg)
                    if (valCls != null && isExitClass(valCls)) {
//                        println(invoke.useBoxes)
//                        println(invoke.getArgBox(0))
//                        println(isLocalValue(invoke.))
                        LogNow.info("Local isBinderInterface: $invoke $arg ${pointToAnalysis.valueDefines[arg]}")
                        val vul = VulnerableApiDesc(valCls)
                        vul.listTag = clsName
                        return vul
                    }
                    LogNow.info("Local value: $arg ${pointToAnalysis.valueDefines[arg]}")
                }
            }


//            DebugTool.exitHere("Contain Exit list in $entryMtd -> $cur")
        }

        return null
    }

    private fun isExitClass(sc: SootClass): Boolean {
//        println("Check isExitClass: $sc")
        if (ServiceImplExtractor.isBinderClass(sc)) return true

        sc.interfaces.forEach { inf ->
            if (inf.name.startsWith(kIBinderInf)) return true
        }

        sc.fields.forEach { fi ->
            val ref = fi.makeRef()
            val ty = ref.type()
            if (ty is RefType) {
                val cls = ty.sootClass
                if (cls.name == kIBinderInf) {
                    return true
                }
                cls.interfaces.forEach { inf ->
                    if (inf.name.startsWith(kIBinderInf)) return true
                }
            }
        }
        return false
    }
}