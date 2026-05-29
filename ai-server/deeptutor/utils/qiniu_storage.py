"""
七牛云对象存储工具。
提供上传、下载、删除等基本操作，供 knowledge.py 调用。
"""

import os
import hashlib
import logging
from io import BytesIO

import httpx
import hmac
import time
import base64
import urllib.parse
from hashlib import sha1

logger = logging.getLogger(__name__)

# 从环境变量读取配置
QINIU_ACCESS_KEY = os.getenv("QINIU_ACCESS_KEY", "")
QINIU_SECRET_KEY = os.getenv("QINIU_SECRET_KEY", "")
QINIU_BUCKET = os.getenv("QINIU_BUCKET", "lqragent")
QINIU_DOMAIN = os.getenv("QINIU_DOMAIN", f"http://{QINIU_BUCKET}.qiniucdn.com")


def _make_token(upload_url: str) -> str:
    """生成七牛云上传凭证（put policy token）。"""
    if not QINIU_ACCESS_KEY or not QINIU_SECRET_KEY:
        raise RuntimeError("七牛云 QINIU_ACCESS_KEY / QINIU_SECRET_KEY 未配置")

    policy = {
        "scope": QINIU_BUCKET,
        "deadline": int(time.time()) + 3600,
        "insertOnly": 1,
    }
    import json
    policy_encoded = base64.urlsafe_b64encode(json.dumps(policy).encode()).decode().rstrip("=")
    sign = hmac.new(QINIU_SECRET_KEY.encode(), policy_encoded.encode(), sha1).digest()
    sign_encoded = base64.urlsafe_b64encode(sign).decode().rstrip("=")
    return f"{QINIU_ACCESS_KEY}:{sign_encoded}:{policy_encoded}"


def _make_download_token(key: str, deadline: int) -> str:
    """生成七牛云私有空间下载签名。"""
    if not QINIU_ACCESS_KEY or not QINIU_SECRET_KEY:
        raise RuntimeError("七牛云 AK/SK 未配置")

    base_url = f"{QINIU_DOMAIN}/{urllib.parse.quote(key, safe='')}"
    sign_str = f"{base_url}?e={deadline}"
    sign = hmac.new(QINIU_SECRET_KEY.encode(), sign_str.encode(), sha1).digest()
    sign_encoded = base64.urlsafe_b64encode(sign).decode().rstrip("=")
    return f"{base_url}?e={deadline}&token={QINIU_ACCESS_KEY}:{sign_encoded}"


def upload_to_qiniu(key: str, data: bytes, content_type: str = "application/octet-stream") -> str:
    """
    上传文件到七牛云。

    Args:
        key: 对象 key（如 uploads/1/xxx.pdf）
        data: 文件字节
        content_type: MIME 类型

    Returns:
        上传后的 key
    """
    token = _make_token(key)
    url = "https://up.qiniupl.com/" if not QINIU_ACCESS_KEY else "https://upload.qiniupl.com/"

    # 使用 S3 兼容接口（更简单）
    # 实际用七牛上传 API
    url = f"https://upload.qiniupl.com/"

    files = {"file": (key.split("/")[-1], BytesIO(data), content_type)}
    headers = {"Authorization": f"UpToken {token}"}

    resp = httpx.post(url, files=files, headers=headers, timeout=60)
    resp.raise_for_status()
    logger.debug(f"[Qiniu] uploaded: key={key}, size={len(data)}")
    return key


def download_from_qiniu(key: str) -> bytes:
    """
    从七牛云下载文件。

    Args:
        key: 对象 key

    Returns:
        文件字节
    """
    deadline = int(time.time()) + 3600
    url = _make_download_token(key, deadline)

    resp = httpx.get(url, timeout=30, follow_redirects=True)
    resp.raise_for_status()
    logger.debug(f"[Qiniu] downloaded: key={key}, size={len(resp.content)}")
    return resp.content


def delete_from_qiniu(key: str) -> bool:
    """从七牛云删除文件。"""
    if not QINIU_ACCESS_KEY or not QINIU_SECRET_KEY:
        return False

    path = f"/delete/{urllib.parse.quote(key, safe='')}"
    url_to_sign = f"{QINIU_ACCESS_KEY}:{path}"
    sign = hmac.new(QINIU_SECRET_KEY.encode(), url_to_sign.encode(), sha1).digest()
    sign_encoded = base64.urlsafe_b64encode(sign).decode().rstrip("=")

    delete_url = f"https://rs.qiniuapi.com{path}"
    headers = {"Authorization": f"QBox {QINIU_ACCESS_KEY}:{sign_encoded}"}

    try:
        resp = httpx.delete(delete_url, headers=headers, timeout=10)
        resp.raise_for_status()
        return True
    except Exception as e:
        logger.warning(f"[Qiniu] delete failed: key={key}, error={e}")
        return False


def compute_sha256(data: bytes) -> str:
    """计算字节数据的 SHA256 哈希。"""
    return hashlib.sha256(data).hexdigest()
