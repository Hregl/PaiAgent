import { create } from 'zustand';
import { ExecutionResult } from '../types/workflow';
import { executionApi } from '../api/execution';

interface DebugState {
  isOpen: boolean;
  input: string;
  result: ExecutionResult | null;
  loading: boolean;
  error: string | null;

  openDrawer: () => void;
  closeDrawer: () => void;
  setInput: (input: string) => void;
  execute: (workflowId: string) => Promise<void>;
  reset: () => void;
}

export const useDebugStore = create<DebugState>((set, get) => ({
  isOpen: false,
  input: '',
  result: null,
  loading: false,
  error: null,

  openDrawer: () => set({ isOpen: true }),
  closeDrawer: () => set({ isOpen: false }),
  setInput: (input) => set({ input }),

  execute: async (workflowId: string) => {
    const { input } = get();
    if (!input.trim()) return;
    set({ loading: true, error: null, result: null });
    try {
      const res = await executionApi.execute(workflowId, input);
      set({ result: res.data, loading: false });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Execution failed';
      set({ error: message, loading: false });
    }
  },

  reset: () => set({ input: '', result: null, error: null }),
}));
