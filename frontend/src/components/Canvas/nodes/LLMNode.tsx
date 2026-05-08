import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { LLMNodeData } from '../../../types/workflow';

function LLMNode({ data, selected }: NodeProps<LLMNodeData>) {
  return (
    <div className={`custom-node llm-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div>{data.label || 'LLM'}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

export default memo(LLMNode);
