import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { WebSearchNodeData } from '../../../types/workflow';

function WebSearchNode({ data, selected }: NodeProps<WebSearchNodeData>) {
  const displayQuery = data.query
    ? (data.query.length > 35 ? data.query.substring(0, 35) + '...' : data.query)
    : '';

  return (
    <div className={`custom-node websearch-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div className="websearch-body">
        <span className="websearch-icon">🔍</span>
        <div className="websearch-content">
          <div className="websearch-label">{data.label || 'Web 搜索'}</div>
          {displayQuery && <div className="websearch-query">{displayQuery}</div>}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

export default memo(WebSearchNode);
