# PaiAgent - AI Agent 工作流编辑与执行平台

## Context

构建一个完整的 AI Agent 工作流可视化编辑器和执行引擎。用户可以通过拖拽节点（大模型节点、工具节点）组建工作流，连接节点形成 DAG 图，配置各节点参数，并通过调试面板测试执行。典型场景：输入文字 → 大模型处理 → 超拟人音频合成 → 输出音频播放（AI 播客）。

## 技术选型

| 层次 | 技术 |
|------|------|
| 前端 | React 18 + TypeScript + Vite + React Flow 11 + Ant Design 5 + Zustand |
| 后端 | Spring Boot 2.7.x + JDK 11 + JPA + OkHttp |
| 数据库 | SQLite (工作流 JSON 存储) |
| 认证 | JWT (jjwt) |
| 部署 | Docker Compose (Nginx + Spring Boot) |

## 项目结构

```
PaiAgent/
├── docker-compose.yml
├── .gitignore
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── Dockerfile
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── api/              # axios 实例 + 各模块 API
│       ├── store/            # Zustand stores (auth/workflow/debug)
│       ├── components/
│       │   ├── TopBar/       # 顶部操作栏
│       │   ├── Sidebar/      # 左侧节点库（可拖拽）
│       │   ├── Canvas/       # React Flow 画布 + 自定义节点
│       │   ├── ConfigPanel/  # 右侧节点配置面板
│       │   ├── DebugDrawer/  # 调试抽屉
│       │   └── Auth/         # 登录页
│       ├── hooks/            # useDragNode, useWorkflow
│       ├── types/            # TypeScript 类型定义
│       └── styles/
└── backend/
    ├── pom.xml
    ├── Dockerfile
    └── src/main/
        ├── java/com/paiagent/
        │   ├── PaiAgentApplication.java
        │   ├── config/       # Security, JWT, SQLite, CORS 配置
        │   ├── controller/   # Auth, Workflow, Execution 控制器
        │   ├── model/        # Entity + DTO + Workflow 模型
        │   ├── repository/   # JPA 数据访问
        │   ├── service/      # 业务逻辑
        │   ├── engine/       # 工作流引擎核心
        │   │   ├── WorkflowEngine.java       # DAG 遍历执行器
        │   │   ├── ExecutionContext.java      # 节点间数据总线
        │   │   ├── NodeExecutor.java          # 节点执行接口
        │   │   ├── NodeExecutorFactory.java   # 工厂
        │   │   └── executors/                 # 各类型节点实现
        │   ├── adapter/      # LLM/TTS 统一适配器
        │   │   ├── LLMAdapter.java + Factory
        │   │   ├── impl/ (DeepSeek, Qwen, ChatGLM, AIPing)
        │   │   └── TTSAdapter.java
        │   └── security/     # JWT Filter + Provider
        └── resources/
            ├── application.yml
            └── schema.sql
```

## 数据库设计

### users 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 自增主键 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | BCrypt 哈希 |
| role | VARCHAR(20) | 角色 (admin) |
| created_at | TIMESTAMP | 创建时间 |

### workflows 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | UUID |
| name | VARCHAR(100) | 工作流名称 |
| user_id | INTEGER FK | 所属用户 |
| definition | TEXT | 工作流 JSON（nodes + edges） |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### execution_logs 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) PK | UUID |
| workflow_id | VARCHAR(36) FK | 关联工作流 |
| input | TEXT | 用户输入 |
| output | TEXT | 执行结果 JSON |
| status | VARCHAR(20) | SUCCESS/FAILED |
| duration_ms | INTEGER | 执行耗时 |
| node_logs | TEXT | 各节点执行日志 JSON |
| created_at | TIMESTAMP | 执行时间 |

### Workflow Definition JSON 格式
```json
{
  "nodes": [
    { "id": "node_1", "type": "input", "position": {"x": 300, "y": 50}, "data": {} },
    { "id": "node_2", "type": "llm", "position": {"x": 300, "y": 200}, "data": {"provider": "qwen", "model": "qwen-turbo", "prompt": "{{node_1.output}}"} },
    { "id": "node_3", "type": "tts", "position": {"x": 300, "y": 350}, "data": {"voiceId": "zhiyan", "inputRef": "node_2.output"} },
    { "id": "node_4", "type": "output", "position": {"x": 300, "y": 500}, "data": {"outputs": [{"key": "output", "ref": "node_3.audioUrl"}], "responseTemplate": "{{output}}"} }
  ],
  "edges": [
    { "id": "e1", "source": "node_1", "target": "node_2" },
    { "id": "e2", "source": "node_2", "target": "node_3" },
    { "id": "e3", "source": "node_3", "target": "node_4" }
  ]
}
```

## REST API 设计

### 认证
- `POST /api/auth/login` → `{username, password}` → `{token, user}`
- `GET /api/auth/me` → 获取当前用户

### 工作流 CRUD
- `GET /api/workflows` → 列表
- `GET /api/workflows/{id}` → 详情
- `POST /api/workflows` → 新建
- `PUT /api/workflows/{id}` → 保存
- `DELETE /api/workflows/{id}` → 删除

### 执行
- `POST /api/workflows/{id}/execute` → `{input: "文本"}` → `{executionId, status, output: {text, audioUrl}, nodeLogs}`
- `GET /api/executions/{id}` → 查询执行结果

### 统一响应格式
```json
{ "code": 200, "data": {...}, "message": "ok" }
```

## 工作流引擎核心逻辑

```
WorkflowEngine.execute(definition, userInput):
  1. 解析 nodes + edges
  2. 拓扑排序确定执行顺序
  3. 创建 ExecutionContext（Map<nodeId, Map<key, value>>）
  4. 按顺序执行每个节点:
     a. 解析输入引用 {{nodeId.key}} → 从 context 获取值
     b. NodeExecutorFactory 获取对应执行器
     c. executor.execute(nodeData, context) → 输出存入 context
  5. 收集输出节点结果返回
```

