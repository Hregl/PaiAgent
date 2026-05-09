import { create } from 'zustand';
import {
  Node,
  Edge,
  OnNodesChange,
  OnEdgesChange,
  OnConnect,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  Connection,
} from 'reactflow';
import { CustomNodeData, LLMProvider } from '../types/workflow';

interface WorkflowState {
  nodes: Node<CustomNodeData>[];
  edges: Edge[];
  selectedNodeId: string | null;
  workflowId: string | null;
  workflowName: string;

  setNodes: (nodes: Node<CustomNodeData>[]) => void;
  setEdges: (edges: Edge[]) => void;
  onNodesChange: OnNodesChange;
  onEdgesChange: OnEdgesChange;
  onConnect: OnConnect;
  addNode: (node: Node<CustomNodeData>) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<CustomNodeData>) => void;
  selectNode: (id: string | null) => void;
  setWorkflowId: (id: string | null) => void;
  setWorkflowName: (name: string) => void;
  resetWorkflow: () => void;
}

let nodeCounter = 0;

function createDefaultNodes(): [Node<CustomNodeData>[], Edge[]] {
  const inputId = `input_${++nodeCounter}`;
  const llmId = `llm_${++nodeCounter}`;
  const ttsId = `tts_${++nodeCounter}`;
  const outputId = `output_${++nodeCounter}`;

  const inputNode: Node<CustomNodeData> = {
    id: inputId,
    type: 'input',
    position: { x: 80, y: 100 },
    data: { label: 'Input', variableName: 'output', variableType: 'String', description: '用户本轮的输入内容', required: true },
  };

  const llmNode: Node<CustomNodeData> = {
    id: llmId,
    type: 'llm',
    position: { x: 340, y: 100 },
    data: {
      label: 'DeepSeek',
      provider: 'deepseek' as LLMProvider,
      model: 'deepseek-chat',
      apiBaseUrl: 'https://api.deepseek.com',
      apiKey: '',
      prompt: `基于以下内容生成一段播客脚本，要求口语化、有吸引力：\n\n{{${inputId}.output}}`,
      temperature: 0.7,
      maxTokens: 2048,
    },
  };

  const ttsNode: Node<CustomNodeData> = {
    id: ttsId,
    type: 'tts',
    position: { x: 600, y: 100 },
    data: {
      label: '超拟人音频合成',
      voiceId: 'zhiyan',
      inputRef: `${llmId}.output`,
    },
  };

  const outputNode: Node<CustomNodeData> = {
    id: outputId,
    type: 'output',
    position: { x: 860, y: 100 },
    data: {
      label: 'Output',
      outputs: [
        { paramName: 'text', paramType: 'reference' as const, value: `${llmId}.output` },
        { paramName: 'audioUrl', paramType: 'reference' as const, value: `${ttsId}.audioUrl` },
      ],
      responseTemplate: '### 播客脚本\n\n{{text}}\n\n### 音频\n\n{{audioUrl}}',
    },
  };

  const edges: Edge[] = [
    { id: `${inputId}->${llmId}`, source: inputId, target: llmId, type: 'smoothstep', animated: true },
    { id: `${llmId}->${ttsId}`, source: llmId, target: ttsId, type: 'smoothstep', animated: true },
    { id: `${ttsId}->${outputId}`, source: ttsId, target: outputId, type: 'smoothstep', animated: true },
  ];

  return [[inputNode, llmNode, ttsNode, outputNode], edges];
}

const [initialNodes, initialEdges] = createDefaultNodes();

export const useWorkflowStore = create<WorkflowState>((set, get) => ({
  nodes: initialNodes,
  edges: initialEdges,
  selectedNodeId: null,
  workflowId: null,
  workflowName: '',

  setNodes: (nodes) => set({ nodes }),
  setEdges: (edges) => set({ edges }),

  onNodesChange: (changes) => {
    // Block deletion of required input/output nodes
    const filteredChanges = changes.filter((change) => {
      if (change.type === 'remove') {
        const node = get().nodes.find((n) => n.id === change.id);
        if (node && (node.type === 'input' || node.type === 'output')) {
          return false;
        }
      }
      return true;
    });
    set({ nodes: applyNodeChanges(filteredChanges, get().nodes) });
  },

  onEdgesChange: (changes) => {
    set({ edges: applyEdgeChanges(changes, get().edges) });
  },

  onConnect: (connection: Connection) => {
    set({ edges: addEdge(connection, get().edges) });
  },

  addNode: (node) => {
    set({ nodes: [...get().nodes, node] });
  },

  removeNode: (id) => {
    set({
      nodes: get().nodes.filter((n) => n.id !== id),
      edges: get().edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeId: get().selectedNodeId === id ? null : get().selectedNodeId,
    });
  },

  updateNodeData: (id, data) => {
    set({
      nodes: get().nodes.map((node) =>
        node.id === id ? { ...node, data: { ...node.data, ...data } } : node
      ),
    });
  },

  selectNode: (id) => set({ selectedNodeId: id }),

  setWorkflowId: (id) => set({ workflowId: id }),
  setWorkflowName: (name) => set({ workflowName: name }),

  resetWorkflow: () => {
    const [nodes, edges] = createDefaultNodes();
    set({
      nodes,
      edges,
      selectedNodeId: null,
      workflowId: null,
      workflowName: '',
    });
  },
}));
