import { useWorkflowStore } from '../../store/workflowStore';
import { Form, Input, Select, InputNumber, Button, Divider, message } from 'antd';
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
          <Form.Item label="Variable Name" name="variableName">
            <Input placeholder="input" />
          </Form.Item>
        );
      case 'output':
        return (
          <>
            <Form.Item label="Output Reference" name={['outputs', 0, 'ref']}>
              <Input placeholder="e.g. node_3.audioUrl" />
            </Form.Item>
            <Form.Item label="Response Template" name="responseTemplate">
              <TextArea rows={3} placeholder="{{output}}" />
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
