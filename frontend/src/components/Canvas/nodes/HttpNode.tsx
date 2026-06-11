import { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { HttpNodeData } from '../../../types/workflow';

const methodColors: Record<string, string> = {
  GET: '#52c41a',
  POST: '#1890ff',
  PUT: '#faad14',
  DELETE: '#ff4d4f',
};

function HttpNode({ data, selected }: NodeProps<HttpNodeData>) {
  const method = data.method || 'GET';
  const color = methodColors[method] || '#999';
  const displayUrl = data.url ? (data.url.length > 35 ? data.url.substring(0, 35) + '...' : data.url) : '';

  return (
    <div className={`custom-node http-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Top} />
      <div className="http-body">
        <div className="http-label">
          <span className="http-method-badge" style={{ background: color }}>{method}</span>
          <span>HTTP 请求</span>
        </div>
        {displayUrl && <div className="http-url">{displayUrl}</div>}
      </div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

export default memo(HttpNode);
