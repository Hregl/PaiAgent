import { Node, Edge } from 'reactflow';

export type NodeType = 'input' | 'output' | 'llm' | 'tts';

export type LLMProvider = 'deepseek' | 'qwen' | 'chatglm' | 'aiping';

export interface LLMNodeData {
  label: string;
  provider: LLMProvider;
  model: string;
  apiBaseUrl: string;
  apiKey: string;
  prompt: string;
  temperature?: number;
  maxTokens?: number;
}

export interface TTSParam {
  paramName: string;
  paramType: 'input' | 'reference';
  value: string;
}

export interface TTSNodeData {
  label: string;
  apiKey: string;
  model: string;
  inputs: TTSParam[];
}

export interface InputNodeData {
  label: string;
  variableName: string;
  variableType: string;
  description: string;
  required: boolean;
}

export interface OutputParam {
  paramName: string;
  paramType: 'input' | 'reference';
  value: string;
}

export interface OutputNodeData {
  label: string;
  outputs: OutputParam[];
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
  definition: WorkflowDefinition | string;
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
  error?: string;
}

export interface NodeLog {
  nodeId: string;
  nodeType: string;
  status: 'SUCCESS' | 'FAILED';
  input: Record<string, unknown>;
  output: Record<string, unknown>;
  durationMs: number;
  error?: string;
}
