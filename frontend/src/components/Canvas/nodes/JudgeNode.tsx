import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { JudgeNodeData } from '../../../types/workflow';

function JudgeNode({ data, selected }: NodeProps<JudgeNodeData>) {
  const criteriaPreview = data.criteria
    ? data.criteria.length > 40
      ? data.criteria.slice(0, 40) + '...'
      : data.criteria
    : '';

  return (
    <div className={`custom-node judge-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div className="judge-body">
        <div className="judge-icon">⚖️</div>
        <div className="judge-content">
          <div className="judge-label">{data.label || 'AI 判断'}</div>
          {criteriaPreview && (
            <div className="judge-criteria" title={data.criteria}>
              {criteriaPreview}
            </div>
          )}
          {!criteriaPreview && (
            <div className="judge-hint">点击配置判断标准</div>
          )}
        </div>
      </div>
      <Handle
        type="source"
        position={Position.Bottom}
        id="true"
        style={{ left: '25%' }}
        title="true"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="false"
        style={{ left: '75%' }}
        title="false"
      />
      <div className="condition-branch-labels">
        <span className="branch-true">T</span>
        <span className="branch-false">F</span>
      </div>
    </div>
  );
}

export default memo(JudgeNode);
