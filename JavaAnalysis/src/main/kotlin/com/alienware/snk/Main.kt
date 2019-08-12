package com.alienware.snk
import com.alienware.snk.cha.SootAnalysis
import com.alienware.snk.cha.runLiveVarAnalysis
import com.alienware.snk.cha.startSootAnalysis
import com.alienware.snk.services.runIPCTest

/*
* soot 读入class
* https://blog.csdn.net/TheSnowBoy_2/article/details/52485087
* */
//

fun testJavapoet() {
    val pt = Poet()
    pt.processAPIDesc()
}

fun runAnalysis() {
    val st = SootAnalysis()
    st.run()
//    startSootAnalysis()
//    runIPCTest()
//    runLiveVarAnalysis()
}

lateinit var Args: Array<String>

open class Main {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            Args = args
//            testJavapoet()
            runAnalysis()
        }
    }
}
