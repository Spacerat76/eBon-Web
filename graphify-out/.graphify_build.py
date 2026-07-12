import json
from pathlib import Path

from graphify.analyze import god_nodes, suggest_questions, surprising_connections
from graphify.build import build_from_json
from graphify.cluster import cluster, score_all
from graphify.export import to_json
from graphify.report import generate


root = Path("graphify-out")
extraction = json.loads((root / ".graphify_extract.json").read_text(encoding="utf-8"))
detection = json.loads((root / ".graphify_detect.json").read_text(encoding="utf-8"))

graph = build_from_json(extraction)
communities = cluster(graph)
cohesion = score_all(graph, communities)
tokens = {
    "input": extraction.get("input_tokens", 0),
    "output": extraction.get("output_tokens", 0),
}
gods = god_nodes(graph)
surprises = surprising_connections(graph, communities)
labels = {community_id: f"Community {community_id}" for community_id in communities}
questions = suggest_questions(graph, communities, labels)

report = generate(
    graph,
    communities,
    cohesion,
    labels,
    gods,
    surprises,
    detection,
    tokens,
    ".",
    suggested_questions=questions,
)
(root / "GRAPH_REPORT.md").write_text(report, encoding="utf-8")
to_json(graph, communities, root / "graph.json")

analysis = {
    "communities": {str(key): value for key, value in communities.items()},
    "cohesion": {str(key): value for key, value in cohesion.items()},
    "gods": gods,
    "surprises": surprises,
    "questions": questions,
}
(root / ".graphify_analysis.json").write_text(
    json.dumps(analysis, indent=2, ensure_ascii=False), encoding="utf-8"
)

if graph.number_of_nodes() == 0:
    raise SystemExit("ERROR: Graph is empty - extraction produced no nodes.")
print(
    f"Graph: {graph.number_of_nodes()} nodes, {graph.number_of_edges()} edges, "
    f"{len(communities)} communities"
)
