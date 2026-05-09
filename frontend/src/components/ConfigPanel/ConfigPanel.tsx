import { useWorkflowStore } from '../../store/workflowStore';
import { Form, Input, Select, InputNumber, Button, Divider, message, Checkbox } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useEffect } from 'react';
import { Node } from 'reactflow';
import { CustomNodeData } from '../../types/workflow';

const { TextArea } = Input;

export default function ConfigPanel() {
  const { nodes, selectedNodeId, updateNodeData } = useWorkflowStore();
  const [form] = Form.useForm();

  const selectedNode: Node<CustomNodeData> | undefined = nodes.find(
    (n) => n.id === selectedNodeId
  );

  useEffect(() => {
    if (selectedNode) {
      form.setFieldsValue(selectedNode.data);
    }
  }, [selectedNode, form]);

  if (!selectedNode) return null;

  const handleSave = () => {
    const values = form.getFieldsValue();
    updateNodeData(selectedNode.id, values);
    message.success('Configuration saved');
  };

  const renderForm = () => {
    switch (selectedNode.type) {
      case 'llm':
        return (
          <>
            <Form.Item label="Provider" name="provider">
              <Select>
                <Select.Option value="deepseek">DeepSeek</Select.Option>
                <Select.Option value="qwen">Qwen</Select.Option>
                <Select.Option value="chatglm">ChatGLM</Select.Option>
                <Select.Option value="aiping">AI Ping</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item label="Model" name="model">
              <Input placeholder="e.g. deepseek-chat, qwen-turbo" />
            </Form.Item>
            <Form.Item label="Prompt" name="prompt">
              <TextArea rows={4} placeholder="Use {{nodeId.output}} to reference" />
            </Form.Item>
            <Form.Item label="Temperature" name="temperature">
              <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Max Tokens" name="maxTokens">
              <InputNumber min={1} max={8192} style={{ width: '100%' }} />
            </Form.Item>
          </>
        );
      case 'tts':
        return (
          <>
            <Form.Item label="Voice ID" name="voiceId">
              <Select>
                <Select.Option value="zhiyan">Zhiyan (Female)</Select.Option>
                <Select.Option value="zhida">Zhida (Male)</Select.Option>
                <Select.Option value="zhimiao">Zhimiao (Child)</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item label="Input Reference" name="inputRef">
              <Input placeholder="e.g. node_2.output" />
            </Form.Item>
          </>
        );
      case 'input':
        return (
          <>
            <Form.Item label="Variable Name" name="variableName">
              <Input placeholder="output" />
            </Form.Item>
            <Form.Item label="Variable Type" name="variableType">
              <Input disabled value="String" />
            </Form.Item>
            <Form.Item label="Description" name="description">
              <Input placeholder="用户本轮的输入内容" />
            </Form.Item>
            <Form.Item label="Required" name="required" valuePropName="checked">
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
                        label="Parameter Name"
                        name={[name, 'paramName']}
                        rules={[{ required: true, message: 'Required' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Input placeholder="e.g. text, audioUrl" />
                      </Form.Item>
                      <Form.Item
                        label="Parameter Type"
                        name={[name, 'paramType']}
                        rules={[{ required: true }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select>
                          <Select.Option value="input">Input</Select.Option>
                          <Select.Option value="reference">Reference</Select.Option>
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
                          const label = paramType === 'reference' ? 'Node Reference' : 'Value';
                          const placeholder =
                            paramType === 'reference'
                              ? 'e.g. llm_2.output'
                              : 'Manual input value';
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
                    Add Output
                  </Button>
                </>
              )}
            </Form.List>

            <Divider orientation="left" plain style={{ fontSize: 12, color: '#999', marginTop: 16 }}>
              回答内容配置
            </Divider>
            <Form.Item
              name="responseTemplate"
              extra="Usage: Use the {{paramName}} format to reference the above output configuration parameters."
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
      <h3 style={{ marginBottom: 16 }}>Node Config</h3>
      <Divider />
      <div style={{ marginBottom: 12 }}>
        <div style={{ fontSize: 12, color: '#999' }}>Node ID</div>
        <div style={{ fontWeight: 600 }}>{selectedNode.id}</div>
      </div>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12, color: '#999' }}>Node Type</div>
        <div style={{ fontWeight: 600 }}>{selectedNode.type}</div>
      </div>
      <Divider />
      <Form form={form} layout="vertical" size="small">
        <Form.Item label="Label" name="label">
          <Input />
        </Form.Item>
        {renderForm()}
      </Form>
      <Button type="primary" block onClick={handleSave}>
        Save Config
      </Button>
    </div>
  );
}
