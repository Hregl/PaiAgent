import api from './index';
import { ApiResponse, LoginRequest, LoginResponse, User } from '../types/api';

export const authApi = {
  login(data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
    return api.post('/auth/login', data);
  },
  getMe(): Promise<ApiResponse<User>> {
    return api.get('/auth/me');
  },
};
