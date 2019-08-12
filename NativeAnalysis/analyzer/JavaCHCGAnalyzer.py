# coding: utf-8
# created at 2019/4/7
import os
from core.JavaParser import JavaClassParser, JavaCallgraphBuilder
import core.utils as utils
from config import CONFIG

__author__ = "fripSide"

"""
参考PScout, version 2
https://github.com/zd2100/PScout/blob/master/src/pscout/core/AnalyzeInvocation.java
https://github.com/zd2100/PScout/blob/master/src/pscout/PScout.java
1. pscout.buildClassHierarchyCallGraph();
2. pscout.analyzeInvocations();
"""


class JavaPackageFileMap:
    """
    build method level method to files mapping
    """
    CACHE_PATH = CONFIG.local_path("data/callgraph/java_methods.json")

    def __init__(self, java_files):
        self.package_map = {}
        self.method_map = {}
        self.files = java_files

    def build(self):
        if os.path.exists(self.CACHE_PATH):
            self.method_map = utils.load_json(self.CACHE_PATH)
            utils.write_log("Load java method declare file: %s", self.CACHE_PATH)
            return
        for fi in self.files:
            self.__read_package(fi)
        utils.write_json(self.method_map, self.CACHE_PATH)

    def __read_package(self, cls_file):
        try:
            parser = JavaClassParser(cls_file)
            mtd_map = parser.get_method_declares()
            self.method_map.update(mtd_map)
        except Exception as ex:
            print(ex)


class JavaClsHierarchyBuilder:

    def __init__(self):
        pass


class JavaCallGraphEdge:
    """
    map[key][set]
    """
    def __init__(self):
        self.src = ""
        self.mtd = ""
        self.key = ""

    def __hash__(self):
        return self.key


class CHCGAnalyzer:
    SERVICE_FILE_PATH = CONFIG.local_path("data/services_path.json")
    """
    https://github.com/zd2100/PScout/blob/master/src/pscout/analyzers/CHCGAnalyzer.java
    class heichary analysis
    """

    def __init__(self, mtd_mp):
        self.mtd_path_map = mtd_mp
        self.executing_path = []
        self.mtd_out_cache = {}
        self.native_calling_map = {}
        self.start_analysis()

    def start_analysis(self):
        test_file = CONFIG.aosp_file(r"frameworks\base\services\core\java\com\android\server\accounts\AccountManagerService.java")
        self.__forward_reachable_analysis(test_file)

    def __start_from_entry_points(self):
        services = utils.load_json(self.SERVICE_FILE_PATH)
        entry_points = []
        for src in services:
            self.__forward_reachable_analysis(src)

    def __forward_reachable_analysis(self, entry_file):
        calling_chain = []
        work_set = self.__get_method_key_list(entry_file)
        self.__get_method_key_list(entry_file)
        while len(work_set) > 0:
            mtd_key = work_set.pop(0)
            if "AccountManagerService_" in mtd_key:
                edges = self.__get_out_edges(mtd_key)
                # print(mtd_key)
                for e in edges:
                    work_set.append(edges)
                exit(-1)

    def __get_method_key_list(self, file):
        parser = JavaClassParser(file)
        return parser.get_public_method_key_list()

    def __get_out_edges(self, mtd_key):
        file = self.mtd_path_map.get(mtd_key)
        if not file:
            return []
        builder = JavaCallgraphBuilder(file)
        builder.get_method_calling_by_method_key(mtd_key)
        # is_end, out_path =
        return []


class CallGraphBuilder:
    """
    visit direction and build callgraph
    """
    pass


def gen_java_callgraph():
    from core.JniAPIExtractor import SourceFileScanner
    files = SourceFileScanner()
    java_files = files.get_files(".java")
    methods = JavaPackageFileMap(java_files)
    methods.build()
    cha = CHCGAnalyzer(methods.method_map)
    cha.start_analysis()


if __name__ == "__main__":
    gen_java_callgraph()
