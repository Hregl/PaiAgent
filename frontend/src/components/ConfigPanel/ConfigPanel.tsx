import { useWorkflowStore } from '../../store/workflowStore';
import { useDebugStore } from '../../store/debugStore';
import { Form, Input, Select, InputNumber, Button, Divider, message, Checkbox } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { Node } from 'reactflow';
import { CustomNodeData, Phase } from '../../types/workflow';
import { workflowApi } from '../../api/workflow';
import { configApi } from '../../api/config';
import PhaseReviewModal from './PhaseReviewModal';

const { TextArea } = Input;

export default function ConfigPanel() {
  const { nodes, edges, selectedNodeId, updateNodeData } = useWorkflowStore();
  const [form] = Form.useForm();
  const [decomposeLoading, setDecomposeLoading] = useState(false);
  const [reviewPhases, setReviewPhases] = useState<Phase[] | null>(null);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  // Store decomposer config for later use when user confirms phases
  const [pendingDecomposeConfig, setPendingDecomposeConfig] = useState<{
    decomposerNodeId: string;
    llmConfigs: {
      workerProvider: string;
      workerModel: string;
      judgeProvider: string;
      judgeModel: string;
      validatorProvider: string;
      validatorModel: string;
    };
  } | null>(null);

  const selectedNode: Node<CustomNodeData> | undefined = nodes.find(
    (n) => n.id === selectedNodeId
  );

  const upstreamRefs = useMemo(() => {
    if (!selectedNode) return [] as { label: string; value: string }[];
    // Find connected upstream nodes via edges
    const incomingEdges = edges.filter((e) => e.target === selectedNode.id);
    const upstreamIds = incomingEdges.map((e) => e.source);
    return nodes
      .filter((n) => upstreamIds.includes(n.id))
      .flatMap((n) => {
        const data = n.data as CustomNodeData;
        if (n.type === 'input') {
          const vn = ('variableName' in data) ? (data as { variableName: string }).variableName : 'output';
          return [{ label: `${n.id} → ${vn}`, value: `${n.id}.${vn}` }];
        }
        if (n.type === 'condition') {
          return [{ label: `${n.id} → branch`, value: `${n.id}.branch` }];
        }
        return [{ label: `${n.id} → output`, value: `${n.id}.output` }];
      });
  }, [selectedNode, nodes, edges]);

  useEffect(() => {
    if (selectedNode) {
      form.setFieldsValue(selectedNode.data);
    }
  }, [selectedNode, form]);

  if (!selectedNode) return null;

  const handleSave = () => {
    const values = form.getFieldsValue();
    updateNodeData(selectedNode.id, values);
    message.success('配置已保存');
  };

  const handleDecompose = async () => {
    const values = form.getFieldsValue();
    // Save first
    updateNodeData(selectedNode!.id, values);

    if (!values.taskDescription?.trim()) {
      message.warning('请先填写任务描述');
      return;
    }

    setDecomposeLoading(true);
    try {
      const res = await workflowApi.decompose({
        taskDescription: values.taskDescription,
        provider: values.workerProvider || 'deepseek',
        model: values.workerModel || 'deepseek-chat',
        apiKey: values.apiKey || '',
        apiBaseUrl: values.apiBaseUrl || '',
      });

      if (res.code !== 200 || !res.data?.phases) {
        message.error(res.message || '分解失败');
        return;
      }

      const llmConfigs = {
        workerProvider: values.workerProvider || 'deepseek',
        workerModel: values.workerModel || 'deepseek-chat',
        judgeProvider: values.judgeProvider || 'deepseek',
        judgeModel: values.judgeModel || 'deepseek-chat',
        validatorProvider: values.validatorProvider || 'deepseek',
        validatorModel: values.validatorModel || 'deepseek-chat',
      };

      const phases = res.data.phases as Phase[];
      if (!phases || phases.length === 0) {
        message.error('分解结果为空，请重试');
        return;
      }

      // Show review modal instead of immediately generating nodes
      setPendingDecomposeConfig({
        decomposerNodeId: selectedNode!.id,
        llmConfigs,
      });
      setReviewPhases(phases);
      setReviewModalOpen(true);

      // Auto-fill debug input with task description so user doesn't need to retype
      useDebugStore.getState().setInput(values.taskDescription);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '分解失败';
      message.error(msg);
    } finally {
      setDecomposeLoading(false);
    }
  };

  const handlePhaseConfirm = (phases: Phase[]) => {
    if (!pendingDecomposeConfig) return;
    const store = useWorkflowStore.getState();
    store.generatePhaseNodes({
      phases,
      decomposerNodeId: pendingDecomposeConfig.decomposerNodeId,
      llmConfigs: pendingDecomposeConfig.llmConfigs,
    });

    // Switch engine to LangGraph: decomposed workflows need conditional edges
    const currentEngine = useWorkflowStore.getState().engineType;
    if (currentEngine !== 'langgraph') {
      configApi.setEngineType('langgraph').catch(() => {});
      useWorkflowStore.getState().setEngineType('langgraph');
    }

    message.success(`已生成 ${phases.length} 个阶段节点`);
    setReviewModalOpen(false);
    setReviewPhases(null);
    setPendingDecomposeConfig(null);
  };

  const handlePhaseCancel = () => {
    setReviewModalOpen(false);
    setReviewPhases(null);
    setPendingDecomposeConfig(null);
  };

  const renderForm = () => {
    switch (selectedNode.type) {
      case 'llm':
        return (
          <>
            <Form.Item label="提供商" name="provider">
              <Input disabled />
            </Form.Item>
            <Form.Item label="API 地址" name="apiBaseUrl">
              <Input placeholder="例如: https://api.deepseek.com" />
            </Form.Item>
            <Form.Item label="API 密钥" name="apiKey">
              <Input.Password placeholder="sk-xxxxxxxx" />
            </Form.Item>
            <Form.Item label="模型" name="model">
              <Input placeholder="例如: deepseek-chat, qwen-turbo" />
            </Form.Item>
            <Form.Item label="提示词" name="prompt">
              <TextArea rows={4} placeholder="使用 {{nodeId.output}} 引用上游输出" />
            </Form.Item>
            <Form.Item label="Temperature" name="temperature">
              <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="最大 Token 数" name="maxTokens">
              <InputNumber min={1} max={8192} style={{ width: '100%' }} />
            </Form.Item>
          </>
        );
      case 'tts':
        return (
          <>
            <Form.Item label="API 密钥" name="apiKey">
              <Input.Password placeholder="sk-xxxxxxxx" />
            </Form.Item>
            <Form.Item label="模型" name="model">
              <Input placeholder="qwen3-tts-flash" />
            </Form.Item>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              输入参数
            </Divider>
            <Form.List name="inputs">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name }) => (
                    <div
                      key={key}
                      style={{
                        marginBottom: 12,
                        padding: 12,
                        background: '#fafafa',
                        borderRadius: 8,
                        position: 'relative',
                      }}
                    >
                      <Form.Item
                        label="参数名"
                        name={[name, 'paramName']}
                        rules={[{ required: true, message: '必填' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select>
                          <Select.Option value="text">text</Select.Option>
                          <Select.Option value="voice">voice</Select.Option>
                          <Select.Option value="language_type">language_type</Select.Option>
                        </Select>
                      </Form.Item>
                      <Form.Item
                        label="参数类型"
                        name={[name, 'paramType']}
                        rules={[{ required: true }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select>
                          <Select.Option value="input">输入</Select.Option>
                          <Select.Option value="reference">引用</Select.Option>
                        </Select>
                      </Form.Item>
                      <Form.Item
                        noStyle
                        shouldUpdate={(prev, cur) => {
                          const pn = prev.inputs?.[name]?.paramName !== cur.inputs?.[name]?.paramName;
                          const pt = prev.inputs?.[name]?.paramType !== cur.inputs?.[name]?.paramType;
                          return pn || pt;
                        }}
                      >
                        {({ getFieldValue }) => {
                          const paramType = getFieldValue(['inputs', name, 'paramType']);
                          const paramName = getFieldValue(['inputs', name, 'paramName']);
                          if (paramType === 'input') {
                            if (paramName === 'voice') {
                              return (
                                <Form.Item label="值" name={[name, 'value']} style={{ marginBottom: 0 }}>
                                  <Select>
                                    <Select.Option value="Cherry">Cherry</Select.Option>
                                    <Select.Option value="Serena">Serena</Select.Option>
                                    <Select.Option value="Ethan">Ethan</Select.Option>
                                  </Select>
                                </Form.Item>
                              );
                            }
                            if (paramName === 'language_type') {
                              return (
                                <Form.Item label="值" name={[name, 'value']} style={{ marginBottom: 0 }}>
                                  <Select>
                                    <Select.Option value="Auto">Auto</Select.Option>
                                  </Select>
                                </Form.Item>
                              );
                            }
                            return (
                              <Form.Item label="值" name={[name, 'value']} style={{ marginBottom: 0 }}>
                                <TextArea rows={2} placeholder="输入要合成的文本..." />
                              </Form.Item>
                            );
                          }
                          return (
                            <Form.Item label="引用" name={[name, 'value']} style={{ marginBottom: 0 }}>
                              {upstreamRefs.length > 0 ? (
                                <Select placeholder="选择上游节点..." allowClear>
                                  {upstreamRefs.map((ref) => (
                                    <Select.Option key={ref.value} value={ref.value}>
                                      {ref.label}
                                    </Select.Option>
                                  ))}
                                </Select>
                              ) : (
                                <Input placeholder="例如: node_1.output" />
                              )}
                            </Form.Item>
                          );
                        }}
                      </Form.Item>
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        size="small"
                        onClick={() => remove(name)}
                        style={{ position: 'absolute', top: 4, right: 4 }}
                      />
                    </div>
                  ))}
                  <Button
                    type="dashed"
                    onClick={() => add({ paramName: 'text', paramType: 'reference', value: '' })}
                    block
                    icon={<PlusOutlined />}
                  >
                  添加参数
                  </Button>
                </>
              )}
            </Form.List>
          </>
        );
      case 'input':
        return (
          <>
            <Form.Item label="变量名" name="variableName">
              <Input placeholder="output" />
            </Form.Item>
            <Form.Item label="变量类型" name="variableType">
              <Input disabled value="String" />
            </Form.Item>
            <Form.Item label="描述" name="description">
              <Input placeholder="用户本轮的输入内容" />
            </Form.Item>
            <Form.Item label="必填" name="required" valuePropName="checked">
              <Checkbox />
            </Form.Item>
          </>
        );
      case 'output':
        return (
          <>
            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              输出配置
            </Divider>
            <Form.List name="outputs">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name }) => (
                    <div
                      key={key}
                      style={{
                        marginBottom: 12,
                        padding: 12,
                        background: '#fafafa',
                        borderRadius: 8,
                        position: 'relative',
                      }}
                    >
                      <Form.Item
                        label="参数名"
                        name={[name, 'paramName']}
                        rules={[{ required: true, message: '必填' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Input placeholder="例如: text, audioUrl" />
                      </Form.Item>
                      <Form.Item
                        label="参数类型"
                        name={[name, 'paramType']}
                        rules={[{ required: true }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select>
                          <Select.Option value="input">输入</Select.Option>
                          <Select.Option value="reference">引用</Select.Option>
                        </Select>
                      </Form.Item>
                      <Form.Item
                        noStyle
                        shouldUpdate={(prev, cur) =>
                          prev.outputs?.[name]?.paramType !== cur.outputs?.[name]?.paramType
                        }
                      >
                        {({ getFieldValue }) => {
                          const paramType = getFieldValue(['outputs', name, 'paramType']);
                          const label = paramType === 'reference' ? '节点引用' : '值';
                          const placeholder =
                            paramType === 'reference'
                              ? '例如: llm_2.output'
                              : '手动输入值';
                          return (
                            <Form.Item
                              label={label}
                              name={[name, 'value']}
                              style={{ marginBottom: 0 }}
                            >
                              <Input placeholder={placeholder} />
                            </Form.Item>
                          );
                        }}
                      </Form.Item>
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        size="small"
                        onClick={() => remove(name)}
                        style={{ position: 'absolute', top: 4, right: 4 }}
                      />
                    </div>
                  ))}
                  <Button
                    type="dashed"
                    onClick={() => add({ paramName: '', paramType: 'reference', value: '' })}
                    block
                    icon={<PlusOutlined />}
                  >
                    添加输出
                  </Button>
                </>
              )}
            </Form.List>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999', marginTop: 16 }}>
              回答内容配置
            </Divider>
            <Form.Item
              name="responseTemplate"
              extra="用法: 使用 {{paramName}} 格式引用上述输出配置参数"
            >
              <TextArea
                rows={4}
                placeholder={`### Answer\n\n{{text}}\n\n### Audio\n\n{{audioUrl}}`}
              />
            </Form.Item>
          </>
        );
      case 'condition':
        return (
          <>
            <Form.Item label="左侧引用" name="leftRef" extra="引用上游节点输出进行比较">
              {upstreamRefs.length > 0 ? (
                <Select placeholder="选择上游节点..." allowClear>
                  {upstreamRefs.map((ref) => (
                    <Select.Option key={ref.value} value={ref.value}>
                      {ref.label}
                    </Select.Option>
                  ))}
                </Select>
              ) : (
                <Input placeholder="例如: llm_2.output" />
              )}
            </Form.Item>
            <Form.Item label="运算符" name="operator" rules={[{ required: true }]}>
              <Select>
                <Select.Option value="equals">等于 (==)</Select.Option>
                <Select.Option value="not_equals">不等于 (!=)</Select.Option>
                <Select.Option value="contains">包含</Select.Option>
                <Select.Option value="not_contains">不包含</Select.Option>
                <Select.Option value="starts_with">开头是</Select.Option>
                <Select.Option value="is_empty">为空</Select.Option>
                <Select.Option value="is_not_empty">非空</Select.Option>
                <Select.Option value="greater_than">大于 (&gt;)</Select.Option>
                <Select.Option value="less_than">小于 (&lt;)</Select.Option>
                <Select.Option value="greater_or_equal">大于等于 (&gt;=)</Select.Option>
                <Select.Option value="less_or_equal">小于等于 (&lt;=)</Select.Option>
                <Select.Option value="matches_regex">正则匹配</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item
              noStyle
              shouldUpdate={(prev, cur) => prev.operator !== cur.operator}
            >
              {({ getFieldValue }) => {
                const operator = getFieldValue('operator') || 'contains';
                if (operator === 'is_empty' || operator === 'is_not_empty') {
                  return null;
                }
                const placeholders: Record<string, string> = {
                  matches_regex: '输入正则表达式，例如: \\d{3}-\\d{4}',
                  greater_than: '输入数值',
                  less_than: '输入数值',
                  greater_or_equal: '输入数值',
                  less_or_equal: '输入数值',
                };
                return (
                  <Form.Item label="右侧值" name="rightValue" extra="与左侧引用比较的值">
                    <Input placeholder={placeholders[operator] || '输入比较值...'} />
                  </Form.Item>
                );
              }}
            </Form.Item>
          </>
        );
      case 'decomposer':
        return (
          <>
            <Form.Item label="API 地址" name="apiBaseUrl">
              <Input placeholder="https://api.deepseek.com" />
            </Form.Item>
            <Form.Item label="API 密钥" name="apiKey">
              <Input.Password placeholder="sk-xxxxxxxx" />
            </Form.Item>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              分解任务
            </Divider>
            <Form.Item
              label="任务描述"
              name="taskDescription"
              rules={[{ required: true, message: '请输入任务描述' }]}
              extra="描述需要AI完成的总任务，支持 {{nodeId.field}} 引用上游"
            >
              <TextArea rows={4} placeholder="例如：撰写一篇关于AI发展的技术博客..." />
            </Form.Item>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              Worker AI 配置
            </Divider>
            <Form.Item label="提供商" name="workerProvider">
              <Select>
                <Select.Option value="deepseek">DeepSeek</Select.Option>
                <Select.Option value="qwen">通义千问</Select.Option>
                <Select.Option value="chatglm">智谱</Select.Option>
                <Select.Option value="aiping">AI Ping</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item label="模型" name="workerModel">
              <Input placeholder="deepseek-chat" />
            </Form.Item>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              Judge AI 配置
            </Divider>
            <Form.Item label="提供商" name="judgeProvider">
              <Select>
                <Select.Option value="deepseek">DeepSeek</Select.Option>
                <Select.Option value="qwen">通义千问</Select.Option>
                <Select.Option value="chatglm">智谱</Select.Option>
                <Select.Option value="aiping">AI Ping</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item label="模型" name="judgeModel">
              <Input placeholder="deepseek-chat" />
            </Form.Item>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              Validator AI 配置
            </Divider>
            <Form.Item label="提供商" name="validatorProvider">
              <Select>
                <Select.Option value="deepseek">DeepSeek</Select.Option>
                <Select.Option value="qwen">通义千问</Select.Option>
                <Select.Option value="chatglm">智谱</Select.Option>
                <Select.Option value="aiping">AI Ping</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item label="模型" name="validatorModel">
              <Input placeholder="deepseek-chat" />
            </Form.Item>

            <Button
              type="primary"
              block
              onClick={handleDecompose}
              loading={decomposeLoading}
              style={{ marginTop: 8, background: '#722ed1', borderColor: '#722ed1' }}
            >
              {decomposeLoading ? 'AI 分解中...' : '🧩 智能分解'}
            </Button>
          </>
        );
      case 'judge':
        return (
          <>
            <Form.Item label="API 地址" name="apiBaseUrl">
              <Input placeholder="https://api.deepseek.com" />
            </Form.Item>
            <Form.Item label="API 密钥" name="apiKey">
              <Input.Password placeholder="sk-xxxxxxxx" />
            </Form.Item>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>
              AI 判断
            </Divider>
            <Form.Item label="提供商" name="provider">
              <Select>
                <Select.Option value="deepseek">DeepSeek</Select.Option>
                <Select.Option value="qwen">通义千问</Select.Option>
                <Select.Option value="chatglm">智谱</Select.Option>
                <Select.Option value="aiping">AI Ping</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item label="模型" name="model">
              <Input placeholder="deepseek-chat" />
            </Form.Item>
            <Form.Item label="上游引用" name="leftRef" extra="引用 Worker 节点的输出进行判断">
              {upstreamRefs.length > 0 ? (
                <Select placeholder="选择上游 Worker..." allowClear>
                  {upstreamRefs.map((ref) => (
                    <Select.Option key={ref.value} value={ref.value}>
                      {ref.label}
                    </Select.Option>
                  ))}
                </Select>
              ) : (
                <Input placeholder="例如: worker_1.output" />
              )}
            </Form.Item>
            <Form.Item
              label="判断标准"
              name="criteria"
              rules={[{ required: true, message: '请输入判断标准' }]}
              extra="AI 将根据此标准评估 Worker 输出是否合格"
            >
              <TextArea rows={4} placeholder="例如：输出应包含完整的技术方案、风险评估和实施步骤" />
            </Form.Item>
            <Form.Item label="最大重试" name="maxRetries" extra="不通过时最多重试次数">
              <InputNumber min={1} max={10} style={{ width: '100%' }} />
            </Form.Item>
          </>
        );
      default:
        return null;
    }
  };

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>节点配置</h3>
      <Divider />
      <div style={{ marginBottom: 12 }}>
        <div style={{ fontSize: 12, color: '#999' }}>节点 ID</div>
        <div style={{ fontWeight: 600 }}>{selectedNode.id}</div>
      </div>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12, color: '#999' }}>节点类型</div>
        <div style={{ fontWeight: 600 }}>{selectedNode.type}</div>
      </div>
      <Divider />
      <Form form={form} layout="vertical" size="small">
        <Form.Item label="标签" name="label">
          <Input />
        </Form.Item>
        {renderForm()}
      </Form>
      <Button type="primary" block onClick={handleSave}>
        保存配置
      </Button>
      {reviewPhases && (
        <PhaseReviewModal
          open={reviewModalOpen}
          phases={reviewPhases}
          onConfirm={handlePhaseConfirm}
          onCancel={handlePhaseCancel}
        />
      )}
    </div>
  );
}
