package com.alienware.snk.cha

import soot.*
import soot.jimple.InvokeExpr
import soot.jimple.spark.SparkTransformer
import soot.jimple.toolkits.callgraph.CHATransformer
import soot.jimple.toolkits.callgraph.CallGraph
import soot.jimple.toolkits.callgraph.Targets
import soot.options.Options
import soot.toolkits.graph.DirectedGraph
import soot.toolkits.scalar.BackwardFlowAnalysis
import soot.toolkits.scalar.SimpleLocalUses
import soot.toolkits.scalar.SmartLocalDefsPool
import java.lang.Exception

const val TEST_CLS = "E:\\PaperWork\\JGRAnalyzer\\SourceAudit\\data\\test\\cls"
const val PROCESS_JAR = "E:\\PaperWork\\ServiceHelper\\jar\\framework_6.0.1_partly.jar"
const val CLASS_PATH = "E:\\PaperWork\\ServiceHelper\\jar\\rt.jar;E:\\PaperWork\\ServiceHelper\\jar\\jce.jar"

/*
https://github.com/Alexandre-Bartel/permission-map

 */

class StartSoot {

    private val visited = HashSet<String>()
    val callPathMap = HashMap<String, String>()

    init {

    }

    fun initSoot() {
        var test_cls = PROCESS_JAR
//        test_cls = TEST_CLS
        soot.G.reset()
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_whole_program(true)
        Options.v().set_src_prec(Options.src_prec_class)
        Options.v().set_process_dir(arrayListOf(test_cls))
        Options.v().set_soot_classpath(CLASS_PATH)
        Options.v().set_validate(false)
        Options.v().set_no_bodies_for_excluded(true)
        // "-p", "cg", "all-reachable", "-p", "cg", "verbose:true",
        val args = arrayOf("-p", "cg", "enabled:false", "-p", "cg.cha", "enabled:false")
        Options.v().parse(args)
    }

    // use spark to generate call graph
    fun generateSparkCallGraph() {
        val opt = HashMap<String, String>()
        opt.put("verbose","true")
        opt.put("propagator","worklist") //worklist
        opt.put("simple-edges-bidirectional","true")
        opt.put("on-fly-cg","true")
        opt.put("set-impl","double")
        opt.put("double-set-old","hybrid")
        opt.put("double-set-new","hybrid")
        opt.put("dump-html","false")
        opt.put("dump-pag","false")
        opt.put("string-constants","false")
//        SparkTransformer.v().transform("Spark",opt)
        SparkTransformer.v().transform()
    }

    fun generateCHACallGraph() {
        CHATransformer.v().transform()
    }

    fun startCallGraphAnalysis() {
        initSoot()
        Scene.v().loadNecessaryClasses()
        PackManager.v().getPack("wjtp").add(Transform("wjtp.myTrans", object : SceneTransformer() {
            override fun internalTransform(p0: String?, p1: MutableMap<String, String>?) {
                println("Start to build callgraph:")
//                getEdgeViaCg()
//                getEdgeWithoutCg()
            }
        }))
        val start = System.currentTimeMillis()
        PackManager.v().runPacks()
        var finish = System.currentTimeMillis()
        println("Spending: " + (finish - start) / 1000F + "s")
    }


    fun getEdgeViaCg() {
        generateSparkCallGraph()
        var entryPointCls = Scene.v().getSootClass("Main")
        var mtd = entryPointCls.getMethodByNameUnsafe("main")
        var cg = Scene.v().callGraph
        var targets = Targets(cg.edgesOutOf(mtd))
        targets.forEach {
            println("$mtd may call $it")
        }
    }

    fun getEdgeWithoutCg() {
        var entryPointCls = Scene.v().getSootClass("Main")
        var mtd = entryPointCls.getMethodByNameUnsafe("main")
        walkCg(mtd, entryPointCls.toString())
    }

    fun walkCg(mtd: SootMethod, callpath: String) {
        val sc = mtd.declaringClass
        if (sc.isPhantomClass) return
        try {
            var b = mtd.retrieveActiveBody()
            var vals = b.useBoxes
            vals.forEach {
                if (it.value is InvokeExpr) {
                    var invoke = it.value as InvokeExpr
                    var curMtd = invoke.method
                    var curPath = mtd.signature + " " + curMtd.signature
                    if (visited.contains(curPath)) {
                        return@forEach
                    }
                    visited.add(curPath)
                    println("CG: $curPath")
//                    walkCg(curMtd, callpath + curMtd.signature)
                }
            }
        } catch (ex: RuntimeException) {
            println(mtd.signature +  " Failed: " + ex.message)
        }
    }
}

