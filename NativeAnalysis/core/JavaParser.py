# coding: utf-8
# created at 2019/2/23
import javalang
import javalang.tree
import javalang.ast
import model.ServicesAPI as sa

__author__ = "fripSide"

TEST_FILE = r"F:\Android_9.0\aosp\frameworks\base\services\core\java\com\android\server\accounts\AccountManagerService.java"


class JavaClassParser:
    """
    遍历一个类，只进行简单的，处理基本的变量方法申明
    node: 'documentation', 'modifiers', 'annotations', 'type', 'declarators'
    """

    def __init__(self, cls_path, name=None):
        self.path = cls_path
        self.tree = self.__parse_cls()
        self.pkg = self.tree.package.name
        self.cls_name = name
        self.imports = None
        self.call_back_map = {}

    def __parse_cls(self):
        with open(self.path, "rb") as fp:
            data = fp.read()
        return javalang.parse.parse(data)

    def filter_members(self, modifiers, tag=None):
        """获取java member"""
        fields = []
        for path, node in self.tree.filter(javalang.tree.FieldDeclaration):
            if all(m in node.modifiers for m in modifiers):
                value = None
                if 'value' in node.declarators[0].initializer.attrs:
                    value = node.declarators[0].initializer.value
                if tag:
                    if tag in node.declarators[0].name:
                        fields.append((node.declarators[0].name, value))
                else:
                    fields.append((node.declarators[0].name, value))
        return fields

    def get_imports(self):
        if self.imports:
            return self.imports
        lst = []
        for path, node in self.tree.filter(javalang.tree.Import):
            lst.append(node.path)
        self.imports = lst
        return lst

    def get_fields(self):
        lst = []
        for path, node in self.tree.filter(javalang.tree.StatementExpression):
            print(node)
        return lst

    def get_root_cls(self):
        for child in self.tree.children:
            for node in child:
                if isinstance(node, (javalang.tree.ClassDeclaration, javalang.tree.InterfaceDeclaration)):
                    # print(type(child), type(node), "#")
                    if self.cls_name:
                        # print(node.name, self.cls_name)
                        assert (node.name == self.cls_name)
                    return node

    def __parse_method_param(self, node):
        """get params list for method declare"""
        params = []
        if isinstance(node, javalang.tree.MethodDeclaration):
            for ty in node.parameters:
                name = ty.type.name
                params.append(name)
        return "_".join(params)

    def __extract_class_in_path(self, path):
        cls = []
        for pt in path:
            if isinstance(pt, (javalang.tree.ClassDeclaration, javalang.tree.InterfaceDeclaration)):
                cls.append(pt.name)
        return ".".join(cls)

    def extract_method_declare(self, path, node):
        """extract one java method declare"""
        params = self.__parse_method_param(node)
        signature = node.name
        if params:
            signature = f"{node.name}_{params}"
        cls_path = self.__extract_class_in_path(path)
        mtd_declare = f"{self.pkg}#{cls_path}.{signature}"
        return mtd_declare

    def get_method_declares(self):
        """get all method declares in a java file"""
        mtd_map = {}
        for path, node in self.tree.filter(javalang.tree.MethodDeclaration):
            mtd_declare = self.extract_method_declare(path, node)
            mtd_map[mtd_declare] = self.path
        return mtd_map

    def _get_nodes_in_children(self, node, pattern):
        nodes = []
        for child in node.children:
            if not child:
                continue
            for node in child:
                # print(type(node), type(child))
                if isinstance(node, pattern):
                    nodes.append(node)
        return nodes

    def get_top_level_nodes(self, pattern):
        """
        get first level nodes in root class
        :param pattern: node type
        """
        cls_declare = self.get_root_cls()
        return self._get_nodes_in_children(cls_declare, pattern)

    def _get_require_permission(self, node):
        for anno in node.annotations:
            if anno.name == "RequiresPermission":
                if isinstance(anno.element, list):
                    ele = anno.element[0].value
                else:
                    ele = anno.element
                return f"{ele.qualifier}.{ele.member}"

    def _print_node(self, node, *args):
        node.__dict__["documentation"] = None
        print(node, *args)

    def get_public_method_key_list(self):
        root_cls = self.get_root_cls()
        api_list = self.get_public_api_list()
        ret = []
        for node in api_list:
            ret.append(self.extract_method_declare([root_cls], node))
        return ret

    def get_available_api(self):
        """
        UnsupportedAppUsage annotation may not work and these api can be called in some lower version of emulator or devices
        :return:
        """
        open_nodes = self.get_public_api_list()
        return self.__parse_api_detail(open_nodes)

    def get_public_api_list(self):
        open_nodes = []
        methods = self.get_top_level_nodes(javalang.tree.MethodDeclaration)
        for node in methods:
            if "public" in node.modifiers:
                # if len(node.annotations) > 0 and node.annotations[0].name == "UnsupportedAppUsage":
                #     continue
                # exclude hide api
                if node.documentation and "@hide" in node.documentation:
                    continue
                # self.__print_node(node, "\n", node.name)
                # ps = self._get_require_permission(node)
                # if node.name == "stopWatchingMode":
                open_nodes.append(node)
        return open_nodes

    def __parse_api_detail(self, node_list):
        sr = sa.ServiceManagerDesc()
        sr.atr_service_name = self.cls_name
        sr.atr_api_list = []
        for node in node_list:
            # print("start===", node.name)
            api = sa.ApiDesc()
            # api.atr_packages.append(self.pkg)
            api.atr_packages.append(f"{self.pkg}.{self.cls_name}")
            api.atr_method_ref = self.__parse_method_node(api, node)
            # print("finish===", node.name)
            # print(mtd.dump())
            sr.atr_api_list.append(api)
        return sr

    def __parse_arg_type(self, api, node):
        """args type for params and return type"""
        pass

    def __parse_method_node(self, api, node):
        mtd = sa.MethodDesc()
        mtd.atr_method_name = node.name
        for param in node.parameters:
            type_name = param.type.name
            pm = sa.ParamTypeDesc()
            pm.atr_param_type = type_name
            pm.atr_param_name = param.name
            if isinstance(param.type, javalang.tree.ReferenceType) and type_name != "String":
                pm.atr_param_ref = self.__get_cls_ref(api, type_name)
            elif type_name == "List":
                print("List==============", param)
                pass
            # print(param.type, pm)
            mtd.atr_params.append(pm)
        # ret type
        if node.return_type:
            ret_type = node.return_type.name
            print("return type==========", ret_type)
            ret = sa.ParamTypeDesc()
            # print(node.return_type)
            ret.atr_param_type = node.return_type.name
            mtd.atr_return_type = ret
        return mtd

    def __parse_callback_node(self, api, node):
        cb = sa.CallBackDesc()
        methods = self._get_nodes_in_children(node, javalang.tree.MethodDeclaration)
        for mtd in methods:
            m = self.__parse_method_node(api, mtd)
            cb.atr_methods.append(m)
            # print("__parse_callback_node", mtd.name, m)
        return cb

    def __get_cls_ref(self, api, type_name):
        if type_name in self.call_back_map:
            # print("====", self.call_back_map[type_name])
            return self.call_back_map[type_name]
        cb = None
        inner_cls = self.get_top_level_nodes((javalang.tree.InterfaceDeclaration, javalang.tree.ClassDeclaration))
        for cls in inner_cls:
            if isinstance(cls, javalang.tree.InterfaceDeclaration) or \
                    (isinstance(cls, javalang.tree.ClassDeclaration) and "abstract" in cls.modifiers):
                if cls.name == type_name:
                    cb = self.__parse_callback_node(api, cls)
                    cb.atr_cls_name = f"{self.cls_name}.{type_name}"
                    # print("find", cls.name)
                    break
        if not cb:
            # cb = sa.CallBackDesc()
            pkg = None
            for imp in self.get_imports():
                if imp.endswith(type_name):
                    pkg = imp
                    break
            if not pkg: # is in current package
                pkg = f"{self.pkg}.{type_name}"
            api.atr_packages.append(pkg)
            # cb.atr_cls_name = type_name
        self.call_back_map[type_name] = cb
        return cb


