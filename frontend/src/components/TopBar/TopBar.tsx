import { Input, Button, Space } from 'antd';
import {
  PlusOutlined,
  FolderOpenOutlined,
  SaveOutlined,
  BugOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { useWorkflowStore } from '../../store/workflowStore';
import { useAuthStore } from '../../store/authStore';
import { useDebugStore } from '../../store/debugStore';
import { workflowApi } from '../../api/workflow';
import { message, Modal } from 'antd';
import { useState } from 'react';
import { Workflow } from '../../types/workflow';

export default function TopBar() {
  const { workflowName, setWorkflowName, workflowId, setWorkflowId, nodes, edges, resetWorkflow, setNodes, setEdges } =
    useWorkflowStore();
  const { user, logout } = useAuthStore();
  const { openDrawer } = useDebugStore();
  const [loadModalOpen, setLoadModalOpen] = useState(false);
  const [workflows, setWorkflows] = useState<Workflow[]>([]);

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
    setNodes(wf.definition.nodes);
    setEdges(wf.definition.edges);
    setLoadModalOpen(false);
    message.success(`Loaded: ${wf.name}`);
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
    </div>
  );
}
