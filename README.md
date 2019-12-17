### Table of Contents 
- [Introduction](#introduction)
- [Build Project](#build-project)
- [Usage](#usage)


### Introduction 
This project is the source code for Java Global Reference Exhuasting vulnerabilities analysis tool. 
There are two modules, Java analyzer is implemented using Soot and Native analyzer is based on LibClang.

### Build Project 
Build and run Java analyzer:

```bash
git clone https://github.com/fripSide/JGREAnalysis.git
cd JavaAnalysis
mvn package
```

Documents for Python Analyzer [link](NativeAnalysis).

Automatical dynamic verification for vulnerabilities [link](https://github.com/fripSide/ServiceApiAutoTest).

### Usage 
Java Analyzer:
```bash
cd JavaAnalysis/bin
# in Windows
java -cp soot-dev.jar;JavaAnalyzer.jar com.alienware.snk.Main conf.json
# in Linux
java -cp soot-dev.jar:JavaAnalyzer.jar com.alienware.snk.Main conf.json
```
Python Analyzer:
``` bash
cd NativeAnalysis
python main.py -p path_to_aosp [-c path_to_compile_db]
```

Change different AOSP Jar:  
We have extracted many versions of Android framework.jar from Android emulator and real devices.  
https://github.com/fripSide/AndroidApiExtract
