import { Input, Button, Space, Modal, Spin, Card, Alert, Select } from 'antd';
import {
  PlusOutlined,
  FolderOpenOutlined,
  SaveOutlined,
  BugOutlined,
  UserOutlined,
  LogoutOutlined,
  CaretRightOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  UndoOutlined,
  RedoOutlined,
  ExportOutlined,
  ImportOutlined,
} from '@ant-design/icons';
import { useWorkflowStore } from '../../store/workflowStore';
import { useAuthStore } from '../../store/authStore';
import { useDebugStore } from '../../store/debugStore';
import { workflowApi } from '../../api/workflow';
import { executionApi } from '../../api/execution';
import { configApi } from '../../api/config';
import { message } from 'antd';
import { useState, useEffect, useRef, useCallback } from 'react';
import { Workflow, ExecutionResult, EngineType } from '../../types/workflow';

export default function TopBar() {
  const { workflowName, setWorkflowName, workflowId, setWorkflowId, nodes, edges, resetWorkflow, setNodes, setEdges, engineType, setEngineType, past, future, undo, redo } =
    useWorkflowStore();
  const { user, logout } = useAuthStore();
  const { openDrawer } = useDebugStore();
  const [loadModalOpen, setLoadModalOpen] = useState(false);
  const [workflows, setWorkflows] = useState<Workflow[]>([]);

  // Execute modal state
  const [executeModalOpen, setExecuteModalOpen] = useState(false);
  const [executeInput, setExecuteInput] = useState('');
  const [executeResult, setExecuteResult] = useState<ExecutionResult | null>(null);
  const [executeLoading, setExecuteLoading] = useState(false);
  const [executeError, setExecuteError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Load current engine type from backend on mount
  useEffect(() => {
    configApi.getEngineType().then((res: unknown) => {
      const data = res as { code: number; data: { engineType: string } };
      if (data.code === 200 && data.data?.engineType) {
        setEngineType(data.data.engineType as EngineType);
      }
    }).catch(() => {
      // Keep default if backend unavailable
    });
  }, [setEngineType]);

  // Keyboard shortcuts for undo/redo
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        useWorkflowStore.getState().undo();
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'y' || (e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'z') {
        e.preventDefault();
        useWorkflowStore.getState().redo();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  const handleExport = useCallback(() => {
    const state = useWorkflowStore.getState();
    const payload = {
      version: 1,
      exportedAt: new Date().toISOString(),
      workflow: {
        name: state.workflowName || '未命名工作流',
        engineType: state.engineType,
        nodes: state.nodes,
        edges: state.edges,
      },
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${state.workflowName || 'workflow'}.paiagent.json`;
    a.click();
    URL.revokeObjectURL(url);
    message.success('导出成功');
  }, []);

  const handleImport = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      try {
        const json = JSON.parse(ev.target?.result as string);
        if (!json.workflow?.nodes || !json.workflow?.edges) {
          message.error('无效的工作流文件格式');
          return;
        }
        const store = useWorkflowStore.getState();
        store.setNodes(json.workflow.nodes);
        store.setEdges(json.workflow.edges);
        if (json.workflow.name) store.setWorkflowName(json.workflow.name);
        if (json.workflow.engineType) store.setEngineType(json.workflow.engineType);
        store.setWorkflowId(null);
        message.success(`已导入: ${json.workflow.name || '未命名工作流'}`);
      } catch {
        message.error('JSON 解析失败，请检查文件格式');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  }, []);

  const handleEngineSwitch = (type: EngineType) => {
    configApi.setEngineType(type).then(() => {
      setEngineType(type);
      message.success(`引擎已切换为: ${type === 'dag' ? 'DAG' : 'LangGraph'}`);
    }).catch(() => {
      message.error('引擎切换失败');
    });
  };

  const handleNew = () => {
    resetWorkflow();
    message.success('已创建新工作流');
  };

  const handleSave = async () => {
    if (!workflowName.trim()) {
      message.warning('请输入工作流名称');
      return;
    }
    try {
      const definition = { nodes, edges };
      if (workflowId) {
        await workflowApi.update(workflowId, { name: workflowName, definition });
      } else {
        const res = await workflowApi.create({ name: workflowName, definition });
        setWorkflowId(res.data.id);
      }
      message.success('保存成功');
    } catch {
      message.error('保存失败');
    }
  };

  const handleLoad = async () => {
    try {
      const res = await workflowApi.list();
      setWorkflows(res.data);
      setLoadModalOpen(true);
    } catch {
      message.error('加载工作流失败');
    }
  };

  const handleDelete = async (id: string, name?: string) => {
    const displayName = name || id;
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除工作流"${displayName}"吗？此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await workflowApi.delete(id);
          message.success('已删除');
          // If deleting current workflow, reset
          if (workflowId === id) {
            resetWorkflow();
          }
          // Refresh list if load modal is open
          if (loadModalOpen) {
            const res = await workflowApi.list();
            setWorkflows(res.data);
          }
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const handleSelectWorkflow = async (wf: Workflow) => {
    setWorkflowId(wf.id);
    setWorkflowName(wf.name);
    const definition =
      typeof wf.definition === 'string' ? JSON.parse(wf.definition) : wf.definition;
    setNodes(definition.nodes);
    setEdges(definition.edges);
    setLoadModalOpen(false);
    message.success(`已加载: ${wf.name}`);
  };

  const handleExecute = async () => {
    if (!workflowId || !executeInput.trim()) return;
    setExecuteLoading(true);
    setExecuteError(null);
    setExecuteResult(null);
    try {
      const res = await executionApi.execute(workflowId, executeInput);
      if (res.code !== 200) {
        setExecuteError(res.message || '执行失败');
      } else {
        setExecuteResult(res.data);
      }
    } catch (err: unknown) {
      const msg =
        (err && typeof err === 'object' && 'message' in err)
          ? String((err as { message: string }).message)
          : '执行失败';
      setExecuteError(msg);
    } finally {
      setExecuteLoading(false);
    }
  };

  const openExecuteModal = () => {
    // DAG engine cannot execute workflows with condition nodes
    if (engineType === 'dag' && nodes.some((n) => n.type === 'condition')) {
      message.warning('当前工作流包含判断节点，DAG 引擎不支持判断节点执行，请切换到 LangGraph 引擎');
      return;
    }
    // Pre-fill from debug input if available (e.g. after decomposition)
    const debugInput = useDebugStore.getState().input;
    setExecuteInput(debugInput || '');
    setExecuteResult(null);
    setExecuteError(null);
    setExecuteModalOpen(true);
  };

  return (
    <div className="top-bar">
      <div className="top-bar-left">
        <span className="top-bar-logo">PaiAgent</span>
        <Input
          value={workflowName}
          onChange={(e) => setWorkflowName(e.target.value)}
          placeholder="工作流名称"
          style={{ width: 160 }}
          size="small"
        />
      </div>
      <div className="top-bar-center">
        <Space>
          <Button icon={<PlusOutlined />} onClick={handleNew}>
            新建
          </Button>
          <Button icon={<UndoOutlined />} onClick={undo} disabled={past.length === 0}>
            撤销
          </Button>
          <Button icon={<RedoOutlined />} onClick={redo} disabled={future.length === 0}>
            重做
          </Button>
          <Button icon={<FolderOpenOutlined />} onClick={handleLoad}>
            加载
          </Button>
          <Button icon={<ImportOutlined />} onClick={() => fileInputRef.current?.click()}>
            导入
          </Button>
          <Button icon={<ExportOutlined />} onClick={handleExport}>
            导出
          </Button>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleImport}
            accept=".json"
            style={{ display: 'none' }}
          />
          <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
            保存
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(workflowId!, workflowName)}
            disabled={!workflowId}
          >
            删除
          </Button>
          <Button
            type="primary"
            icon={<CaretRightOutlined />}
            onClick={openExecuteModal}
            disabled={!workflowId}
            style={{ background: '#52c41a', borderColor: '#52c41a' }}
          >
            运行
          </Button>
          <Select
            value={engineType}
            onChange={handleEngineSwitch}
            size="small"
            style={{ width: 110 }}
            options={[
              { label: 'DAG', value: 'dag' as EngineType },
              { label: 'LangGraph', value: 'langgraph' as EngineType },
            ]}
          />
          <Button
            type="primary"
            icon={<BugOutlined />}
            onClick={openDrawer}
            style={{ background: '#333' }}
          >
            调试
          </Button>
        </Space>
      </div>
      <div className="top-bar-right">
        <Space>
          <UserOutlined />
          <span>{user?.username || 'admin'}</span>
          <Button type="text" icon={<LogoutOutlined />} onClick={logout}>
            退出登录
          </Button>
        </Space>
      </div>

      <Modal
        title="加载工作流"
        open={loadModalOpen}
        onCancel={() => setLoadModalOpen(false)}
        footer={null}
      >
        {workflows.length === 0 ? (
          <p>暂无工作流</p>
        ) : (
          <div>
            {workflows.map((wf) => (
              <div
                key={wf.id}
                onClick={(e) => {
                  // Don't trigger select if clicking delete
                  if ((e.target as HTMLElement).closest('.delete-btn')) return;
                  handleSelectWorkflow(wf);
                }}
                style={{
                  padding: '10px 16px',
                  cursor: 'pointer',
                  borderBottom: '1px solid #f0f0f0',
                  borderRadius: 6,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.background = '#f5f5f5')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              >
                <div>
                  <strong>{wf.name}</strong>
                  <div style={{ fontSize: 12, color: '#999' }}>{wf.updatedAt}</div>
                </div>
                <Button
                  className="delete-btn"
                  type="text"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDelete(wf.id, wf.name);
                  }}
                />
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Modal
        title="运行工作流"
        open={executeModalOpen}
        onCancel={() => setExecuteModalOpen(false)}
        footer={null}
        width={520}
      >
        {!workflowId && (
          <Alert
            type="warning"
            message="请先保存工作流再执行"
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Input.TextArea
          value={executeInput}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setExecuteInput(e.target.value)}
          placeholder="输入要发送到工作流的文本..."
          rows={4}
          style={{ marginBottom: 12 }}
          disabled={executeLoading}
        />
        <Button
          type="primary"
          icon={<CaretRightOutlined />}
          onClick={handleExecute}
          loading={executeLoading}
          disabled={!workflowId || !executeInput.trim()}
          block
          style={{ background: '#52c41a', borderColor: '#52c41a' }}
        >
          执行
        </Button>

        {executeLoading && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
            <div style={{ marginTop: 12, color: '#999' }}>正在执行工作流...</div>
          </div>
        )}

        {executeError && (
          <div style={{ marginTop: 12, padding: 12, background: '#fff2f0', borderRadius: 8, color: '#ff4d4f' }}>
            {executeError}
          </div>
        )}

        {executeResult && (
          <div style={{ marginTop: 16 }}>
            <Card size="small" title="结果" style={{ marginBottom: 12 }}>
              <span style={{ color: executeResult.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f', fontWeight: 600 }}>
                {executeResult.status}
              </span>
              <span style={{ marginLeft: 12, color: '#999' }}>{executeResult.durationMs}ms</span>
            </Card>

            {executeResult.output?.text && (
              <Card size="small" title="文本输出" style={{ marginBottom: 12 }}>
                <p style={{ whiteSpace: 'pre-wrap', maxHeight: 300, overflowY: 'auto' }}>
                  {executeResult.output.text}
                </p>
              </Card>
            )}

            {executeResult.output?.audioUrl && (
              <Card size="small" title="音频" style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <PlayCircleOutlined style={{ fontSize: 20, color: '#667eea' }} />
                  <span>AI 播客音频</span>
                </div>
                <audio controls style={{ width: '100%' }} src={executeResult.output.audioUrl}>
                  您的浏览器不支持音频播放。
                </audio>
              </Card>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
