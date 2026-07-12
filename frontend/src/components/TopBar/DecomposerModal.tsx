import { useState } from 'react';
import { Modal, Form, Input, Select, Button, Steps, Spin, message, Space, Divider } from 'antd';
import { ThunderboltOutlined, EditOutlined, CheckCircleOutlined, PlusOutlined, DeleteOutlined, ArrowUpOutlined, ArrowDownOutlined, BulbOutlined } from '@ant-design/icons';
import { Phase, LLMProvider, MODELS_BY_PROVIDER, DEFAULT_MODEL } from '../../types/workflow';
import { workflowApi } from '../../api/workflow';
import { configApi } from '../../api/config';
import { useWorkflowStore } from '../../store/workflowStore';
import { useDebugStore } from '../../store/debugStore';

const { TextArea } = Input;

interface DecomposerModalProps {
  open: boolean;
  onClose: () => void;
}

type Step = 'config' | 'loading' | 'review';

export default function DecomposerModal({ open, onClose }: DecomposerModalProps) {
  const [step, setStep] = useState<Step>('config');
  const [phases, setPhases] = useState<Phase[]>([]);
  const [form] = Form.useForm();
  const [expandingTopic, setExpandingTopic] = useState(false);
  const [reviewForm] = Form.useForm();

  const handleDecompose = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;

    if (!values.taskDescription?.trim()) {
      message.warning('请先填写任务描述');
      return;
    }

    setStep('loading');

    try {
      const res = await workflowApi.decompose({
        taskDescription: values.taskDescription,
        provider: values.workerProvider || 'deepseek',
        model: values.workerModel || 'deepseek-chat',
        apiKey: values.apiKey || '',
        apiBaseUrl: values.apiBaseUrl || '',
      });

      if (res.code !== 200 || !res.data?.phases?.length) {
        message.error(res.message || '分解失败，请重试');
        setStep('config');
        return;
      }

      setPhases(res.data.phases);
      // Update review form with the received phases
      reviewForm.setFieldsValue({ phases: res.data.phases });
      // Auto-fill debug input
      useDebugStore.getState().setInput(values.taskDescription);
      setStep('review');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '分解失败';
      message.error(msg);
      setStep('config');
    } finally {
      // step already set to 'review' on success, 'config' on error
    }
  };

  const handleExpandTopic = async () => {
    const topic = form.getFieldValue('topic');
    if (!topic?.trim()) {
      message.warning('请先输入任务主题');
      return;
    }

    const values = form.getFieldsValue();
    setExpandingTopic(true);
    try {
      const res = await workflowApi.generateDescription({
        topic: topic.trim(),
        provider: values.workerProvider || 'deepseek',
        model: values.workerModel || DEFAULT_MODEL.deepseek,
        apiKey: values.apiKey || '',
        apiBaseUrl: values.apiBaseUrl || '',
      });

      if (res.code === 200 && res.data?.description) {
        form.setFieldValue('taskDescription', res.data.description);
        message.success('已生成详细任务描述');
      } else {
        message.error(res.message || '生成失败');
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : '生成失败');
    } finally {
      setExpandingTopic(false);
    }
  };

  const handleConfirm = async () => {
    const values = await reviewForm.validateFields().catch(() => null);
    if (!values) return;

    const confirmedPhases = values.phases as Phase[];
    const invalid = confirmedPhases.some(
      (p) => !p.name?.trim() || !p.description?.trim() || !p.criteria?.trim()
    );
    if (invalid) {
      message.warning('每个阶段必须填写名称、描述和完成标准');
      return;
    }

    const configValues = form.getFieldsValue();
    const store = useWorkflowStore.getState();

    store.generatePhaseNodes({
      phases: confirmedPhases,
      llmConfigs: {
        workerProvider: (configValues.workerProvider || 'deepseek') as LLMProvider,
        workerModel: configValues.workerModel || 'deepseek-chat',
        judgeProvider: (configValues.judgeProvider || 'deepseek') as LLMProvider,
        judgeModel: configValues.judgeModel || 'deepseek-chat',
        validatorProvider: (configValues.validatorProvider || 'deepseek') as LLMProvider,
        validatorModel: configValues.validatorModel || 'deepseek-chat',
      },
      inheritedApiKey: configValues.apiKey || '',
      inheritedApiBaseUrl: configValues.apiBaseUrl || '',
    });

    if (store.engineType !== 'langgraph') {
      configApi.setEngineType('langgraph').catch(() => {});
      store.setEngineType('langgraph');
    }

    message.success(`已生成 ${confirmedPhases.length} 个阶段节点`);
    handleReset();
    onClose();
  };

  const handleReset = () => {
    setStep('config');
    setPhases([]);
    reviewForm.resetFields();
  };

  const handleCancel = () => {
    handleReset();
    onClose();
  };

  const handleAddPhase = (add: (defaultValue: Phase, insertIndex?: number) => void, index: number) => {
    add({ name: '新阶段', description: '', criteria: '' }, index + 1);
  };

  const renderConfig = () => (
    <Form form={form} layout="vertical" size="small" style={{ marginTop: 16 }}
      initialValues={{
        workerProvider: 'deepseek',
        workerModel: DEFAULT_MODEL.deepseek,
        judgeProvider: 'deepseek',
        judgeModel: DEFAULT_MODEL.deepseek,
        validatorProvider: 'deepseek',
        validatorModel: DEFAULT_MODEL.deepseek,
      }}
    >
      <Form.Item label="API 地址" name="apiBaseUrl">
        <Input placeholder="https://api.deepseek.com" />
      </Form.Item>
      <Form.Item label="API 密钥" name="apiKey">
        <Input.Password placeholder="sk-xxxxxxxx（留空使用全局配置）" />
      </Form.Item>

      <Form.Item
        label="任务描述"
        name="taskDescription"
        rules={[{ required: true, message: '请输入任务描述' }]}
        extra={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
            <span style={{ color: '#999' }}>写不出详细描述？</span>
            <Form.Item noStyle name="topic">
              <Input
                size="small"
                placeholder="输入主题，AI 帮你扩展..."
                style={{ width: 200 }}
                onPressEnter={handleExpandTopic}
              />
            </Form.Item>
            <Button
              size="small"
              type="link"
              icon={<BulbOutlined />}
              loading={expandingTopic}
              onClick={handleExpandTopic}
              style={{ padding: 0 }}
            >
              AI 生成描述
            </Button>
          </div>
        }
      >
        <TextArea rows={5} placeholder="例如：撰写一篇关于 AI Agent 技术发展趋势的深度分析报告，要求不少于 3000 字，包含技术原理、行业应用和发展预测三个部分" />
      </Form.Item>

      <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>Worker AI（执行各阶段任务）</Divider>
      <div style={{ display: 'flex', gap: 12 }}>
        <Form.Item label="提供商" name="workerProvider" style={{ flex: 1 }}>
          <Select>
            <Select.Option value="deepseek">DeepSeek</Select.Option>
            <Select.Option value="qwen">通义千问</Select.Option>
            <Select.Option value="chatglm">智谱 ChatGLM</Select.Option>
            <Select.Option value="aiping">AI Ping</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, cur) => prev.workerProvider !== cur.workerProvider}>
          {({ getFieldValue }) => {
            const p = (getFieldValue('workerProvider') || 'deepseek') as LLMProvider;
            return (
              <Form.Item label="模型" name="workerModel" style={{ flex: 1 }}>
                <Select placeholder="选择模型">
                  {MODELS_BY_PROVIDER[p].map(m => (
                    <Select.Option key={m.value} value={m.value} disabled={m.deprecated}>
                      {m.label}{m.deprecated ? ' (即将弃用)' : ''}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            );
          }}
        </Form.Item>
      </div>

      <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>Judge AI（评审各阶段输出）</Divider>
      <div style={{ display: 'flex', gap: 12 }}>
        <Form.Item label="提供商" name="judgeProvider" style={{ flex: 1 }}>
          <Select>
            <Select.Option value="deepseek">DeepSeek</Select.Option>
            <Select.Option value="qwen">通义千问</Select.Option>
            <Select.Option value="chatglm">智谱 ChatGLM</Select.Option>
            <Select.Option value="aiping">AI Ping</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, cur) => prev.judgeProvider !== cur.judgeProvider}>
          {({ getFieldValue }) => {
            const p = (getFieldValue('judgeProvider') || 'deepseek') as LLMProvider;
            return (
              <Form.Item label="模型" name="judgeModel" style={{ flex: 1 }}>
                <Select placeholder="选择模型">
                  {MODELS_BY_PROVIDER[p].map(m => (
                    <Select.Option key={m.value} value={m.value} disabled={m.deprecated}>
                      {m.label}{m.deprecated ? ' (即将弃用)' : ''}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            );
          }}
        </Form.Item>
      </div>

      <Divider orientation="left" plain style={{ fontSize: 12, color: '#999' }}>Validator AI（最终验证）</Divider>
      <div style={{ display: 'flex', gap: 12 }}>
        <Form.Item label="提供商" name="validatorProvider" style={{ flex: 1 }}>
          <Select>
            <Select.Option value="deepseek">DeepSeek</Select.Option>
            <Select.Option value="qwen">通义千问</Select.Option>
            <Select.Option value="chatglm">智谱 ChatGLM</Select.Option>
            <Select.Option value="aiping">AI Ping</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, cur) => prev.validatorProvider !== cur.validatorProvider}>
          {({ getFieldValue }) => {
            const p = (getFieldValue('validatorProvider') || 'deepseek') as LLMProvider;
            return (
              <Form.Item label="模型" name="validatorModel" style={{ flex: 1 }}>
                <Select placeholder="选择模型">
                  {MODELS_BY_PROVIDER[p].map(m => (
                    <Select.Option key={m.value} value={m.value} disabled={m.deprecated}>
                      {m.label}{m.deprecated ? ' (即将弃用)' : ''}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            );
          }}
        </Form.Item>
      </div>
    </Form>
  );

  const renderLoading = () => (
    <div style={{ textAlign: 'center', padding: '60px 0' }}>
      <Spin size="large" />
      <div style={{ marginTop: 16, color: '#666', fontSize: 14 }}>
        AI 正在分析任务并拆解为执行阶段...
      </div>
      <div style={{ marginTop: 8, color: '#999', fontSize: 12 }}>
        这可能需要 10-30 秒，请耐心等待
      </div>
    </div>
  );

  const renderReview = () => (
    <>
      <p style={{ color: '#666', marginBottom: 16, fontSize: 13 }}>
        AI 已将任务分解为以下阶段。你可以修改内容、增删或调整顺序，确认后自动生成工作流。
      </p>
      <Form
        form={reviewForm}
        layout="vertical"
        style={{ maxHeight: '55vh', overflow: 'auto', paddingRight: 4 }}
      >
        <Form.List name="phases">
          {(fields, { add, remove, move }) => (
            <>
              {fields.map(({ key, name }, index) => (
                <div
                  key={key}
                  style={{
                    marginBottom: 12,
                    padding: 14,
                    background: '#fafafa',
                    borderRadius: 8,
                    border: '1px solid #e8e8e8',
                    position: 'relative',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <strong style={{ color: '#5b21b6' }}>阶段 {index + 1}</strong>
                    <Space size="small">
                      <Button type="text" size="small" icon={<ArrowUpOutlined />}
                        disabled={index === 0} onClick={() => move(index, index - 1)} />
                      <Button type="text" size="small" icon={<ArrowDownOutlined />}
                        disabled={index === fields.length - 1} onClick={() => move(index, index + 1)} />
                      <Button type="text" danger size="small" icon={<DeleteOutlined />}
                        disabled={fields.length <= 1} onClick={() => remove(name)} />
                    </Space>
                  </div>
                  <Form.Item name={[name, 'name']} rules={[{ required: true, message: '必填' }]}
                    style={{ marginBottom: 6 }}>
                    <Input placeholder="阶段名称" size="small" />
                  </Form.Item>
                  <Form.Item name={[name, 'description']} rules={[{ required: true, message: '必填' }]}
                    style={{ marginBottom: 6 }}>
                    <TextArea placeholder="Worker 执行指令" rows={2} />
                  </Form.Item>
                  <Form.Item name={[name, 'criteria']} rules={[{ required: true, message: '必填' }]}
                    style={{ marginBottom: 0 }}>
                    <TextArea placeholder="完成标准（Judge 用此判断阶段是否达标）" rows={2} />
                  </Form.Item>
                  <Button type="dashed" size="small" icon={<PlusOutlined />} block
                    style={{ marginTop: 8 }}
                    onClick={() => handleAddPhase(add, index)}>
                    在此之后插入阶段
                  </Button>
                </div>
              ))}
              {fields.length === 0 && (
                <Button type="dashed" onClick={() => add({ name: '新阶段', description: '', criteria: '' })}
                  block icon={<PlusOutlined />}>添加阶段</Button>
              )}
            </>
          )}
        </Form.List>
      </Form>
    </>
  );

  const stepItems = [
    { title: '配置任务', icon: step === 'config' ? <EditOutlined /> : step === 'loading' ? undefined : <CheckCircleOutlined style={{ color: '#52c41a' }} /> },
    { title: 'AI 分解', status: step === 'loading' ? 'process' as const : step === 'review' ? 'finish' as const : 'wait' as const },
    { title: '审查确认', icon: step === 'review' ? <EditOutlined /> : undefined },
  ];

  return (
    <Modal
      title={
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <ThunderboltOutlined style={{ color: '#722ed1' }} />
          智能任务分解
        </span>
      }
      open={open}
      onCancel={handleCancel}
      width={760}
      style={{ top: 20 }}
      footer={
        step === 'config' ? (
          <>
            <Button onClick={handleCancel}>取消</Button>
            <Button type="primary" onClick={handleDecompose}
              style={{ background: '#722ed1', borderColor: '#722ed1' }}>
              🧩 智能分解
            </Button>
          </>
        ) : step === 'loading' ? null : (
          <>
            <Button onClick={() => { setStep('config'); }}>
              返回修改配置
            </Button>
            <Button type="primary" onClick={handleConfirm}
              style={{ background: '#52c41a', borderColor: '#52c41a' }}>
              确认生成 {phases.length} 个阶段节点
            </Button>
          </>
        )
      }
    >
      <Steps current={step === 'config' ? 0 : step === 'loading' ? 1 : 2}
        size="small" style={{ marginBottom: 8 }}
        items={stepItems} />

      {step === 'config' && renderConfig()}
      {step === 'loading' && renderLoading()}
      {step === 'review' && renderReview()}
    </Modal>
  );
}
