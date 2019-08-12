# coding: utf-8
# created at 2019/2/20
__author__ = "fripSide"

import os
from config import CONFIG
from core import utils


osp = os.path


class SourceFileScanner:
    """
    通过扫每一个文件，根据后缀找出java、c/c++文件，然后找出JNI接口的定义。
    TODO:
    1. （完成）获取目录下所有目标文件。
    2.初步提取Java和C++ JNI接口。
    """
    FILE_LIST_PATH = CONFIG.local_path("data/file_list.txt")
    TARGET_EXT = [".java", ".h", ".cc", ".c", ".cpp", ".aidl"]
    CPP_EXT = [".h", ".cc", ".cpp", ".c"]
    SCAN_DIRS = ["frameworks"]

    def __init__(self, aosp_path=CONFIG.DEFAULT_AOSP_PATH):
        self.file_list = []
        self.file_ext_mp = {}
        self.__scan_files(aosp_path)
        self.__process_files()

    def __scan_files(self, base_dir):
        if osp.exists(self.FILE_LIST_PATH):
            utils.write_log("Load file list from cache: %s", self.FILE_LIST_PATH)
            with open(self.FILE_LIST_PATH) as fp:
                for line in fp:
                    self.file_list.append(line.rstrip())
            return
        for ch in self.SCAN_DIRS:
            dir_path = osp.join(base_dir, ch)
            files = self.__walk_dir(dir_path)
            with open(self.FILE_LIST_PATH, "wb") as fp:
                for fi in files:
                    fp.write((fi + "\n").encode("utf-8"))
            self.file_list += files

    def __walk_dir(self, pt):
        all_files = []
        files = os.listdir(pt)
        for fp in files:
            cur_path = osp.join(pt, fp)
            if osp.isdir(cur_path):
                all_files += self.__walk_dir(cur_path)
            else:
                if os.path.isfile(cur_path):
                    name, ext = osp.splitext(fp)
                    if ext in self.TARGET_EXT:
                        all_files.append(cur_path)
                # else:
                #     print("is not file", cur_path, os.path.exists(cur_path))
        return all_files

    def __process_files(self):
        processor_map = {
            ".java": self.__process_java_file,
            ".c": self.__process_cpp_file,
            ".cc": self.__process_cpp_file,
            ".cpp": self.__process_cpp_file,
            ".h": self.__process_cpp_file,
            ".aidl": self.__process_aidl
        }
        # for fp in self.file_list:
        #     pass

    def __process_java_file(self, file):
        pass

    def __process_cpp_file(self, file):
        pass

    def __process_aidl(self, file):
        pass

    def get_files(self, ext):
        if ext not in self.file_ext_mp:
            self.file_ext_mp[ext] = filter(lambda f: f.endswith(ext), self.file_list)
        return self.file_ext_mp[ext]

    def get_cpp_files(self):
        for ext in self.CPP_EXT:
            for item in self.get_files(ext):
                yield item

    def statistics(self):
        num = len(self.file_list)
        utils.write_log("Total Files: %d", num)
        java_num = len(list(filter(lambda f: f.endswith(".java"), self.file_list)))
        cpp_num = len(list(filter(lambda f: f.endswith(".c") or f.endswith("h")
                                            or f.endswith("cc") or f.endswith("cpp"), self.file_list)))
        aidl_num = len(list(filter(lambda f: f.endswith(".aidl"), self.file_list)))
        utils.write_log("java files: %d", java_num)
        utils.write_log("cpp files: %d", cpp_num)
        utils.write_log("aidl files: %d", aidl_num)


class JNIBridgeMap:

    def __init__(self):
        self.java_to_cpp = {}
        self.cpp_to_java = {}
        self.jni_bridge_map = {}

    def __parser_jni_cpp_register_func(self, file):
        from core.CxxParser import CxxJniParser
        parser = CxxJniParser(file)
        results = parser.get_results()
        self.jni_bridge_map.update(results)

    def process_cpp_files(self, files):
        for fi in files:
            self.__parser_jni_cpp_register_func(fi)
        print(len(self.jni_bridge_map), self.jni_bridge_map)
        utils.write_json(self.jni_bridge_map, CONFIG.local_path("data/jni.json"))


if __name__ == "__main__":
    jni_bridge = JNIBridgeMap()
    src = SourceFileScanner()
    files = src.get_cpp_files()
    jni_bridge.process_cpp_files(files)
