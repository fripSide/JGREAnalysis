package com.alienware.snk.services

import com.alienware.snk.CONFIG
import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.*
import soot.jimple.InvokeExpr
import java.io.File

object ImplicitCallResolver {

    val callbacks = HashSet<String>()
    var isInit = false

    fun isImplicitCall(inv: InvokeExpr): Boolean {
        val name = getMethodSmaliName(inv)
        return callbacks.contains(name)
    }


    fun resolve(inv: InvokeExpr, mtd: SootMethod): HashSet<SootMethod> {
        val methods = HashSet<SootMethod>()
        val name = getMethodSmaliName(inv)
        val mtdRef = inv.methodRef
        if (callbacks.contains(name)) {
            for (i in 0..(mtdRef.parameterTypes.size - 1)) {
                val param = mtdRef.parameterTypes[i]
                val cls = SootTool.getClsFromType(param)
                if (cls != null) {
                    val arg = inv.getArg(i)
                    val realType = resolveRealTypeInMethod(arg, mtd, cls)
                    if (realType != null) {
                        methods.addAll(getOverrideMethods(cls, realType))
                    }
                }
            }
        }
//        println("methods $methods")
        return methods
    }

    init { // use EdgeMiner results
        loadEdgeMinerCallbacks()
    }


//    private fun


    private fun filterCallbacks(cb: String): Boolean {
        when {
            cb.startsWith("android.R$") -> return false
            cb.startsWith("android.test") -> return false
        }
        return true
    }

    private fun getMethodSmaliName(inv: InvokeExpr): String {
        val cls = inv.methodRef.declaringClass
        val mtdName = inv.methodRef.name
        val name = "$cls.$mtdName"
        return name
    }

    private fun loadEdgeMinerCallbacks() {
        val pt = CONFIG.EDGE_MINER
        try {
            File(pt).forEachLine { li ->
                if (li.startsWith("android")) {
                    if (filterCallbacks(li)) {
                        val items = li.split(":")
                        callbacks.add(items[0])
                    }
                }
            }
        } catch (ex: Exception) {
            LogNow.error("Failed to loead Callback.txt")
            DebugTool.panic(ex)
        }
        LogNow.info("Load EdgeMiner. Total callbacks: ${callbacks.size}")
    }

    private fun resolveRealTypeInMethod(v: Value, mtd: SootMethod, paramType: SootClass): SootClass? {
        if (v !is Local) return null
        val pointTo = PointToAnalysis(mtd)
        pointTo.run()
        val define = pointTo.getLocalDefine(v as Local)
        val hir = Scene.v().fastHierarchy
        if (define != null && (hir.isSubclass(define, paramType) or define.interfaces.contains(paramType))) {
            return define
        }
        return null
    }

    private fun getOverrideMethods(parent: SootClass, overri: SootClass): HashSet<SootMethod> {
        val methods = HashSet<String>()
        val mtdSet = HashSet<SootMethod>()
        val exclude = hashSetOf("void <init>()")
        for (mtd in parent.methods) {
            if (mtd.isProtected || mtd.isPublic) {
                val ms = mtd.subSignature
                if (!exclude.contains(ms)) {
                    methods.add(ms)
                }
            }
        }
        for (mtd in overri.methods) {
            val ms = mtd.subSignature
//            println(ms)
            if (methods.contains(ms)) {
                mtdSet.add(mtd)
            }
        }
        return mtdSet
    }
}