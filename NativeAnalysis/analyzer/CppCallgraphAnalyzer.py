# coding: utf-8
# created at 2019/4/27
import json
from config import CONFIG
from core.CxxParser import parse_one, dump_node
from clang.cindex import *

__author__ = "fripSide"

"""
https://github.com/XrXr/libclang-callgraph/blob/master/callgraph.c

"""


class MethodLocation:

	def __init__(self, n, f, name):
		self.node = n
		self.file = f
		self.name = name


class CppCallgraph:

	def __init__(self):
		self.root = None
		self.entry_mtd = None
		self.fi = None
		self.mtd_loc = {}

	def add_entry_point(self, fi, mtd):
		self.fi = fi
		self.entry_mtd = mtd

	def load_cpp_file(self, fi):
		self.__get_all_call_in_file(fi)
		for key in self.mtd_loc.keys():
			print(key)

	def __get_root_for_file(self, fi):
		tu = parse_one(fi)
		return tu.cursor

	def _extract_func_in_files(self, fi):
		"""extract public func in file or in class"""
		# parse_methods_in_file(fi)
		tu = parse_one(fi)
		self.root = tu.cursor
		n = self.__get_entry_method_node(self.root)
		if n:
			print("find entry method", n.displayname)
			traverse(n, 0)

	def __add_method(self, mtd):
		name = mtd.name
		if name not in self.mtd_loc:
			self.mtd_loc[name] = set()
		self.mtd_loc[name].add(mtd)

	def __get_entry_method_node(self, node):
		for n in node.get_children():
			if n.spelling == self.entry_mtd:
				m = MethodLocation(n, self.fi, n.spelling)
				self.__add_method(m)
				return n
			m = self.__get_entry_method_node(n)
			if m:
				return m

	def __extract_method_call(self, node, fi):
		for n in node.get_children():
			if n.kind == CursorKind.FUNCTION_DECL:
				self.__add_method(MethodLocation(n, fi, n.spelling))
			self.__extract_method_call(n, fi)

	def __get_all_call_in_file(self, fi):
		root = self.__get_root_for_file(fi)
		self.__extract_method_call(root, fi)


def build_callgraph_test():
	pt = CONFIG.local_path("data/jni.json")
	with open(pt, "r") as fp:
		entry_points = json.load(fp)
	cg = CppCallgraph()
	for mtd in entry_points:
		mp = entry_points[mtd]
		cpp = mp["cpp"]
		print(mtd, cpp)
		cg.load_cpp_file(cpp)
		# cg.add_entry_point(cpp, mtd)
		# cg._extract_func_in_files(cpp)
		break


def traverse(node, level):
	print('%s %-35s %-20s %-10s [%-6s:%s - %-6s:%s] %s %s ' % (' ' * level,
	node.kind, node.spelling, node.type.spelling, node.extent.start.line, node.extent.start.column,
	node.extent.end.line, node.extent.end.column, node.location.file, node.mangled_name))
	if node.kind == CursorKind.CALL_EXPR:
		for arg in node.get_arguments():
			print("ARG=%s %s" % (arg.kind, arg.spelling))

	for child in node.get_children():
		traverse(child, level+1)


def walk_node(node, cur=None):
	# dump_node(node)
	for n in node.get_children():
		print(n.kind, n.displayname, n.spelling)
		if n.kind == CursorKind.FUNCTION_DECL:
			# return
			pass
		else:
			# print(n.kind, n.displayname)
			pass

		if n.kind == CursorKind.NAMESPACE:
			# print("Go deep into")
			# walk_node(n, None)
			pass
		# break
		walk_node(n, node)


def parse_methods_in_file(fi):
	tu = parse_one(fi)
	root = tu.cursor
	walk_node(root, None)
	return root


if __name__ == "__main__":
	build_callgraph_test()
