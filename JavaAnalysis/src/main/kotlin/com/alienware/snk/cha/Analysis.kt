package com.alienware.snk.cha

import com.alienware.snk.utils.DebugTool
import soot.*
import soot.jimple.toolkits.callgraph.CHATransformer
import soot.jimple.toolkits.callgraph.Targets
import soot.options.Options
import soot.PackManager



/*
soot callgraph example
https://gist.github.com/bdqnghi/9d8d990b29caeb4e5157d7df35e083ce
TODO:
1. 实现基本的CHA分析
2. 分析framework.jar和android.jar

https://github.com/secure-software-engineering/SuSi/blob/df72e9d87ad2946c50a47d5188fa29135807338b/src/de/ecspride/sourcesinkfinder/SourceSinkFinder.java

 */

class SootAnalysis {

    val PROCESS_JAVA = "E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\java"
    val PROCESS_CLASS = "E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\class"
    var PROCESS_JAR = "E:\\PaperWork\\ServiceHelper\\jar\\framework_6.0.1_partly.jar"
    var CLASS_PATH = "E:\\PaperWork\\ServiceHelper\\jar\\rt.jar;E:\\PaperWork\\ServiceHelper\\jar\\jce.jar"

    init {
//        initSoot()

    }

    fun initSoot() {
        soot.G.reset()
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_prepend_classpath(true)
        Options.v().set_validate(true)
        Options.v().set_whole_program(true)
        Options.v().set_output_format(Options.output_format_jimple)
        Options.v().set_src_prec(Options.src_prec_class)
        Options.v().set_process_dir(arrayListOf("E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\java"))
//        Options.v().set_soot_classpath("E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\rt.jar;E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\jce.jar")
        Options.v().keep_line_number()
//        Options.v().set_no_bodies_for_excluded(true)
//        Options.v().set_app(true)
        Options.v().set_output_dir("E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\output")
        Scene.v().addBasicClass("java.lang.CharSequence", SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses()
        println(SourceLocator.v().getClassesUnder("E:\\PaperWork\\JGRAnalyzer\\AppGen\\test\\java"))

    }

    fun initSootForFramework() {
        val javaDemo = "E:\\PaperWork\\JGRAnalyzer\\SourceAudit\\data\\test\\cls"
        val dir = arrayListOf(javaDemo)
        soot.G.reset()
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_whole_program(true)
        Options.v().set_src_prec(Options.src_prec_class)
        Options.v().set_process_dir(dir)
        Options.v().set_soot_classpath(CLASS_PATH)
        Options.v().set_validate(false)
        Options.v().set_no_bodies_for_excluded(true)
//        Options.v().parse()
        Scene.v().loadNecessaryClasses()
    }

    fun setSootRunPkt() {
        //PackManager.v().getPack("wjtp").add(Transform("wjtp.MyInstrumentation", Transformer.))
        PackManager.v().getPack("wjtp").apply()
        // PackManager.v().runPacks();
        PackManager.v().writeOutput()

    }

    fun initSimplest() {
        soot.G.reset()
        Options.v().set_whole_program(true)
        Options.v().set_process_dir(arrayListOf(PROCESS_CLASS))

//        Options.v().set_src_prec(Options.src_prec_class)
//        Options.v().set_output_format(Options.src_prec_class)
//        Options.v().set_main_class("CallGraphs")

    }

    fun testDispath() {
        val sc = Scene.v().getSootClass("Cat")
        sc.methods.forEach { mtd ->
            if (mtd.name == "saySomething") {
                val hir = Scene.v().activeHierarchy
                println(hir.resolveAbstractDispatch(sc, mtd))
            }
//            println(mtd.name)
        }
    }

    fun run() {
//        initSimplest()
        initSootForFramework()
//        loadClass("A")
//        loadClass("CallGraphs", true)
//        dumpMethods("CallGraphs")
        testCallGraph()
        testDispath()

        val testWords = "CheckGroupOwner"
        var pos = 0
        val last = testWords.length - 1
        var upCase = true
        for (i in 0..last) {
           if (!upCase && testWords[i].isUpperCase()) {
               println(testWords.subSequence(pos, i))
               pos = i
           }
            upCase = testWords[i].isUpperCase()
        }
        println(testWords.subSequence(pos, last))
    }

    fun dumpMethods(mtd: String) {
        var appclass = Scene.v().getSootClass(mtd)
        println(appclass)
        println("Class Name :" + appclass.name)
        //获取类中的相关的方法
        appclass.getMethods().forEach {
            println("\tfunction member: " + it.name)
        }
    }

    fun testCallGraph() {
//        Scene.v().entryPoints =
        PackManager.v().writeOutput()
        PackManager.v().getPack("wjtp").add(Transform("wjtp.myTrans", object : SceneTransformer() {
            override fun internalTransform(p0: String?, p1: MutableMap<String, String>?) {
                CHATransformer.v().transform()
                println("Build Call Graph Using CHA")
                var cg = Scene.v().callGraph
                println(cg)
//                print(Scene.v().mainClass)
//                var srcMtd = Scene.v().mainClass.getMethodByName("doStuff")
//                var targets = Targets(cg.edgesOutOf(srcMtd))
//                targets.forEach {
//                    println("CG: $srcMtd may call $it")
//                }
            }
        }))
        PackManager.v().runPacks()
    }

     fun loadClass(name: String, main: Boolean = false): SootClass {
//         var mainCls = Scene.v().loadClass("CallGraphs", SootClass.BODIES)
        var c = Scene.v().loadClass(name, SootClass.BODIES)
        c.setApplicationClass()
        if (main) {
            println(c.methods)
            Scene.v().mainClass = c
//            val mtd = c.getMethodByNameUnsafe("main")
//            println("main method: $mtd")
//            Options.v().set_main_class(mtd.signature)
//            Scene.v().setEntryPoints(arrayListOf(mtd))
        }
        return c
    }

}

class CHACallgraph {
    init {

    }


}