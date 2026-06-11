import { describe, it, expect, beforeEach } from 'vitest';
import { useDebugStore } from '../store/debugStore';

describe('debugStore', () => {
  beforeEach(() => {
    useDebugStore.getState().reset();
  });

  describe('drawer state', () => {
    it('starts closed', () => {
      expect(useDebugStore.getState().isOpen).toBe(false);
    });

    it('opens and closes', () => {
      useDebugStore.getState().openDrawer();
      expect(useDebugStore.getState().isOpen).toBe(true);

      useDebugStore.getState().closeDrawer();
      expect(useDebugStore.getState().isOpen).toBe(false);
    });
  });

  describe('input management', () => {
    it('sets input text', () => {
      useDebugStore.getState().setInput('test input');
      expect(useDebugStore.getState().input).toBe('test input');
    });

    it('resets clears everything', () => {
      useDebugStore.getState().setInput('hello');
      useDebugStore.setState({ error: 'some error' });

      useDebugStore.getState().reset();

      const state = useDebugStore.getState();
      expect(state.input).toBe('');
      expect(state.error).toBeNull();
      expect(state.result).toBeNull();
    });
  });
});
