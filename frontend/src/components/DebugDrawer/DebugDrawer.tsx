import { useRef, useState, useCallback } from 'react';
import { Drawer, Input, Button, Alert, Spin, Card, Collapse, Tag, Steps } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined, SendOutlined, CaretRightOutlined, LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { useDebugStore } from '../../store/debugStore';
import { useWorkflowStore } from '../../store/workflowStore';

const { TextArea } = Input;

export default function DebugDrawer() {
  const { isOpen, closeDrawer, input, setInput, result, progressMessages, loading, error, execute } =
    useDebugStore();
  const workflowId = useWorkflowStore((s) => s.workflowId);

  const audioRef = useRef<HTMLAudioElement>(null);
  const [audioPlaying, setAudioPlaying] = useState(false);

  const toggleAudio = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) {
      audio.play();
      setAudioPlaying(true);
    } else {
      audio.pause();
      setAudioPlaying(false);
    }
  }, []);

  const onAudioEnded = useCallback(() => setAudioPlaying(false), []);
  const onAudioPause = useCallback(() => setAudioPlaying(false), []);
  const onAudioPlay = useCallback(() => setAudioPlaying(true), []);

  const handleExecute = () => {
    if (!workflowId) return;
    if (!input.trim()) return;
    execute(workflowId);
  };

  return (
    <Drawer
      title="调试工作流"
      placement="right"
      width={420}
      open={isOpen}
      onClose={closeDrawer}
    >
      <div className="debug-input-area">
        <TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入测试文本..."
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
          执行
        </Button>
        {!workflowId && (
          <Alert
            type="warning"
            message="请先保存工作流"
            style={{ marginTop: 8 }}
            showIcon
          />
        )}
      </div>

      {/* Running status — always visible when there are progress messages */}
      {progressMessages.length > 0 && (
        <Card size="small" title={
          <span>
            执行状态
            {loading && <Tag color="processing" style={{ marginLeft: 8 }}>运行中</Tag>}
            {!loading && error && <Tag color="error" style={{ marginLeft: 8 }}>失败</Tag>}
            {!loading && !error && result && <Tag color="success" style={{ marginLeft: 8 }}>已完成</Tag>}
          </span>
        } style={{ marginTop: 12, marginBottom: 12 }}>
          <Steps
            direction="vertical"
            size="small"
            current={progressMessages.length - 1}
            items={progressMessages.map((p) => ({
              title: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Tag color={p.nodeType === 'input' ? 'blue' : p.nodeType === 'llm' ? 'purple' : p.nodeType === 'tts' ? 'orange' : 'green'}>
                    {p.nodeType === 'input' ? '输入' : p.nodeType === 'llm' ? 'LLM' : p.nodeType === 'tts' ? 'TTS' : '输出'}
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
                  {p.durationMs != null && p.status !== 'RUNNING' && (
                    <span style={{ color: '#999', marginLeft: 4 }}>({p.durationMs}ms)</span>
                  )}
                </span>
              ),
              status: p.status === 'RUNNING' ? 'process' : p.status === 'SUCCESS' ? 'finish' : 'error',
            }))}
          />
        </Card>
      )}

      {loading && progressMessages.length === 0 && (
        <div style={{ textAlign: 'center', padding: 20, marginTop: 12 }}>
          <Spin size="large" />
          <div style={{ marginTop: 8, color: '#999', fontSize: 13 }}>正在启动执行...</div>
        </div>
      )}

      {error && <Alert type="error" message={error} style={{ marginTop: 12 }} showIcon />}

      {result && (
        <div className="debug-result">
          <Card size="small" title="执行结果" style={{ marginBottom: 12 }}>
            <div>
              <strong>状态:</strong>{' '}
              <span style={{ color: result.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f' }}>
                {result.status === 'SUCCESS' ? '成功' : '失败'}
              </span>
            </div>
            <div>
              <strong>耗时:</strong> {result.durationMs}ms
            </div>
          </Card>

          {result.status === 'FAILED' && result.error && (
            <Alert type="error" message={result.error} style={{ marginBottom: 12 }} showIcon />
          )}

          {result.output?.audioUrl && (
            <Card
              size="small"
              title={
                <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <PlayCircleOutlined style={{ color: '#667eea' }} />
                  <span>音频输出</span>
                  {audioPlaying && (
                    <Tag color="processing" style={{ marginLeft: 4, fontSize: 11 }}>
                      播放中
                    </Tag>
                  )}
                </span>
              }
              style={{ marginBottom: 12, borderColor: audioPlaying ? '#667eea' : undefined }}
            >
              <audio
                ref={audioRef}
                src={result.output.audioUrl}
                onEnded={onAudioEnded}
                onPause={onAudioPause}
                onPlay={onAudioPlay}
                style={{ display: 'none' }}
              />
              <Button
                type={audioPlaying ? 'default' : 'primary'}
                icon={audioPlaying ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                onClick={toggleAudio}
                size="large"
                block
                style={{
                  height: 48,
                  fontSize: 15,
                  fontWeight: 600,
                  borderRadius: 8,
                  background: audioPlaying
                    ? undefined
                    : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  border: audioPlaying ? undefined : 'none',
                }}
              >
                {audioPlaying ? '暂停' : '播放 AI 播客音频'}
              </Button>
            </Card>
          )}

          {result.output?.text && (
            <Card size="small" title="文本输出" style={{ marginBottom: 12 }}>
              <p style={{ whiteSpace: 'pre-wrap' }}>{result.output.text}</p>
            </Card>
          )}

          {result.nodeLogs && result.nodeLogs.length > 0 && (
            <Card size="small" title="节点执行详情" style={{ marginBottom: 12 }}>
              <Collapse
                expandIcon={({ isActive }) => <CaretRightOutlined rotate={isActive ? 90 : 0} />}
                items={result.nodeLogs.map((log) => ({
                  key: log.nodeId,
                  label: (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Tag color={log.nodeType === 'input' ? 'blue' : log.nodeType === 'llm' ? 'purple' : log.nodeType === 'tts' ? 'orange' : 'green'}>
                        {log.nodeType === 'input' ? '输入' : log.nodeType === 'llm' ? 'LLM' : log.nodeType === 'tts' ? 'TTS' : '输出'}
                      </Tag>
                      <span style={{ fontWeight: 600 }}>{log.nodeId}</span>
                      <Tag color={log.status === 'SUCCESS' ? 'success' : 'error'}>
                        {log.status === 'SUCCESS' ? '成功' : '失败'}
                      </Tag>
                      <span style={{ color: '#999', fontSize: 12 }}>{log.durationMs}ms</span>
                    </span>
                  ),
                  children: (
                    <div>
                      <div style={{ marginBottom: 8 }}>
                        <strong style={{ color: '#1890ff' }}>输入:</strong>
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
                          <strong style={{ color: '#52c41a' }}>输出:</strong>
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
                          <strong style={{ color: '#ff4d4f' }}>错误:</strong>
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
