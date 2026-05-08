import { create } from 'zustand';
import { User } from '../types/api';
import { authApi } from '../api/auth';

interface AuthState {
  token: string | null;
  user: User | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  checkAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  user: null,
  loading: false,

  login: async (username: string, password: string) => {
    set({ loading: true });
    try {
      const res = await authApi.login({ username, password });
      const { token, user } = res.data;
      localStorage.setItem('token', token);
      set({ token, user, loading: false });
    } catch {
      set({ loading: false });
      throw new Error('Login failed');
    }
  },

  logout: () => {
    localStorage.removeItem('token');
    set({ token: null, user: null });
    window.location.href = '/login';
  },

  checkAuth: async () => {
    const token = localStorage.getItem('token');
    if (!token) return;
    try {
      const res = await authApi.getMe();
      set({ user: res.data, token });
    } catch {
      localStorage.removeItem('token');
      set({ token: null, user: null });
    }
  },
}));
