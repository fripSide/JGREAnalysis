import com.alienware.snk.utils.LogNow
import soot.SootClass

class ApiData(var helperClass: String? = null, var inf: String? = null, var implClass: String? = null, var managerName: String? = null, var serviceName: String? = null)

class ServiceApiClass(var helperClass: SootClass? = null, var inf: SootClass? = null, var implClass: SootClass? = null) {

    var managerName: String? = null // service helper register name

    var serviceName: String? = null

    fun isHelperClass(): Boolean {
        return helperClass != null
    }

    override fun toString(): String {
        return "$managerName $serviceName $helperClass $implClass"
    }

    override fun hashCode(): Int {
        if (inf != null) {
            return inf.toString().hashCode()
        }
        if (helperClass != null) {
            return helperClass.toString().hashCode()
        }
        if (implClass != null) {
            return implClass.toString().hashCode()
        }
        if (managerName != null) {
            return managerName!!.hashCode()
        }
        if (serviceName != null) {
            return serviceName!!.hashCode()
        }
        return super.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is ServiceApiList) {
            return this.hashCode() == other.hashCode()
        }
        return super.equals(other)
    }

    fun getDataClass(): ApiData {
        var helperClsStr = helperClass?.name ?: ""
        var infStr = inf?.name ?: ""
        val implStr = implClass?.name ?: ""
        return ApiData(helperClsStr, infStr, implStr, managerName, serviceName)
    }
}

class ServiceApiList {


    companion object {
        val allApi = HashSet<ServiceApiClass>()

        fun getServiceApiForImple(sc: SootClass): ServiceApiClass? {
            allApi.forEach { api ->
                if (api.implClass == sc) return api
            }
            return null
        }

        fun getAllList(): HashSet<ServiceApiClass> {
            return allApi
        }
    }

    init {
        allApi.clear()
    }

    fun addServiceApiList(cls: HashSet<ServiceApiClass>) {
        allApi.addAll(cls)
    }

}