import { create } from 'zustand';
import { ExecutionResult, ProgressEntry } from '../types/workflow';

interface DebugState {
  isOpen: boolean;
  input: string;
  result: ExecutionResult | null;
  progressMessages: ProgressEntry[];
  loading: boolean;
  error: string | null;
  eventSource: EventSource | null;

  openDrawer: () => void;
  closeDrawer: () => void;
  setInput: (input: string) => void;
  execute: (workflowId: string) => void;
  reset: () => void;
}

export const useDebugStore = create<DebugState>((set, get) => ({
  isOpen: false,
  input: '',
  result: null,
  progressMessages: [],
  loading: false,
  error: null,
  eventSource: null,

  openDrawer: () => set({ isOpen: true }),
  closeDrawer: () => {
    // Close existing SSE connection
    const { eventSource } = get();
    if (eventSource) {
      eventSource.close();
    }
    set({ isOpen: false });
  },
  setInput: (input) => set({ input }),

  execute: (workflowId: string) => {
    const { input, eventSource: existingES } = get();
    if (!input.trim()) return;

    // Close any existing SSE connection
    if (existingES) {
      existingES.close();
    }

    set({ loading: true, error: null, result: null, progressMessages: [] });

    const url = `/api/workflows/${workflowId}/execute-stream?input=${encodeURIComponent(input)}`;
    const es = new EventSource(url);
    set({ eventSource: es });

    es.addEventListener('progress', (event) => {
      const progress: ProgressEntry = JSON.parse(event.data);
      set((state) => ({
        progressMessages: [...state.progressMessages, progress],
      }));
    });

    es.addEventListener('result', (event) => {
      es.close();
      const result: ExecutionResult = JSON.parse(event.data);
      set({ result, loading: false, eventSource: null });
    });

    es.addEventListener('error', (event: Event & { data?: string }) => {
      es.close();
      let message = '执行失败';
      try {
        if (event.data) {
          const parsed = JSON.parse(event.data);
          message = parsed.message || message;
        }
      } catch {
        // keep default message
      }
      set({ error: message, loading: false, eventSource: null });
    });

    // Fallback onerror for network errors
    es.onerror = () => {
      if (!get().result && !get().error) {
        set({ error: '执行期间连接断开', loading: false, eventSource: null });
      }
      es.close();
    };
  },

  reset: () => {
    const { eventSource } = get();
    if (eventSource) {
      eventSource.close();
    }
    set({ input: '', result: null, progressMessages: [], error: null, eventSource: null });
  },
}));
