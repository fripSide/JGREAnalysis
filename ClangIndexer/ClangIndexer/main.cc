#include <cstdio>
#include "clang-c/Index.h"
#include "LibClangIndexer.h"
#include "ClangIndexer.h"

/*
https://shaharmike.com/cpp/libclang/
*/

const char* art_dir = "F:\\Android_9.0\\aosp\\art\\runtime";
const char* framework_dir = "F:\\Android_9.0\\aosp\\frameworks";

int main(int argc, const char** argv) {
	CXTranslationUnit tu;
	CXIndex index = clang_createIndex(1, 1);
	const char* filePath = "F:\\Android_9.0\\aosp\\frameworks\\base\\core\\jni\\android\\graphics\\Matrix.cpp";
	tu = clang_parseTranslationUnit(index, filePath, NULL, 0, nullptr, 0, CXTranslationUnit_Incomplete);
	if (!tu) {
		printf("Couldn't create translation unit");
		return 1;
	}
	CXCursor cursor = clang_getTranslationUnitCursor(tu);
	clang_visitChildren(
		cursor,
		[](CXCursor c, CXCursor parent, CXClientData client_data)
		{

			CXString spelling = clang_getCursorSpelling(c);
			CXString s2 = clang_getCursorKindSpelling(clang_getCursorKind(c));
			printf("Cursor: %s %s\n", spelling.data, s2.data);
			return CXChildVisit_Recurse;
		},
		nullptr);

	clang_disposeTranslationUnit(tu);
	clang_disposeIndex(index);

	JniFileWalker walker;
	//walker.WalkDir(framework_dir);
	//walker.WalkDir(art_dir);
	//walker.SaveJniFileList("jni_files.txt");
	walker.LoadJniFileList("jni_files.txt");

	CommonOptionsParser op(argc, argv, ToolingSampleCategory);
	ClangTool Tool(op.getCompilations(), op.getSourcePathList());

	// ClangTool::run accepts a FrontendActionFactory, which is then used to
	// create new objects implementing the FrontendAction interface. Here we use
	// the helper newFrontendActionFactory to create a default factory that will
	// return a new MyFrontendAction object every time.
	// To further customize this, we could create our own factory class.
	Tool.run(newFrontendActionFactory<MyFrontendAction>().get());
	return 0;
}