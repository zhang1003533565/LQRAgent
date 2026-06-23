#!/usr/bin/env python3
"""Sync Qiniu upload objects into ai-server knowledge bases and reindex Chroma."""

from __future__ import annotations

import mimetypes
import pathlib
import time
import urllib.parse

import requests
from qiniu import Auth, BucketManager

AK = "ZEgWhB7xlVpiZWa-43g52NQ0raPcKzX0dqQpzYcX"
SK = "h8zJ1RMMXw2T00w5O6whS-Z6lTzoFu-zUgxP279f"
BUCKET = "lqragent"
DOMAIN = "http://tfsu0oz0j.hn-bkt.clouddn.com"
AI = "http://localhost:8001"
KB_BASE = pathlib.Path(__file__).resolve().parents[1] / "ai-server" / "data" / "knowledge_bases"


def list_qiniu_uploads() -> list[tuple[str, str]]:
    auth = Auth(AK, SK)
    bm = BucketManager(auth)
    marker = None
    files: list[tuple[str, str]] = []
    while True:
        ret, eof, _info = bm.list(BUCKET, prefix="uploads/", limit=100, marker=marker)
        if not ret:
            break
        for item in ret.get("items", []):
            key = item["key"]
            name = key.split("/", 1)[-1]
            if "_" in name:
                name = name.split("_", 1)[1]
            files.append((key, name))
        if eof:
            break
        marker = ret.get("marker")
    return files


def resolve_kb(filename: str) -> str:
    if "第7章" in filename:
        return "kb-private-2"
    return "kb-public"


def existing_raw_names(kb: str) -> set[str]:
    raw = KB_BASE / kb / "raw"
    if not raw.exists():
        return set()
    return {p.name for p in raw.iterdir() if p.is_file()}


def wait_progress(kb: str, task_id: str | None = None, timeout: int = 600) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        resp = requests.get(f"{AI}/api/v1/knowledge/{kb}/progress", timeout=20)
        resp.raise_for_status()
        progress = resp.json()
        stage = progress.get("stage")
        msg = (progress.get("message") or "")[:100]
        tid = progress.get("task_id")
        print(f"  [{kb}] {stage}: {msg}")
        if task_id and tid and tid != task_id:
            time.sleep(2)
            continue
        if stage == "completed":
            return
        if stage == "error":
            raise RuntimeError(progress)
        time.sleep(3)
    raise TimeoutError(f"timeout waiting for {kb}")


def kb_ready(kb: str) -> bool:
    resp = requests.get(f"{AI}/api/v1/knowledge/list", timeout=20)
    resp.raise_for_status()
    for item in resp.json():
        if item.get("name") != kb:
            continue
        stats = item.get("statistics") or {}
        versions = stats.get("index_versions") or []
        return any(bool(v.get("ready")) for v in versions)
    return False


def delete_kb(kb: str) -> None:
    resp = requests.delete(f"{AI}/api/v1/knowledge/{urllib.parse.quote(kb, safe='')}", timeout=30)
    print(f"delete {kb}: HTTP {resp.status_code}")
    if resp.status_code not in (200, 204, 404):
        print(resp.text[:300])
        resp.raise_for_status()


def create_kb(kb: str, file_items: list[tuple[str, bytes]]) -> str | None:
    multipart = [("name", (None, kb))]
    for filename, content in file_items:
        mime = mimetypes.guess_type(filename)[0] or "application/octet-stream"
        multipart.append(("files", (filename, content, mime)))
    resp = requests.post(f"{AI}/api/v1/knowledge/create", files=multipart, timeout=300)
    print(f"create {kb} with {len(file_items)} file(s): HTTP {resp.status_code}")
    if resp.status_code >= 400:
        print(resp.text[:500])
        resp.raise_for_status()
    return resp.json().get("task_id")


def upload_to_kb(kb: str, filename: str, content: bytes) -> str | None:
    mime = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    url = f"{AI}/api/v1/knowledge/{urllib.parse.quote(kb, safe='')}/upload"
    files = {"files": (filename, content, mime)}
    resp = requests.post(url, files=files, timeout=180)
    print(f"  upload {filename} -> {kb}: HTTP {resp.status_code}")
    if resp.status_code >= 400:
        print(resp.text[:500])
        resp.raise_for_status()
    data = resp.json()
    return data.get("task_id")


def reindex_kb(kb: str) -> None:
    url = f"{AI}/api/v1/knowledge/{urllib.parse.quote(kb, safe='')}/reindex"
    resp = requests.post(url, timeout=30)
    print(f"reindex {kb}: HTTP {resp.status_code} {resp.text[:200]}")
    resp.raise_for_status()
    data = resp.json()
    if data.get("noop"):
        print(f"  {kb} reindex noop")
        return
    wait_progress(kb, data.get("task_id"))


def verify_search(kb: str, query: str) -> None:
    resp = requests.post(
        f"{AI}/api/v1/knowledge/{kb}/search",
        data={"query": query, "top_k": 3},
        timeout=60,
    )
    resp.raise_for_status()
    data = resp.json()
    sources = data.get("sources") or []
    print(f"search [{kb}] '{query}' -> {len(sources)} hits")
    for src in sources[:3]:
        title = src.get("title")
        score = src.get("score")
        print(f"  - {title} (score={score})")


def main() -> None:
    files = list_qiniu_uploads()
    print(f"Qiniu uploads: {len(files)}")
    for key, name in files:
        print(f"  {name} ({key})")

    downloads: dict[str, list[tuple[str, bytes]]] = {"kb-public": [], "kb-private-2": []}
    for key, filename in files:
        kb = resolve_kb(filename)
        encoded_key = urllib.parse.quote(key, safe="")
        content = requests.get(f"{DOMAIN}/{encoded_key}", timeout=180).content
        downloads[kb].append((filename, content))
        print(f"cached {filename} ({len(content)} bytes) -> {kb}")

    public_items = downloads["kb-public"]
    if public_items and not kb_ready("kb-public"):
        print("kb-public not initialized — rebuilding from Qiniu uploads")
        delete_kb("kb-public")
        task_id = create_kb("kb-public", public_items)
        if task_id:
            wait_progress("kb-public", task_id, timeout=900)
    else:
        synced = 0
        for filename, content in public_items:
            if filename in existing_raw_names("kb-public"):
                print(f"skip (exists): {filename} in kb-public")
                continue
            print(f"sync: {filename} -> kb-public")
            task_id = upload_to_kb("kb-public", filename, content)
            if task_id:
                wait_progress("kb-public", task_id, timeout=900)
            synced += 1
        print(f"synced {synced} new public file(s)")

    for filename, content in downloads["kb-private-2"]:
        kb = "kb-private-2"
        if filename in existing_raw_names(kb):
            print(f"skip (exists): {filename} in {kb}")
            continue
        print(f"sync: {filename} -> {kb}")
        task_id = upload_to_kb(kb, filename, content)
        if task_id:
            wait_progress(kb, task_id, timeout=900)
    for kb in ("kb-public", "kb-private-2"):
        try:
            reindex_kb(kb)
        except Exception as exc:
            print(f"reindex warning for {kb}: {exc}")

    verify_search("kb-public", "Python 字符串")
    verify_search("kb-public", "新建 DOCX")
    verify_search("kb-private-2", "数据处理")


if __name__ == "__main__":
    main()
