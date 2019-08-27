#include "LibClangIndexer.h"
#include <filesystem>
#include <fstream>
#include "clang-c/Index.h"

namespace fs = std::filesystem;

namespace {

	bool StringEndsWith(const std::string& str, const std::string& postfix) {
		int len1 = str.length();
		int len2 = postfix.length();
		int offset = len1 - len2;
		if (offset < 0) return false;
		return str.rfind(postfix, offset) != std::string::npos;
	}
}


void JniFileWalker::WalkDir(fs::path dir) {
	//fs::recursive_directory_iterator itr(dir);
	try {
		for (auto& pt : fs::directory_iterator(dir)) {
			std::string file_name = pt.path().filename().generic_string();
			//printf("%s\n", file_name.data());
			if (file_name[0] == '.') continue;
			if (fs::is_directory(pt)) {
				WalkDir(pt);
			} else if (fs::is_regular_file(pt)) {
				std::string s = pt.path().generic_string();
				if (IsJniFile(s.data())) {
					printf("Find Jni file: %s\n", s.data());
					jni_files_.insert(s);
				}
			}
			
		}
	}
	catch (std::exception ex) {
		printf("%s error: %s\n", dir.generic_string().data(), ex.what());
	}
	
}

bool JniFileWalker::LoadJniFileList(const char* fi) {
	std::ifstream is(fi);
	if (!is.is_open()) return false;
	std::string line;
	while (getline(is, line)) {
		jni_files_.insert(line);
	}
	printf("Total Jni files: %zu\n", jni_files_.size());
	return true;
}

bool JniFileWalker::SaveJniFileList(const char* fi) {
	FILE* fp;
	fopen_s(&fp, fi, "wb");
	if (fp == nullptr) return false;
	for (auto it : jni_files_) {
		it += "\n";
		fwrite(it.data(), 1, it.length(), fp);
	}
	fclose(fp);
	printf("Total Jni files: %zu\n", jni_files_.size());
	return true;
}

bool JniFileWalker::IsJniFile(const char* fi) {
	std::string name(fi);
	bool is_cpp_file = false;
	std::string post_fixs[] = {".h", ".cc", ".c", "cpp"};
	for (const auto& p : post_fixs) {
		if (StringEndsWith(name, p)) {
			is_cpp_file = true;
			break;
		}
	}
	if (!is_cpp_file) return false;

	std::ifstream is(fi);
	if (!is.is_open()) return false;
	std::string line;
	while (getline(is, line)) {
		if (line.find(kJniDeclare, 0) != std::string::npos) {
			return true;
		}
	}
	return false;
}

bool ClangIndexer::ParseFile(const char* fi) {
	CXTranslationUnit tu;
	CXIndex index = clang_createIndex(1, 1);
	const char* filePath = "F:\\Android_9.0\\aosp\\frameworks\\base\\core\\jni\\android\\graphics\\Matrix.cpp";
	tu = clang_parseTranslationUnit(index, filePath, NULL, 0, nullptr, 0, CXTranslationUnit_Incomplete);
	if (!tu) {
		printf("Couldn't create translation unit");
		return false;
	}
	CXCursor cursor = clang_getTranslationUnitCursor(tu);
}