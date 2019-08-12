# coding: utf-8
# created at 2019/3/27
from config import CONFIG
from core.JniAPIExtractor import SourceFileScanner

__author__ = "fripSide"

import javalang

TEST_JAVA_CLS = CONFIG.local_path("data/test/DynamicDispatchExample.java")
AIDL_FILES_LIST = CONFIG.local_path("data/aidl.txt")


def get_aidl():
    sc = SourceFileScanner()
    files = sc.get_files("aidl")
    files = list(files)
    with open(AIDL_FILES_LIST, "w") as fp:
        for fi in files:
            print(fi)
            fp.write(f"{fi}\n")
    print(len(files))

if __name__ == "__main__":
    get_aidl()

