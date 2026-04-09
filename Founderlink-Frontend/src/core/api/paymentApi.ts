import { AxiosResponse } from 'axios';
import api from './axiosConfig';
import {
  Payment,
  CreateOrderRequest,
  CreateOrderResponse,
  VerifyPaymentRequest,
  VerifyPaymentResponse,
  PaymentSaga,
} from '../../types';

export const createOrder = (data: CreateOrderRequest): Promise<AxiosResponse<CreateOrderResponse>> =>
  api.post('/api/payments/create-order', data);

export const verifyPayment = (data: VerifyPaymentRequest): Promise<AxiosResponse<VerifyPaymentResponse>> =>
  api.post('/api/payments/verify', data);

export const acceptPayment = (paymentId: number): Promise<AxiosResponse<Payment>> =>
  api.put(`/api/payments/${paymentId}/accept`);

export const rejectPayment = (paymentId: number): Promise<AxiosResponse<Payment>> =>
  api.put(`/api/payments/${paymentId}/reject`);

export const getPaymentsByInvestor = (investorId: number): Promise<AxiosResponse<Payment[]>> =>
  api.get(`/api/payments/investor/${investorId}`);

export const getPaymentsByFounder = (founderId: number): Promise<AxiosResponse<Payment[]>> =>
  api.get(`/api/payments/founder/${founderId}`);

export const getPaymentsByStartup = (startupId: number): Promise<AxiosResponse<Payment[]>> =>
  api.get(`/api/payments/startup/${startupId}`);

export const getSagaStatus = (paymentId: number): Promise<AxiosResponse<PaymentSaga>> =>
  api.get(`/api/payments/${paymentId}/saga`);