class JavaCallgraphBuilder:
    EXCLUDING_LIST = ["Log_v"]

    def __init__(self, file):
        self.parser = JavaClassParser(file)

    def get_method_calling_by_method_key(self, mtd_key):
        tree = self.parser.tree
        current_node = None
        print("analysis", mtd_key)
        for path, node in tree.filter(javalang.tree.MethodDeclaration):
            node_key = self.parser.extract_method_declare(path, node)
            if node_key == mtd_key:
                current_node = node
                break
        if current_node:
            for path, node in current_node:
                if isinstance(node, (javalang.tree.MethodInvocation, javalang.tree.SuperMethodInvocation)):
                    print(path[-1])
                    qulifier = node.qualifier
                    member = node.member
                    print("calling", qulifier, member)

    def extract_class_method(self, mtd_key):
        pass

    def __get_calling_in_method(self, mtd_node):
        """
        1. get Variable Declare and build local var map
        2. get var type and func call
        """
        pass


class StubClassParser(JavaClassParser):
    """find out IInterfaceStub"""
    AIDL_MAP = {}

    def __init__(self, path):
        super(StubClassParser, self).__init__(path)

    def _get_base_cls(self, node):
        ret = []
        if isinstance(node, javalang.tree.ReferenceType):
            ret.append(node.name)
            if isinstance(node.sub_type, javalang.tree.ReferenceType):
                ret.append(node.sub_type.name)
        return ret

    def _build_aild_file_map(self):
        from core.JniAPIExtractor import SourceFileScanner
        aidl_files = SourceFileScanner().get_files("aidl")
        for af in aidl_files:
            name = af.split("\\").pop()
            StubClassParser.AIDL_MAP[name] = af

    def _get_aidl_public_api(self, aidl_name):
        if not StubClassParser.AIDL_MAP:
            self._build_aild_file_map()
        if aidl_name in StubClassParser.AIDL_MAP:
            path = StubClassParser.AIDL_MAP[aidl_name]
            cnt = 0
            with open(path, "r") as fp:
                for li in fp:
                    if "(" in li and ")" in li:
                        cnt += 1
            return cnt
        return 0

    def _is_stub_class(self):
        for child in self.tree.children:
            for node in child:
                if isinstance(node, (javalang.tree.ClassDeclaration, javalang.tree.InterfaceDeclaration)):
                    base_cls = self._get_base_cls(node.extends)
                    # print(base_cls)
                    if len(base_cls) == 2 and base_cls[1] == "Stub":
                        return True, node.name, base_cls[0]
        return False, None, None

    def get_public_apis_in_stub(self):
        is_sub, cur, base = self._is_stub_class()
        if is_sub:
            # aps_list = self.get_public_api_list()
            aidl_name = f"{base}.aidl"
            num = self._get_aidl_public_api(aidl_name)
            print(aidl_name, num)
            return num
        return 0


