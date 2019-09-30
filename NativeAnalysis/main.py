# -*- coding: utf-8 -*-
import os
import sys
import clang
import logging
import argparse
from conf import Conf, config
from clang import cindex

TEST_DIR = r"E:\Projects\CPP\leveldb"

"""
https://eli.thegreenplace.net/tag/llvm-clang
"""

TEST_FILE = "D:\\ubuntu\\Android-7.1.1_r10\\frameworks\\base\\core\\jni\\android_util_Binder.cpp"


def setup():
	cur_path = os.getcwd()
	clang_lib_path = os.path.join(cur_path, "libclang")
	print(clang_lib_path)
	clang_lib_path = r"D:\Dev\LLVM\bin"
	cindex.Config.set_library_path(clang_lib_path)


def get_annotations(node):
	return [c.displayname for c in node.get_children()
			if c.kind == clang.cindex.CursorKind.ANNOTATE_ATTR]


class Function(object):
	def __init__(self, cursor):
		self.name = cursor.spelling
		self.annotations = get_annotations(cursor)
		self.access = cursor.access_specifier
		#        template_pars = [c.extent for c in cursor.get_children() if c.kind == clang.cindex.CursorKind.TEMPLATE_TYPE_PARAMETER]
		parameter_dec = [c for c in cursor.get_children() if c.kind == clang.cindex.CursorKind.PARM_DECL]

		parameters = []
		for p in parameter_dec:
			children = []
			for c in p.get_children():
				#                print(c.spelling)
				children.append(c.spelling)
			parameters.append((p.spelling, p.type.spelling, children))

		self.parameters = parameters
		self.documentation = cursor.raw_comment


class Enum(object):
	def __init__(self, cursor):
		self.name = cursor.spelling
		self.constants = [c.spelling for c in cursor.get_children() if c.kind ==
						  clang.cindex.CursorKind.ENUM_CONSTANT_DECL]
		self.documentation = cursor.raw_comment


class Class(object):
	def __init__(self, cursor):
		self.name = cursor.spelling
		#        self.functions = []
		self.annotations = get_annotations(cursor)


def traverse(c, path, objects):
	if c.location.file and not c.location.file.name.endswith(path):
		return

	if c.spelling == "PARULA_COLOR_MAP":  # Fix to prevent python stack overflow from infinite recursion
		return

	#    print(c.kind, c.spelling)

	if c.kind == clang.cindex.CursorKind.TRANSLATION_UNIT or c.kind == clang.cindex.CursorKind.UNEXPOSED_DECL:
		# Ignore  other cursor kinds
		pass

	elif c.kind == clang.cindex.CursorKind.NAMESPACE:
		objects["namespaces"].append(c.spelling)
		# print("Namespace", c.spelling, c.get_children())
		pass

	elif c.kind == clang.cindex.CursorKind.FUNCTION_TEMPLATE:
		#        print("Function Template", c.spelling, c.raw_comment)
		objects["functions"].append(Function(c))
		return

	elif c.kind == clang.cindex.CursorKind.FUNCTION_DECL:
		# print("FUNCTION_DECL", c.spelling, c.raw_comment)
		objects["functions"].append(Function(c))
		return

	elif c.kind == clang.cindex.CursorKind.ENUM_DECL:
		# print("ENUM_DECL", c.spelling, c.raw_comment)
		objects["enums"].append(Enum(c))
		return

	elif c.kind == clang.cindex.CursorKind.CLASS_DECL:
		objects["classes"].append(Class(c))
		return

	elif c.kind == clang.cindex.CursorKind.CLASS_TEMPLATE:
		objects["classes"].append(Class(c))
		return

	elif c.kind == clang.cindex.CursorKind.STRUCT_DECL:
		objects["structs"].append(Class(c))
		return

	else:
		# print("Unknown", c.kind, c.spelling)
		pass

	for child_node in c.get_children():
		traverse(child_node, path, objects)


def parse(path, headers):
	index = cindex.Index.create()
	# Clang can't parse files with missing definitions, add static library definition or not?
	args = ['-x', 'c++', '-std=c++11', '-fparse-all-comments', '-DIGL_STATIC_LIBRARY']
	args += ["-i{}".format(h) for h in headers]

	tu = index.parse(path, args)
	objects = {"functions": [], "enums": [], "namespaces": [], "classes": [], "structs": []}
	# node = tu.cursor
	traverse(tu.cursor, path, objects)
	# print(tu)
	# clang_getCursorLocation
	conf = cindex.conf
	# conf.lib.clang_getCursorLocation()
	# clang.getCursorLocation()
	# cindex.getCursorLocation()
	for ty in objects:
		print(objects[ty])
	return objects


def get_files(work_dir):
	headers, srcs = [], []
	for root, dirs, files in os.walk(work_dir):
		for fi in files:
			cur_path = os.path.join(root, fi)
			if cur_path.endswith(".h"):
				headers.append(cur_path)
			elif cur_path.endswith(".cpp"):
				srcs.append(cur_path)
	return headers, srcs


def analyze_one_file():
	setup()
	headers, srcs = get_files(TEST_DIR)
	print(srcs[0])
	parse(srcs[0], headers)


def init_arg_parser():
	arg_parser = argparse.ArgumentParser()
	arg_parser.add_argument("-p", dest="source", help="set source path")
	arg_parser.add_argument("-c", dest="db", help="set compile_db_path")
	args = arg_parser.parse_args()
	if not args.source:
		arg_parser.print_help()
		sys.exit()
	Conf.DEFAULT_AOSP_PATH = args.source
	Conf.COMPILE_DB_FILE = args.db
	logging.info("AOSP Path: {} Compile DB:{}".format(args.source, args.db))


def main():
	import utils
	from call_graph_analysis import process_jni_dir
	utils.init()
	init_arg_parser()
	logging.info(config.DEFAULT_AOSP_PATH)
	logging.info(config.framework_core_jni_path())
	process_jni_dir(config.framework_core_jni_path())


if __name__ == "__main__":
	main()
