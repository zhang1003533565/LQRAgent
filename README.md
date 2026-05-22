# LQRAgent

面向《Python 高级语言程序设计》的个性化 AI 学习系统（软件杯）。

## 快速开始

详见 **[docs/启动指南.md](./docs/启动指南.md)**。

```powershell
# 1. MySQL 创建库 agent，并修改 backend 数据库密码
# 2. 后端
cd backend && mvn spring-boot:run
# 3. 前端（新终端）
cd frontend && npm install && npm run dev
```

- 前端：http://localhost:5173  
- 管理员：`admin` / `123456`  
- 学生：`student1` / `123456`  

## 文档

| 文档 | 说明 |
|------|------|
| [启动指南](./docs/启动指南.md) | 环境、启动顺序、配置、排错 |
| [项目精简开发指南](./docs/项目精简开发指南.md) | 架构与 MVP |
| [schema.sql](./docs/schema.sql) | 数据库 DDL |
