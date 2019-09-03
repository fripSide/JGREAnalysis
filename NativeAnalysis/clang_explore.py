# -*- coding: utf-8 -*-
import os
import sys
import clang.cindex
from clang.cindex import *

osp = os.path

__author__ = 'fripSide'

Config.set_library_path(r"D:\Dev\LLVM\bin")

"""
1.用linty测试，https://github.com/holtgrewe/linty/。 -i -s命令

https://github.com/libigl/libigl/blob/master/python/scripts/parser.py

"""

src_dir = r"E:\Projects\CPP\xy2_make\xy2_make"


def parse_files(includes, src):
	index = clang.cindex.Index.create()
	args = ['-I%s' % s for s in includes]
	# args += ['--std=c++11']
	print("Parse", src, args)
	tu = None
	try:
		tu = index.parse(src, args=args)
	except Exception as ex:
		import traceback
		traceback.print_exc()
	if not tu:
		print("Parsing problem: ", src)
	# exit(-1)
	return tu


def traverse(node, level):
	print('%s %-35s %-20s %-10s [%-6s:%s - %-6s:%s] %s %s ' % (' ' * level,
															   node.kind, node.spelling, node.type.spelling,
															   node.extent.start.line, node.extent.start.column,
															   node.extent.end.line, node.extent.end.column,
															   node.location.file, node.mangled_name))
	# if node.kind == CursorKind.CALL_EXPR:
	# 	for arg in node.get_arguments():
	# 		print("ARG=%s %s" % (arg.kind, arg.spelling))
	if level > 3:
		return
	for child in node.get_children():
		traverse(child, level + 1)


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


def main():
	files = os.listdir(src_dir)
	print(files)
	headers = [osp.join(src_dir, fi) for fi in filter(lambda v: v.endswith(".h"), files)]
	src = [osp.join(src_dir, fi) for fi in filter(lambda v: v.endswith(".cc"), files)]
	print(headers, src)
	tu = parse_files(headers, src[0])
	walk_node(tu.cursor)


if __name__ == "__main__":
	main()
