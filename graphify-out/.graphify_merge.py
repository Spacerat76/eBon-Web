import json
from pathlib import Path

from graphify.cache import save_semantic_cache


root = Path("graphify-out")
new = json.loads((root / ".graphify_semantic_new.json").read_text(encoding="utf-8"))
saved = save_semantic_cache(
    new.get("nodes", []), new.get("edges", []), new.get("hyperedges", [])
)

cached_path = root / ".graphify_cached.json"
cached = (
    json.loads(cached_path.read_text(encoding="utf-8"))
    if cached_path.exists()
    else {"nodes": [], "edges": [], "hyperedges": []}
)

seen = set()
semantic_nodes = []
for node in cached["nodes"] + new["nodes"]:
    if node["id"] not in seen:
        seen.add(node["id"])
        semantic_nodes.append(node)

semantic = {
    "nodes": semantic_nodes,
    "edges": cached["edges"] + new["edges"],
    "hyperedges": cached.get("hyperedges", []) + new.get("hyperedges", []),
    "input_tokens": new.get("input_tokens", 0),
    "output_tokens": new.get("output_tokens", 0),
}
(root / ".graphify_semantic.json").write_text(
    json.dumps(semantic, indent=2, ensure_ascii=False), encoding="utf-8"
)

ast = json.loads((root / ".graphify_ast.json").read_text(encoding="utf-8"))
all_nodes = list(ast["nodes"])
seen = {node["id"] for node in all_nodes}
for node in semantic_nodes:
    if node["id"] not in seen:
        seen.add(node["id"])
        all_nodes.append(node)

extraction = {
    "nodes": all_nodes,
    "edges": ast["edges"] + semantic["edges"],
    "hyperedges": semantic["hyperedges"],
    "input_tokens": semantic["input_tokens"],
    "output_tokens": semantic["output_tokens"],
}
(root / ".graphify_extract.json").write_text(
    json.dumps(extraction, indent=2, ensure_ascii=False), encoding="utf-8"
)

print(f"Cached {saved} files")
print(f"Semantic: {len(semantic_nodes)} nodes, {len(semantic['edges'])} edges")
print(f"Merged: {len(all_nodes)} nodes, {len(extraction['edges'])} edges")
