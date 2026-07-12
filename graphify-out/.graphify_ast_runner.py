import json
from pathlib import Path

from graphify.extract import collect_files, extract


def main() -> None:
    detect = json.loads(
        Path("graphify-out/.graphify_detect.json").read_text(encoding="utf-8")
    )
    code_files = []
    for file_name in detect.get("files", {}).get("code", []):
        path = Path(file_name)
        code_files.extend(collect_files(path) if path.is_dir() else [path])

    if code_files:
        result = extract(code_files, cache_root=Path("."))
    else:
        result = {"nodes": [], "edges": [], "input_tokens": 0, "output_tokens": 0}

    Path("graphify-out/.graphify_ast.json").write_text(
        json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    Path("graphify-out/.graphify_ast.done").write_text(
        f'{len(result["nodes"])} nodes, {len(result["edges"])} edges', encoding="utf-8"
    )


if __name__ == "__main__":
    main()
