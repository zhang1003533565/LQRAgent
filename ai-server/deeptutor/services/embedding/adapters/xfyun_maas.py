"""iFlytek Spark MaaS embedding adapter."""

import logging
from typing import Any

import httpx

from .base import BaseEmbeddingAdapter, EmbeddingProviderError, EmbeddingRequest, EmbeddingResponse

logger = logging.getLogger(__name__)


class XfyunMaasEmbeddingAdapter(BaseEmbeddingAdapter):
    """Adapter for iFlytek MaaS Embedding HTTP API."""

    DEFAULT_BASE_URL = "https://maas-api.cn-huabei-1.xf-yun.com/v2"

    def _endpoint(self) -> str:
        url = str(self.base_url or self.DEFAULT_BASE_URL).strip().rstrip("/")
        if not url:
            return f"{self.DEFAULT_BASE_URL}/embeddings"
        if url.endswith("/embeddings"):
            return url
        if url.endswith("/v1") or url.endswith("/v2"):
            return f"{url}/embeddings"
        return f"{url}/embeddings"

    @staticmethod
    def _extract_embeddings(data: Any) -> list[list[float]]:
        if not isinstance(data, dict):
            raise ValueError(f"Unexpected embedding response type: {type(data).__name__}")
        if data.get("error"):
            raise ValueError(str(data.get("error")))
        items = data.get("data")
        if not isinstance(items, list):
            raise ValueError(f"Embedding response missing data array: keys={list(data.keys())}")
        embeddings: list[list[float]] = []
        for item in items:
            if not isinstance(item, dict) or not isinstance(item.get("embedding"), list):
                continue
            embeddings.append(item["embedding"])
        if not embeddings:
            raise ValueError("Embedding response contains no embedding vectors")
        return embeddings

    async def embed(self, request: EmbeddingRequest) -> EmbeddingResponse:
        url = self._endpoint()
        payload: dict[str, Any] = {
            "model": request.model or self.model,
            "input": request.texts[0] if len(request.texts) == 1 else request.texts,
        }
        if request.encoding_format:
            payload["encoding_format"] = request.encoding_format
        if self.send_dimensions is True and (request.dimensions or self.dimensions):
            payload["dimensions"] = request.dimensions or self.dimensions

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        headers.update({str(k): str(v) for k, v in self.extra_headers.items()})

        timeout = httpx.Timeout(
            connect=10.0,
            read=max(self.request_timeout, 60),
            write=10.0,
            pool=10.0,
        )
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(url, json=payload, headers=headers)
        if response.status_code >= 400:
            logger.error("HTTP %s from %s: %s", response.status_code, url, response.text[:2000])
            raise EmbeddingProviderError(
                f"Xfyun MaaS embedding returned HTTP {response.status_code}",
                status=response.status_code,
                body=response.text,
                model=request.model or self.model,
                url=url,
                provider="xfyun",
            )

        data = response.json()
        embeddings = self._extract_embeddings(data)
        return EmbeddingResponse(
            embeddings=embeddings,
            model=str(data.get("model") or request.model or self.model),
            dimensions=len(embeddings[0]) if embeddings else 0,
            usage=data.get("usage") if isinstance(data.get("usage"), dict) else {},
        )

    def get_model_info(self) -> dict[str, Any]:
        return {
            "provider": "xfyun",
            "model": self.model,
            "dimensions": self.dimensions or 0,
            "supports_variable_dimensions": self.send_dimensions is True,
        }
