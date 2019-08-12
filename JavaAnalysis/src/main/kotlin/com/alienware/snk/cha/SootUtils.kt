package com.alienware.snk.cha

import soot.Scene
import soot.SootClass
import soot.SootMethod





object DebugTool {
    private val DEBUG = true

    fun panic(ex: Exception) {
        println("============================Panic Exit=========================")
        println(getLineInfo())
        println("=============================End Panic ========================")
        exitOrThrow(ex)
    }

    fun getLineInfo(): String { // 一层调用
        val stacks = Throwable().stackTrace
        val ste = stacks[2]
        return "File:" + ste.className + ".java Method:" + ste.methodName + " Line:" + ste.lineNumber
    }

    private fun exitOrThrow(ex: Exception) {
        if (DEBUG) {
            println("DEBUG EXIT!")
            System.exit(-1)
        } else {
            throw RuntimeException(ex)
        }
    }

    fun printSootClasses() {
        println("****************PrintSootClasses List****************")
        val classes = soot.Scene.v().applicationClasses
        for (sc in classes) {
            val isPhantom = if (sc.isPhantomClass) "Is PhantomClass" else "Not PhantomClass"
            println(String.format("%-100s %s", "PrintSootClasses: $sc", isPhantom))
        }
        println("**************End PrintSootClasses: " + classes.size + "***************")
    }

    fun dumpClass(sc: SootClass) {
        if (!Scene.v().containsClass(sc.name)) {
            sc.setApplicationClass()
        }
        println("****************DumpSootClass Start****************")
        println(sc)
        for (m in sc.methods) {
            println(m.toString())
        }
        println("****************DumpSootClass End****************")
    }

    fun exitHere(info: String = "") {
        if (info.isNotEmpty()) {
            println("Exit Message: $info")
        }
        println("*****************Debug Exit Here*****************")
       println(getLineInfo())
        System.exit(-1)
    }
}