def get_service_registers(cls_path):
    java = JavaClassParser(cls_path)
    imps = java.get_imports()
    service_manager_mp = {}
    for path, node in java.tree.filter(javalang.tree.StatementExpression):
        expr = node.expression
        if isinstance(expr, javalang.tree.MethodInvocation):
            if len(expr.arguments) >= 2:
                ctx_name, cls_name = None, None
                for arg in expr.arguments:
                    if isinstance(arg, javalang.tree.MemberReference):
                        ctx_name = f"{arg.qualifier}.{arg.member}"
                    elif isinstance(arg, javalang.tree.ClassReference):
                        cls_name = arg.type.name
                if ctx_name.startswith("Context"):
                    service_manager_mp[ctx_name] = dict(pkg="", cls=cls_name)
            else:
                pass
    for k, v in service_manager_mp.items():
        pkg = list(filter(lambda t: v["cls"] in t, imps))
        if len(pkg) == 0:
            pkg = [java.pkg]
        v["pkg"] = pkg[0]
    return service_manager_mp


def test_java_lang():
    with open(TEST_FILE, "r") as fp:
        data = fp.read()
    tree = javalang.parse.parse(data)
    for path, node in tree.filter(javalang.tree.FieldDeclaration):
        # print(node.attrs, node.modifiers)
        if 'value' in node.declarators[0].initializer.attrs:
            print(node.declarators[0].name, node.declarators[0].initializer.value)
        else:
            print(node.declarators[0].name, node)


def test_java_expr():
    data = """
    class Test {
         static {
            Register(AAA.class);
        }
        
        public void test() {
            T t = new T();
            t.run_test();
        }
    }
    """
    tokens = javalang.tokenizer.tokenize(data)
    parser = javalang.parser.Parser(tokens)
    tree = parser.parse()
    current_node = None
    for path, node in tree.filter(javalang.tree.MethodDeclaration):
        if node.name == "test":
            current_node = node
            break
    # print(current_node)
    for path, node in current_node:
        print(node)


def test_method_extract():
    parser = JavaClassParser(TEST_FILE)
    parser.get_method_declares()


def test_parser_aidl():
    file = r"F:\Android_9.0\aosp\frameworks\opt\net\wifi\service\java\com\android\server\wifi\WifiServiceImpl.java"
    jp = StubClassParser(file)
    jp.get_public_apis_in_stub()


if __name__ == "__main__":
    # test_java_lang()
    # test_java_expr()
    # test_method_extract()
    test_parser_aidl()
