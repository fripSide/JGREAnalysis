package com.alienware.snk.cha

import soot.*
import soot.Unit
import soot.toolkits.graph.UnitGraph
import soot.toolkits.scalar.ArraySparseSet
import soot.toolkits.scalar.BackwardFlowAnalysis
import soot.toolkits.scalar.FlowSet
import soot.jimple.spark.ondemand.pautil.SootUtil.getMainMethod
import soot.toolkits.graph.BriefUnitGraph
import soot.PackManager
import soot.options.Options


class LiveVariableAnalysis(var g: UnitGraph): BackwardFlowAnalysis<Unit, FlowSet<Local>>(g) {

    private val emptySet: FlowSet<Local> = ArraySparseSet<Local>()

    init {
        doAnalysis()
    }

    override fun newInitialFlow(): FlowSet<Local> {
        return emptySet.clone()
    }

    override fun merge(inSet1: FlowSet<Local>?, inSet2: FlowSet<Local>?, outSet: FlowSet<Local>?) {
        inSet1?.union(inSet2, emptySet)
    }

    override fun copy(srcSet: FlowSet<Local>?, destSet: FlowSet<Local>?) {
        srcSet?.copy(destSet)
    }

    override fun flowThrough(inSet: FlowSet<Local>?, node: Unit?, outSet: FlowSet<Local>?) {
        // outSet is the set at enrty of the node
        // inSet is the set at exit of the node
        // out <- (in - write(node)) union read(node)

        // out <- (in - write(node))


        val writes: FlowSet<Local> = emptySet.clone()
        node?.useAndDefBoxes?.forEach { def ->
            if (def.value is Local) {
                writes.add(def.value as Local)
            }
        }

        inSet?.difference(writes, outSet)

        node?.useBoxes?.forEach { use ->
            if (use.value is Local) {
                outSet?.add(use.value as Local)
            }
        }
        println("Unit: $node $inSet $outSet")
    }
}

class AnalysisTransform: SceneTransformer() {
    override fun internalTransform(p0: String?, p1: MutableMap<String, String>?) {
        // Get Main Method
//        var entryPointCls = Scene.v().getSootClass("Main")
//        println(entryPointCls)
//        var mtd = entryPointCls.getMethodByNameUnsafe("main")
//        println(mtd)
//        println(Scene.v().mainMethod.activeBody)
//        val cls = Scene.v().mainClass
        val sMethod = Scene.v().mainMethod
        val graph = BriefUnitGraph(sMethod.activeBody)
        println(graph)
        val analysis = LiveVariableAnalysis(graph)

        graph.forEach { s ->
            System.out.print(s)

            var d = 40 - s.toString().length
            while (d > 0) {
                print(".")
                d--
            }

            var set = analysis.getFlowBefore(s)

            print("\t[entry: ")
            for (local in set) {
                print(local.toString() + " ")
            }

            set = analysis.getFlowAfter(s)

            print("]\t[exit: ")
            for (local in set) {
                print(local.toString() + " ")
            }
            println("]")
        }
    }

}

fun runLiveVarAnalysis() {
    var test_cls = PROCESS_JAR
        test_cls = TEST_CLS
    soot.G.reset()
    Options.v().set_allow_phantom_refs(true)
    Options.v().set_whole_program(true)
    Options.v().set_src_prec(Options.src_prec_class)
    Options.v().set_process_dir(arrayListOf(test_cls))
    Options.v().set_soot_classpath(CLASS_PATH)
    Options.v().set_main_class("Main")
    Options.v().set_output_format(Options.output_format_none)
    Options.v().set_no_bodies_for_excluded(true)
//        Options.v().setPhaseOption("jp", "off")
    // "-p", "cg", "all-reachable", "-p", "cg", "verbose:true",
    val args = arrayOf("-p", "cg", "enabled:false", "-p", "cg.cha", "enabled:false")
    Options.v().parse(args)
    Scene.v().loadNecessaryClasses()
    // if disable call graph, should run packs to set the mainMethod
    PackManager.v().runPacks()
    PackManager.v().getPack("wjtp").add(Transform("wjtp.dfa", AnalysisTransform()))
    PackManager.v().runPacks()

//    var entryPointCls = Scene.v().getSootClass("Main")
//    println(entryPointCls)
//    var mtd = entryPointCls.getMethodByNameUnsafe("main")
//    println(mtd.activeBody)
//    println(Scene.v().mainMethod.activeBody)

    val mainClass = "Main"
    val sootArgs = arrayOf("-cp", test_cls, "-pp", // sets the class path for Soot
            "-w", // Whole program analysis, necessary for using Transformer
            "-src-prec", "java", // Specify type of source file
            "-main-class", mainClass, // Specify the main class
            "-f", "J", // Specify type of output file
            mainClass)

//    PackManager.v().getPack("wjtp").add(Transform("wjtp.dfa", AnalysisTransform()))
//    soot.Main.main(sootArgs)
}