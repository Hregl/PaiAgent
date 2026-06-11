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
import { CustomNodeData, LLMProvider, EngineType, Phase, DecomposerNodeData, WorkflowSnapshot } from '../types/workflow';

interface GeneratePhaseNodesParams {
  phases: Phase[];
  decomposerNodeId: string;
  llmConfigs: {
    workerProvider: LLMProvider;
    workerModel: string;
    judgeProvider: LLMProvider;
    judgeModel: string;
    validatorProvider: LLMProvider;
    validatorModel: string;
  };
}

interface WorkflowState {
  nodes: Node<CustomNodeData>[];
  edges: Edge[];
  selectedNodeId: string | null;
  workflowId: string | null;
  workflowName: string;
  engineType: EngineType;
  past: WorkflowSnapshot[];
  future: WorkflowSnapshot[];

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
  setEngineType: (type: EngineType) => void;
  resetWorkflow: () => void;
  generatePhaseNodes: (params: GeneratePhaseNodesParams) => void;
  pushHistory: () => void;
  undo: () => boolean;
  redo: () => boolean;
}

const MAX_HISTORY = 50;

function deepClone(obj: { nodes: Node<CustomNodeData>[]; edges: Edge[] }): WorkflowSnapshot {
  return JSON.parse(JSON.stringify(obj));
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
    data: { label: '输入', variableName: 'output', variableType: 'String', description: '用户本轮的输入内容', required: true },
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
      apiKey: '',
      model: 'qwen3-tts-flash',
      inputs: [
        { paramName: 'text', paramType: 'reference' as const, value: `${llmId}.output` },
        { paramName: 'voice', paramType: 'input' as const, value: 'Cherry' },
        { paramName: 'language_type', paramType: 'input' as const, value: 'Auto' },
      ],
    },
  };

  const outputNode: Node<CustomNodeData> = {
    id: outputId,
    type: 'output',
    position: { x: 860, y: 100 },
    data: {
      label: '输出',
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
  engineType: 'langgraph',
  past: [] as WorkflowSnapshot[],
  future: [] as WorkflowSnapshot[],

  pushHistory: () => {
    const { nodes, edges, past } = get();
    const snapshot = deepClone({ nodes, edges });
    // Truncate if over limit
    const updatedPast = past.length >= MAX_HISTORY ? past.slice(1).concat(snapshot) : [...past, snapshot];
    set({ past: updatedPast, future: [] });
  },

  undo: () => {
    const { past, nodes, edges } = get();
    if (past.length === 0) return false;
    const prev = past[past.length - 1];
    set({
      past: past.slice(0, -1),
      future: [{ nodes: deepClone({ nodes, edges }).nodes, edges: deepClone({ nodes, edges }).edges }, ...get().future],
      nodes: prev.nodes,
      edges: prev.edges,
    });
    return true;
  },

  redo: () => {
    const { future, nodes, edges } = get();
    if (future.length === 0) return false;
    const next = future[0];
    set({
      future: future.slice(1),
      past: [...get().past, { nodes: deepClone({ nodes, edges }).nodes, edges: deepClone({ nodes, edges }).edges }],
      nodes: next.nodes,
      edges: next.edges,
    });
    return true;
  },

  setNodes: (nodes) => {
    get().pushHistory();
    set({ nodes });
  },
  setEdges: (edges) => {
    get().pushHistory();
    set({ edges });
  },

  onNodesChange: (changes) => {
    const hasNonDragChange = changes.some((c) => c.type !== 'position');
    if (hasNonDragChange) {
      get().pushHistory();
    }
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
    get().pushHistory();
    set({ edges: applyEdgeChanges(changes, get().edges) });
  },

  onConnect: (connection: Connection) => {
    get().pushHistory();
    // Preserve branch label from condition node source handles
    const extraData: Record<string, string> = {};
    if (connection.sourceHandle) {
      extraData.branch = connection.sourceHandle;
    }
    const edge = { ...connection, ...extraData };
    set({ edges: addEdge(edge, get().edges) });
  },

  addNode: (node) => {
    get().pushHistory();
    set({ nodes: [...get().nodes, node] });
  },

  removeNode: (id) => {
    get().pushHistory();
    set({
      nodes: get().nodes.filter((n) => n.id !== id),
      edges: get().edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeId: get().selectedNodeId === id ? null : get().selectedNodeId,
    });
  },

  updateNodeData: (id, data) => {
    get().pushHistory();
    set({
      nodes: get().nodes.map((node) =>
        node.id === id ? { ...node, data: { ...node.data, ...data } } : node
      ),
    });
  },

  selectNode: (id) => set({ selectedNodeId: id }),

  setWorkflowId: (id) => set({ workflowId: id }),
  setWorkflowName: (name) => set({ workflowName: name }),

  setEngineType: (type) => set({ engineType: type }),

  resetWorkflow: () => {
    const [nodes, edges] = createDefaultNodes();
    set({
      nodes,
      edges,
      selectedNodeId: null,
      workflowId: null,
      workflowName: '',
      past: [],
      future: [],
    });
  },

  generatePhaseNodes: ({ phases, decomposerNodeId, llmConfigs }) => {
    get().pushHistory();
    const state = get();
    const decomposerNode = state.nodes.find((n) => n.id === decomposerNodeId);
    const baseX = (decomposerNode?.position.x ?? 200) + 280;
    const baseY = (decomposerNode?.position.y ?? 100);
    const verticalGap = 200;

    // Inherit API credentials from the decomposer node
    const decomposerData = decomposerNode?.data as DecomposerNodeData | undefined;
    const inheritedApiKey = decomposerData?.apiKey || '';
    const inheritedApiBaseUrl = decomposerData?.apiBaseUrl || '';

    const newNodes: Node<CustomNodeData>[] = [];
    const newEdges: Edge[] = [];
    const workerIds: string[] = [];

    // Find Input node to inject user input into worker prompts
    const inputNode = state.nodes.find((n) => n.type === 'input');
    const userInputRef = inputNode ? `{{${inputNode.id}.output}}` : '';

    // Generate worker + AI judge pairs for each phase
    phases.forEach((phase, i) => {
      const workerId = `worker_${++nodeCounter}`;
      const judgeId = `judge_${++nodeCounter}`;
      const y = baseY + i * verticalGap;

      // Build worker prompt with user input + chain context from previous phases
      let workerPrompt = '';
      if (userInputRef) {
        workerPrompt += `【用户输入】\n${userInputRef}\n\n`;
      }
      if (i > 0) {
        const previousRefs = workerIds
          .map((id, idx) => `阶段${idx + 1} (${phases[idx].name}): {{${id}.output}}`)
          .join('\n');
        workerPrompt += `【前序阶段工作成果】\n${previousRefs}\n\n`;
      }
      workerPrompt += `【当前阶段任务】\n${phase.description}\n\n请基于用户输入和前序阶段的工作成果，完成当前阶段的任务。`;

      // Worker LLM node
      const workerNode: Node<CustomNodeData> = {
        id: workerId,
        type: 'llm',
        position: { x: baseX, y },
        data: {
          label: phase.name,
          provider: llmConfigs.workerProvider,
          model: llmConfigs.workerModel,
          apiBaseUrl: inheritedApiBaseUrl,
          apiKey: inheritedApiKey,
          prompt: workerPrompt,
          temperature: 0.7,
          maxTokens: 2048,
          phaseIndex: i,
          totalPhases: phases.length,
          phaseName: phase.name,
        },
      };

      // AI Judge node (replaces old condition node)
      const judgeNode: Node<CustomNodeData> = {
        id: judgeId,
        type: 'judge',
        position: { x: baseX + 280, y },
        data: {
          label: `判断: ${phase.name}`,
          provider: llmConfigs.judgeProvider,
          model: llmConfigs.judgeModel,
          apiKey: inheritedApiKey,
          apiBaseUrl: inheritedApiBaseUrl,
          leftRef: `${workerId}.output`,
          criteria: phase.criteria,
          temperature: 0.1,
          maxTokens: 256,
          maxRetries: 3,
        },
      };

      newNodes.push(workerNode, judgeNode);
      workerIds.push(workerId);

      // Edge: worker → judge
      newEdges.push({
        id: `${workerId}->${judgeId}`,
        source: workerId,
        target: judgeId,
        type: 'smoothstep',
        animated: true,
      });

      // Edge: judge.false → worker (loop back with retry)
      newEdges.push({
        id: `${judgeId}->${workerId}`,
        source: judgeId,
        sourceHandle: 'false',
        target: workerId,
        type: 'smoothstep',
        animated: false,
        style: { stroke: '#faad14', strokeDasharray: '5,5' },
        label: '重试',
      });

      // Edge: judge.true → next worker (except last phase)
      if (i < phases.length - 1) {
        newEdges.push({
          id: `${judgeId}->worker_${i + 2}`,
          source: judgeId,
          sourceHandle: 'true',
          target: `worker_${nodeCounter + 1}`, // next worker will have this id
          type: 'smoothstep',
          animated: true,
        });
      }
    });

    // Validator LLM node (after last judge)
    const validatorId = `validator_${++nodeCounter}`;
    const lastJudge = newNodes.filter((n) => n.type === 'judge').pop();

    const phaseSummaries = workerIds
      .map((id, idx) => `阶段${idx + 1} (${phases[idx].name}): {{${id}.output}}`)
      .join('\n');
    const validatorPrompt = `你是一个多阶段任务的最终验证专家。以下是一个复杂任务被分解为${phases.length}个阶段后的全部执行结果。\n\n请逐阶段审查并给出最终验证结论：\n- 每个阶段是否达到了预期目标\n- 各阶段成果之间的逻辑一致性\n- 整体任务是否已完成\n\n${phaseSummaries}`;
    
    const validatorNode: Node<CustomNodeData> = {
      id: validatorId,
      type: 'llm',
      position: { x: baseX + 560, y: baseY + ((phases.length - 1) * verticalGap) / 2 },
      data: {
        label: '最终验证',
        provider: llmConfigs.validatorProvider,
        model: llmConfigs.validatorModel,
        apiBaseUrl: inheritedApiBaseUrl,
        apiKey: inheritedApiKey,
        prompt: validatorPrompt,
        temperature: 0.3,
        maxTokens: 2048,
      },
    };
    newNodes.push(validatorNode);

    // Edge: last judge.true → validator
    if (lastJudge) {
      newEdges.push({
        id: `${lastJudge.id}->${validatorId}`,
        source: lastJudge.id,
        sourceHandle: 'true',
        target: validatorId,
        type: 'smoothstep',
        animated: true,
      });
    }

    // Remove decomposer node, keep other nodes, add generated ones
    const remainingNodes = state.nodes.filter(
      (n) => n.id !== decomposerNodeId
    );
    // Discard ALL old edges: decomposition rebuilds the entire graph topology.
    // Keeping stale edges from previous decompositions causes "edge with sourceId
    // doesn't exist" errors when the backend can't find referenced nodes.
    const remainingEdges: Edge[] = [];

    // Connect the generated chain to Input / Output nodes
    const outputNode = remainingNodes.find((n) => n.type === 'output');
    const firstWorker = newNodes.find((n) => n.type === 'llm');

    // inputNode was already found above for prompt building

    if (inputNode && firstWorker) {
      newEdges.push({
        id: `${inputNode.id}->${firstWorker.id}`,
        source: inputNode.id,
        target: firstWorker.id,
        type: 'smoothstep',
        animated: true,
      });
    }

    if (outputNode) {
      newEdges.push({
        id: `${validatorId}->${outputNode.id}`,
        source: validatorId,
        target: outputNode.id,
        type: 'smoothstep',
        animated: true,
      });
    }

    // Rewire Output node to collect all phase results + validator
    const updatedRemainingNodes = remainingNodes.map((n) => {
      if (n.type !== 'output') return n;
      const phaseOutputs = workerIds.map((id, idx) => ({
        paramName: `phase_${idx + 1}`,
        paramType: 'reference' as const,
        value: `${id}.output`,
      }));
      const allOutputs = [
        ...phaseOutputs,
        { paramName: 'validation', paramType: 'reference' as const, value: `${validatorId}.output` },
      ];
      const phaseSections = workerIds
        .map((_id, idx) => `### 阶段${idx + 1}: ${phases[idx].name}\n\n{{phase_${idx + 1}}}`)
        .join('\n\n');
      const responseTemplate =
        `${phaseSections}\n\n---\n\n### 最终验证\n\n{{validation}}`;
      return {
        ...n,
        data: {
          ...n.data,
          label: '汇总输出',
          outputs: allOutputs,
          responseTemplate,
        },
      };
    });

    set({
      nodes: [...updatedRemainingNodes, ...newNodes],
      edges: [...remainingEdges, ...newEdges],
      selectedNodeId: null,
    });
  },
}));
