import com.alienware.snk.utils.DebugTool
import com.alienware.snk.utils.LogNow
import com.alienware.snk.utils.SootTool
import soot.Scene
import soot.SootClass
import soot.SootMethod
import soot.jimple.ClassConstant
import soot.jimple.InvokeExpr
import java.lang.Exception

object ServiceHelperExtractor {

    private val kServiceHelperRegisterCls: String = "android.app.SystemServiceRegistry"
    private val kServiceHelperRegisterFunc: String = "registerService"
    private val kServiceHelperCreateFunc = "createService"
    private val kGetServiceMtd = "<android.os.ServiceManager: android.os.IBinder getService(java.lang.String)>"
    private val kGetServiceMtd2 = "<android.os.ServiceManager: android.os.IBinder getServiceOrThrow(java.lang.String)>"
    private val warnTag = "The framework.jar is not the full version which need to isIn services.jar or do not support current Android SDK version. "

    fun findRegisteredServiceHelperClass(): HashSet<ServiceApiClass> {
        val cls = Scene.v().getSootClass(kServiceHelperRegisterCls)
        var registerMtd: SootMethod? = null
        run l1@{
            cls.methods.forEach { mtd ->
                if (SootTool.isInitMethod(mtd)) {
                    registerMtd = mtd
                    return@l1 //break
                }
            }
        }
        val body = registerMtd?.retrieveActiveBody()
        if (body == null) {
            DebugTool.panic(Exception("ServiceRegisterClass Should Have cinit! $warnTag"))
        }

        val helperApiList = HashSet<ServiceApiClass>()
        var helperCnt = 0
        var implCnt = 0
        for (useBox in body!!.useBoxes) {
            if (useBox.value is InvokeExpr) {
                val api = resolveServiceHelperRegister(useBox.value as InvokeExpr)
                if (api != null) {
                    val pkg = api.inf
//                    val focusCls = "android.app.NotificationManager"
//                    if (pkg.name != focusCls) continue
                    helperCnt++
//                    if (findServiceImplStubUsage(api)) implCnt++
//                    else LogNow.debug("$pkg Service impl for helper does not find!")
//                    DebugTool.exitHere("extract one cls $api")
                    helperApiList.add(api)
                }
            }
        }
//        LogNow.info("Service Helper Class Total Num: $helperCnt Impl: $implCnt")
        return helperApiList
    }

    private fun resolveServiceHelperRegister(invoke: InvokeExpr): ServiceApiClass? {
        val funcName = invoke.method.name
        // is service helper register block
        if (funcName == kServiceHelperRegisterFunc && invoke.argCount > 2 && invoke.args[1] is ClassConstant) {
            val managerName = invoke.args[0]
            val blockCls = SootTool.getClsFromValue(invoke.args[2])
            var api = extractServiceApiFromRegisterBlock(blockCls!!)

            DebugTool.assert(api != null)
            if (api == null) {
                val helper = getServiceHelperRegisterName(invoke)
                if (helper != null)
                    api = ServiceApiClass(helper)
            }
            if (api != null) {
                api.managerName = managerName.toString()
            }
            return api
        }
        return null
    }

    private fun extractServiceApiFromRegisterBlock(cls: SootClass): ServiceApiClass? {
        // find
        var createMtd: SootMethod? = null
        for (mtd in cls.methods) {
            if (mtd.name == kServiceHelperCreateFunc && mtd.returnType.toString() != SootTool.OBJECT.name) {
                createMtd = mtd
                break
            }
        }
        createMtd = createMtd!!
        val ret = SootTool.getReturnFromMethod(createMtd, true)
        val helper = SootTool.getClsFromValue(ret!!)
        val api = ServiceApiClass(helper!!)
        api.serviceName = extractBinderTag(createMtd)

        return api
    }

    // get return type of registerService
    private fun getServiceHelperRegisterName(invoke: InvokeExpr) : SootClass? {
        val funcName = invoke.method.name
        if (funcName == kServiceHelperRegisterFunc && invoke.argCount > 1 && invoke.args[1] is ClassConstant) {
            val call = invoke.args[2]
            val cls = SootTool.getClsFromValue(call)
            var sc  = SootTool.getClsFromValue(invoke.args[1])
            cls?.methods?.forEach { mtd->
                if (mtd.name == kServiceHelperCreateFunc && mtd.returnType.toString() != SootTool.OBJECT.name) {
                    val ret = SootTool.getReturnFromMethod(mtd, true)
                    sc = SootTool.getClsFromValue(ret!!)
                    if (sc != null) {
                        return sc
                    } else {
                        DebugTool.panic("null sc $ret $mtd")
                    }
                }
            }
            return sc
        }
        return null
    }

    private fun extractBinderTag(mtd: SootMethod): String? {
        val b = mtd.retrieveActiveBody()
        for (box in b.useBoxes) {
            if (box.value is InvokeExpr) {
                val inv = box.value as InvokeExpr
                if (inv.method.toString() == kGetServiceMtd || inv.method.toString() == kGetServiceMtd2) {
                    return inv.args[0].toString()
                }
            }
        }
        return null
    }
}