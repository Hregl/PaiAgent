import { Node, Edge } from 'reactflow';

export type NodeType = 'input' | 'output' | 'llm' | 'tts' | 'condition' | 'decomposer' | 'judge';

export type LLMProvider = 'deepseek' | 'qwen' | 'chatglm' | 'aiping';

export type EngineType = 'dag' | 'langgraph';

export interface LLMNodeData {
  label: string;
  provider: LLMProvider;
  model: string;
  apiBaseUrl: string;
  apiKey: string;
  prompt: string;
  temperature?: number;
  maxTokens?: number;
  phaseIndex?: number;
  totalPhases?: number;
  phaseName?: string;
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

export type ConditionOperator = 'equals' | 'not_equals' | 'contains' | 'starts_with' | 'is_empty' | 'is_not_empty' | 'greater_than' | 'less_than' | 'greater_or_equal' | 'less_or_equal' | 'matches_regex' | 'not_contains';

export interface ConditionNodeData {
  label: string;
  leftRef: string;
  operator: ConditionOperator;
  rightValue: string;
}

export interface DecomposerNodeData {
  label: string;
  taskDescription: string;
  apiKey: string;
  apiBaseUrl: string;
  workerProvider: LLMProvider;
  workerModel: string;
  judgeProvider: LLMProvider;
  judgeModel: string;
  validatorProvider: LLMProvider;
  validatorModel: string;
}

export interface Phase {
  name: string;
  description: string;
  criteria: string;
}

export interface JudgeNodeData {
  label: string;
  provider: LLMProvider;
  model: string;
  apiKey: string;
  apiBaseUrl: string;
  leftRef: string;
  criteria: string;
  temperature?: number;
  maxTokens?: number;
  maxRetries?: number;
}

export type CustomNodeData = LLMNodeData | TTSNodeData | InputNodeData | OutputNodeData | ConditionNodeData | DecomposerNodeData | JudgeNodeData;

export type WorkflowNode = Node<CustomNodeData>;
export type WorkflowEdge = Edge;

export interface WorkflowSnapshot {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

export interface ExportPayload {
  version: number;
  exportedAt: string;
  workflow: {
    name: string;
    engineType: EngineType;
    nodes: WorkflowNode[];
    edges: WorkflowEdge[];
  };
}

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

export interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

export interface NodeLog {
  nodeId: string;
  nodeType: string;
  status: 'SUCCESS' | 'FAILED';
  input: Record<string, unknown>;
  output: Record<string, unknown>;
  durationMs: number;
  error?: string;
  phaseIndex?: number;
  totalPhases?: number;
  tokenUsage?: TokenUsage;
}

export interface ProgressEntry {
  nodeId: string;
  nodeType: string;
  label: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  message: string;
  durationMs?: number;
  phaseIndex?: number;
  totalPhases?: number;
  branch?: string;
  confidence?: number;
  reasoning?: string;
  tokenUsage?: TokenUsage;
  warning?: string;
}

export interface ExecutionHistoryItem {
  id: string;
  workflowId: string;
  input: string;
  output: string; // JSON string, parse to get ExecutionResult
  status: 'SUCCESS' | 'FAILED';
  durationMs: number;
  nodeLogs: string | null;
  createdAt: string;
}
