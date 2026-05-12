import { DragEvent } from 'react';

interface NodeItemConfig {
  type: string;
  subtype: string;
  label: string;
  icon: string;
  color: string;
}

const llmNodes: NodeItemConfig[] = [
  { type: 'llm', subtype: 'deepseek', label: 'DeepSeek', icon: '🧠', color: '#e8f4fd' },
  { type: 'llm', subtype: 'qwen', label: '通义千问', icon: '🌟', color: '#fff7e6' },
  { type: 'llm', subtype: 'aiping', label: 'AI Ping', icon: '🏓', color: '#fff1f0' },
  { type: 'llm', subtype: 'chatglm', label: '智谱', icon: '🎯', color: '#f6ffed' },
];

const toolNodes: NodeItemConfig[] = [
  { type: 'tts', subtype: 'tts', label: '超拟人音频合成', icon: '🎙️', color: '#f9f0ff' },
];

export default function Sidebar() {
  const onDragStart = (event: DragEvent, nodeConfig: NodeItemConfig) => {
    event.dataTransfer.setData('application/paiagent-node', JSON.stringify(nodeConfig));
    event.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div>
      <h3 style={{ fontSize: 16, marginBottom: 16, color: '#333' }}>节点库</h3>

      <div className="sidebar-section">
        <div className="sidebar-section-title">🧠 LLM 节点</div>
        {llmNodes.map((node) => (
          <div
            key={node.subtype}
            className="node-item"
            draggable
            onDragStart={(e) => onDragStart(e, node)}
          >
            <div className="node-item-icon" style={{ background: node.color }}>
              {node.icon}
            </div>
            <span>{node.label}</span>
          </div>
        ))}
      </div>

      <div className="sidebar-section">
        <div className="sidebar-section-title">🔧 工具节点</div>
        {toolNodes.map((node) => (
          <div
            key={node.subtype}
            className="node-item"
            draggable
            onDragStart={(e) => onDragStart(e, node)}
          >
            <div className="node-item-icon" style={{ background: node.color }}>
              {node.icon}
            </div>
            <span>{node.label}</span>
          </div>
        ))}
      </div>

      <div className="sidebar-hint">💡 拖拽节点到画布</div>
    </div>
  );
}
