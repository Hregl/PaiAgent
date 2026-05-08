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
import { CustomNodeData, LLMProvider } from '../../types/workflow';

const nodeTypes: NodeTypes = {
  input: InputNode,
  output: OutputNode,
  llm: LLMNode,
  tts: ToolNode,
};

let nodeId = 0;
const getNewNodeId = () => `node_${++nodeId}`;

export default function WorkflowCanvas() {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const reactFlowInstance = useRef<ReactFlowInstance | null>(null);

  const { nodes, edges, onNodesChange, onEdgesChange, onConnect, addNode, selectNode } =
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
    case 'llm':
      return {
        label: config.label,
        provider: config.subtype as LLMProvider,
        model: '',
        prompt: '{{input.output}}',
        temperature: 0.7,
        maxTokens: 2048,
      };
    case 'tts':
      return {
        label: config.label,
        voiceId: 'zhiyan',
        inputRef: '',
      };
    case 'input':
      return {
        label: 'Input',
        variableName: 'user_input',
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
    default:
      return { label: config.label };
  }
}
