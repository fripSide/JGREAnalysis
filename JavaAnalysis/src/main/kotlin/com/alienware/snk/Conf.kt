package com.alienware.snk

//object Config {
//    const val TEST_CLS = "E:\\PaperWork\\JGRAnalyzer\\SourceAudit\\data\\test\\cls"
//    const val PROCESS_JAR = "E:\\PaperWork\\ServiceHelper\\jar\\framework_6.0.1_partly.jar"
//    const val CLASS_PATH = "E:\\PaperWork\\ServiceHelper\\jar\\rt.jar;E:\\PaperWork\\ServiceHelper\\jar\\jce.jar"
//
//}
const val version = "0.1"

data class Conf(val ANDROID_JAR: String = "", val CLASS_PATH: String = "",
                val DEBUG: Boolean = true, val LOG_LEV: String = "info")

lateinit var CONFIG: Conf
