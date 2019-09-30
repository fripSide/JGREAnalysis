package com.alienware.snk.services

import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.*
import java.util.*

data class VulData(public var serviceName: String, var mtd: String, var listTag: String, var callChain: List<String>)

class VulnerableApiDesc(var binderCls: SootClass) {
    var entryPoint: SootMethod? = null
    var callChain = LinkedList<SootMethod>()
    var listTag: String = ""

    override fun toString(): String {
        return "$entryPoint Calls: $callChain By $listTag -> $binderCls"
    }

    fun getData(): VulData {
        val calls = mutableListOf<String>()
        callChain.forEach { m ->
            calls.add(m.signature)
        }
        return VulData("", entryPoint.toString(), listTag, calls)
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

    //
    private val exitPointMthods = hashSetOf("asBinder", "linkToDeath")

    private val pointToAnalysis = PointToAnalysis(entryMtd)

    var containExitPoint = false

    fun containBinderList(): VulnerableApiDesc? {
//        println("containBinderList: $entryMtd")
        val body = SootTool.tryGetMethodBody(entryMtd)
        pointToAnalysis.run()

        // detect Binder list
        body?.units?.forEach { u ->
            val stmt = u as Stmt
            if (stmt.containsInvokeExpr()) {
                val invoke = stmt.invokeExpr
//                println(invoke)
                checkContainExitPoint(invoke)
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
            containExitPoint = true
            LogNow.info("Contain RemoteList: $invoke ${invoke.method.signature}")
            return vul
        }
        if (clsName in listClassTypes && cur.name in listAddMethods) {
            val loc = SootTool.getInvokeLocalFiled(invoke)
            if (loc != null && pointToAnalysis.isLocalValue(loc)) {
                LogNow.debug("$clsName $loc is local List.")
                return null
            }
            for (arg in invoke.args) {
                if (arg is Local) {
                    val valCls = pointToAnalysis.getLocalDefine(arg)
                    if (valCls != null && isExitClass(valCls)) {
//                        println(invoke.useBoxes)
//                        println(invoke.getArgBox(0))
//                        println(isLocalValue(invoke.))
                        LogNow.debug("Local isBinderInterface: $valCls $invoke $arg ${pointToAnalysis.valueDefines[arg]}")
                        val vul = VulnerableApiDesc(valCls)
                        vul.listTag = clsName
                        return vul
                    }
                    LogNow.debug("Local value: $arg ${pointToAnalysis.valueDefines[arg]}")
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

        sc.fields.forEach { fi ->
            val ref = fi.makeRef()
            val ty = ref.type()
            if (ty is RefType) {
                val cls = ty.sootClass
                if (cls.name == kIBinderInf) {
                    return true
                }
//                if (ServiceImplExtractor.isBinderClass(sc)) return true
                cls.interfaces.forEach { inf ->
                    if (inf.name.startsWith(kIBinderInf)) return true
                }
            }
        }
        return false
    }

    private fun checkContainExitPoint(inv: InvokeExpr) {
        val mtd = inv.methodRef
        if (mtd.name in exitPointMthods) {
            containExitPoint = true
        }
    }
}