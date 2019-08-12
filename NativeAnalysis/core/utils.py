# coding: utf-8
# created at 2019/2/20
__author__ = "fripSide"

import os
import sys
import logging
import json
import collections

logging.basicConfig(stream=sys.stdout, level=logging.INFO)


def write_log(msg, *args):
    if (args and len(args) == 1 and isinstance(args[0], collections.Mapping)
            and args[0]):
        args = args[0]
    info = str(msg) % args
    print(info)


def load_json(path):
    with open(path, "r") as fp:
        return json.load(fp)


def write_json(data, path):
    with open(path, "w") as fp:
        return json.dump(data, fp)


def check_and_mkdir(path):
    if not os.path.exists(path):
        os.makedirs(path)


def check_and_remove(path):
    if os.path.exists(path):
        os.remove(path)
