import { Node, Edge } from 'reactflow';

export type NodeType = 'input' | 'output' | 'llm' | 'tts';

export type LLMProvider = 'deepseek' | 'qwen' | 'chatglm' | 'aiping';

export interface LLMNodeData {
  label: string;
  provider: LLMProvider;
  model: string;
  prompt: string;
  temperature?: number;
  maxTokens?: number;
}

export interface TTSNodeData {
  label: string;
  voiceId: string;
  inputRef: string;
}

export interface InputNodeData {
  label: string;
  variableName: string;
}

export interface OutputNodeData {
  label: string;
  outputs: { key: string; ref: string }[];
  responseTemplate: string;
}

export type CustomNodeData = LLMNodeData | TTSNodeData | InputNodeData | OutputNodeData;

export type WorkflowNode = Node<CustomNodeData>;
export type WorkflowEdge = Edge;

export interface WorkflowDefinition {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

export interface Workflow {
  id: string;
  name: string;
  userId: number;
  definition: WorkflowDefinition;
  createdAt: string;
  updatedAt: string;
}

export interface ExecutionResult {
  executionId: string;
  status: 'SUCCESS' | 'FAILED';
  output: {
    text?: string;
    audioUrl?: string;
  };
  nodeLogs: NodeLog[];
  durationMs: number;
}

export interface NodeLog {
  nodeId: string;
  nodeType: string;
  status: 'SUCCESS' | 'FAILED';
  output: Record<string, unknown>;
  durationMs: number;
  error?: string;
}
