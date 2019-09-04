# -*- coding: utf-8 -*-
import os
import utils
import logging
import traceback
from clang import cindex
from clang.cindex import CursorKind

__author__ = 'fripSide'

TEST_FILE = "D:\\ubuntu\\Android-7.1.1_r10\\frameworks\\base\\core\\jni\\android_util_Binder.cpp"
JNI_HEADER = "D:\\ubuntu\\Android-7.1.1_r10\\prebuilts\\ndk\\current\\platforms\\android-14\\arch-arm\\usr\\include\\"

HEADERS = ["D:\\ubuntu\Android-7.1.1_r10\\frameworks\\native\\include",
		   # "D:\\ubuntu\Android-7.1.1_r10\\frameworks\\base\\core\\jni\\include",
		   "D:\\ubuntu\\Android-7.1.1_r10\\system\\core\\include",  # sp defined in RefBase.h
		   JNI_HEADER]


# TEST_FILE_Ubuntu = "/mnt/d/ubuntu/Android-7.1.1_r10/frameworks/base/core/jni/android_util_Binder.cpp"


def get_method_simple_name(node):
	"""
	For class ref, we don't use fully qualified name.
	constructor: class::class
	destructor: class::class~
	"""
	if node.kind == CursorKind.TYPE_REF or node.kind == CursorKind.CLASS_DECL:
		class_name = node.type.spelling
		return "{}::{}".format(class_name, class_name)
	if node.kind == CursorKind.DESTRUCTOR:
		ref = node.semantic_parent
		return "{}::{}~".format(ref.spelling, ref.spelling)
	if node.kind == CursorKind.CXX_METHOD or node.kind == CursorKind.CONSTRUCTOR:
		return utils.fully_qualified_spelling(node)
	return utils.fully_qualified_spelling(node)


class Edge:
	def __init__(self, called):
		self.call_expr = called
		self.node = called.referenced or called

	def is_class_constructor(self):
		pass

	@property
	def simple_name(self):
		if self.call_expr.kind == CursorKind.TYPE_REF:
			pass
		return get_method_simple_name(self.node)

	@property
	def full_name(self):
		c = self.node
		if c.kind == CursorKind.CLASS_DECL:
			return utils.fully_qualified(c.referenced)
		elif c.kind == CursorKind.CXX_METHOD:
			return utils.fully_qualified(c.referenced)
		return self.node.displayname

	def __hash__(self):
		return hash(self.full_name)

	def __eq__(self, other):
		return hash(self) == hash(other)

	def __repr__(self):
		return self.full_name


class Scope:
	SCOPE_FILE = 1
	SCOPE_CLASS = 2
	SCOPE_METHOD = 3

	def __init__(self):
		self.node = None
		self.scope_type = self.SCOPE_FILE
		self.child_scope = {}

	@property
	def unique_name(self):
		return utils.fully_qualified(self.node)


