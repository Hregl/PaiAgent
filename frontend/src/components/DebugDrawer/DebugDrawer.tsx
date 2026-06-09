import { useRef, useState, useCallback, useEffect } from 'react';
import { Drawer, Input, Button, Alert, Spin, Card, Collapse, Tag, Steps, Divider, List, Modal } from 'antd';
import { PlayCircleOutlined, PauseCircleOutlined, SendOutlined, CaretRightOutlined, LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined, HistoryOutlined, ReloadOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { useDebugStore } from '../../store/debugStore';
import { useWorkflowStore } from '../../store/workflowStore';
import { executionApi } from '../../api/execution';
import { ExecutionHistoryItem, ExecutionResult } from '../../types/workflow';

const { TextArea } = Input;

export default function DebugDrawer() {
  const { isOpen, closeDrawer, input, setInput, result, progressMessages, loading, error, execute } =
    useDebugStore();
  const workflowId = useWorkflowStore((s) => s.workflowId);

  const audioRef = useRef<HTMLAudioElement>(null);
  const [audioPlaying, setAudioPlaying] = useState(false);

  // Execution history
  const [history, setHistory] = useState<ExecutionHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [selectedHistoryId, setSelectedHistoryId] = useState<string | null>(null);
  const [historyResult, setHistoryResult] = useState<ExecutionResult | null>(null);

  // Result popup modal — auto-shows when execution produces comprehensive output
  const [resultModalOpen, setResultModalOpen] = useState(false);

  // Auto-open result modal when execution completes with text output
  useEffect(() => {
    if (result && result.output?.text && !loading) {
      setResultModalOpen(true);
    }
  }, [result, loading]);

  const fetchHistory = useCallback(async () => {
    if (!workflowId) return;
    setHistoryLoading(true);
    try {
      const res = await executionApi.listExecutions(workflowId);
      if (res.code === 200) {
        setHistory(res.data);
      }
    } catch {
      // silently ignore
    } finally {
      setHistoryLoading(false);
    }
  }, [workflowId]);

  // Auto-fetch history when drawer opens or workflow changes
  useEffect(() => {
    if (isOpen && workflowId) {
      fetchHistory();
      setSelectedHistoryId(null);
      setHistoryResult(null);
    }
  }, [isOpen, workflowId, fetchHistory]);

  const viewHistoryDetail = useCallback((item: ExecutionHistoryItem) => {
    setSelectedHistoryId(item.id);
    // Parse output JSON to reconstruct ExecutionResult
    try {
      const parsedOutput = item.output ? JSON.parse(item.output) : null;
      setHistoryResult(parsedOutput as ExecutionResult);
    } catch {
      setHistoryResult(null);
    }
  }, []);

  const formatTime = useCallback((dateStr: string) => {
    try {
      const d = new Date(dateStr);
      const pad = (n: number) => String(n).padStart(2, '0');
      return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    } catch {
      return dateStr;
    }
  }, []);

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
    <>
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
                  {p.phaseIndex != null && p.totalPhases != null && (
                    <Tag color="geekblue" style={{ fontSize: 11 }}>
                      阶段 {p.phaseIndex + 1}/{p.totalPhases}
                    </Tag>
                  )}
                  <Tag color={p.nodeType === 'input' ? 'blue' : p.nodeType === 'llm' ? 'purple' : p.nodeType === 'tts' ? 'orange' : p.nodeType === 'judge' ? 'pink' : 'green'}>
                    {p.nodeType === 'input' ? '输入' : p.nodeType === 'llm' ? 'LLM' : p.nodeType === 'tts' ? 'TTS' : p.nodeType === 'judge' ? '判断' : '输出'}
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
            <Card
              size="small"
              title="综合输出"
              style={{ marginBottom: 12, borderColor: '#667eea' }}
              extra={
                <Button
                  type="primary"
                  size="small"
                  onClick={() => setResultModalOpen(true)}
                >
                  展开查看
                </Button>
              }
            >
              <p style={{ whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'hidden', position: 'relative' }}>
                {result.output.text}
              </p>
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
                      {log.phaseIndex != null && log.totalPhases != null && (
                        <Tag color="geekblue" style={{ fontSize: 10 }}>阶段 {log.phaseIndex + 1}/{log.totalPhases}</Tag>
                      )}
                      <Tag color={log.nodeType === 'input' ? 'blue' : log.nodeType === 'llm' ? 'purple' : log.nodeType === 'tts' ? 'orange' : log.nodeType === 'judge' ? 'pink' : 'green'}>
                        {log.nodeType === 'input' ? '输入' : log.nodeType === 'llm' ? 'LLM' : log.nodeType === 'tts' ? 'TTS' : log.nodeType === 'judge' ? '判断' : '输出'}
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

      {/* Execution History */}
      {workflowId && (
        <>
          <Divider style={{ marginTop: 16, marginBottom: 12 }}>
            <span style={{ fontSize: 13, color: '#999' }}>
              <HistoryOutlined style={{ marginRight: 4 }} />
              执行历史
            </span>
          </Divider>

          {historyLoading && (
            <div style={{ textAlign: 'center', padding: 12 }}>
              <Spin size="small" />
            </div>
          )}

          {!historyLoading && history.length === 0 && (
            <div style={{ textAlign: 'center', padding: 16, color: '#999', fontSize: 13 }}>
              暂无执行记录
            </div>
          )}

          {!historyLoading && history.length > 0 && (
            <>
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={fetchHistory}
                style={{ marginBottom: 8 }}
              >
                刷新
              </Button>

              <List
                size="small"
                dataSource={history}
                renderItem={(item) => {
                  const isSelected = selectedHistoryId === item.id;
                  return (
                    <>
                      <Card
                        size="small"
                        hoverable
                        onClick={() => viewHistoryDetail(item)}
                        style={{
                          marginBottom: 6,
                          cursor: 'pointer',
                          borderColor: isSelected ? '#667eea' : undefined,
                          background: isSelected ? '#f9f0ff' : undefined,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <span style={{ fontSize: 12, color: '#999' }}>
                            <ClockCircleOutlined style={{ marginRight: 4 }} />
                            {formatTime(item.createdAt)}
                          </span>
                          <Tag color={item.status === 'SUCCESS' ? 'success' : 'error'}>
                            {item.status === 'SUCCESS' ? '成功' : '失败'}
                          </Tag>
                        </div>
                        <div style={{ marginTop: 4, fontSize: 12, color: '#666' }}>
                          输入: {item.input.length > 60 ? item.input.slice(0, 60) + '...' : item.input}
                        </div>
                        <div style={{ marginTop: 2, fontSize: 12, color: '#999' }}>
                          耗时: {item.durationMs}ms
                        </div>
                      </Card>

                      {/* Expanded history detail */}
                      {isSelected && historyResult && (
                        <Card size="small" title="历史执行详情" style={{ marginBottom: 8 }}>
                          <div>
                            <strong>状态:</strong>{' '}
                            <span style={{ color: historyResult.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f' }}>
                              {historyResult.status === 'SUCCESS' ? '成功' : '失败'}
                            </span>
                          </div>
                          <div>
                            <strong>耗时:</strong> {historyResult.durationMs}ms
                          </div>

                          {historyResult.error && (
                            <Alert type="error" message={historyResult.error} style={{ marginTop: 8 }} showIcon />
                          )}

                          {historyResult.output?.audioUrl && (
                            <div style={{ marginTop: 8 }}>
                              <audio
                                controls
                                src={historyResult.output.audioUrl}
                                style={{ width: '100%', height: 36 }}
                              />
                            </div>
                          )}

                          {historyResult.output?.text && (
                            <div style={{ marginTop: 8 }}>
                              <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>文本输出:</div>
                              <p style={{
                                whiteSpace: 'pre-wrap',
                                fontSize: 12,
                                background: '#fafafa',
                                padding: 8,
                                borderRadius: 4,
                                maxHeight: 150,
                                overflow: 'auto',
                              }}>
                                {historyResult.output.text}
                              </p>
                            </div>
                          )}

                          {historyResult.nodeLogs && historyResult.nodeLogs.length > 0 && (
                            <Collapse
                              size="small"
                              style={{ marginTop: 8 }}
                              expandIcon={({ isActive }) => <CaretRightOutlined rotate={isActive ? 90 : 0} />}
                              items={historyResult.nodeLogs.map((log) => ({
                                key: log.nodeId,
                                label: (
                                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                    {log.phaseIndex != null && log.totalPhases != null && (
                                      <Tag color="geekblue" style={{ fontSize: 10 }}>阶段 {log.phaseIndex + 1}/{log.totalPhases}</Tag>
                                    )}
                                    <Tag color={log.nodeType === 'input' ? 'blue' : log.nodeType === 'llm' ? 'purple' : log.nodeType === 'tts' ? 'orange' : log.nodeType === 'judge' ? 'pink' : 'green'} style={{ fontSize: 10 }}>
                                      {log.nodeType === 'input' ? '输入' : log.nodeType === 'llm' ? 'LLM' : log.nodeType === 'tts' ? 'TTS' : log.nodeType === 'judge' ? '判断' : '输出'}
                                    </Tag>
                                    <span style={{ fontSize: 12 }}>{log.nodeId}</span>
                                    <Tag color={log.status === 'SUCCESS' ? 'success' : 'error'} style={{ fontSize: 10 }}>
                                      {log.status === 'SUCCESS' ? '成功' : '失败'}
                                    </Tag>
                                  </span>
                                ),
                                children: (
                                  <div style={{ fontSize: 11 }}>
                                    <div style={{ marginBottom: 4 }}>
                                      <strong style={{ color: '#1890ff' }}>输入:</strong>
                                      <pre style={{
                                        background: '#f5f5f5',
                                        padding: 6,
                                        borderRadius: 3,
                                        maxHeight: 120,
                                        overflow: 'auto',
                                        marginTop: 2,
                                      }}>
                                        {JSON.stringify(log.input, null, 2)}
                                      </pre>
                                    </div>
                                    {log.status === 'SUCCESS' && (
                                      <div>
                                        <strong style={{ color: '#52c41a' }}>输出:</strong>
                                        <pre style={{
                                          background: '#f5f5f5',
                                          padding: 6,
                                          borderRadius: 3,
                                          maxHeight: 120,
                                          overflow: 'auto',
                                          marginTop: 2,
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
                                          padding: 6,
                                          borderRadius: 3,
                                          marginTop: 2,
                                        }}>
                                          {log.error}
                                        </pre>
                                      </div>
                                    )}
                                  </div>
                                ),
                              }))}
                            />
                          )}
                        </Card>
                      )}
                    </>
                  );
                }}
              />
            </>
          )}
        </>
      )}
    </Drawer>

    {/* Result Popup Modal */}
    <Modal
      title="综合输出结果"
      open={resultModalOpen}
      onCancel={() => setResultModalOpen(false)}
      footer={
        <Button type="primary" onClick={() => setResultModalOpen(false)}>
          关闭
        </Button>
      }
      width={720}
      style={{ top: 20 }}
    >
      <div
        style={{
          whiteSpace: 'pre-wrap',
          fontSize: 14,
          lineHeight: 1.8,
          maxHeight: '70vh',
          overflow: 'auto',
          background: '#fafafa',
          padding: 20,
          borderRadius: 8,
          border: '1px solid #f0f0f0',
        }}
      >
        {result?.output?.text || '暂无输出'}
      </div>
    </Modal>
    </>
  );
}
