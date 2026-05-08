import api from './index';
import { ApiResponse } from '../types/api';
import { ExecutionResult } from '../types/workflow';

export const executionApi = {
  execute(workflowId: string, input: string): Promise<ApiResponse<ExecutionResult>> {
    return api.post(`/workflows/${workflowId}/execute`, { input });
  },
  getResult(executionId: string): Promise<ApiResponse<ExecutionResult>> {
    return api.get(`/executions/${executionId}`);
  },
};
