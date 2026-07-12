import { create } from 'zustand';
import { ExecutionResult, ProgressEntry } from '../types/workflow';
import { useWorkflowStore } from './workflowStore';

interface DebugState {
  isOpen: boolean;
  input: string;
  result: ExecutionResult | null;
  progressMessages: ProgressEntry[];
  latestProgress: ProgressEntry | null;
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
  latestProgress: null,
  loading: false,
  error: null,
  eventSource: null,

  openDrawer: () => set({ isOpen: true }),
  closeDrawer: () => {
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

    // Pre-execution: check for disconnected nodes
    const { nodes, edges } = useWorkflowStore.getState();
    const connectedIds = new Set<string>();
    edges.forEach(e => { connectedIds.add(e.source); connectedIds.add(e.target); });
    const disconnected = nodes.filter(n =>
      n.type !== 'input' && n.type !== 'output' &&
      (!connectedIds.has(n.id) || !edges.some(ed => ed.source === n.id) && !edges.some(ed => ed.target === n.id))
    );
    if (disconnected.length > 0) {
      const names = disconnected.map(n => (n.data as { label?: string }).label || n.id).join('、');
      set({ error: `存在未连通节点: ${names}，请删除后重试` });
      return;
    }

    // Close any existing SSE connection
    if (existingES) {
      existingES.close();
    }

    set({ loading: true, error: null, result: null, progressMessages: [], latestProgress: null });

    // Client-side timeout
    const EXECUTION_TIMEOUT_MS = 600_000;
    let timeoutId: ReturnType<typeof setTimeout> | null = null;
    const clearTimer = () => { if (timeoutId) { clearTimeout(timeoutId); timeoutId = null; } };
    timeoutId = setTimeout(() => {
      const es = get().eventSource;
      if (es) es.close();
      set({ error: '执行超时（超过 10 分钟未完成），建议减少阶段数后重试', loading: false, eventSource: null });
    }, EXECUTION_TIMEOUT_MS);

    const url = `/api/workflows/${workflowId}/execute-stream?input=${encodeURIComponent(input)}`;
    const es = new EventSource(url);
    set({ eventSource: es });

    es.addEventListener('progress', (event) => {
      const progress: ProgressEntry = JSON.parse(event.data);
      set((state) => ({
        progressMessages: [...state.progressMessages, progress],
        latestProgress: progress,
      }));
    });

    es.addEventListener('result', (event) => {
      clearTimer();
      es.close();
      const result: ExecutionResult = JSON.parse(event.data);
      set({ result, loading: false, eventSource: null });
    });

    es.addEventListener('error', (event: Event & { data?: string }) => {
      clearTimer();
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

    es.onerror = () => {
      if (!get().result && !get().error) {
        clearTimer();
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
    set({ input: '', result: null, progressMessages: [], latestProgress: null, error: null, eventSource: null });
  },
}));
