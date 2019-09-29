# -*- coding: utf-8 -*-
import os
import logging
import json
from clang import cindex
from clang.cindex import CursorKind

__author__ = 'fripSide'


def init_logging():
	logging.basicConfig(level=logging.DEBUG)


def setup_lib_path():
	cur_path = os.getcwd()
	clang_lib_path = os.path.join(cur_path, "libclang")
	print(clang_lib_path)
	# clang_lib_path = r"D:\Dev\LLVM\bin"
	cindex.Config.set_library_path(clang_lib_path)


def init():
	init_logging()
	setup_lib_path()


def debug_parse_file(src):
	index = cindex.Index.create()
	tu = index.parse(src)
	root = tu.cursor
	debug_walk_node(root)


def debug_walk_node(node, level=0):
	debug_dump_node(node, level)
	for child in node.get_children():
		debug_walk_node(child, level + 1)


def debug_dump_node(node, level=0):
	logging.debug('%s %-35s %-20s %-10s [%-6s:%s - %-6s:%s] %s %s ' % (' ' * level,
																	   node.kind, node.spelling, node.type.spelling,
																	   node.extent.start.line, node.extent.start.column,
																	   node.extent.end.line, node.extent.end.column,
																	   node.location.file, node.mangled_name))


def fully_qualified(c):
	if c is None:
		return ''
	elif c.kind == CursorKind.TRANSLATION_UNIT:
		return ''
	else:
		res = fully_qualified(c.semantic_parent)
		if res != '':
			return res + '::' + c.displayname
	return c.displayname


def fully_qualified_spelling(c):
	if c is None:
		return ''
	elif c.kind == CursorKind.TRANSLATION_UNIT:
		return ''
	else:
		res = fully_qualified(c.semantic_parent)
		if res != '':
			return res + '::' + c.spelling
	return c.spelling


def extract_method_defines_in_file(src):
	index = cindex.Index.create()
	tu = index.parse(src)
	root = tu.cursor
	nodes = []

	def extract_method_in_node(node):
		cur_nodes = []
		for n in node.get_children():
			if n.kind == CursorKind.FUNCTION_DECL:
				cur_nodes.append(n)
			cur_nodes += extract_method_in_node(n)
		return cur_nodes

	# for node in root.get_children():
	# 	if node.kind == CursorKind.FUNCTION_DECL:
	# 		nodes.append(node)
	# 	pass
	return extract_method_in_node(root)


def read_lines(file_path, start, end):
	try:
		with open(file_path, "r") as fp:
			data = list(fp.readlines())
			return data[start:end]
	except Exception as ex:
		print("Failed to read file: {} {}".format(file_path, ex.args))


def save_results():
	pass


def load_json(path):
	with open(path, "r") as fp:
		return json.load(fp)


def write_json(data, path):
	with open(path, "w") as fp:
		return json.dump(data, fp)


def check_and_mkdir(path):
	if not os.path.exists(path):
		os.makedirs(path)


def check_and_remove(path):
	if os.path.exists(path):
		os.remove(path)
