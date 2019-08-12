# coding: utf-8
# created at 2019/2/23
import re
import os
from config import CONFIG
from core import utils
from core.JavaParser import JavaClassParser, get_service_registers, StubClassParser

__author__ = "fripSide"

osp = os.path
RAW_SERVICE_LIST = "data/raw_service_list.txt"
SERVICE_LIST = "data/services.txt"

"""
TODO:
1. 找到Service定义，看Service Manger类。
2. 处理Java接口、C++接口、aidl接口，提取参数。
3. 自动生成fuzzy 参数。(先手动构造)
4. Java call graph分析（判断是否需要使用ICFG）。
暂定方案：
C++找出JGR API，通过call graph验证native api，判断是否使用了JGR API，找出这类native API。
找出存在漏洞的native API对应的Java Native API调用。然后以Java IPC methods 为入口，判断是否使这些用了Java Native API。
"""


def parse_service_list():
    """
    处理 services list输出的services列表，转成更容易读取的文本。
    :return:
    """
    services = []
    with open(RAW_SERVICE_LIST, "r") as fp:
        for li in fp:
            m = re.match("([a-zA-Z0-9\_\.]+)\:\s\[([a-zA-Z0-9\.]*)\]", li)
            services.append((m.group(1), m.group(2)))
    with open(SERVICE_LIST, 'w') as fp:
        for sv in services:
            fp.write(f"{sv[0]} {sv[1]}\n")
    return services


def get_service_list():
    """
    read services list dump from android 9
    :return: java_service_list, cpp_service_list
    """
    if not osp.exists(SERVICE_LIST):
        parse_service_list()
    java_services = []
    cpp_services = []
    with open(SERVICE_LIST, "r") as fp:
        for line in fp:
            items = line.rstrip().split(" ")
            if len(items) == 2:
                java_services.append(items)
            else:
                cpp_services.append(items[0])
    return java_services, cpp_services


def analysis_services_impl():
    ctx_cls = osp.join(CONFIG.DEFAULT_AOSP_PATH, CONFIG.CONTEXT_CLS)
    java, cpp = get_service_list()
    service_cls = JavaClassParser(ctx_cls).filter_members(["public", "static", "final"], "SERVICE")
    java_name = {}
    for it in java:
        java_name[it[0]] = it[1]
    print("service num", len(service_cls))
    print(java_name)
    for it in service_cls:
        val_name = it[0]
        cls_name = it[1].strip('\"')
        if cls_name in java_name.keys():
            print("contain in service list ", cls_name, java_name[cls_name], val_name)
        else:
            pass
            # print("not contain in service list", cls_name)


class ServiceImplAnalyzer:
    SERVICE_FILE_PATH = CONFIG.local_path("data/services_path.json")
    FUZZY_GEN_PATH = CONFIG.local_path("data/fuzzy")

    def __init__(self):
        self.service_file_mp = {}
        self.service_ctx_str = {}
        self.__init_service_file_mp()

    def __init_service_file_mp(self):
        if os.path.exists(self.SERVICE_FILE_PATH):
            utils.write_log("Load service path from cache: %s", self.SERVICE_FILE_PATH)
            self.service_file_mp = utils.load_json(self.SERVICE_FILE_PATH)
            return
        sr_cls = osp.join(CONFIG.DEFAULT_AOSP_PATH, CONFIG.SERVICE_REGISTER_CLS)
        service_manager = get_service_registers(sr_cls)
        from core.JniAPIExtractor import SourceFileScanner
        jni = SourceFileScanner(CONFIG.DEFAULT_AOSP_PATH)
        java_files = jni.get_files(".java")
        for jf in java_files:
            for k, it in service_manager.items():
                cls = it["cls"] + ".java"
                fi = jf.split(os.sep).pop()
                if fi == cls:
                    self.service_file_mp[it["cls"]] = jf
        utils.write_json(self.service_file_mp, self.SERVICE_FILE_PATH)

    def __init_service_ctx_str(self):
        # get service register string: Context.XXX_SERVICE
        if self.service_ctx_str:
            return
        sr_cls = osp.join(CONFIG.DEFAULT_AOSP_PATH, CONFIG.SERVICE_REGISTER_CLS)
        service_manager = get_service_registers(sr_cls)
        for k, v in service_manager.items():
            self.service_ctx_str[v["cls"]] = k

    def gen_api_params(self):
        utils.check_and_mkdir(self.FUZZY_GEN_PATH)
        # test_file = self.service_file_mp["TvInputManager"]
        # print(test_file)
        # jp = JavaClassParser(test_file, "TvInputManager")
        # print(jp.get_available_api())
        self.__init_service_ctx_str()
        for cls, path in self.service_file_mp.items():
            print(path)
            jp = JavaClassParser(path, cls)
            svr = jp.get_available_api()
            svr.atr_ctx_str = self.service_ctx_str[cls]
            utils.write_json(svr.dump(), CONFIG.local_path(f"data/fuzzy/{cls}.json"))


def service_number_statistics():
    from core.JniAPIExtractor import SourceFileScanner
    java_files = SourceFileScanner().get_files("java")
    stub_num = 0
    for file in java_files:
        try:
            p = StubClassParser(file)
            l = p.get_public_apis_in_stub()
            if l > 0:
                stub_num += l
                print("Find stub", file, l)
        except Exception as ex:
            # print(ex.args)
            # print("failed to parser", file)
            pass

    # service manager api number
    svc_num = 0
    sr = ServiceImplAnalyzer()
    for cls, path in sr.service_file_mp.items():
        jp = JavaClassParser(path, cls)
        svr = jp.get_public_api_list()
        svc_num += len(svr)
    print("total: ", svc_num + stub_num, svc_num, stub_num)


def test_get_services():
    sr_cls = osp.join(CONFIG.DEFAULT_AOSP_PATH, CONFIG.SERVICE_REGISTER_CLS)
    service_manager = get_service_registers(sr_cls)
    print(service_manager)
    for k, v in service_manager.items():
        # print(k, v["cls"])
        if "IBinder" in v["cls"]:
            print(k)

def test_analysis_aidl():
    pass


if __name__ == "__main__":
    # test_get_services()
    # sa = ServiceImplAnalyzer()
    # sa.gen_api_params()
    service_number_statistics()
