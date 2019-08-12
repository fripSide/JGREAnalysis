# coding: utf-8
# created at 2019/3/5
__author__ = "fripSide"

import six


class TypeDef:

    Float = "float"
    Int = "int"
    Bool = "bool"


class Model(dict):

    def __getattr__(self, key):
        try:
            return self[key]
        except KeyError:
            return None

    def __setattr__(self, key, value):
        self[key] = value

    def _get_val(self, it):
        if isinstance(it, Model):
            # print(type(it))
            return it.dump()
        else:
            # print(it, type(it))
            return it

    def dump(self):
        data = {}
        for key, val in self.items():
            if key.startswith("atr_"):
                nk = key.replace("atr_", "")
                if isinstance(val, Model):
                    data[nk] = val.dump()
                elif isinstance(val, list):
                    data[nk] = []
                    for it in val:
                        # print(nk, "list", it.dump())
                        data[nk].append(self._get_val(it))
                elif isinstance(val, dict):
                    data[nk] = {}
                    for k, v in val.items():
                        # print(k, "dict")
                        data[nk][k] = self._get_val(v)
                else:
                    data[nk] = self._get_val(val)
        return data


class CallBackDesc(Model):

    def __init__(self):
        super(CallBackDesc, self).__init__()
        self.atr_cls_name = ""
        # override methods
        self.atr_methods = []


class ParamTypeDesc(Model):
    """params: string, int, float, """
    def __init__(self):
        super(ParamTypeDesc, self).__init__()
        self.atr_param_name = ""
        self.atr_param_type = ""
        self.atr_param_ref = None


class MethodDesc(Model):

    def __init__(self):
        super(MethodDesc, self).__init__()
        self.atr_method_name = ""
        self.atr_params = []
        self.atr_return_type = None


class ApiDesc(Model):

    def __init__(self):
        super(ApiDesc, self).__init__()
        self.atr_packages = []
        self.atr_permissions = []
        self.atr_method_ref = None


class ServiceManagerDesc(Model):

    def __init__(self):
        super(ServiceManagerDesc, self).__init__()
        self.atr_service_name = ""
        self.atr_ctx_str = ""
        self.atr_api_list = []
