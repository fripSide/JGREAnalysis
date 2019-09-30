package com.alienware.snk.services

import com.alienware.snk.CONFIG
import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.SootClass
import soot.SootMethod
import soot.jimple.InvokeExpr
import java.io.File

object ImplicitCallResolver {

    val callbacks = HashSet<String>()
    var isInit = false

    fun isImplicitCall(inv: InvokeExpr): Boolean {
        val name = getMethodSmaliName(inv)
        return callbacks.contains(name)
    }


    fun resolve(inv: InvokeExpr): HashSet<SootMethod> {
        val methods = HashSet<SootMethod>()
        val name = getMethodSmaliName(inv)
        val mtdRef = inv.methodRef
        if (callbacks.contains(name)) {
            if (mtdRef.parameterTypes.size == 1) {
                val ty = mtdRef.getParameterType(0)
                val cls = SootTool.getClsFromType(ty)
                println(cls?.methods)
            }

        }
        return methods
    }

    init { // use EdgeMiner results
        loadEdgeMinerCallbacks()
    }


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
            DebugTool.panic(ex)
        }
        LogNow.info("Load EdgeMiner. Total callbacks: ${callbacks.size}")
    }
}