package com.alienware.snk.services

import com.alienware.snk.utils.SootTool
import soot.RefType
import soot.Scene
import soot.SootClass
import soot.SootMethod

/*
Trace binder object.
*/
class CallGraphAnalysis {

    val findExitPoint: Boolean = false

    private val valueTypes = HashMap<String, SootClass>()

    fun analysisEntryPoint(mtd: SootMethod, lev1: Boolean = true) {
        val params = mtd.parameterTypes
        for (param in params) {
            if (param is RefType) {
                createBinder(param)
            }
            println("${param.javaClass} ${param}")
        }
        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            println(u)
        }
    }

    private fun checkFunctionAddBinderToList(mtd: SootMethod) {

    }

    private fun parseMethodBinderParameters(mtd: SootMethod) {

    }

    private fun createBinder(ref: RefType): Boolean {
        val sc = ref.sootClass
        val hir = Scene.v().activeHierarchy
        var supList: List<SootClass>
        if (sc.isInterface) {
            supList =  hir.getSuperinterfacesOf(sc)
        } else {
            supList = hir.getSuperclassesOf(sc)
        }

        var isInf = false
        supList.forEach { sup -> // stub
            val infList = sup.interfaces
//            println(infList)
            for (inf in infList) {
                for (supInf in inf.interfaces) {
                    if (supInf.name == ServiceImplExtractor.kServiceImplInterface) {
                        isInf = true
                        return@forEach
                    }
                }
            }
        }

        return isInf
    }
}