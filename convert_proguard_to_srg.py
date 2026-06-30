#!/usr/bin/env python3
"""把官方 ProGuard mapping 转成 SpecialSource 可用的 SRG 映射（obf -> official）。

SRG 左侧使用混淆名（匹配 vanilla client jar），右侧使用官方名。
"""

import re
import sys
from pathlib import Path

PRIMITIVE_DESC = {
    'void': 'V',
    'boolean': 'Z',
    'byte': 'B',
    'char': 'C',
    'short': 'S',
    'int': 'I',
    'long': 'J',
    'float': 'F',
    'double': 'D',
}


def split_array(t: str):
    dim = 0
    while t.endswith('[]'):
        t = t[:-2]
        dim += 1
    return t, dim


def type_to_desc(t: str, class_map: dict) -> str:
    """把 ProGuard 里的 Java source 类型名转成 JVM descriptor。
    class_map: official class name -> obfuscated class name（用点分隔）
    """
    t, dim = split_array(t.strip())
    prefix = '[' * dim
    if t in PRIMITIVE_DESC:
        return prefix + PRIMITIVE_DESC[t]
    # java/lang 等未混淆类直接转换；否则查表
    obf = class_map.get(t, t)
    return prefix + 'L' + obf.replace('.', '/') + ';'


def type_to_desc_official(t: str) -> str:
    """转成官方类名的 descriptor（用于 SRG 右侧）。"""
    t, dim = split_array(t.strip())
    prefix = '[' * dim
    if t in PRIMITIVE_DESC:
        return prefix + PRIMITIVE_DESC[t]
    return prefix + 'L' + t.replace('.', '/') + ';'


def method_desc(params: str, ret: str, class_map: dict, official=False) -> str:
    pdesc = ''
    if params.strip():
        for p in params.split(','):
            if official:
                pdesc += type_to_desc_official(p)
            else:
                pdesc += type_to_desc(p, class_map)
    if official:
        return '(' + pdesc + ')' + type_to_desc_official(ret)
    return '(' + pdesc + ')' + type_to_desc(ret, class_map)


def main():
    if len(sys.argv) != 3:
        print("Usage: convert_proguard_to_srg.py <input-mapping> <output-srg>")
        sys.exit(1)
    in_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])

    class_re = re.compile(r'^([a-zA-Z0-9_.$]+)\s*->\s*([a-zA-Z0-9_.$]+):$')
    method_re = re.compile(r'^\s*\d+:\d+:(.+?)\s+(\S+)\s*\((.*)\)\s*->\s*(\S+)\s*$')
    field_re = re.compile(r'^\s*(\S+)\s+(\S+)\s*->\s*(\S+)\s*$')

    # 第一遍：收集类映射
    class_map = {}
    with in_path.open('r', encoding='utf-8') as f:
        for raw in f:
            line = raw.rstrip('\n').rstrip('\r')
            if not line or line.startswith('#'):
                continue
            m = class_re.match(line)
            if m:
                official = m.group(1)
                obf = m.group(2)
                class_map[official] = obf

    # 第二遍：生成 SRG
    out_lines = []
    current_official = None
    current_obf = None

    with in_path.open('r', encoding='utf-8') as f:
        for raw in f:
            line = raw.rstrip('\n').rstrip('\r')
            if not line or line.startswith('#'):
                continue

            m = class_re.match(line)
            if m:
                current_official = m.group(1)
                current_obf = m.group(2)
                out_lines.append(f'CL: {current_obf.replace(".", "/")} {current_official.replace(".", "/")}')
                continue

            if current_official is None:
                continue

            m = method_re.match(line)
            if m:
                ret = m.group(1).strip()
                name = m.group(2)
                params = m.group(3)
                obf_name = m.group(4)
                if '<' in name:
                    continue
                old_desc = method_desc(params, ret, class_map, official=False)
                new_desc = method_desc(params, ret, class_map, official=True)
                out_lines.append(
                    f'MD: {current_obf.replace(".", "/")}/{obf_name} {old_desc} '
                    f'{current_official.replace(".", "/")}/{name} {new_desc}'
                )
                continue

            m = field_re.match(line)
            if m:
                ftype = m.group(1)
                fname = m.group(2)
                obf_name = m.group(3)
                if '(' in ftype or '(' in fname:
                    continue
                old_desc = type_to_desc(ftype, class_map)
                new_desc = type_to_desc_official(ftype)
                out_lines.append(
                    f'FD: {current_obf.replace(".", "/")}/{obf_name} '
                    f'{current_official.replace(".", "/")}/{fname} {old_desc}'
                )
                continue

    out_path.write_text('\n'.join(out_lines) + '\n', encoding='utf-8')
    print(f'Wrote {len(out_lines)} mappings to {out_path}')


if __name__ == '__main__':
    main()
