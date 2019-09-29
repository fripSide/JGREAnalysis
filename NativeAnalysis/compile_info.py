# encoding: utf-8

"""
Setup compile_db.json

"""
import os
from conf import config
import utils

default_includes = [
	"prebuilts\\ndk\\current\\platforms\\android-14\\arch-arm\\usr\\include\\",
	"frameworks\\native\\include",
	# "frameworks\\base\\core\\jni\\include",
	"system\\core\\include",  # sp defined in RefBase.h
]


class CompileInfo:

	def __init__(self, db):
		self.db_path = db or ""
		self.header_map = {}
		self.default_conf = self.__get_default_commands()
		self.__load_db()

	def __load_db(self):
		name = "compile_commands.json"
		file_path = os.path.join(self.db_path, name)
		if os.path.exists(file_path):
			self.db_path = file_path
		try:
			data = utils.load_json(self.db_path)
			for item in data:
				fi = item["file"]
				cmd = item["command"]
				self.header_map[fi] = cmd
		except:
			print("Failed to load compile_commands.json. Use default commands.")

	def __get_default_commands(self):
		aosp = config.DEFAULT_AOSP_PATH
		cmds = []
		for header in default_includes:
			cmds.append("-I{}".format(os.path.join(aosp, header)))
		return cmds

	def get_commands_for_file(self, fi):
		if fi not in self.header_map:
			return self.default_conf
		return self.header_map[fi]


compile_commands = CompileInfo(config.COMPILE_DB_FILE)


if __name__ == "__main__":
	db = CompileInfo(os.getcwd())
	print(db.header_map)
	print(db.default_conf)
