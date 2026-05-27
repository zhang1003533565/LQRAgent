# `src` 目录说明

当前前端目录按你要求统一为：

```text
src/
├── api/
│   ├── admin/
│   ├── user/
│   └── http.ts
├── assets/
│   ├── admin/
│   └── user/
├── components/
│   ├── admin/
│   └── user/
├── pages/
│   ├── admin/
│   └── user/
├── router/
├── utils/
├── App.tsx
├── App.module.css
├── index.css
├── main.tsx
└── vite-env.d.ts
```

**归类约定**

- `api/`：接口定义，按 `admin` / `user` 拆分；公共请求实例放 `api/http.ts`
- `assets/`：静态资源，按 `admin` / `user` 拆分
- `components/`：组件目录，根下只保留 `admin` 和 `user`
- `pages/`：页面与页面域内状态、hooks、types、styles 等归档到各自的 `admin` / `user`
- `router/`：预留给后续统一路由管理
- `utils/`：预留给后续请求拦截器、响应拦截器等工具

**导入前缀**

- `@/api/...`
- `@/assets/...`
- `@/components/...`
- `@/pages/...`
