import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { InputNodeData } from '../../../types/workflow';

function InputNode({ data, selected }: NodeProps<InputNodeData>) {
  return (
    <div className={`custom-node input-node ${selected ? 'selected' : ''}`}>
      <div>{data.label || 'Input'}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

export default memo(InputNode);
