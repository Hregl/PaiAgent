import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { DecomposerNodeData } from '../../../types/workflow';

function DecomposerNode({ data, selected }: NodeProps<DecomposerNodeData>) {
  const summary = data.taskDescription
    ? data.taskDescription.length > 40
      ? data.taskDescription.slice(0, 40) + '...'
      : data.taskDescription
    : '点击配置任务';

  return (
    <div className={`custom-node decomposer-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div className="decomposer-body">
        <div className="decomposer-icon">🧩</div>
        <div className="decomposer-content">
          <div className="decomposer-label">{data.label || '任务分解器'}</div>
          <div className="decomposer-summary">{summary}</div>
          {!data.taskDescription && (
            <div className="decomposer-hint">配置面板中填写任务描述后点击"智能分解"</div>
          )}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

export default memo(DecomposerNode);
