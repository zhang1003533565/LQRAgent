"""Chroma 向量数据库客户端连接管理。"""

from __future__ import annotations

import logging
import os
from typing import Any

import chromadb
from chromadb import HttpClient as ChromaHttpClient


logger = logging.getLogger(__name__)


def _chroma_host() -> str:
    return os.environ.get("CHROMA_HOST", "localhost")


def _chroma_port() -> int:
    return int(os.environ.get("CHROMA_PORT", "8000"))


def get_chroma_client() -> chromadb.ClientAPI:
    """获取 Chroma HTTP 客户端（连接 Docker 中的 Chroma 服务）。"""
    host = _chroma_host()
    port = _chroma_port()
    logger.info(f"Connecting to Chroma at {host}:{port}")
    return ChromaHttpClient(host=host, port=port)


def get_or_create_collection(
    client: chromadb.ClientAPI,
    collection_name: str,
    metadata: dict[str, Any] | None = None,
) -> chromadb.Collection:
    """获取或创建 Chroma Collection。

    collection_name 通常为知识库名称（如 kb_name）。
    metadata 可存入 embedding signature 等信息用于版本追踪。
    """
    try:
        return client.get_collection(collection_name)
    except ValueError:
        return client.create_collection(
            name=collection_name,
            metadata=metadata or {},
        )


def collection_exists(client: chromadb.ClientAPI, collection_name: str) -> bool:
    """检查 Collection 是否存在。"""
    try:
        client.get_collection(collection_name)
        return True
    except ValueError:
        return False


def delete_collection(client: chromadb.ClientAPI, collection_name: str) -> bool:
    """删除 Collection。"""
    try:
        client.delete_collection(collection_name)
        logger.info(f"Deleted Chroma collection: {collection_name}")
        return True
    except ValueError:
        return False
