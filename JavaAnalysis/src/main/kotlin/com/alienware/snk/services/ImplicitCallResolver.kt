package com.alienware.snk.services

import soot.SootClass
import soot.SootMethod
import soot.jimple.InvokeExpr

class ImplicitCallResolver {

    companion object {
        fun isImplicitCall(inv: InvokeExpr): Boolean {


            return false
        }
    }

    fun resolve(inv: InvokeExpr): HashSet<SootMethod> {
        val methods = HashSet<SootMethod>()

        return methods
    }

    private fun processHandler() {

    }
}