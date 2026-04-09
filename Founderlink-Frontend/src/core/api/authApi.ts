import { AxiosResponse } from 'axios';
import api from './axiosConfig';
import { LoginFormData, RegisterFormData, LoginResponse } from '../../types';

export const login = (data: LoginFormData): Promise<AxiosResponse<LoginResponse>> =>
  api.post('/auth/login', data);

export const register = (data: RegisterFormData): Promise<AxiosResponse<void>> =>
  api.post('/auth/register', data);

export const refreshToken = (data: Record<string, unknown>): Promise<AxiosResponse<{ token?: string; accessToken?: string }>> =>
  api.post('/auth/refresh', data);
