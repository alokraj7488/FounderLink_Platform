/**
 * investmentApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  createInvestment,
  getInvestmentsByStartup,
  getMyInvestments,
  approveInvestment,
  rejectInvestment,
} from '../../core/api/investmentApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockInvestment = {
  id: 1,
  investorId: 10,
  startupId: 1,
  startupName: 'TechCo',
  startupIndustry: 'FinTech',
  amount: 50000,
  status: 'PENDING',
  createdAt: '2024-03-01T00:00:00Z',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('investmentApi – Normal', () => {
  it('createInvestment: POSTs and returns the created investment', async () => {
    mock.onPost('/investments').reply(201, mockInvestment);
    const res = await createInvestment({ amount: 50000, investorId: 10, startupId: 1 });
    expect(res.status).toBe(201);
    expect(res.data.amount).toBe(50000);
    expect(res.data.status).toBe('PENDING');
  });

  it('getInvestmentsByStartup: GETs all investments for a startup', async () => {
    mock.onGet('/investments/startup/1').reply(200, [mockInvestment]);
    const res = await getInvestmentsByStartup(1);
    expect(res.data).toHaveLength(1);
    expect(res.data[0].startupId).toBe(1);
  });

  it('getMyInvestments: GETs investments for a given investor', async () => {
    mock.onGet('/investments/investor/10').reply(200, [mockInvestment]);
    const res = await getMyInvestments(10);
    expect(res.data[0].investorId).toBe(10);
  });

  it('approveInvestment: PUTs to /approve and returns APPROVED status', async () => {
    mock.onPut('/investments/1/approve').reply(200, { ...mockInvestment, status: 'APPROVED' });
    const res = await approveInvestment(1);
    expect(res.data.status).toBe('APPROVED');
  });

  it('rejectInvestment: PUTs to /reject and returns REJECTED status', async () => {
    mock.onPut('/investments/1/reject').reply(200, { ...mockInvestment, status: 'REJECTED' });
    const res = await rejectInvestment(1);
    expect(res.data.status).toBe('REJECTED');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('investmentApi – Boundary', () => {
  it('createInvestment: accepts the minimum allowed amount (₹1,000)', async () => {
    mock.onPost('/investments').reply(201, { ...mockInvestment, amount: 1000 });
    const res = await createInvestment({ amount: 1000, investorId: 10, startupId: 1 });
    expect(res.data.amount).toBe(1000);
  });

  it('createInvestment: accepts a very large investment amount', async () => {
    mock.onPost('/investments').reply(201, { ...mockInvestment, amount: 999999999 });
    const res = await createInvestment({ amount: 999999999, investorId: 10, startupId: 1 });
    expect(res.data.amount).toBe(999999999);
  });

  it('getInvestmentsByStartup: returns empty array when startup has no investments', async () => {
    mock.onGet('/investments/startup/99').reply(200, []);
    const res = await getInvestmentsByStartup(99);
    expect(res.data).toHaveLength(0);
  });

  it('getMyInvestments: returns empty array when investor has no investments', async () => {
    mock.onGet('/investments/investor/99').reply(200, []);
    const res = await getMyInvestments(99);
    expect(res.data).toHaveLength(0);
  });

  it('approveInvestment: works even when called on an already-approved investment', async () => {
    mock.onPut('/investments/1/approve').reply(200, { ...mockInvestment, status: 'APPROVED' });
    const res = await approveInvestment(1);
    expect(res.data.status).toBe('APPROVED');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('investmentApi – Exception Handling', () => {
  it('createInvestment: rejects on 403 (investor not authorised)', async () => {
    mock.onPost('/investments').reply(403, { message: 'Access denied' });
    await expect(createInvestment({ amount: 5000, investorId: 10, startupId: 1 })).rejects.toThrow();
  });

  it('createInvestment: rejects on 400 (invalid startup id)', async () => {
    mock.onPost('/investments').reply(400, { message: 'Invalid startup' });
    await expect(createInvestment({ amount: 5000, investorId: 10, startupId: -1 })).rejects.toThrow();
  });

  it('getInvestmentsByStartup: rejects on 500 server error', async () => {
    mock.onGet('/investments/startup/1').reply(500);
    await expect(getInvestmentsByStartup(1)).rejects.toThrow();
  });

  it('getMyInvestments: rejects on 401 (unauthenticated)', async () => {
    mock.onGet('/investments/investor/10').reply(401);
    await expect(getMyInvestments(10)).rejects.toThrow();
  });

  it('approveInvestment: rejects on 404 (investment not found)', async () => {
    mock.onPut('/investments/9999/approve').reply(404);
    await expect(approveInvestment(9999)).rejects.toThrow();
  });

  it('rejectInvestment: rejects on 403 (not the startup founder)', async () => {
    mock.onPut('/investments/1/reject').reply(403);
    await expect(rejectInvestment(1)).rejects.toThrow();
  });

  it('createInvestment: rejects on network error', async () => {
    mock.onPost('/investments').networkError();
    await expect(createInvestment({ amount: 5000, investorId: 10, startupId: 1 })).rejects.toThrow();
  });
});
