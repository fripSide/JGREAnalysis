### Dependecy 
[versions](libclang/readme.txt) of python and libclang.

### Usage
python main.py -p path_to_aosp \[-c path_to_compile_db\]

### Compile Commands of clang  
Native code analysis need to use compile_command.json to get the compile commands for each file.

Here is the steps to generate compile_command.json:  
1.Use ninja to generate CMakeList.txt of framework projects  
2.Use CMake command to generate compile_command.json:  
```bash
cmake . -DCMAKE_EXPORT_COMPILE_COMMANDS=ON  
```