class CallGraph:
	TYPE_METHOD_CALL = [CursorKind.CALL_EXPR]

	def __init__(self):
		self.method_defines = {}
		self.method_calls = {}

	def add_file(self, file_path, headers):
		try:
			index = cindex.Index.create()
			args = ["-I{}".format(t) for t in headers]
			tu = index.parse(file_path, args=args)
			root = tu.cursor
			self.__get_entry_method_node(root)
		except Exception as ex:
			err = traceback.format_exc()
			logging.error(err)
		logging.info("Finish process: {}".format(file_path))

	def get_call_set(self, mtd):
		if mtd in self.method_calls:
			return self.method_calls[mtd]
		return []

	def get_define_node(self, mtd):
		pass

	def __get_entry_method_node(self, node):
		focus_tag = "javaObjectForIBinder"
		for n in node.get_children():
			# if n.spelling != focus_tag:
			# 	continue
			entry_method = n
			if n.kind == CursorKind.CLASS_DECL:
				self.__extract_method_calls_in_class(n)

			if n.kind == CursorKind.FUNCTION_DECL:
				self.__extract_child_call_node(entry_method, n, self.TYPE_METHOD_CALL)
			# utils.debug_walk_node(n)

			# utils.debug_dump_node(n)

	def __extract_method_calls_in_class(self, node):
		# class constructor methods
		for n in node.get_children():
			entry_method = n
			name = utils.fully_qualified(n)
			if n.kind == CursorKind.CONSTRUCTOR:
				logging.info("Enter Class Constructor: {}".format(name))
				self.__extract_child_call_node(entry_method, n, self.TYPE_METHOD_CALL)
			# return

			if n.kind == CursorKind.DESTRUCTOR:
				logging.info("Enter Class Destructor: {}".format(name))
				self.__extract_child_call_node(entry_method, n, self.TYPE_METHOD_CALL)

			if n.kind == CursorKind.CXX_METHOD:
				logging.info("Enter Class Method: {}".format(name))
				self.__extract_child_call_node(entry_method, n, self.TYPE_METHOD_CALL)

			# utils.debug_dump_node(n)

	def __extract_child_call_node(self, entry_method, node, node_types):
		for n in node.get_children():
			if n.kind in node_types:
				self.__add_edge(entry_method, n)

			if n.kind == CursorKind.VAR_DECL:
				# utils.debug_walk_node(n.referenced)
				self.__resolve_sp_class_init(entry_method, n)
			# utils.debug_walk_node(n)
			# utils.debug_dump_node(n)
			self.__extract_child_call_node(entry_method, n, self.TYPE_METHOD_CALL)

	def __resolve_sp_class_init(self, method_node, call_node):
		"""
		sp<XXXClass> sc = new XXXClass()
		"""
		is_sp_pointer = False
		for n in call_node.get_children():
			# CursorKind.TEMPLATE_REF  sp
			# CursorKind.TYPE_REF
			if n.kind == CursorKind.TEMPLATE_REF and n.spelling == "sp":
				is_sp_pointer = True
			if is_sp_pointer and n.kind == CursorKind.TYPE_REF:
				mtd = utils.fully_qualified(n)
				if mtd.startswith("class"):
					method_name = get_method_simple_name(n)
					# print(n.spelling, n.type, method_name, mtd)
					self.__add_edge(method_node, n)

	def __add_edge(self, method_node, call_node):
		# utils.debug_dump_node(method_node)
		method = get_method_simple_name(method_node)
		edge = Edge(call_node)
		# print(get_method_simple_name(call_node), get_method_simple_name(call_node.referenced), call_node.referenced.kind)
		# utils.debug_dump_node(call_node)
		logging.info("\t->Found Method Calling: {} called {} {}".format(method, edge, edge.simple_name))
		simple_name = edge.simple_name
		if simple_name not in self.method_defines:
			self.method_defines[simple_name] = set()
		self.method_defines[simple_name].add(Edge(call_node))
		if method not in self.method_calls:
			self.method_calls[method] = set()
		self.method_calls[method].add(edge)

	def __find_new_global_ref_in_hardcode(self, node):
		file_path = node.location.file
		first_line = node.extent.start.line
		last_line = node.extent.end.line


class CallGraphAnalysis:
	def __init__(self, call_graph):
		self.graph = call_graph # type: CallGraph
		self.exit_points = ["_JNIEnv::NewGlobalRef"]
		self.call_chains = {}

	def run_analysis(self, entry_points=None):
		# self.__setup_exit_points(exit_points)
		for ep in entry_points:
			self.__analysis_call_chain(ep)
		self.__save_results()

	def __analysis_call_chain(self, start):
		path = []
		find = self.__walk_call_graph(start, path)
		if find:
			self.call_chains[start] = find

	def __walk_call_graph(self, start, path):
		call_set = self.graph.get_call_set(start)
		# print("__walk_call_graph", start)
		for call in call_set:
			simple_name = call.simple_name
			cur_path = path.copy()
			cur_path.append(call)
			ret = self.__walk_call_graph(simple_name, cur_path)
			if ret:
				return ret
			if simple_name in self.exit_points:
				logging.info("Find exit point: {}".format(simple_name))
				return cur_path

	def __setup_exit_points(self, exit_points):
		for et in exit_points:
			self.exit_points.add(et)

	def __save_results(self):
		for k, item in self.call_chains.items():
			chains = " -> ".join([e.simple_name for e in item])
			logging.info("{}->{}".format(k, chains))


def parse_files():
	call_graph = CallGraph()
	work_dir = "D:\\ubuntu\\Android-7.1.1_r10\\frameworks\\base\\core\\jni\\"
	all_files = os.listdir(work_dir)
	cpp_files = [os.path.join(work_dir, f) for f in filter(lambda v: v.endswith("cpp"), all_files)]
	for cpp in cpp_files:
		call_graph.add_file(cpp, HEADERS)

	entry_points = set("android_os_BinderProxy_linkToDeath")
	for cpp in cpp_files:
		nodes = utils.extract_method_defines_in_file(cpp)
		for n in nodes:
			spelling = n.spelling
			if spelling.startswith("android_"):
				entry_points.add(spelling)
	# call_graph.add_file(TEST_FILE, HEADERS)
	analysis = CallGraphAnalysis(call_graph)
	analysis.run_analysis(entry_points)


if __name__ == "__main__":
	parse_files()
