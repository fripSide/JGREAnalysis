# coding: utf-8
# created at 2019/2/23
__author__ = "fripSide"


class Conf:
    DEFAULT_AOSP_PATH = r"F:\Android_9.0\aosp"
    DEPLOY_DIR = r"E:\PaperWork\JGRE\JGREAnalyzer\NativeAnalysis"
    APPGEN_PATH = r"E:\PaperWork\JGRE\JGRAnalyzer\AppGen"
    CONTEXT_CLS = r"frameworks\base\core\java\android\content\Context.java"
    SERVICE_REGISTER_CLS = r"frameworks\base\core\java\android\app\SystemServiceRegistry.java"
    ADB_BIN_DIR = r"D:\dev\AndroidSDK\platform-tools"
    DYNAMIC_RUN = 60

    @staticmethod
    def local_path(res):
        import os
        return os.path.join(Conf.DEPLOY_DIR, res)

    @staticmethod
    def aosp_file(rel):
        import os
        return os.path.join(Conf.DEFAULT_AOSP_PATH, rel)


CONFIG = Conf()
