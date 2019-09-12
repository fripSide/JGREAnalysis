package com.alienware.snk
import com.alienware.snk.cha.SootAnalysis
import com.alienware.snk.cha.runLiveVarAnalysis
import com.alienware.snk.cha.startSootAnalysis
import com.alienware.snk.services.runIPCTest
import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.beust.klaxon.Klaxon
import java.io.File

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
//    st.run()
//    startSootAnalysis()
//    runIPCTest()
//    runLiveVarAnalysis()
}

open class Main {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: JavaAnalyzer.jar conf.json")
                return
            }
            val confPath = args[0]
            val txt = File(confPath)
            try {
                CONFIG = Klaxon().parse<Conf>(txt)!!
            } catch (ex: Exception) {
                CONFIG = Conf()
                DebugTool.fatalError("Failed to parse Conf file: $confPath!", ex)
            }
            LogNow.setLogLevel()
//            testJavapoet()
            runAnalysis()
        }
    }
}
