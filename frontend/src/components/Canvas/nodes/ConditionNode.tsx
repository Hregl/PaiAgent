import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { ConditionNodeData } from '../../../types/workflow';

const operatorLabels: Record<string, string> = {
  equals: '==',
  not_equals: '!=',
  contains: '包含',
  starts_with: '开头是',
  is_empty: '为空',
  is_not_empty: '非空',
  greater_than: '>',
  less_than: '<',
  greater_or_equal: '>=',
  less_or_equal: '<=',
  matches_regex: '正则匹配',
  not_contains: '不包含',
};

function ConditionNode({ data, selected }: NodeProps<ConditionNodeData>) {
  const opLabel = operatorLabels[data.operator] || data.operator;
  return (
    <div className={`custom-node condition-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div className="condition-body">
        <div className="condition-label">{data.label || '判断'}</div>
        <div className="condition-operator">{opLabel}</div>
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

export default memo(ConditionNode);
