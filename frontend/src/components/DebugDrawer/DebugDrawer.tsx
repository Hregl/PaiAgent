import { Drawer, Input, Button, Alert, Spin, Card } from 'antd';
import { PlayCircleOutlined, SendOutlined } from '@ant-design/icons';
import { useDebugStore } from '../../store/debugStore';
import { useWorkflowStore } from '../../store/workflowStore';

const { TextArea } = Input;

export default function DebugDrawer() {
  const { isOpen, closeDrawer, input, setInput, result, loading, error, execute } =
    useDebugStore();
  const workflowId = useWorkflowStore((s) => s.workflowId);

  const handleExecute = () => {
    if (!workflowId) return;
    if (!input.trim()) return;
    execute(workflowId);
  };

  return (
    <Drawer
      title="Debug Workflow"
      placement="right"
      width={420}
      open={isOpen}
      onClose={closeDrawer}
    >
      <div className="debug-input-area">
        <TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Enter test input text..."
          rows={4}
          style={{ marginBottom: 12 }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleExecute}
          loading={loading}
          disabled={!workflowId || !input.trim()}
          block
        >
          Execute
        </Button>
        {!workflowId && (
          <Alert
            type="warning"
            message="Please save workflow first"
            style={{ marginTop: 8 }}
            showIcon
          />
        )}
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin size="large" />
          <div style={{ marginTop: 12, color: '#999' }}>Executing workflow...</div>
        </div>
      )}

      {error && <Alert type="error" message={error} style={{ marginTop: 12 }} showIcon />}

      {result && (
        <div className="debug-result">
          <Card size="small" title="Execution Result" style={{ marginBottom: 12 }}>
            <div>
              <strong>Status:</strong>{' '}
              <span style={{ color: result.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f' }}>
                {result.status}
              </span>
            </div>
            <div>
              <strong>Duration:</strong> {result.durationMs}ms
            </div>
          </Card>

          {result.output?.text && (
            <Card size="small" title="Text Output" style={{ marginBottom: 12 }}>
              <p style={{ whiteSpace: 'pre-wrap' }}>{result.output.text}</p>
            </Card>
          )}

          {result.output?.audioUrl && (
            <Card size="small" title="Audio Output" className="audio-player">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <PlayCircleOutlined style={{ fontSize: 20, color: '#667eea' }} />
                <span>AI Podcast Audio</span>
              </div>
              <audio controls style={{ width: '100%' }} src={result.output.audioUrl}>
                Your browser does not support audio playback.
              </audio>
            </Card>
          )}

          {result.nodeLogs && result.nodeLogs.length > 0 && (
            <Card size="small" title="Node Execution Logs">
              {result.nodeLogs.map((log) => (
                <div
                  key={log.nodeId}
                  style={{
                    padding: '6px 0',
                    borderBottom: '1px solid #f0f0f0',
                    fontSize: 12,
                  }}
                >
                  <span style={{ fontWeight: 600 }}>{log.nodeId}</span>
                  <span
                    style={{
                      marginLeft: 8,
                      color: log.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f',
                    }}
                  >
                    {log.status}
                  </span>
                  <span style={{ marginLeft: 8, color: '#999' }}>{log.durationMs}ms</span>
                </div>
              ))}
            </Card>
          )}
        </div>
      )}
    </Drawer>
  );
}
