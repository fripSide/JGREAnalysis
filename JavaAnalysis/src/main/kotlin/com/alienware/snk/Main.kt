package com.alienware.snk
import com.alienware.snk.services.runDetecting
import com.alienware.snk.services.runIPCExtractor
import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.beust.klaxon.Klaxon
import java.io.File

/*
* soot 读入class
* https://blog.csdn.net/TheSnowBoy_2/article/details/52485087
* */
//

fun runAnalysis() {
    LogNow.show("Start to run JGREAnalyzer:")
    val apiList = runIPCExtractor()
    runDetecting(apiList)
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
            runAnalysis()
        }
    }
}
