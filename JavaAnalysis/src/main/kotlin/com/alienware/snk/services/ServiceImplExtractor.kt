package com.alienware.snk.services

import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.Scene
import soot.SootClass
import soot.SootMethod
import soot.Value
import soot.jimple.InvokeExpr
import java.lang.Exception
import ServiceApiClass

object ServiceImplExtractor {

    private val kServiceImplAddMtdName = "addService"
    private val kServiceImplAddClsName = "android.os.ServiceManager"
    private val kSystemServiceBaseClass = "com.android.server.SystemService"
    private val kIBinderCls = "android.os.IBinder"
    private val kServiceImplPublishFunc = "publishBinderService"
    public val kServiceImplInterface = "android.os.IInterface"

    private val implList = mutableListOf<ServiceApiClass>()

    fun extractServiceImpl(): HashSet<ServiceApiClass> {
        implList.clear()
        searchServiceImplRegister()
        searchServiceImplPublish()
        val remainList = HashSet<ServiceApiClass>()
        implList.forEach { impl ->
            if (impl.implClass != null) {
                setStubInterfaceFromImpl(impl)
                remainList.add(impl)
            }
        }

        return remainList
    }

    fun isBinderClass(sc: SootClass): Boolean {
        if (sc.isInterface) {
            return ServiceImplExtractor.isBinderInterface(sc)
        }
        val hir = Scene.v().activeHierarchy
        val supList = hir.getSuperclassesOf(sc)

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

    private fun isBinderInterface(sc: SootClass): Boolean {
        val supList =  sc.interfaces
        supList.forEach { sup ->
            if (sup.name == kServiceImplInterface) return true
        }
        return false
    }

    private fun searchServiceImplRegister() {
        val clsList = findAddServiceList()
        for (cls in clsList) {
//            val focus = "com.android.server.am.BatteryStatsService"
//            if (cls.name != focus) continue
            resolveServiceImplRegister(cls)
        }
    }


    private fun findAddServiceList(): List<SootClass> {
        val clsList = mutableListOf<SootClass>()
        Scene.v().applicationClasses.forEach { cls ->
            if (cls.name.startsWith("com.android") && cls.isConcrete) {
                if (SootTool.checkClsContainsMethodCall(cls, kServiceImplAddMtdName, kServiceImplAddClsName)) {
                    clsList.add(cls)
                }
            }
        }
        return clsList
    }

    private fun resolveServiceImplRegister(cls: SootClass) {
//        println("resolveServiceImplRegister $cls")
        val mtdSet = filterMethodContainCalls(cls, kServiceImplAddMtdName, kServiceImplAddClsName)
        var mtdCnt = 0
        mtdSet.forEach{ mtd ->
            val body = SootTool.tryGetMethodBody(mtd)
            body?.useBoxes?.forEach { box ->
                if (box.value is InvokeExpr) {
                    val expr = box.value as InvokeExpr
                    val call = expr.method
                    val callMtdCls = call.declaringClass
                    if (call.name == kServiceImplAddMtdName && callMtdCls.name == kServiceImplAddClsName) {
                        val name = expr.args[0].toString()
//                        val implClass = resolveImplClassFromValue(expr.args[1], interfaceMtd)
                        var implClass = resolveServiceImplInMethod(expr.args[1], mtd)
                        if (implClass != null) {
                            implClass.serviceName = name
                            implList.add(implClass)
                            mtdCnt++
                        } else {
                            implClass = ServiceApiClass(SootTool.OBJECT)
                            implClass.serviceName = name
                            implList.add(implClass)
//                            println("Not find $name $interfaceMtd")
                        }
//                        println("$name $cls")

                    }
                }
            }
        }
//        println("Mtd count: $mtdCnt")
    }

    private fun filterMethodContainCalls(cls: SootClass, callMtdName: String, callMtdCls: String? = null): HashSet<SootMethod> {
        val methods = HashSet<SootMethod>()
        for (mtd in cls.methods) {
            val body = SootTool.tryGetMethodBody(mtd)
            body?.useBoxes?.forEach { box ->
                if (box.value is InvokeExpr) {
                    val expr = box.value as InvokeExpr
                    try {
                        val curMtd = expr.method
                        if (curMtd.name == callMtdName) {
                            if (callMtdCls != null) {
                                val sc = curMtd.declaringClass
                                if (sc.name == callMtdCls)
                                    methods.add(mtd)
                            } else {
                                methods.add(mtd)
                            }
                        }
                    } catch (ex: Exception) {
                        LogNow.debug("Failed to get method of $expr")
                    }
                }
            }
        }
        return methods
    }

    private fun resolveServiceImplInMethod(v: Value, mtd: SootMethod): ServiceApiClass? {
        val solver = JimpleValueSolver()
        var impl: ServiceApiClass? = null
        solver.checkAndGetCls = { mService ->
            var ret = false
            if (mService.name != kIBinderCls) {
                impl = ServiceApiClass(null, null, mService)
                ret = true
            }
            ret
        }
        solver.startToSearchValue(v, mtd)
        return impl
    }

    private fun searchServiceImplPublish() {
        val baseCls = Scene.v().getSootClass(kSystemServiceBaseClass)
        val subCls = SootTool.getSubClassList(baseCls)
        for (cls in subCls) {
            val sc = extractServicePublic(cls)
            if (sc != null) implList.add(sc)
        }
    }

    private fun extractServicePublic(cls: SootClass): ServiceApiClass? {
        if (cls.isInterface) return null
        var inv: InvokeExpr? = null
        var invMtd: SootMethod? = null
        run l1@{
            for (mtd in cls.methods) {
                val body = SootTool.tryGetMethodBody(mtd)
                body?.useBoxes?.forEach { box ->
                    if (box.value is InvokeExpr) {
                        val expr = box.value as InvokeExpr
                        if (expr.method.name == kServiceImplPublishFunc) {
                            inv = expr
                            invMtd = mtd
                            return@l1
                        }
                    }
                }
            }
        }

        if (inv != null) {
//            println("Find $inv in $cls")
            val name = inv!!.args[0].toString()
//            val implClass = resolveImplClassFromValue(inv!!.args[1], invMtd!!)
            var implClass = resolveServiceImplInMethod(inv!!.args[1], invMtd!!)
            if (implClass != null) {
                implClass.serviceName = name
                return implClass
            } else {
//                println("Not find: $name $inv")
                implClass = ServiceApiClass()
                implClass.serviceName = name
                return implClass
            }
        }
        return null
    }

    fun setStubInterfaceFromImpl(impl: ServiceApiClass) {
        val cls  = impl.implClass
        if (cls == null || cls.isInterface) return
        impl.inf = SootTool.getInfForImpl(cls)
        val hir = Scene.v().activeHierarchy
        val supList = hir.getSuperclassesOf(cls)
        supList.forEach { sup -> // stub
            val infList = sup.interfaces
//            println(infList)
            for (inf in infList) {
                for (supInf in inf.interfaces) {
                    if (supInf.name == kServiceImplInterface) {
                        impl.inf = inf
//                        println("Find sup: $sup")
                        return@forEach
                    }
                }
            }
        }

    }
}