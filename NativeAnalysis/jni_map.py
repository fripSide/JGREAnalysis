# encoding: utf-8


import os
from conf import config
import utils
import logging

osp = os.path


class SourceFileScanner:
	FILE_LIST_PATH = config.local_path("data/file_list.txt")
	TARGET_EXT = [".java", ".h", ".cc", ".c", ".cpp", ".aidl"]
	CPP_EXT = [".h", ".cc", ".cpp", ".c"]
	SCAN_DIRS = [""]

	def __init__(self, aosp_path=config.DEFAULT_AOSP_PATH):
		self.file_list = []
		self.file_ext_mp = {}
		self.__scan_files(aosp_path)
		self.__process_files()

	def __scan_files(self, base_dir):
		# if osp.exists(self.FILE_LIST_PATH):
		# 	logging.info("Load file list from cache: %s", self.FILE_LIST_PATH)
		# 	with open(self.FILE_LIST_PATH) as fp:
		# 		for line in fp:
		# 			self.file_list.append(line.rstrip())
		# 	return
		for ch in self.SCAN_DIRS:
			dir_path = osp.join(base_dir, ch)
			if not osp.exists(dir_path):
				continue
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
		logging.info("Total Files: %d", num)
		java_num = len(list(filter(lambda f: f.endswith(".java"), self.file_list)))
		cpp_num = len(list(filter(lambda f: f.endswith(".c") or f.endswith("h")
											or f.endswith("cc") or f.endswith("cpp"), self.file_list)))
		aidl_num = len(list(filter(lambda f: f.endswith(".aidl"), self.file_list)))
		logging.info("java files: %d", java_num)
		logging.info("cpp files: %d", cpp_num)
		logging.info("aidl files: %d", aidl_num)


class JNIBridgeMap:

	def __init__(self):
		self.java_to_cpp = {}
		self.cpp_to_java = {}
		self.jni_bridge_map = {}

	def __parser_jni_cpp_register_func(self, file):
		from cxx_parser import CxxJniParser
		parser = CxxJniParser(file)
		results = parser.get_results()
		self.jni_bridge_map.update(results)

	def process_cpp_files(self, files):
		for fi in files:
			self.__parser_jni_cpp_register_func(fi)
		logging.info("Find Jni Methods: %d", len(self.jni_bridge_map))
		utils.write_json(self.jni_bridge_map, config.local_path("data/jni.json"))


def parse_jni_in_framework():
	jni_bridge = JNIBridgeMap()
	src = SourceFileScanner(config.framework_core_jni_path())
	files = src.get_cpp_files()
	jni_bridge.process_cpp_files(files)
	return jni_bridge.jni_bridge_map


if __name__ == "__main__":
	parse_jni_in_framework()
