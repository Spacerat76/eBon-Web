import json
import re
from pathlib import Path

from graphify.analyze import suggest_questions
from graphify.build import build_from_json
from graphify.report import generate


def plain_label(value: str, file_type: str, community_id: int) -> str:
    value = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", value)
    words = re.findall(r"[A-Za-z0-9]+", value)
    words = [word for word in words if word.lower() not in {"java", "tsx", "ts", "sql"}]
    if not words:
        words = ["Project", "Component"]
    if len(words) == 1:
        words.append("Module" if file_type == "code" else "Concept")
    acronyms = {"ai": "AI", "api": "API", "csv": "CSV", "dto": "DTO", "e2e": "E2E", "ocr": "OCR", "sql": "SQL", "ui": "UI"}
    label = " ".join(acronyms.get(word.lower(), word.title()) for word in words[:5])
    return label or f"Project Community {community_id}"


root = Path("graphify-out")
extraction = json.loads((root / ".graphify_extract.json").read_text(encoding="utf-8"))
detection = json.loads((root / ".graphify_detect.json").read_text(encoding="utf-8"))
analysis = json.loads((root / ".graphify_analysis.json").read_text(encoding="utf-8"))

graph = build_from_json(extraction)
communities = {int(key): value for key, value in analysis["communities"].items()}
cohesion = {int(key): value for key, value in analysis["cohesion"].items()}

labels = {}
used = set()
for community_id, node_ids in communities.items():
    representative = max(
        node_ids,
        key=lambda node_id: (graph.degree(node_id), len(graph.nodes[node_id].get("label", ""))),
    )
    attrs = graph.nodes[representative]
    label = plain_label(
        str(attrs.get("label", representative)),
        str(attrs.get("file_type", "concept")),
        community_id,
    )
    if label.casefold() in used:
        label = " ".join(label.split()[:3] + ["Cluster", str(community_id)])
    used.add(label.casefold())
    labels[community_id] = label

tokens = {
    "input": extraction.get("input_tokens", 0),
    "output": extraction.get("output_tokens", 0),
}
questions = suggest_questions(graph, communities, labels)
report = generate(
    graph,
    communities,
    cohesion,
    labels,
    analysis["gods"],
    analysis["surprises"],
    detection,
    tokens,
    ".",
    suggested_questions=questions,
)
(root / "GRAPH_REPORT.md").write_text(report, encoding="utf-8")
(root / ".graphify_labels.json").write_text(
    json.dumps({str(key): value for key, value in labels.items()}, indent=2),
    encoding="utf-8",
)
analysis["questions"] = questions
(root / ".graphify_analysis.json").write_text(
    json.dumps(analysis, indent=2, ensure_ascii=False), encoding="utf-8"
)
print(f"Report updated with {len(labels)} community labels")
