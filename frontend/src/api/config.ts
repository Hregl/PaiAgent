import api from './index';
import { EngineType } from '../types/workflow';

export const configApi = {
  getEngineType: () =>
    api.get<{ code: number; data: { engineType: string } }>('/config/engine'),

  setEngineType: (engineType: EngineType) =>
    api.put<{ code: number; data: { engineType: string } }>('/config/engine', { engineType }),
};
