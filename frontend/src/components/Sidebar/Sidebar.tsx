import { DragEvent } from 'react';
import { useWorkflowStore } from '../../store/workflowStore';

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

const conditionNode: NodeItemConfig = {
  type: 'condition', subtype: 'condition', label: '判断分支', icon: '⑂', color: '#fff7e6',
};

const decomposerNode: NodeItemConfig = {
  type: 'decomposer', subtype: 'decomposer', label: '任务分解器', icon: '🧩', color: '#f0f0ff',
};

const judgeNode: NodeItemConfig = {
  type: 'judge', subtype: 'judge', label: 'AI 判断', icon: '⚖️', color: '#fff0f6',
};

export default function Sidebar() {
  const engineType = useWorkflowStore((s) => s.engineType);

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

      <div className="sidebar-section">
        <div className="sidebar-section-title">🔀 控制流</div>
        {engineType === 'dag' && (
          <div style={{ fontSize: 11, color: '#faad14', marginBottom: 8, padding: '4px 8px', background: '#fffbe6', borderRadius: 4 }}>
            ⚠ 判断节点仅在 LangGraph 引擎下可执行
          </div>
        )}
        <div
          className="node-item"
          draggable
          onDragStart={(e) => onDragStart(e, conditionNode)}
        >
          <div className="node-item-icon" style={{ background: conditionNode.color }}>
            {conditionNode.icon}
          </div>
          <span>{conditionNode.label}</span>
        </div>
        <div
          className="node-item"
          draggable
          onDragStart={(e) => onDragStart(e, decomposerNode)}
        >
          <div className="node-item-icon" style={{ background: decomposerNode.color }}>
            {decomposerNode.icon}
          </div>
          <span>{decomposerNode.label}</span>
        </div>
        <div
          className="node-item"
          draggable
          onDragStart={(e) => onDragStart(e, judgeNode)}
        >
          <div className="node-item-icon" style={{ background: judgeNode.color }}>
            {judgeNode.icon}
          </div>
          <span>{judgeNode.label}</span>
        </div>
      </div>

      <div className="sidebar-hint">💡 拖拽节点到画布</div>
    </div>
  );
}
