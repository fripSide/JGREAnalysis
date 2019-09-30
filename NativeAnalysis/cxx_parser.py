# encoding: utf-8

import os
import clang.cindex
import codecs
import logging
from clang.cindex import *
from compile_info import compile_commands


def dump_node(cursor):
	print(cursor.location.line or 0, cursor.kind, cursor.spelling, cursor.displayname)


class CxxJniParser:
	"""parse single files
	There is some bugs in python-clang to walk node of files.
	The JNINativeMethod declare can not be get via node_walker. (The node is missing.)
	So we use token analysis to find out JNI register.
	"""

	def __init__(self, file):
		self.jni_map = {}
		self.src = file
		if self.__fast_check():
			self.__process()

	def get_results(self):
		return self.jni_map

	def __parse_one(self, src):
		index = clang.cindex.Index.create()
		tu = None
		try:
			args = compile_commands.get_commands_for_file(src)
			tu = index.parse(src, args=args)
		except Exception as ex:
			import traceback
			traceback.print_exc()
		if not tu:
			print("Parsing problem: ", src)
		# exit(-1)
		return tu

	def __fast_check(self):
		file = self.src
		if not os.path.exists(file):  # is file links
			return False
		# print(file, os.path.isfile(file))
		try:
			with codecs.open(file, "r", encoding='utf-8') as fp:
				# print(fp.readlines())
				for li in fp:
					# print(li)
					if "JNINativeMethod" in li:
						return True
		except:
			return False

	def __process(self):
		file = self.src
		tu = self.__parse_one(file)
		if not tu:
			return
		# for f in tu.get_includes():
		#     print(f.depth, f.include.name)
		jni_tokens, jni_block = [], []
		extract_jni = False
		for tk in tu.get_tokens(extent=tu.cursor.extent):
			if tk.spelling == "JNINativeMethod":
				extract_jni = True
				jni_block = []
				logging.debug("Find JNINativeMethod: %s %s %s", tk.kind, tk.spelling, tk.location.line)

			if extract_jni:
				if tk.spelling == ";":
					extract_jni = False
					jni_tokens.append(jni_block)
					# print("Exit define", tk.location.line)
				else:
					# if tk.kind == TokenKind.IDENTIFIER:
					jni_block.append(tk)
				# print(tk.kind, tk.spelling, tk.location.line)
		for tokens in jni_tokens:
			self.__extract_jni_block(tokens)

	# self.__jni_define_walker(tu.cursor)
	# dump_ast(tu, file)

	def __extract_jni_block(self, tokens):
		"""
		extract jni method name and package name
		"""
		items = []
		for tk in tokens:
			if tk.kind == TokenKind.IDENTIFIER or tk.kind == TokenKind.LITERAL:
				if tk.spelling != "JNINativeMethod":
					items.append(tk)
		items.pop(0)
		t = int(len(items) / 3)
		# print(t * 3, len(items))
		if t * 3 != len(items):
			logging.debug("Is not jni register: %s", self.src)
			return
		for it in range(0, t):
			key = 3 * it
			func_name, param, cpp_name = items[key: key + 3]
			pkg = self.__get_package_name(cpp_name)
			if pkg:
				print(func_name.spelling, param.spelling, cpp_name.spelling)
			else:
				pass
				# print("ignore", func_name.spelling, param.spelling, cpp_name.spelling)
			self.jni_map[cpp_name.spelling] = dict(cpp=self.src, java=func_name.spelling)

	def __get_package_name(self, cpp_name):
		"""ignore the """
		# print(cpp_name, self.src)
		pkg = "android"
		if cpp_name.kind == TokenKind.IDENTIFIER and pkg in cpp_name.spelling:
			return True
		return None

	def __jni_define_walker(self, node, lev=0):
		"""
		notes: walk through all the nodes of ast cannot get the JNINativeMethod
		"""
		if not node.location.file or str(node.location.file) == self.src:
			if node.kind == CursorKind.FUNCTION_DECL:
				dump_node(node)
			if node.spelling == "JNINativeMethod":
				print("find the JNINativeMethod")
				dump_node(node)
				exit(-1)

			for child in node.get_children():
				self.__jni_define_walker(child, lev + 1)
		else:
			# print("stop at", node.kind, node.location.file)
			pass
