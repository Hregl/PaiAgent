import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { OutputNodeData } from '../../../types/workflow';

function OutputNode({ data, selected }: NodeProps<OutputNodeData>) {
  return (
    <div className={`custom-node output-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div>{data.label || '输出'}</div>
    </div>
  );
}

export default memo(OutputNode);
