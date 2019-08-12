# coding: utf-8
# created at 2019/2/24
__author__ = "fripSide"

import os
import sys
from config import CONFIG
import clang.cindex
from clang.cindex import *
"""
Build callgraph for given h/c


https://github.com/bluhm/lockfish
"""

TEST_FILE = CONFIG.local_path("data/test/test.cpp")
TEST_JNI_FILE = CONFIG.aosp_file(r"frameworks\base\core\jni\android_util_Binder.cpp")


def find_typerefs(node, typename):
    """ Find all references to the type named 'typename'
    """
    if node.kind.is_reference():
        ref_node = node.get_definition()
        if ref_node.spelling == typename:
            print('Found %s [line=%s, col=%s]' % (
                typename, node.location.line, node.location.column))
    # Recurse for children of this node
    for c in node.get_children():
        find_typerefs(c, typename)


def test_clang():
    index = clang.cindex.Index.create()
    tu = index.parse(TEST_FILE)
    print ('Translation unit:', tu.spelling)
    find_typerefs(tu.cursor, "Person")


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


def parse_one(src):
    index = clang.cindex.Index.create()
    print("Parse", src)
    tu = None
    try:
        tu = index.parse(src)
    except Exception as ex:
        import traceback
        traceback.print_exc()
    if not tu:
        print("Parsing problem: ", src)
        # exit(-1)
    return tu


def dump_node(cursor):
    info = {"displayname": cursor.displayname, "kind": cursor.kind, "usr": cursor.get_usr(), "spelling": cursor.spelling,
            "location": cursor.location, "extent.start": cursor.extent.start, "extend.end": cursor.extent.end,
            "is_definition": cursor.is_definition()}
    print(info)


def walk_node(node, cur_fun=None):
    # print(fully_qualified(node))
    # print(node.get_children())
    if node.kind == CursorKind.FUNCTION_TEMPLATE:
        cur_fun = node

    if node.kind == CursorKind.CXX_METHOD or node.kind == CursorKind.FUNCTION_DECL:
        print("function define: ", node.displayname, node.spelling, node.location.file)
        cur_fun = node

    if node.kind == CursorKind.CALL_EXPR:
        if node.referenced:
            print(fully_qualified(cur_fun), "call", fully_qualified(node))
    for c in node.get_children():
        walk_node(c, cur_fun)


def get_all_cxx_files():
    from core.JniAPIExtractor import SourceFileScanner
    sc = SourceFileScanner()
    headers = sc.get_files("h")
    sources = []
    source_ext = [".cc", ".c", ".cpp"]
    for ext in source_ext:
        sources += sc.get_files(ext)
    print(sources)
    for src in sources:
        print(src)
        tu = parse_one(src)
        print(tu.cursor)
        walk_node(tu.cursor)
        break


def dump_ast(tu, file):
    """打印1层的tree"""
    import asciitree

    def node_children(node):
        return (c for c in node.get_children() if c.location and c.location.file and c.location.file.name == file)

    def print_node(node):
        text = node.spelling or node.displayname
        kind = str(node.kind)[str(node.kind).index('.') + 1:]
        return '{} {}'.format(kind, text)

    print(asciitree.draw_tree(tu.cursor, node_children, print_node))


class CxxCallGraphWalker:
    pass


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

    def __fast_check(self):
        file = self.src
        if not os.path.exists(file): # is file links
            return False
        # print(file, os.path.isfile(file))
        with open(file, "r", encoding='utf-8') as fp:
            for li in fp:
                # print(li)
                if "JNINativeMethod" in li:
                    return True

    def __process(self):
        file = self.src
        tu = parse_one(file)
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
                print("Find JNINativeMethod:", tk.kind, tk.spelling, tk.location.line)

            if extract_jni:
                if tk.spelling == ";":
                    extract_jni = False
                    jni_tokens.append(jni_block)
                    print("Exit define", tk.location.line)
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
        print(t * 3, len(items))
        if t * 3 != len(items):
            print("is not jni register", self.src, items)
            return
        for it in range(0, t):
            key = 3 * it
            func_name, param, cpp_name = items[key: key + 3]
            pkg = self.__get_package_name(cpp_name)
            if pkg:
                print(func_name.spelling, param.spelling, cpp_name.spelling)
                self.jni_map[cpp_name.spelling] = dict(cpp=self.src)
            else:
                print("ignore", func_name.spelling, param.spelling, cpp_name.spelling)

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


def file_node_walker(pt, node):
    for n in node:
        yield n


def get_methods_in_file(fi):
    tu = parse_one(fi)
    walk_node(tu.cursor)


def test_jni_parser():
    cur = "F:\\Android_9.0\\aosp\\frameworks\\base\\core\\jni\\android\\graphics\\Matrix.cpp"
    # cur = r"F:\Android_9.0\aosp\frameworks\base\core\jni\AndroidRuntime.cpp"
    cur = "F:\\Android_9.0\\aosp\\frameworks\\base\\core\\jni\\android\\graphics\\GraphicBuffer.cpp"
    TEST_JNI_FILE = cur
    # jni_file = CxxJniParser(TEST_JNI_FILE)
    get_methods_in_file(cur)


if __name__ == "__main__":
    test_jni_parser()
