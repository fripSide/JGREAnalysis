# coding: utf-8
# created at 2019/3/13
__author__ = "fripSide"

import os
import shutil
import time
import subprocess
from threading import Thread
from core import utils
from config import CONFIG
from datetime import datetime

osp = os.path
# path for java service class
ADB_BIN = osp.join(CONFIG.ADB_BIN_DIR, "adb.exe")
cls_dir = osp.join(CONFIG.APPGEN_PATH, "class")
# log_path = osp.join(CONFIG.APPGEN_PATH, f"log{datetime.now().strftime('_%m%d%H%M')}.txt")
log_path = osp.join(CONFIG.APPGEN_PATH, f"log.txt")
proj_dir = osp.join(CONFIG.APPGEN_PATH, "app/AutoTest")
target_path = osp.join(CONFIG.APPGEN_PATH, "app/AutoTest/app/src/main/java/com/autotest/gen/MyService.java")
target_apk_path = osp.join(CONFIG.APPGEN_PATH, "app\\AutoTest\\app\\build\\outputs\\apk\\debug\\app-debug.apk")


def write_log(s):
    print(s)
    with open(log_path, "a") as fp:
        fp.write(s + "\n")


def gen_one_app_project(cls_path):
    utils.check_and_remove(target_path)
    utils.check_and_remove(target_apk_path)
    shutil.copyfile(cls_path, target_path)
    cmd = "gradlew build"
    # subprocess.check_call(cmd, cwd=proj_dir, shell=True)
    run_cmd(cmd, cwd=proj_dir)
    if osp.exists(target_apk_path):
        return True


def run_cmd(cmd, cwd, is_sync=True, timeout=60):
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=cwd, shell=True)
    if is_sync:
        try:
            outs, errs = proc.communicate(timeout=timeout)
        except subprocess.TimeoutExpired:
            import os
            import signal
            # os.kill(proc.pid, signal.SIGTERM)
            # proc.terminate()
            proc.kill()
            outs = ""
        return str(outs)
    # else:
    #     count = timeout
    #     while proc.poll() is not None and count > 0:
    #         time.sleep(1)
    #         count -= 1
    #     proc.terminate()
    #     return str(proc.stdout)


def test_app_and_save_log(apk_path, cls_name):
    # install
    cmd_install = f"adb install -r {apk_path}"
    print(cmd_install)
    adb_cwd = CONFIG.ADB_BIN_DIR
    txt = run_cmd(cmd_install, cwd=adb_cwd)
    if "Success" not in txt:
        print(f"{apk_path} install failed")
        return False
    # run
    # cmd_run = "adb shell monkey -p com.autotest.gen -c android.intent.category.LAUNCHER -v 5"
    cmd_run = "adb shell am start -n com.autotest.gen/com.autotest.gen.MainActivity"
    run_cmd(cmd_run, cwd=adb_cwd, timeout=CONFIG.DYNAMIC_RUN)
    apk_log = osp.join(CONFIG.APPGEN_PATH, f"app/{cls_name}.log")
    cmd_run = f"adb logcat -b crash > {apk_log}"
    run_cmd(cmd_run, cwd=adb_cwd, timeout=10)
    run_cmd("adb kill-server", cwd=adb_cwd)
    run_cmd("adb logcat -b crash -c", cwd=adb_cwd)


def run_one_app(cls_path, cls_name):

    ret = gen_one_app_project(cls_path)
    # ret = True
    if ret:
        write_log(f"S: success to build {cls_name}")
        apk_path = f"{CONFIG.APPGEN_PATH}/app/{cls_name}.apk"
        shutil.copyfile(target_apk_path, apk_path)
        test_app_and_save_log(apk_path, cls_name)
    else:
        write_log(f"F: failed to build {cls_name}")


def get_finish_num():
    with open(log_path, "r") as fp:
        li = len(fp.readlines())
    return li


def main():
    files = os.listdir(cls_dir)
    run_current = "KeyguardManager_exitKeyguardSecurely"
    # run_current = None
    for idx, fp in enumerate(files):
        name, ext = os.path.splitext(fp)
        if run_current and name != run_current:
            print(idx)
            continue
        # if idx < 1236:
        #     continue
        cls_path = osp.join(cls_dir, fp)
        print("start to run", idx, cls_path, name)
        run_one_app(cls_path, name)

"""
get log: ls -l *.log | awk '{if ($5 != 0) print $9}'
get apk num: ls -l *.apk | wc -l

block:
adb install -r E:\PaperWork\JGRAnalyzer\AppGen/app/AudioManager_adjustVolume.apk
dead, AudioManager_adjustSuggestedStreamVolume.apk
dead, AudioManager_playSoundEffect.apk
crash, ConnectivityManager_addDefaultNetworkActiveListener
crash, IpSecManager_openUdpEncapsulationSocket
crash, KeyguardManager_exitKeyguardSecurely
"""

if __name__ == "__main__":
    main()
