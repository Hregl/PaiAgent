import React, { useCallback, useRef, DragEvent, useMemo } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  ReactFlowInstance,
  NodeTypes,
  Node,
  MarkerType,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { useWorkflowStore } from '../../store/workflowStore';
import InputNode from './nodes/InputNode';
import OutputNode from './nodes/OutputNode';
import LLMNode from './nodes/LLMNode';
import ToolNode from './nodes/ToolNode';
import ConditionNode from './nodes/ConditionNode';
import DecomposerNode from './nodes/DecomposerNode';
import JudgeNode from './nodes/JudgeNode';
import { CustomNodeData, LLMProvider } from '../../types/workflow';

const nodeTypes: NodeTypes = {
  input: InputNode,
  output: OutputNode,
  llm: LLMNode,
  tts: ToolNode,
  condition: ConditionNode,
  decomposer: DecomposerNode,
  judge: JudgeNode,
};

let nodeId = 0;
const getNewNodeId = () => `node_${++nodeId}`;

export default function WorkflowCanvas() {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const reactFlowInstance = useRef<ReactFlowInstance | null>(null);

  const { nodes, edges, onNodesChange, onEdgesChange, onConnect, addNode, selectNode, pushHistory } =
    useWorkflowStore();

  const onInit = useCallback((instance: ReactFlowInstance) => {
    reactFlowInstance.current = instance;
  }, []);

  const onDragOver = useCallback((event: DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: DragEvent) => {
      event.preventDefault();

      const data = event.dataTransfer.getData('application/paiagent-node');
      if (!data || !reactFlowInstance.current || !reactFlowWrapper.current) return;

      const nodeConfig = JSON.parse(data);
      const bounds = reactFlowWrapper.current.getBoundingClientRect();
      const position = reactFlowInstance.current.project({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top,
      });

      const newNode: Node<CustomNodeData> = {
        id: getNewNodeId(),
        type: nodeConfig.type,
        position,
        data: getDefaultNodeData(nodeConfig) as CustomNodeData,
      };

      addNode(newNode);
    },
    [addNode]
  );

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: { id: string }) => {
      selectNode(node.id);
    },
    [selectNode]
  );

  const onNodeDragStart = useCallback(() => {
    pushHistory();
  }, [pushHistory]);

  const onPaneClick = useCallback(() => {
    selectNode(null);
  }, [selectNode]);

  const defaultEdgeOptions = useMemo(
    () => ({
      type: 'smoothstep' as const,
      animated: true,
      markerEnd: { type: MarkerType.ArrowClosed, width: 20, height: 20, color: '#999' },
    }),
    []
  );

  return (
    <div ref={reactFlowWrapper} style={{ width: '100%', height: '100%' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onInit={onInit}
        onDragOver={onDragOver}
        onDrop={onDrop}
        onNodeClick={onNodeClick}
        onNodeDragStart={onNodeDragStart}
        onPaneClick={onPaneClick}
        nodeTypes={nodeTypes}
        fitView
        defaultEdgeOptions={defaultEdgeOptions}
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
}

function getDefaultNodeData(config: { type: string; subtype: string; label: string }) {
  switch (config.type) {
    case 'llm': {
      const defaults: Record<string, { model: string; apiBaseUrl: string }> = {
        deepseek: { model: 'deepseek-chat', apiBaseUrl: 'https://api.deepseek.com' },
        qwen: { model: 'qwen-turbo', apiBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
        chatglm: { model: 'glm-4-flash', apiBaseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
        aiping: { model: '', apiBaseUrl: '' },
      };
      const d = defaults[config.subtype] || { model: '', apiBaseUrl: '' };
      return {
        label: config.label,
        provider: config.subtype as LLMProvider,
        model: d.model,
        apiBaseUrl: d.apiBaseUrl,
        apiKey: '',
        prompt: '{{input.output}}',
        temperature: 0.7,
        maxTokens: 2048,
      };
    }
    case 'tts':
      return {
        label: config.label,
        apiKey: '',
        model: 'qwen3-tts-flash',
        inputs: [
          { paramName: 'text', paramType: 'reference' as const, value: '' },
          { paramName: 'voice', paramType: 'input' as const, value: 'Cherry' },
          { paramName: 'language_type', paramType: 'input' as const, value: 'Auto' },
        ],
      };
    case 'input':
      return {
        label: 'Input',
        variableName: 'output',
        variableType: 'String',
        description: '用户本轮的输入内容',
        required: true,
      };
    case 'output':
      return {
        label: 'Output',
        outputs: [{ paramName: 'output', paramType: 'reference' as const, value: '' }],
        responseTemplate: '{{output}}',
      };
    case 'condition':
      return {
        label: '判断',
        leftRef: '',
        operator: 'contains',
        rightValue: '',
      };
    case 'decomposer':
      return {
        label: '任务分解器',
        taskDescription: '',
        apiKey: '',
        apiBaseUrl: '',
        workerProvider: 'deepseek' as LLMProvider,
        workerModel: 'deepseek-chat',
        judgeProvider: 'deepseek' as LLMProvider,
        judgeModel: 'deepseek-chat',
        validatorProvider: 'deepseek' as LLMProvider,
        validatorModel: 'deepseek-chat',
      };
    case 'judge':
      return {
        label: 'AI 判断',
        provider: 'deepseek' as LLMProvider,
        model: 'deepseek-chat',
        apiKey: '',
        apiBaseUrl: '',
        leftRef: '',
        criteria: '',
        temperature: 0.1,
        maxTokens: 256,
        maxRetries: 3,
      };
    default:
      return { label: config.label };
  }
}
