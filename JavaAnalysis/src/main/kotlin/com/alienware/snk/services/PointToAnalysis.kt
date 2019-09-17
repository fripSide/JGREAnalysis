package com.alienware.snk.services

import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.Local
import soot.RefType
import soot.SootClass
import soot.SootMethod
import soot.jimple.AssignStmt
import soot.jimple.IdentityStmt
import soot.jimple.ParameterRef
import soot.jimple.internal.JInstanceFieldRef
import soot.jimple.internal.JInterfaceInvokeExpr
import soot.jimple.internal.JNewExpr
import soot.jimple.internal.JimpleLocal

/*
Binder Pointer analysis
 */
class PointToAnalysis(var entryMtd: SootMethod) {

    private val kAsBinder = "asBinder"

    val localValues = HashSet<Local>()

    val valueDefines = HashMap<Local, SootClass>() // class definitions and binder object definitions

    // do not compute set for a value, only save directly assign
    val pointToSet = HashMap<Local, Local>()

    fun run() {
        val params = parseMethodBinderParameters(entryMtd)
        params.forEach { t, u ->
            valueDefines[t] = u
        }

        val body = SootTool.tryGetMethodBody(entryMtd)
        // find value defines
        body?.units?.forEach { u ->
//            println("${u.javaClass} $u")
            if (u is AssignStmt) {
                findValueDefines(u)
            }
        }
    }

    fun getLocalDefine(loc: Local): SootClass? {
        val key = getLocalPointTo(loc)
        return valueDefines[key]
    }

    fun isLocalValue(loc: Local): Boolean {
        val real = getLocalPointTo(loc)
        return localValues.contains(real)
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
                localValues.add(left)
                LogNow.info("Is Local value: $left $u ${getLocalPointTo(left)}")
            }

            // binder define
            if (right is JInterfaceInvokeExpr) {
                val cls = resolveInterfaceInvoke(right)
                if (cls != null) {
                    valueDefines[left] = cls
                }
            }
        }
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

    private fun parseMethodBinderParameters(mtd: SootMethod): java.util.HashMap<Local, SootClass> {
        val valueTypes = java.util.HashMap<Local, SootClass>()
        val body = SootTool.tryGetMethodBody(mtd)
        body?.units?.forEach { u ->
            if (u is IdentityStmt) {
                val right = u.rightOp
                if (right is ParameterRef) {
                    val left = u.leftOp
//                    println("$left ${left.javaClass} ${right.javaClass} ${right.type} ${right.type.javaClass}")
                    if (left is Local && right.type is RefType) {
                        val ref = right.type as RefType
                        if (ServiceImplExtractor.isBinderClass(ref.sootClass)) {
                            valueTypes[left] = ref.sootClass
                        }
//                        println("is ref type: ${ref.sootClass}")
                    }
                }
            }
        }
        return valueTypes
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
}