# PaiAgent - AI 智能体工作流编排平台

PaiAgent 是一个可视化 AI 工作流编排系统，支持通过拖拽方式构建包含 LLM 调用、TTS 音频合成等节点的 DAG 工作流，并一键执行调试。

---

## 技术栈

### 前端
- React 18 + TypeScript
- React Flow（画布编辑器）
- Ant Design 5（UI 组件）
- Zustand（状态管理）
- Axios（HTTP 客户端）
- Vite（构建工具）

### 后端
- Spring Boot 2.7.18 + JDK 11
- Spring Security + JWT 认证
- Spring Data JPA + SQLite
- OkHttp（LLM API 调用）

### 部署
- Docker Compose（Nginx + Spring Boot）

---

## 核心功能

| 功能模块 | 说明 |
|---------|------|
| 画布编辑器 | React Flow 拖拽式节点编排，支持 Input / LLM / TTS / Output 四种节点 |
| 节点配置面板 | 动态表单配置各节点参数（模型选择、Prompt 模板、温度等） |
| 调试抽屉 | 输入测试文本 → 执行工作流 → 查看文本/音频输出结果 |
| JWT 认证 | 登录页 + 路由守卫，默认账号 admin / admin123 |
| 工作流管理 | 新建 / 保存 / 加载 / 删除工作流 |
| DAG 引擎 | Kahn 算法拓扑排序 + 模板变量解析（`{{nodeId.output}}`） |
| 多 LLM 适配 | 支持 DeepSeek / 通义千问 / 智谱 ChatGLM / AI Ping |
| TTS 合成 | 超拟人音频合成，支持外部 API 接入 + 占位音频 fallback |

---

## 项目结构

```plaintext
PaiAgent/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/paiagent/
│   │   ├── adapter/                  # LLM/TTS 适配器
│   │   ├── config/                   # 安全配置、CORS、数据初始化
│   │   ├── controller/               # REST API
│   │   ├── engine/                   # DAG 工作流引擎 + 执行器
│   │   ├── model/                    # Entity + DTO
│   │   ├── repository/               # JPA 仓库
│   │   └── security/                 # JWT 过滤器 + Token 生成
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                         # React 前端
│   ├── src/
│   │   ├── api/                      # Axios 封装
│   │   ├── components/               # 画布、节点、配置面板、调试抽屉
│   │   ├── store/                    # Zustand 状态管理
│   │   └── types/                    # TypeScript 类型定义
│   ├── Dockerfile
│   └── vite.config.ts
└── docker-compose.yml


