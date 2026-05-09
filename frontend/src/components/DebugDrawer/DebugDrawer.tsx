import { Drawer, Input, Button, Alert, Spin, Card, Collapse, Tag, Steps } from 'antd';
import { PlayCircleOutlined, SendOutlined, CaretRightOutlined, LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { useDebugStore } from '../../store/debugStore';
import { useWorkflowStore } from '../../store/workflowStore';

const { TextArea } = Input;

export default function DebugDrawer() {
  const { isOpen, closeDrawer, input, setInput, result, progressMessages, loading, error, execute } =
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
        <div style={{ marginTop: 12 }}>
          {progressMessages.length > 0 ? (
            <Steps
              direction="vertical"
              size="small"
              current={progressMessages.length - 1}
              items={progressMessages.map((p) => ({
                title: (
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Tag color={p.nodeType === 'input' ? 'blue' : p.nodeType === 'llm' ? 'purple' : p.nodeType === 'tts' ? 'orange' : 'green'}>
                      {p.nodeType.toUpperCase()}
                    </Tag>
                    <span style={{ fontWeight: 500 }}>{p.label}</span>
                  </span>
                ),
                description: (
                  <span style={{ color: p.status === 'RUNNING' ? '#1890ff' : p.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f' }}>
                    {p.status === 'RUNNING' && <LoadingOutlined style={{ marginRight: 4 }} />}
                    {p.status === 'SUCCESS' && <CheckCircleOutlined style={{ marginRight: 4 }} />}
                    {p.status === 'FAILED' && <CloseCircleOutlined style={{ marginRight: 4 }} />}
                    {p.message}
                  </span>
                ),
                status: p.status === 'RUNNING' ? 'process' : p.status === 'SUCCESS' ? 'finish' : 'error',
              }))}
            />
          ) : (
            <div style={{ textAlign: 'center', padding: 20 }}>
              <Spin size="large" />
              <div style={{ marginTop: 8, color: '#999', fontSize: 13 }}>Starting execution...</div>
            </div>
          )}
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

          {result.status === 'FAILED' && result.error && (
            <Alert type="error" message={result.error} style={{ marginBottom: 12 }} showIcon />
          )}

          {result.output?.text && (
            <Card size="small" title="Text Output" style={{ marginBottom: 12 }}>
              <p style={{ whiteSpace: 'pre-wrap' }}>{result.output.text}</p>
            </Card>
          )}

          {result.output?.audioUrl && (
            <Card size="small" title="Audio Output" className="audio-player" style={{ marginBottom: 12 }}>
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
            <Card size="small" title="Node Execution Detail" style={{ marginBottom: 12 }}>
              <Collapse
                expandIcon={({ isActive }) => <CaretRightOutlined rotate={isActive ? 90 : 0} />}
                items={result.nodeLogs.map((log) => ({
                  key: log.nodeId,
                  label: (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Tag color={log.nodeType === 'input' ? 'blue' : log.nodeType === 'llm' ? 'purple' : log.nodeType === 'tts' ? 'orange' : 'green'}>
                        {log.nodeType.toUpperCase()}
                      </Tag>
                      <span style={{ fontWeight: 600 }}>{log.nodeId}</span>
                      <Tag color={log.status === 'SUCCESS' ? 'success' : 'error'}>
                        {log.status}
                      </Tag>
                      <span style={{ color: '#999', fontSize: 12 }}>{log.durationMs}ms</span>
                    </span>
                  ),
                  children: (
                    <div>
                      <div style={{ marginBottom: 8 }}>
                        <strong style={{ color: '#1890ff' }}>Input:</strong>
                        <pre style={{
                          background: '#f5f5f5',
                          padding: 8,
                          borderRadius: 4,
                          fontSize: 12,
                          maxHeight: 200,
                          overflow: 'auto',
                          marginTop: 4,
                        }}>
                          {JSON.stringify(log.input, null, 2)}
                        </pre>
                      </div>
                      {log.status === 'SUCCESS' && (
                        <div>
                          <strong style={{ color: '#52c41a' }}>Output:</strong>
                          <pre style={{
                            background: '#f5f5f5',
                            padding: 8,
                            borderRadius: 4,
                            fontSize: 12,
                            maxHeight: 200,
                            overflow: 'auto',
                            marginTop: 4,
                          }}>
                            {JSON.stringify(log.output, null, 2)}
                          </pre>
                        </div>
                      )}
                      {log.error && (
                        <div>
                          <strong style={{ color: '#ff4d4f' }}>Error:</strong>
                          <pre style={{
                            background: '#fff2f0',
                            padding: 8,
                            borderRadius: 4,
                            fontSize: 12,
                            marginTop: 4,
                          }}>
                            {log.error}
                          </pre>
                        </div>
                      )}
                    </div>
                  ),
                }))}
              />
            </Card>
          )}
        </div>
      )}
    </Drawer>
  );
}
