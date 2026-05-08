import { create } from 'zustand';
import {
  Node,
  Edge,
  OnNodesChange,
  OnEdgesChange,
  OnConnect,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  Connection,
} from 'reactflow';
import { CustomNodeData } from '../types/workflow';

interface WorkflowState {
  nodes: Node<CustomNodeData>[];
  edges: Edge[];
  selectedNodeId: string | null;
  workflowId: string | null;
  workflowName: string;

  setNodes: (nodes: Node<CustomNodeData>[]) => void;
  setEdges: (edges: Edge[]) => void;
  onNodesChange: OnNodesChange;
  onEdgesChange: OnEdgesChange;
  onConnect: OnConnect;
  addNode: (node: Node<CustomNodeData>) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<CustomNodeData>) => void;
  selectNode: (id: string | null) => void;
  setWorkflowId: (id: string | null) => void;
  setWorkflowName: (name: string) => void;
  resetWorkflow: () => void;
}

const initialNodes: Node<CustomNodeData>[] = [];
const initialEdges: Edge[] = [];

export const useWorkflowStore = create<WorkflowState>((set, get) => ({
  nodes: initialNodes,
  edges: initialEdges,
  selectedNodeId: null,
  workflowId: null,
  workflowName: '',

  setNodes: (nodes) => set({ nodes }),
  setEdges: (edges) => set({ edges }),

  onNodesChange: (changes) => {
    set({ nodes: applyNodeChanges(changes, get().nodes) });
  },

  onEdgesChange: (changes) => {
    set({ edges: applyEdgeChanges(changes, get().edges) });
  },

  onConnect: (connection: Connection) => {
    set({ edges: addEdge(connection, get().edges) });
  },

  addNode: (node) => {
    set({ nodes: [...get().nodes, node] });
  },

  removeNode: (id) => {
    set({
      nodes: get().nodes.filter((n) => n.id !== id),
      edges: get().edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeId: get().selectedNodeId === id ? null : get().selectedNodeId,
    });
  },

  updateNodeData: (id, data) => {
    set({
      nodes: get().nodes.map((node) =>
        node.id === id ? { ...node, data: { ...node.data, ...data } } : node
      ),
    });
  },

  selectNode: (id) => set({ selectedNodeId: id }),

  setWorkflowId: (id) => set({ workflowId: id }),
  setWorkflowName: (name) => set({ workflowName: name }),

  resetWorkflow: () =>
    set({
      nodes: [],
      edges: [],
      selectedNodeId: null,
      workflowId: null,
      workflowName: '',
    }),
}));
