"""
Mock AI Server — 用于验证 Spring Boot 生命周期管理的最简 FastAPI 服务。
启动命令: python mock_app.py
"""
import uvicorn
from fastapi import FastAPI

app = FastAPI()


@app.get("/api/v1/system/status")
def system_status():
    return {"status": "healthy", "msg": "Mock AI Server is running"}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
