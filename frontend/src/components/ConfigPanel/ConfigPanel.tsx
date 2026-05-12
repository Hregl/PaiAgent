import { useWorkflowStore } from '../../store/workflowStore';
import { Form, Input, Select, InputNumber, Button, Divider, message, Checkbox } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useEffect, useMemo } from 'react';
import { Node } from 'reactflow';
import { CustomNodeData } from '../../types/workflow';

const { TextArea } = Input;

export default function ConfigPanel() {
  const { nodes, edges, selectedNodeId, updateNodeData } = useWorkflowStore();
  const [form] = Form.useForm();

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
    </div>
  );
}
