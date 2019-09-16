package com.alienware.snk.services

import soot.SootMethod

class ExitPoint(var mtd: SootMethod) {
    var isNative: Boolean =  false
    var isBinder: Boolean = false
}

object ExitPointIdentify {

    fun checkIsExitPoint(mtd: SootMethod) {

    }

    private fun checkHoldBinderInList() {

    }
}