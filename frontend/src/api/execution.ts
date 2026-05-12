import api from './index';
import { ApiResponse } from '../types/api';
import { ExecutionResult, ExecutionHistoryItem } from '../types/workflow';

export const executionApi = {
  execute(workflowId: string, input: string): Promise<ApiResponse<ExecutionResult>> {
    return api.post(`/workflows/${workflowId}/execute`, { input });
  },
  getResult(executionId: string): Promise<ApiResponse<ExecutionResult>> {
    return api.get(`/executions/${executionId}`);
  },
  listExecutions(workflowId: string): Promise<ApiResponse<ExecutionHistoryItem[]>> {
    return api.get(`/workflows/${workflowId}/executions`);
  },
};
