import api from './index';
import { ApiResponse } from '../types/api';
import { Workflow, WorkflowDefinition } from '../types/workflow';

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
};
