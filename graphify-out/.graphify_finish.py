import json
from datetime import datetime, timezone
from pathlib import Path

from graphify.detect import save_manifest


root = Path("graphify-out")
detection = json.loads((root / ".graphify_detect.json").read_text(encoding="utf-8"))
save_manifest(detection.get("all_files") or detection["files"])

cost_path = root / "cost.json"
cost = (
    json.loads(cost_path.read_text(encoding="utf-8"))
    if cost_path.exists()
    else {"runs": [], "total_input_tokens": 0, "total_output_tokens": 0}
)
cost["runs"].append(
    {
        "date": datetime.now(timezone.utc).isoformat(),
        "input_tokens": None,
        "output_tokens": None,
        "usage_available": False,
        "files": detection.get("total_files", 0),
    }
)
cost_path.write_text(json.dumps(cost, indent=2, ensure_ascii=False), encoding="utf-8")

print("This run: token usage unavailable from Codex collaboration API")
print(
    f"All time recorded: {cost.get('total_input_tokens', 0):,} input, "
    f"{cost.get('total_output_tokens', 0):,} output ({len(cost['runs'])} runs)"
)
