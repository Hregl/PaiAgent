import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from '../store/authStore';

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null, loading: false });
  });

  describe('initial state', () => {
    it('has token null when no token in localStorage', () => {
      expect(useAuthStore.getState().token).toBeNull();
    });

    it('has no user initially', () => {
      expect(useAuthStore.getState().user).toBeNull();
    });

    it('has loading false initially', () => {
      expect(useAuthStore.getState().loading).toBe(false);
    });
  });

  describe('logout', () => {
    it('clears token and user', () => {
      useAuthStore.setState({ token: 'abc', user: { id: 1, username: 'admin', role: 'admin' } });

      useAuthStore.getState().logout();

      const state = useAuthStore.getState();
      expect(state.token).toBeNull();
      expect(state.user).toBeNull();
      expect(localStorage.getItem('token')).toBeNull();
    });
  });
});
