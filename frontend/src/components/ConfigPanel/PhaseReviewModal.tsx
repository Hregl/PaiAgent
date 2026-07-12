import { Modal, Form, Input, Button, Space, message } from 'antd';
import { PlusOutlined, DeleteOutlined, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import { Phase } from '../../types/workflow';

const { TextArea } = Input;

interface PhaseReviewModalProps {
  open: boolean;
  phases: Phase[];
  onConfirm: (phases: Phase[]) => void;
  onCancel: () => void;
}

export default function PhaseReviewModal({ open, phases: initialPhases, onConfirm, onCancel }: PhaseReviewModalProps) {
  const [form] = Form.useForm();

  const handleOk = () => {
    form.validateFields().then((values) => {
      const updatedPhases = values.phases as Phase[];
      // Validate each phase has required fields
      const invalid = updatedPhases.some(
        (p) => !p.name?.trim() || !p.description?.trim() || !p.criteria?.trim()
      );
      if (invalid) {
        message.warning('每个阶段必须填写名称、描述和完成标准');
        return;
      }
      onConfirm(updatedPhases);
    });
  };

  const handleAdd = (add: (defaultValue: Phase, insertIndex?: number) => void, index: number) => {
    const nextIndex = index + 1;
    add({ name: '新阶段', description: '', criteria: '' }, nextIndex);
  };

  return (
    <Modal
      title="审查任务分解结果"
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      okText="确认生成"
      cancelText="取消"
      width={740}
      style={{ top: 20 }}
    >
      <p style={{ color: '#666', marginBottom: 16, fontSize: 13 }}>
        AI 已将任务分解为以下阶段。你可以修改每个阶段的内容，或增删／调整顺序，确认后自动生成工作流图。
      </p>
      <Form
        form={form}
        initialValues={{ phases: initialPhases }}
        layout="vertical"
      >
        <Form.List name="phases">
          {(fields, { add, remove, move }) => (
            <>
              {fields.map(({ key, name }, index) => (
                <div
                  key={key}
                  style={{
                    marginBottom: 16,
                    padding: 16,
                    background: '#fafafa',
                    borderRadius: 8,
                    border: '1px solid #e8e8e8',
                    position: 'relative',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <strong style={{ color: '#5b21b6' }}>阶段 {index + 1}</strong>
                    <Space size="small">
                      <Button
                        type="text"
                        size="small"
                        icon={<ArrowUpOutlined />}
                        disabled={index === 0}
                        onClick={() => move(index, index - 1)}
                      />
                      <Button
                        type="text"
                        size="small"
                        icon={<ArrowDownOutlined />}
                        disabled={index === fields.length - 1}
                        onClick={() => move(index, index + 1)}
                      />
                      <Button
                        type="text"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        disabled={fields.length <= 1}
                        onClick={() => remove(name)}
                      />
                    </Space>
                  </div>
                  <Form.Item
                    name={[name, 'name']}
                    rules={[{ required: true, message: '请输入阶段名称' }]}
                    style={{ marginBottom: 8 }}
                  >
                    <Input placeholder="阶段名称" size="small" />
                  </Form.Item>
                  <Form.Item
                    name={[name, 'description']}
                    rules={[{ required: true, message: '请输入阶段描述（Worker 执行指令）' }]}
                    style={{ marginBottom: 8 }}
                  >
                    <TextArea placeholder="Worker 执行指令（该阶段 AI 需要完成什么）" rows={2} size="small" />
                  </Form.Item>
                  <Form.Item
                    name={[name, 'criteria']}
                    rules={[{ required: true, message: '请输入完成标准' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <TextArea
                      placeholder="完成标准（Judge 用来判断该阶段是否达标的条件）"
                      rows={2}
                      size="small"
                    />
                  </Form.Item>
                  <Button
                    type="dashed"
                    size="small"
                    icon={<PlusOutlined />}
                    block
                    style={{ marginTop: 8 }}
                    onClick={() => handleAdd(add, index)}
                  >
                    在此之后插入阶段
                  </Button>
                </div>
              ))}
              {fields.length === 0 && (
                <Button
                  type="dashed"
                  onClick={() => add({ name: '新阶段', description: '', criteria: '' })}
                  block
                  icon={<PlusOutlined />}
                >
                  添加阶段
                </Button>
              )}
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}
