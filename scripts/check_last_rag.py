import json
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "messages.json"
msgs = json.load(open(path, encoding="utf-8"))
print(f"total messages: {len(msgs)}")
for m in reversed(msgs):
    role = m.get("role")
    content = (m.get("content") or "")[:120].replace("\n", " ")
    agent = m.get("agentName")
    meta = m.get("metadata")
    md = {}
    if meta:
        try:
            md = json.loads(meta) if isinstance(meta, str) else meta
        except Exception:
            pass
    rag = md.get("ragSources") if isinstance(md, dict) else None
    print("---")
    print(f"id={m.get('id')} role={role} agent={agent} at={str(m.get('createdAt', ''))[:19]}")
    print(f"content: {content}")
    if isinstance(rag, list) and rag:
        print(f"ragSources: {len(rag)} hits")
        for s in rag[:5]:
            print(f"  - {s.get('kbName')} | {s.get('title')} | score={s.get('score')}")
    else:
        print("ragSources: none")
