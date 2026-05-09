import { Input, Button, Space, Modal, Spin, Card, Alert } from 'antd';
import {
  PlusOutlined,
  FolderOpenOutlined,
  SaveOutlined,
  BugOutlined,
  UserOutlined,
  LogoutOutlined,
  CaretRightOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { useWorkflowStore } from '../../store/workflowStore';
import { useAuthStore } from '../../store/authStore';
import { useDebugStore } from '../../store/debugStore';
import { workflowApi } from '../../api/workflow';
import { executionApi } from '../../api/execution';
import { message } from 'antd';
import { useState } from 'react';
import { Workflow, ExecutionResult } from '../../types/workflow';

export default function TopBar() {
  const { workflowName, setWorkflowName, workflowId, setWorkflowId, nodes, edges, resetWorkflow, setNodes, setEdges } =
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

  const handleNew = () => {
    resetWorkflow();
    message.success('New workflow created');
  };

  const handleSave = async () => {
    if (!workflowName.trim()) {
      message.warning('Please enter workflow name');
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
      message.success('Saved successfully');
    } catch {
      message.error('Save failed');
    }
  };

  const handleLoad = async () => {
    try {
      const res = await workflowApi.list();
      setWorkflows(res.data);
      setLoadModalOpen(true);
    } catch {
      message.error('Failed to load workflows');
    }
  };

  const handleSelectWorkflow = async (wf: Workflow) => {
    setWorkflowId(wf.id);
    setWorkflowName(wf.name);
    const definition =
      typeof wf.definition === 'string' ? JSON.parse(wf.definition) : wf.definition;
    setNodes(definition.nodes);
    setEdges(definition.edges);
    setLoadModalOpen(false);
    message.success(`Loaded: ${wf.name}`);
  };

  const handleExecute = async () => {
    if (!workflowId || !executeInput.trim()) return;
    setExecuteLoading(true);
    setExecuteError(null);
    setExecuteResult(null);
    try {
      const res = await executionApi.execute(workflowId, executeInput);
      if (res.code !== 200) {
        setExecuteError(res.message || 'Execution failed');
      } else {
        setExecuteResult(res.data);
      }
    } catch (err: unknown) {
      const msg =
        (err && typeof err === 'object' && 'message' in err)
          ? String((err as { message: string }).message)
          : 'Execution failed';
      setExecuteError(msg);
    } finally {
      setExecuteLoading(false);
    }
  };

  const openExecuteModal = () => {
    setExecuteInput('');
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
          placeholder="Workflow name"
          style={{ width: 160 }}
          size="small"
        />
      </div>
      <div className="top-bar-center">
        <Space>
          <Button icon={<PlusOutlined />} onClick={handleNew}>
            New
          </Button>
          <Button icon={<FolderOpenOutlined />} onClick={handleLoad}>
            Load
          </Button>
          <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
            Save
          </Button>
          <Button
            type="primary"
            icon={<CaretRightOutlined />}
            onClick={openExecuteModal}
            disabled={!workflowId}
            style={{ background: '#52c41a', borderColor: '#52c41a' }}
          >
            Run
          </Button>
          <Button
            type="primary"
            icon={<BugOutlined />}
            onClick={openDrawer}
            style={{ background: '#333' }}
          >
            Debug
          </Button>
        </Space>
      </div>
      <div className="top-bar-right">
        <Space>
          <UserOutlined />
          <span>{user?.username || 'admin'}</span>
          <Button type="text" icon={<LogoutOutlined />} onClick={logout}>
            Logout
          </Button>
        </Space>
      </div>

      <Modal
        title="Load Workflow"
        open={loadModalOpen}
        onCancel={() => setLoadModalOpen(false)}
        footer={null}
      >
        {workflows.length === 0 ? (
          <p>No workflows found</p>
        ) : (
          <div>
            {workflows.map((wf) => (
              <div
                key={wf.id}
                onClick={() => handleSelectWorkflow(wf)}
                style={{
                  padding: '10px 16px',
                  cursor: 'pointer',
                  borderBottom: '1px solid #f0f0f0',
                  borderRadius: 6,
                }}
                onMouseEnter={(e) => (e.currentTarget.style.background = '#f5f5f5')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              >
                <strong>{wf.name}</strong>
                <div style={{ fontSize: 12, color: '#999' }}>{wf.updatedAt}</div>
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Modal
        title="Run Workflow"
        open={executeModalOpen}
        onCancel={() => setExecuteModalOpen(false)}
        footer={null}
        width={520}
      >
        {!workflowId && (
          <Alert
            type="warning"
            message="Please save the workflow first before executing"
            style={{ marginBottom: 12 }}
            showIcon
          />
        )}
        <Input.TextArea
          value={executeInput}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setExecuteInput(e.target.value)}
          placeholder="Enter input text to send to the workflow..."
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
          Execute
        </Button>

        {executeLoading && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
            <div style={{ marginTop: 12, color: '#999' }}>Executing workflow...</div>
          </div>
        )}

        {executeError && (
          <div style={{ marginTop: 12, padding: 12, background: '#fff2f0', borderRadius: 8, color: '#ff4d4f' }}>
            {executeError}
          </div>
        )}

        {executeResult && (
          <div style={{ marginTop: 16 }}>
            <Card size="small" title="Result" style={{ marginBottom: 12 }}>
              <span style={{ color: executeResult.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f', fontWeight: 600 }}>
                {executeResult.status}
              </span>
              <span style={{ marginLeft: 12, color: '#999' }}>{executeResult.durationMs}ms</span>
            </Card>

            {executeResult.output?.text && (
              <Card size="small" title="Text Output" style={{ marginBottom: 12 }}>
                <p style={{ whiteSpace: 'pre-wrap', maxHeight: 300, overflowY: 'auto' }}>
                  {executeResult.output.text}
                </p>
              </Card>
            )}

            {executeResult.output?.audioUrl && (
              <Card size="small" title="Audio" style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <PlayCircleOutlined style={{ fontSize: 20, color: '#667eea' }} />
                  <span>AI Podcast Audio</span>
                </div>
                <audio controls style={{ width: '100%' }} src={executeResult.output.audioUrl}>
                  Your browser does not support audio playback.
                </audio>
              </Card>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
