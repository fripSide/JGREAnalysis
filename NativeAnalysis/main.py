# coding: utf-8
# created at 2019/2/19
__author__ = "fripSide"

from config import CONFIG
from core.JniAPIExtractor import SourceFileScanner


def parse_args():
    return ""


def build_api_map():
    extractor = SourceFileScanner(CONFIG.DEFAULT_AOSP_PATH)
    extractor.statistics()


if __name__ == "__main__":
    args = parse_args()
    build_api_map()
