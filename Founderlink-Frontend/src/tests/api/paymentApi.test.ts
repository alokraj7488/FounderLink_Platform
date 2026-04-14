/**
 * paymentApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  createOrder,
  verifyPayment,
  acceptPayment,
  rejectPayment,
  getPaymentsByInvestor,
  getPaymentsByFounder,
  getPaymentsByStartup,
  getSagaStatus,
} from '../../core/api/paymentApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockPayment = {
  id: 1,
  status: 'PENDING',
  amount: 50000,
  investorId: 10,
  founderId: 2,
  startupId: 1,
  razorpayOrderId: 'order_abc123',
  createdAt: '2024-03-01T00:00:00Z',
};

const mockOrder = { orderId: 'order_abc123', amount: 50000, currency: 'INR', keyId: 'rzp_test_key' };

const baseVerifyPayload = {
  razorpayOrderId: 'order_abc123',
  razorpayPaymentId: 'pay_xyz789',
  razorpaySignature: 'sig_abc',
  investmentId: 1,
  investorId: 10,
  startupId: 1,
  amount: 50000,
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('paymentApi – Normal', () => {
  it('createOrder: POSTs and returns Razorpay order details', async () => {
    mock.onPost('/payments/create-order').reply(200, mockOrder);
    const res = await createOrder({
      investorId: 10,
      founderId: 2,
      startupId: 1,
      startupName: 'Tech Startup',
      investorName: 'John Doe',
      investorEmail: 'john@example.com',
      founderEmail: 'founder@example.com',
      amount: 50000,
    });
    expect(res.status).toBe(200);
    expect(res.data.orderId).toBe('order_abc123');
    expect(res.data.currency).toBe('INR');
  });

  it('verifyPayment: POSTs signature data and returns success', async () => {
    mock.onPost('/payments/verify').reply(200, { success: true, paymentId: 1 });
    const res = await verifyPayment(baseVerifyPayload);
    expect(res.data.success).toBe(true);
  });

  it('acceptPayment: PUTs to /accept and returns ACCEPTED payment', async () => {
    mock.onPut('/payments/1/accept').reply(200, { ...mockPayment, status: 'ACCEPTED' });
    const res = await acceptPayment(1);
    expect(res.data.status).toBe('ACCEPTED');
  });

  it('rejectPayment: PUTs to /reject and returns REJECTED payment', async () => {
    mock.onPut('/payments/1/reject').reply(200, { ...mockPayment, status: 'REJECTED' });
    const res = await rejectPayment(1);
    expect(res.data.status).toBe('REJECTED');
  });

  it('getPaymentsByInvestor: GETs all payments for an investor', async () => {
    mock.onGet('/payments/investor/10').reply(200, [mockPayment]);
    const res = await getPaymentsByInvestor(10);
    expect(res.data).toHaveLength(1);
    expect(res.data[0].investorId).toBe(10);
  });

  it('getPaymentsByFounder: GETs all payments for a founder', async () => {
    mock.onGet('/payments/founder/2').reply(200, [mockPayment]);
    const res = await getPaymentsByFounder(2);
    expect(res.data[0].founderId).toBe(2);
  });

  it('getPaymentsByStartup: GETs all payments for a startup', async () => {
    mock.onGet('/payments/startup/1').reply(200, [mockPayment]);
    const res = await getPaymentsByStartup(1);
    expect(res.data[0].startupId).toBe(1);
  });

  it('getSagaStatus: GETs saga status for a payment', async () => {
    mock.onGet('/payments/1/saga').reply(200, { paymentId: 1, status: 'COMPLETED', steps: [] });
    const res = await getSagaStatus(1);
    expect(res.data.status).toBe('COMPLETED');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('paymentApi – Boundary', () => {
  it('createOrder: accepts amount of 1 (smallest positive value)', async () => {
    mock.onPost('/payments/create-order').reply(200, { ...mockOrder, amount: 1 });
    const res = await createOrder({
      investorId: 1,
      founderId: 2,
      startupId: 1,
      startupName: 'Tech Startup',
      investorName: 'John Doe',
      investorEmail: 'john@example.com',
      founderEmail: 'founder@example.com',
      amount: 1,
    });
    expect(res.data.amount).toBe(1);
  });

  it('getPaymentsByInvestor: returns empty array when no payments exist', async () => {
    mock.onGet('/payments/investor/99').reply(200, []);
    const res = await getPaymentsByInvestor(99);
    expect(res.data).toHaveLength(0);
  });

  it('getPaymentsByFounder: returns empty array when no payments received', async () => {
    mock.onGet('/payments/founder/99').reply(200, []);
    const res = await getPaymentsByFounder(99);
    expect(res.data).toHaveLength(0);
  });

  it('getPaymentsByStartup: returns empty array for a new startup', async () => {
    mock.onGet('/payments/startup/99').reply(200, []);
    const res = await getPaymentsByStartup(99);
    expect(res.data).toHaveLength(0);
  });

  it('createOrder: accepts very large amount without error', async () => {
    mock.onPost('/payments/create-order').reply(200, { ...mockOrder, amount: 999999999 });
    const res = await createOrder({
      investorId: 10,
      founderId: 2,
      startupId: 1,
      startupName: 'Tech Startup',
      investorName: 'John Doe',
      investorEmail: 'john@example.com',
      founderEmail: 'founder@example.com',
      amount: 999999999,
    });
    expect(res.data.amount).toBe(999999999);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('paymentApi – Exception Handling', () => {
  it('verifyPayment: rejects on 400 (Razorpay signature mismatch)', async () => {
    mock.onPost('/payments/verify').reply(400, { message: 'Signature mismatch' });
    await expect(verifyPayment(baseVerifyPayload)).rejects.toThrow();
  });

  it('createOrder: rejects on 403 (not an investor)', async () => {
    mock.onPost('/payments/create-order').reply(403, { message: 'Access denied' });
    await expect(
      createOrder({
        investorId: 10,
        founderId: 2,
        startupId: 1,
        startupName: 'Tech Startup',
        investorName: 'John Doe',
        investorEmail: 'john@example.com',
        founderEmail: 'founder@example.com',
        amount: 5000,
      })
    ).rejects.toThrow();
  });

  it('acceptPayment: rejects on 404 (payment not found)', async () => {
    mock.onPut('/payments/9999/accept').reply(404);
    await expect(acceptPayment(9999)).rejects.toThrow();
  });

  it('rejectPayment: rejects on 403 (not authorised)', async () => {
    mock.onPut('/payments/1/reject').reply(403);
    await expect(rejectPayment(1)).rejects.toThrow();
  });

  it('getSagaStatus: rejects on 404 (saga record missing)', async () => {
    mock.onGet('/payments/9999/saga').reply(404);
    await expect(getSagaStatus(9999)).rejects.toThrow();
  });

  it('createOrder: rejects on network error', async () => {
    mock.onPost('/payments/create-order').networkError();
    await expect(
      createOrder({
        investorId: 10,
        founderId: 2,
        startupId: 1,
        startupName: 'Tech Startup',
        investorName: 'John Doe',
        investorEmail: 'john@example.com',
        founderEmail: 'founder@example.com',
        amount: 5000,
      })
    ).rejects.toThrow();
  });

  it('verifyPayment: rejects on 500 server error', async () => {
    mock.onPost('/payments/verify').reply(500);
    await expect(verifyPayment(baseVerifyPayload)).rejects.toThrow();
  });
});
