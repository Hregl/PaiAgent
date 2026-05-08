import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { TTSNodeData } from '../../../types/workflow';

function ToolNode({ data, selected }: NodeProps<TTSNodeData>) {
  return (
    <div className={`custom-node tts-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div>{data.label || 'Tool'}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

export default memo(ToolNode);
