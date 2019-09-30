# -*- coding: utf-8 -*-
__author__ = 'fripSide'
import os


class Conf:

	DEFAULT_AOSP_PATH = "D:\\ubuntu\\Android-7.1.1_r10\\"
	DEPLOY_DIR = os.getcwd()
	COMPILE_DB_FILE = None
	SAVE_PATH = "data"

	@staticmethod
	def local_path(res):
		return os.path.join(Conf.DEPLOY_DIR, res)

	@staticmethod
	def aosp_file(rel):
		return os.path.join(Conf.DEFAULT_AOSP_PATH, rel)

	@staticmethod
	def framework_core_jni_path():
		raw_path = os.path.join(Conf.DEFAULT_AOSP_PATH, "frameworks\\base\\core\\jni")
		path = raw_path.replace("\\", os.sep)
		path = path.replace("/", os.sep)
		return path


config = Conf()