class Task1 {
    fun initSoot() {
        var test_cls = PROCESS_JAR
//        test_cls = TEST_CLS
        soot.G.reset()
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_whole_program(true)
        Options.v().set_src_prec(Options.src_prec_class)
        Options.v().set_process_dir(arrayListOf(test_cls))
        Options.v().set_soot_classpath(CLASS_PATH)
        Options.v().set_output_format(Options.output_format_none)
        Options.v().set_validate(false)
        Options.v().set_no_bodies_for_excluded(true)
//        Options.v().setPhaseOption("jp", "off")
        // "-p", "cg", "all-reachable", "-p", "cg", "verbose:true",
        val args = arrayOf("-p", "cg", "enabled:false", "-p", "cg.cha", "enabled:false")
        Options.v().parse(args)
        Scene.v().loadNecessaryClasses()
    }

    fun getAllFields() {
        initSoot()
        try {
//            PackManager.v().runPacks()
        } catch (ex : java.lang.RuntimeException) {
            ex.printStackTrace()
            println("run packs failed!")
        }

        println("Run Packs finish!")
//        DebugTool.printSootClasses()
        val sc1 = Scene.v().getSootClass("SystemServiceRegistry")
        println(sc1.isFinal)
        val fields = sc1.fields
        fields.forEach {
            println(it.signature)
            println(it.makeRef().name())
            println(it.declaration)
        }
        println(sc1.fields)
        val mtds = sc1.methods
        println(mtds)
        mtds.forEach {
//            println(it.activeBody)
            val body = it.activeBody
            val graph = BriefUnitPrinter(body)
            println(graph)
            val defAnalysis = SmartLocalDefsPool.v().getSmartLocalDefsFor(body)
            val useAnalysis = SimpleLocalUses(body, defAnalysis)
            println(useAnalysis)
        }

        findServiceHelper()
    }


    fun findServiceHelper() {
        Scene.v().applicationClasses.forEach {
//            println(it.shortJavaStyleName)
            val name = it.shortJavaStyleName
            if (name.equals("AudioManager")) {
                println("AudioManager: $it")
            }
            try {
                it.methods.forEach { mtd ->
                    val name = mtd.name
                    if (name.contains("asInterface")) {
//                        println("Find ${mtd.signature}")
                    }
                    if (name == "getService") {
                        println("Find getService ${mtd.signature} ${mtd.name}")
                    }
                    checkMethod(mtd)
                }
            } catch (ex : Exception) {
                ex.printStackTrace()
            }

        }
    }

    fun checkMethod(mtd: SootMethod) {
        if (mtd.isNative) {
//            println("Find native method: $mtd")
            return
        }
        if (mtd.isAbstract ) return
        val sc = mtd.declaringClass
        if (sc.isPhantomClass) return
        try {
            var b = mtd.retrieveActiveBody()
            var vals = b.useBoxes
            vals.forEach {
                if (it.value is InvokeExpr) {
                    var invoke = it.value as InvokeExpr
                    var curMtd = invoke.method
                    if (curMtd.name.equals("addService")) {
                        println("Find registerService $curMtd")
                    }
//                    walkCg(curMtd, callpath + curMtd.signature)
                }
            }
        } catch (ex: RuntimeException) {
//            println(mtd.signature +  " Failed to pass: " + ex.message)

        }
    }
}

//https://svn.sable.mcgill.ca/soot/soot/releases/soot-2.3.0/src/soot/toolkits/scalar/GuaranteedDefs.java
class FlowAnalysisDemo<N, A> (graph: DirectedGraph<N>): BackwardFlowAnalysis<N, A>(graph) {
    override fun newInitialFlow(): A {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun merge(p0: A, p1: A, p2: A) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun copy(p0: A, p1: A) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun flowThrough(p0: A, p1: N, p2: A) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

fun startSootAnalysis() {
//    val sootRun = StartSoot()
//    sootRun.startCallGraphAnalysis()
    val task1 = Task1()
    task1.getAllFields()
}