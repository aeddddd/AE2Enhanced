#!/usr/bin/env python3
import argparse
import pathlib
import re
from collections import defaultdict

IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?com\.github\.aeddddd\.ae2enhanced\.([A-Za-z0-9_.]+)\s*;", re.MULTILINE)


def scan(source_root):
    edges = defaultdict(set)
    for file in sorted(source_root.rglob("*.java")):
        relative = file.relative_to(source_root)
        source = relative.parts[0] if len(relative.parts) > 1 else "(root)"
        text = file.read_text(encoding="utf-8")
        for match in IMPORT.finditer(text):
            target = match.group(1).split(".")[0]
            if source != target:
                edges[source].add(target)
    return edges


def strongly_connected(nodes, edges):
    index = 0
    indices = {}
    lowlinks = {}
    stack = []
    on_stack = set()
    components = []

    def visit(node):
        nonlocal index
        indices[node] = lowlinks[node] = index
        index += 1
        stack.append(node)
        on_stack.add(node)
        for target in sorted(edges.get(node, ())):
            if target not in indices:
                visit(target)
                lowlinks[node] = min(lowlinks[node], lowlinks[target])
            elif target in on_stack:
                lowlinks[node] = min(lowlinks[node], indices[target])
        if lowlinks[node] == indices[node]:
            component = []
            while True:
                member = stack.pop()
                on_stack.remove(member)
                component.append(member)
                if member == node:
                    break
            components.append(sorted(component))

    for node in sorted(nodes):
        if node not in indices:
            visit(node)
    return components


def main():
    parser = argparse.ArgumentParser(description="Scan top-level Java package imports and report cyclic SCCs.")
    parser.add_argument("source_root", nargs="?", default="src/main/java/com/github/aeddddd/ae2enhanced")
    parser.add_argument("--output")
    parser.add_argument("--edges-output")
    args = parser.parse_args()
    source_root = pathlib.Path(args.source_root)
    edges = scan(source_root)
    nodes = set(edges)
    for targets in edges.values():
        nodes.update(targets)
    edge_count = sum(map(len, edges.values()))
    components = strongly_connected(nodes, edges)
    cyclic = [component for component in components if len(component) > 1 or component[0] in edges.get(component[0], ())]
    cyclic.sort(key=lambda component: (-len(component), component))
    lines = [f"packages={len(nodes)} edges={edge_count} cyclic_sccs={len(cyclic)} cyclic_packages={sum(map(len, cyclic))}"]
    for number, component in enumerate(cyclic, 1):
        members = set(component)
        lines.append(f"SCC {number}: size={len(component)} packages={', '.join(component)}")
        internal = sorted((source, target) for source in component for target in edges.get(source, ()) if target in members)
        lines.extend(f"  {source} > {target}" for source, target in internal)
    report = "\n".join(lines) + "\n"
    print(report, end="")
    if args.output:
        output = pathlib.Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(report, encoding="utf-8")
    if args.edges_output:
        output = pathlib.Path(args.edges_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        rows = sorted((source, target) for source, targets in edges.items() for target in targets)
        output.write_text("".join(f"{source}\t{target}\n" for source, target in rows), encoding="utf-8")


if __name__ == "__main__":
    main()
