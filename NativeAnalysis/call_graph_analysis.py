# -*- coding: utf-8 -*-
import os
import utils
import logging
import traceback
from conf import config
from clang import cindex
from clang.cindex import CursorKind
from compile_info import compile_commands
from jni_map import parse_jni_in_framework

__author__ = 'fripSide'

TEST_FILE = "frameworks\\base\\core\\jni\\android_util_Binder.cpp"

JGR_METHOD_SIGNATURE = "_JNIEnv::NewGlobalRef"


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
		self.is_exit_point = False
		self.call_expr = called
		self.node = called.referenced or called

	def is_class_constructor(self):
		pass

	@property
	def simple_name(self):
		if self.is_exit_point:
			return get_method_simple_name(self.node) + " -> " + JGR_METHOD_SIGNATURE
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
	JGR_TAG = "env->NewGlobalRef"

	def __init__(self):
		self.method_defines = {}
		self.method_calls = {}

	def add_file(self, file_path, headers=None):
		try:
			index = cindex.Index.create()
			args = compile_commands.get_commands_for_file(file_path)
			if headers:
				args += ["-I{}".format(t) for t in headers]
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
		focus_tag = "android_os_Binder_init"
		for n in node.get_children():
			# if n.spelling != focus_tag:
			# 	continue
			entry_method = n
			if n.kind == CursorKind.CLASS_DECL:
				self.__extract_method_calls_in_class(n)

			if n.kind == CursorKind.FUNCTION_DECL:
				self.__extract_child_call_node(entry_method, n, self.TYPE_METHOD_CALL)
			# utils.debug_walk_node(n)

			if n.kind == CursorKind.NAMESPACE:
				self.__get_entry_method_node(n)

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
		self.__find_new_global_ref_by_hardcode(entry_method, node)

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
		self.__add_to_call_set(method, edge)

	def __find_new_global_ref_by_hardcode(self, entry_method, node):
		"""
		If the node is not parser correctly, try to read the source code directly
		"""
		if node.kind != CursorKind.FUNCTION_DECL:
			return
		file_path = str(node.location.file)
		first, last = self.__get_node_range(node)
		if first < last:
			lines = utils.read_lines(file_path, first, last)
			for li in lines:
				if self.JGR_TAG in li:
					name = get_method_simple_name(entry_method)
					edge = Edge(node)
					edge.is_exit_point = True
					self.__add_to_call_set(name, edge)

	def __add_to_call_set(self, name, edge):
		if name not in self.method_calls:
			self.method_calls[name] = set()
		self.method_calls[name].add(edge)

	def __get_node_range(self, node):
		first, last = int(node.extent.start.line), int(node.extent.end.line)
		for n in node.get_children():
			child_last = int(n.extent.end.line)
			last = max(last, child_last)
		return first, last


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
			if simple_name in self.exit_points or call.is_exit_point:
				logging.info("Find exit point: {}".format(simple_name))
				return cur_path

	def __setup_exit_points(self, exit_points):
		for et in exit_points:
			self.exit_points.append(et)

	def __save_results(self):
		save_path = config.local_path("data/results.txt")
		items = []
		for k, item in self.call_chains.items():
			chains = " -> ".join([e.simple_name for e in item])
			items.append("{}->{}".format(k, chains))
			logging.info("{}->{}".format(k, chains))
		with open(save_path, "w") as fp:
			fp.write("\n".join(items))


def process_jni_dir(work_dir):
	call_graph = CallGraph()

	all_files = os.listdir(work_dir)
	cpp_files = [os.path.join(work_dir, f) for f in filter(lambda v: v.endswith("cpp"), all_files)]
	# for cpp in cpp_files:
	# 	call_graph.add_file(cpp, HEADERS)

	# jni_map = parse_jni_in_framework()
	# jni_methods = jni_map.keys()
	jni_methods = []

	entry_points = set()
	for cpp in cpp_files:
		nodes = utils.extract_method_defines_in_file(cpp)
		for n in nodes:
			spelling = n.spelling
			if spelling.startswith("android_") or spelling in jni_methods:
				entry_points.add(spelling)
	call_graph.add_file(config.aosp_file(TEST_FILE))
	analysis = CallGraphAnalysis(call_graph)
	analysis.run_analysis(entry_points)


if __name__ == "__main__":
	work_dir = "F:\\Android_9.0\\aosp\\frameworks\\base\\core\\jni\\"
	utils.init()
	process_jni_dir(work_dir)
