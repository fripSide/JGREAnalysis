#pragma once
#include <string>
#include <vector>
#include <set>
#include <filesystem>
namespace fs = std::filesystem;

/*
Use clangindexer to generate JNI bridge map.

*/

class JniFileWalker {
public:
	void WalkDir(fs::path pt);
	bool LoadJniFileList(const char* fi);
	bool SaveJniFileList(const char* fi);

private:
	std::set<std::string> jni_files_;
	const char* kJniDeclare = "JNINativeMethod";

	bool IsJniFile(const char* fi);
};

class ClangIndexer {
public:
	bool ParseFile(const char* fi);
};