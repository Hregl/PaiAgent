import api from './index';
import { ApiResponse } from '../types/api';
import { Workflow, WorkflowDefinition, Phase } from '../types/workflow';

export const workflowApi = {
  list(): Promise<ApiResponse<Workflow[]>> {
    return api.get('/workflows');
  },
  get(id: string): Promise<ApiResponse<Workflow>> {
    return api.get(`/workflows/${id}`);
  },
  create(data: { name: string; definition: WorkflowDefinition }): Promise<ApiResponse<Workflow>> {
    return api.post('/workflows', data);
  },
  update(id: string, data: { name: string; definition: WorkflowDefinition }): Promise<ApiResponse<Workflow>> {
    return api.put(`/workflows/${id}`, data);
  },
  delete(id: string): Promise<ApiResponse<void>> {
    return api.delete(`/workflows/${id}`);
  },
  decompose(data: { taskDescription: string; provider?: string; model?: string; apiKey?: string; apiBaseUrl?: string }): Promise<ApiResponse<{ phases: Phase[] }>> {
    return api.post('/workflows/decompose', data);
  },
  generateDescription(data: { topic: string; provider?: string; model?: string; apiKey?: string; apiBaseUrl?: string }): Promise<ApiResponse<{ description: string }>> {
    return api.post('/workflows/generate-description', data);
  },
};