### 节点执行器
| 类型 | 逻辑 |
|------|------|
| InputNodeExecutor | 将用户输入存为 output |
| LLMNodeExecutor | 解析 prompt 模板 → 调用 LLMAdapter → 存储回复 |
| TTSNodeExecutor | 解析输入文本 → 调用 TTSAdapter → 保存音频 → 存储 audioUrl |
| OutputNodeExecutor | 解析响应模板 → 组装最终输出 |

### LLM 适配器模式
- `LLMAdapter` 接口: `String chat(String prompt, Map<String, Object> config)`
- 各厂商实现: DeepSeek/Qwen/ChatGLM/AIPing（大部分兼容 OpenAI 格式）
- `LLMAdapterFactory.getAdapter(provider)` 返回对应实现

### TTS 适配器
- 接收文本 + 语音配置 → 调用 TTS API → 接收音频字节 → 保存文件 → 返回 URL

## 前端组件架构

```
App
├── LoginPage (/login)
└── EditorPage (/, JWT 保护)
    ├── TopBar [项目名 | 新建/加载/保存/调试 | 用户/登出]
    ├── MainLayout (flex)
    │   ├── Sidebar (左, 240px) [节点库, 可拖拽]
    │   ├── WorkflowCanvas (中) [React Flow + 自定义节点]
    │   └── ConfigPanel (右, 320px) [选中节点配置表单]
    └── DebugDrawer (抽屉) [输入框 + 执行按钮 + 结果 + 音频播放器]
```

### 拖拽流程
1. Sidebar NodeItem 设置 `draggable`，`onDragStart` 存入 nodeType
2. Canvas `onDrop` 读取 nodeType → 计算位置 → 创建节点 → 加入 store

### 状态管理 (Zustand)
- `workflowStore`: nodes, edges, selectedNodeId, workflowId, workflowName
- `authStore`: token, user, login/logout
- `debugStore`: isOpen, input, result, loading

## Docker 部署

### docker-compose.yml
```yaml
services:
  frontend:
    build: ./frontend
    ports: ["8080:80"]
    depends_on: [backend]
  backend:
    build: ./backend
    ports: ["8081:8081"]
    volumes: ["./data:/app/data"]
    environment:
      - DEEPSEEK_API_KEY
      - QWEN_API_KEY
      - CHATGLM_API_KEY
      - TTS_API_KEY
      - JWT_SECRET
```

- Frontend: Vite 构建 → Nginx 托管静态文件 + 反向代理 /api 到 backend
- Backend: Maven 构建 JAR → OpenJDK 运行
- SQLite 数据库文件 + 音频文件通过 volume 持久化

## 实现顺序

### Phase 1: 项目脚手架
- [ ] 初始化前端 (Vite + React + TS + 依赖安装)
- [ ] 初始化后端 (Spring Boot + pom.xml + 基础配置)
- [ ] docker-compose.yml 骨架
- [ ] .gitignore

### Phase 2: 认证系统
- [ ] 后端: SQLite + JPA + User entity + schema.sql (含 admin 种子数据)
- [ ] 后端: JWT 生成/验证 + AuthController + JwtAuthFilter
- [ ] 前端: LoginPage + authStore + axios 拦截器

### Phase 3: 工作流画布 (核心 UI)
- [ ] 前端: React Flow 集成 + WorkflowCanvas
- [ ] 前端: 自定义节点组件 (Input/Output/LLM/Tool)
- [ ] 前端: Sidebar 节点库 + 拖拽
- [ ] 前端: TopBar + 布局框架
- [ ] 前端: workflowStore (节点/边/选中状态)

### Phase 4: 节点配置面板
- [ ] 前端: ConfigPanel + 各节点类型配置表单
- [ ] 前端: 输出配置 (引用其他节点输出)

### Phase 5: 工作流 CRUD
- [ ] 后端: Workflow entity + repository + controller
- [ ] 前端: 保存/加载/新建功能

### Phase 6: 工作流引擎
- [ ] 后端: WorkflowEngine + ExecutionContext + 拓扑排序
- [ ] 后端: NodeExecutor 接口 + Factory + 各类型执行器
- [ ] 后端: 变量模板解析 ({{nodeId.key}})
- [ ] 后端: ExecutionController

### Phase 7: LLM 适配器
- [ ] 后端: LLMAdapter 接口 + Factory
- [ ] 后端: DeepSeek/Qwen/ChatGLM/AIPing 适配器实现
- [ ] 后端: LLMNodeExecutor 集成

### Phase 8: TTS 集成
- [ ] 后端: TTSAdapter + 音频文件存储
- [ ] 后端: TTSNodeExecutor + 文件服务端点

### Phase 9: 调试面板
- [ ] 前端: DebugDrawer (输入/执行/结果/音频播放)
- [ ] 前端: debugStore 集成

### Phase 10: 容器化与收尾
- [ ] Frontend/Backend Dockerfile
- [ ] Nginx 配置
- [ ] docker-compose.yml 完善
- [ ] 错误处理 + Loading 状态

## 验证方式

1. **本地启动验证**: 前端 `npm run dev` + 后端 `mvn spring-boot:run`
2. **登录测试**: admin/admin 登录获取 JWT
3. **画布操作**: 拖拽节点、连线、配置参数、保存/加载
4. **执行测试**: 调试抽屉输入文字 → 执行工作流 → 验证 LLM 返回 → 验证音频生成
5. **Docker 测试**: `docker-compose up --build` → 浏览器访问 http://localhost:8080
