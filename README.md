# PaiAgent - AI 智能体工作流编排平台

PaiAgent 是一个可视化 AI 工作流编排系统，支持通过拖拽方式构建包含 LLM 调用、TTS 音频合成等节点的 DAG / LangGraph 工作流，并一键执行调试。

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
- Java 21 + Spring Boot 3.4.1
- Spring AI 1.0.0-M5（OpenAI 兼容多模型 LLM 调用）
- LangGraph4j 1.8.14（LangGraph 引擎，可选）
- Spring Security 6 + jjwt 0.12.6（JWT 认证）
- Spring Data JPA + Hibernate 6 + SQLite

### 部署
- Docker Compose（Nginx + Spring Boot，eclipse-temurin:21-jre）

---

## 核心功能

| 功能模块 | 说明 |
|---------|------|
| 画布编辑器 | React Flow 拖拽式节点编排，支持 Input / LLM / TTS / Output 四种节点 |
| 节点配置面板 | 动态表单配置各节点参数（模型选择、Prompt 模板、温度等） |
| 调试抽屉 | 输入测试文本 → 执行工作流 → 查看文本/音频输出结果 |
| JWT 认证 | 登录页 + 路由守卫，默认账号 admin / admin123 |
| 工作流管理 | 新建 / 保存 / 加载 / 删除工作流 |
| DAG 引擎 | Kahn 算法拓扑排序 + 模板变量解析（`{{nodeId.output}}`），默认引擎 |
| LangGraph 引擎 | 基于 LangGraph4j 的 StateGraph 循环图引擎，支持 Agent 式状态管理 |
| 双引擎切换 | 通过 `ENGINE_TYPE` 环境变量在 DAG / LangGraph 间零代码切换 |
| 多 LLM 统一调用 | Spring AI 统一适配 DeepSeek / 通义千问 / 智谱 ChatGLM / AI Ping |
| TTS 合成 | 超拟人音频合成，支持外部 API 接入 + 占位音频 fallback |

---

## 引擎架构

```
┌─────────────────────────────────────────┐
│           WorkflowEngine (接口)           │
│   execute(definitionJson, userInput)     │
├─────────────────────┬───────────────────┤
│  DagWorkflowEngine  │ LangGraphWorkflow  │
│  (engine.type=dag)  │ Engine            │
│                     │ (engine.type=     │
│  • Kahn 拓扑排序     │  langgraph)       │
│  • 严格 DAG 检测     │ • StateGraph 构建  │
│  • 线性执行顺序      │ • 支持循环图       │
│                     │ • Agent 状态管理   │
├─────────────────────┴───────────────────┤
│         NodeExecutorFactory              │
│   InputNode / LLMNode / TTSNode /       │
│   OutputNode                            │
├─────────────────────────────────────────┤
│        SpringAiChatService              │
│   OpenAiChatModel (多 Provider 路由)     │
└─────────────────────────────────────────┘
```

---

## 快速启动

### 前置要求
- Docker Desktop

### 配置环境变量

创建 `.env` 文件或直接 export：

```bash
# 必填：至少配置一个 LLM API Key
export DEEPSEEK_API_KEY=sk-xxx
export QWEN_API_KEY=sk-xxx
# 可选
export CHATGLM_API_KEY=xxx
export AIPING_API_KEY=xxx
export TTS_API_KEY=xxx
export ENGINE_TYPE=dag        # dag（默认）或 langgraph
```

### 启动

```bash
docker compose up -d
```

- 前端：http://localhost:8080
- 后端 API：http://localhost:8081
- 默认登录：`admin` / `admin123`

### 切换引擎

```bash
ENGINE_TYPE=langgraph docker compose up -d
```

---

## 项目结构

```plaintext
PaiAgent/
├── backend/
│   ├── src/main/java/com/paiagent/
│   │   ├── adapter/
│   │   │   ├── SpringAiChatService.java   # Spring AI 统一 LLM 服务
│   │   │   └── TTSAdapter.java            # 文本转语音适配器
│   │   ├── config/
│   │   │   ├── SecurityConfig.java        # Spring Security 6 Lambda DSL
│   │   │   ├── SQLiteDialect.java         # Hibernate 6 自定义方言
│   │   │   ├── DataInitConfig.java        # 数据库初始化
│   │   │   └── WebConfig.java             # CORS 配置
│   │   ├── controller/
│   │   │   ├── AuthController.java        # 登录认证 API
│   │   │   ├── ExecutionController.java   # 工作流执行 API
│   │   │   └── WorkflowController.java    # 工作流 CRUD API
│   │   ├── engine/
│   │   │   ├── WorkflowEngine.java        # 引擎接口
│   │   │   ├── DagWorkflowEngine.java     # DAG 拓扑排序引擎
│   │   │   ├── LangGraphWorkflowEngine.java # LangGraph4j 引擎
│   │   │   ├── NodeExecutorFactory.java   # 节点执行器工厂
│   │   │   ├── ExecutionContext.java      # 执行上下文（状态传递）
│   │   │   └── executors/                 # 各类型节点执行器
│   │   ├── model/
│   │   │   ├── entity/                    # JPA Entity
│   │   │   └── dto/                       # 数据传输对象
│   │   ├── repository/                    # JPA 仓库接口
│   │   └── security/
│   │       ├── JwtAuthFilter.java         # JWT 认证过滤器
│   │       └── JwtTokenProvider.java      # JWT Token 生成/验证
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── api/                           # Axios 封装
│   │   ├── components/                    # 画布、节点、配置面板、调试抽屉
│   │   ├── store/                         # Zustand 状态管理
│   │   └── types/                         # TypeScript 类型定义
│   ├── Dockerfile
│   └── vite.config.ts
├── docker-compose.yml
└── .env                                  # 环境变量（不提交）
```
