import { describe, it, expect, beforeEach } from 'vitest';
import { useWorkflowStore } from '../store/workflowStore';
import { Phase } from '../types/workflow';

function resetStore() {
  useWorkflowStore.getState().resetWorkflow();
}

describe('workflowStore', () => {
  beforeEach(() => {
    resetStore();
  });

  describe('initial state', () => {
    it('has default input, llm, tts, and output nodes', () => {
      const { nodes } = useWorkflowStore.getState();
      const types = nodes.map((n) => n.type);
      expect(types).toContain('input');
      expect(types).toContain('llm');
      expect(types).toContain('tts');
      expect(types).toContain('output');
    });

    it('has edges connecting nodes in sequence', () => {
      const { edges } = useWorkflowStore.getState();
      expect(edges.length).toBeGreaterThanOrEqual(3);
    });

    it('has no selected node', () => {
      const { selectedNodeId } = useWorkflowStore.getState();
      expect(selectedNodeId).toBeNull();
    });
  });

  describe('node CRUD', () => {
    it('adds a node', () => {
      useWorkflowStore.getState().addNode({
        id: 'test_llm',
        type: 'llm',
        position: { x: 0, y: 0 },
        data: {
          label: 'Test LLM',
          provider: 'deepseek',
          model: 'deepseek-chat',
          apiBaseUrl: '',
          apiKey: '',
          prompt: 'test',
        },
      });
      const { nodes } = useWorkflowStore.getState();
      expect(nodes.find((n) => n.id === 'test_llm')).toBeDefined();
    });

    it('removes a node and its edges', () => {
      const state = useWorkflowStore.getState();
      const firstLlm = state.nodes.find((n) => n.type === 'llm');
      expect(firstLlm).toBeDefined();

      useWorkflowStore.getState().removeNode(firstLlm!.id);

      const { nodes, edges } = useWorkflowStore.getState();
      expect(nodes.find((n) => n.id === firstLlm!.id)).toBeUndefined();
      expect(edges.filter((e) => e.source === firstLlm!.id || e.target === firstLlm!.id)).toHaveLength(0);
    });

    it('blocks deletion of input and output nodes', () => {
      const state = useWorkflowStore.getState();
      const inputNode = state.nodes.find((n) => n.type === 'input')!;

      useWorkflowStore.getState().onNodesChange([
        { id: inputNode.id, type: 'remove' },
      ]);

      const { nodes } = useWorkflowStore.getState();
      expect(nodes.find((n) => n.id === inputNode.id)).toBeDefined();
    });

    it('selects a node', () => {
      const { nodes } = useWorkflowStore.getState();
      const firstNode = nodes[0];
      useWorkflowStore.getState().selectNode(firstNode.id);
      expect(useWorkflowStore.getState().selectedNodeId).toBe(firstNode.id);
    });

    it('updates node data', () => {
      const { nodes } = useWorkflowStore.getState();
      const llmNode = nodes.find((n) => n.type === 'llm')!;

      useWorkflowStore.getState().updateNodeData(llmNode.id, { label: 'Updated' });

      const updated = useWorkflowStore.getState().nodes.find((n) => n.id === llmNode.id);
      expect(updated?.data.label).toBe('Updated');
    });
  });

  describe('workflow metadata', () => {
    it('sets workflow id and name', () => {
      useWorkflowStore.getState().setWorkflowId('wf-123');
      useWorkflowStore.getState().setWorkflowName('Test Workflow');

      const { workflowId, workflowName } = useWorkflowStore.getState();
      expect(workflowId).toBe('wf-123');
      expect(workflowName).toBe('Test Workflow');
    });

    it('sets engine type', () => {
      useWorkflowStore.getState().setEngineType('dag');
      expect(useWorkflowStore.getState().engineType).toBe('dag');

      useWorkflowStore.getState().setEngineType('langgraph');
      expect(useWorkflowStore.getState().engineType).toBe('langgraph');
    });

    it('resets to default state', () => {
      useWorkflowStore.getState().setWorkflowId('wf-abc');
      useWorkflowStore.getState().setWorkflowName('My WF');
      useWorkflowStore.getState().selectNode('some-node');

      useWorkflowStore.getState().resetWorkflow();

      const state = useWorkflowStore.getState();
      expect(state.workflowId).toBeNull();
      expect(state.workflowName).toBe('');
      expect(state.selectedNodeId).toBeNull();
      expect(state.nodes.length).toBeGreaterThan(0);
    });
  });

  describe('generatePhaseNodes', () => {
    const samplePhases: Phase[] = [
      { name: 'Research', description: 'Research the topic thoroughly', criteria: 'At least 3 sources cited' },
      { name: 'Draft', description: 'Write the first draft', criteria: 'At least 500 words' },
    ];

    it('replaces decomposer node with generated worker and judge nodes', () => {
      const state = useWorkflowStore.getState();
      // Add a decomposer node
      state.addNode({
        id: 'decomp_1',
        type: 'decomposer',
        position: { x: 100, y: 100 },
        data: {
          label: 'Decomposer',
          taskDescription: 'Write an article',
          apiKey: '',
          apiBaseUrl: '',
          workerProvider: 'deepseek',
          workerModel: 'deepseek-chat',
          judgeProvider: 'deepseek',
          judgeModel: 'deepseek-chat',
          validatorProvider: 'deepseek',
          validatorModel: 'deepseek-chat',
        },
      });

      useWorkflowStore.getState().generatePhaseNodes({
        phases: samplePhases,
        decomposerNodeId: 'decomp_1',
        llmConfigs: {
          workerProvider: 'deepseek',
          workerModel: 'deepseek-chat',
          judgeProvider: 'deepseek',
          judgeModel: 'deepseek-chat',
          validatorProvider: 'deepseek',
          validatorModel: 'deepseek-chat',
        },
      });

      const { nodes, edges } = useWorkflowStore.getState();

      // Decomposer should be gone
      expect(nodes.find((n) => n.id === 'decomp_1')).toBeUndefined();

      // Should have 2 workers + 2 judges + 1 validator = 5 new nodes
      const workers = nodes.filter((n) => n.data.label === 'Research' || n.data.label === 'Draft');
      const judges = nodes.filter((n) => n.type === 'judge');
      const validators = nodes.filter((n) => n.data.label === '最终验证');

      expect(workers).toHaveLength(2);
      expect(judges).toHaveLength(2);
      expect(validators).toHaveLength(1);
    });

    it('output node is rewired to collect phase results', () => {
      const state = useWorkflowStore.getState();
      state.addNode({
        id: 'decomp_2',
        type: 'decomposer',
        position: { x: 100, y: 100 },
        data: {
          label: 'Decomposer',
          taskDescription: 'Test',
          apiKey: '',
          apiBaseUrl: '',
          workerProvider: 'deepseek',
          workerModel: 'deepseek-chat',
          judgeProvider: 'deepseek',
          judgeModel: 'deepseek-chat',
          validatorProvider: 'deepseek',
          validatorModel: 'deepseek-chat',
        },
      });

      useWorkflowStore.getState().generatePhaseNodes({
        phases: samplePhases,
        decomposerNodeId: 'decomp_2',
        llmConfigs: {
          workerProvider: 'deepseek',
          workerModel: 'deepseek-chat',
          judgeProvider: 'deepseek',
          judgeModel: 'deepseek-chat',
          validatorProvider: 'deepseek',
          validatorModel: 'deepseek-chat',
        },
      });

      const { nodes } = useWorkflowStore.getState();
      const outputNode = nodes.find((n) => n.type === 'output');
      expect(outputNode).toBeDefined();
      // Output should have phase references
      const outputs = outputNode?.data.outputs as Array<{ paramName: string }> | undefined;
      expect(outputs?.some((o) => o.paramName === 'phase_1')).toBe(true);
      expect(outputs?.some((o) => o.paramName === 'phase_2')).toBe(true);
      expect(outputs?.some((o) => o.paramName === 'validation')).toBe(true);
    });
  });
});
