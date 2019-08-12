package com.alienware.snk.services

import com.alienware.snk.Config
import soot.Scene
import soot.SootMethod
import soot.options.Options

class IPCExtractor {

    private fun initSoot() {
        var test_cls = Config.TEST_CLS
        soot.G.reset()
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_whole_program(true)
        Options.v().set_src_prec(Options.src_prec_class)
        Options.v().set_process_dir(arrayListOf(test_cls))
        Options.v().set_soot_classpath(Config.CLASS_PATH)
        Options.v().set_output_format(Options.output_format_none)
        Options.v().set_validate(false)
        Options.v().set_no_bodies_for_excluded(true)
//        Options.v().setPhaseOption("jp", "off")
        // "-p", "cg", "all-reachable", "-p", "cg", "verbose:true",
        val args = arrayOf("-p", "cg", "enabled:false", "-p", "cg.cha", "enabled:false")
        Options.v().parse(args)
        Scene.v().loadNecessaryClasses()
    }
}

fun runIPCTest() {
//    var ipc = IPCExtractor()
}