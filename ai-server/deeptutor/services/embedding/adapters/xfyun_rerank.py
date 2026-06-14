"""iFlytek Spark MaaS rerank client."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import httpx


@dataclass(frozen=True)
class RerankResult:
    index: int
    relevance_score: float


class XfyunMaasRerankClient:
    DEFAULT_BASE_URL = "https://maas-api.cn-huabei-1.xf-yun.com/v2"

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        base_url: str | None = None,
        request_timeout: int = 60,
    ) -> None:
        self.api_key = api_key
        self.model = model
        self.base_url = base_url
        self.request_timeout = request_timeout

    def _endpoint(self) -> str:
        url = str(self.base_url or self.DEFAULT_BASE_URL).strip().rstrip("/")
        if not url:
            return f"{self.DEFAULT_BASE_URL}/rerank"
        if url.endswith("/rerank"):
            return url
        if url.endswith("/embeddings"):
            return url[: -len("/embeddings")] + "/rerank"
        if url.endswith("/v1") or url.endswith("/v2"):
            return f"{url}/rerank"
        return f"{url}/rerank"

    async def rerank(self, query: str, documents: list[str]) -> list[RerankResult]:
        payload = {
            "model": self.model,
            "query": query,
            "documents": documents,
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        timeout = httpx.Timeout(
            connect=10.0,
            read=max(self.request_timeout, 60),
            write=10.0,
            pool=10.0,
        )
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(self._endpoint(), json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
        if data.get("error"):
            raise RuntimeError(str(data["error"]))
        results = data.get("results")
        if not isinstance(results, list):
            raise ValueError(f"Rerank response missing results array: keys={list(data.keys())}")
        parsed: list[RerankResult] = []
        for item in results:
            if not isinstance(item, dict):
                continue
            parsed.append(
                RerankResult(
                    index=int(item["index"]),
                    relevance_score=float(item["relevance_score"]),
                )
            )
        return parsed
