/**
 * startupApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  createStartup,
  getAllStartups,
  getStartupById,
  updateStartup,
  deleteStartup,
  approveStartup,
  rejectStartup,
  followStartup,
  getStartupsByFounder,
  getAllStartupsAdmin,
} from '../../core/api/startupApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockStartup = {
  id: 1,
  name: 'TechCo',
  industry: 'FinTech',
  description: 'A fintech startup solving payments.',
  problemStatement: 'Payments are slow.',
  solution: 'We speed them up.',
  fundingGoal: 500000,
  stage: 'MVP',
  location: 'Mumbai',
  founderId: 10,
  isApproved: false,
  isRejected: false,
  createdAt: '2024-01-01T00:00:00Z',
};

const mockFormData = {
  name: 'TechCo',
  industry: 'FinTech',
  description: 'A fintech startup solving payments.',
  problemStatement: 'Payments are slow.',
  solution: 'We speed them up.',
  fundingGoal: 500000,
  stage: 'MVP',
  location: 'Mumbai',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('startupApi – Normal', () => {
  it('createStartup: POSTs form data and returns created startup', async () => {
    mock.onPost('/startups').reply(201, mockStartup);
    const res = await createStartup(mockFormData);
    expect(res.status).toBe(201);
    expect(res.data.name).toBe('TechCo');
    expect(res.data.isApproved).toBe(false);
  });

  it('getAllStartups: GETs paginated list with default page=0 & size=10', async () => {
    mock.onGet('/startups?page=0&size=10').reply(200, {
      content: [mockStartup],
      totalPages: 3,
      totalElements: 25,
    });
    const res = await getAllStartups();
    expect(res.data.content).toHaveLength(1);
    expect(res.data.totalPages).toBe(3);
  });

  it('getAllStartups: accepts custom page and size', async () => {
    mock.onGet('/startups?page=2&size=5').reply(200, { content: [], totalPages: 5, totalElements: 25 });
    const res = await getAllStartups(2, 5);
    expect(res.data.totalPages).toBe(5);
  });

  it('getStartupById: GETs single startup by id', async () => {
    mock.onGet('/startups/1').reply(200, mockStartup);
    const res = await getStartupById(1);
    expect(res.data.id).toBe(1);
    expect(res.data.industry).toBe('FinTech');
  });

  it('updateStartup: PUTs updated data and returns startup', async () => {
    mock.onPut('/startups/1').reply(200, { ...mockStartup, name: 'TechCo v2' });
    const res = await updateStartup(1, { ...mockFormData, name: 'TechCo v2' });
    expect(res.data.name).toBe('TechCo v2');
  });

  it('deleteStartup: DELETEs and returns 204 No Content', async () => {
    mock.onDelete('/startups/1').reply(204);
    const res = await deleteStartup(1);
    expect(res.status).toBe(204);
  });

  it('approveStartup: PUTs to /approve and sets isApproved true', async () => {
    mock.onPut('/startups/1/approve').reply(200, { ...mockStartup, isApproved: true });
    const res = await approveStartup(1);
    expect(res.data.isApproved).toBe(true);
  });

  it('rejectStartup: PUTs to /reject and sets isRejected true', async () => {
    mock.onPut('/startups/1/reject').reply(200, { ...mockStartup, isRejected: true });
    const res = await rejectStartup(1);
    expect(res.data.isRejected).toBe(true);
  });

  it('followStartup: POSTs to /follow endpoint', async () => {
    mock.onPost('/startups/1/follow').reply(200);
    const res = await followStartup(1);
    expect(res.status).toBe(200);
  });

  it('getStartupsByFounder: GETs startups list for founder', async () => {
    mock.onGet('/startups/founder/10').reply(200, [mockStartup]);
    const res = await getStartupsByFounder(10);
    expect(res.data).toHaveLength(1);
    expect(res.data[0].founderId).toBe(10);
  });

  it('getAllStartupsAdmin: GETs all startups from admin endpoint', async () => {
    mock.onGet('/startups/admin/all?page=0&size=100').reply(200, {
      content: [mockStartup],
      totalPages: 1,
      totalElements: 1,
    });
    const res = await getAllStartupsAdmin();
    expect(res.data.content).toHaveLength(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('startupApi – Boundary', () => {
  it('getAllStartups: page=0, size=1 returns one item', async () => {
    mock.onGet('/startups?page=0&size=1').reply(200, { content: [mockStartup], totalPages: 25, totalElements: 25 });
    const res = await getAllStartups(0, 1);
    expect(res.data.content).toHaveLength(1);
    expect(res.data.totalPages).toBe(25);
  });

  it('getAllStartups: returns empty content on last page', async () => {
    mock.onGet('/startups?page=99&size=10').reply(200, { content: [], totalPages: 3, totalElements: 25 });
    const res = await getAllStartups(99, 10);
    expect(res.data.content).toHaveLength(0);
  });

  it('getStartupsByFounder: returns empty array when founder has no startups', async () => {
    mock.onGet('/startups/founder/99').reply(200, []);
    const res = await getStartupsByFounder(99);
    expect(res.data).toHaveLength(0);
  });

  it('createStartup: fundingGoal of 1 (minimum positive value)', async () => {
    mock.onPost('/startups').reply(201, { ...mockStartup, fundingGoal: 1 });
    const res = await createStartup({ ...mockFormData, fundingGoal: 1 });
    expect(res.data.fundingGoal).toBe(1);
  });

  it('approveStartup: can be called on already-approved startup', async () => {
    mock.onPut('/startups/1/approve').reply(200, { ...mockStartup, isApproved: true });
    const res = await approveStartup(1);
    expect(res.data.isApproved).toBe(true);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('startupApi – Exception Handling', () => {
  it('getStartupById: rejects on 404 (not found)', async () => {
    mock.onGet('/startups/9999').reply(404);
    await expect(getStartupById(9999)).rejects.toThrow();
  });

  it('createStartup: rejects on 400 (bad input)', async () => {
    mock.onPost('/startups').reply(400, { message: 'Validation failed' });
    await expect(createStartup(mockFormData)).rejects.toThrow();
  });

  it('deleteStartup: rejects on 403 (not the owner)', async () => {
    mock.onDelete('/startups/1').reply(403, { message: 'Access denied' });
    await expect(deleteStartup(1)).rejects.toThrow();
  });

  it('updateStartup: rejects on 404 (startup does not exist)', async () => {
    mock.onPut('/startups/9999').reply(404);
    await expect(updateStartup(9999, mockFormData)).rejects.toThrow();
  });

  it('approveStartup: rejects on 403 (not admin)', async () => {
    mock.onPut('/startups/1/approve').reply(403);
    await expect(approveStartup(1)).rejects.toThrow();
  });

  it('getAllStartups: rejects on network error', async () => {
    mock.onGet(/\/startups/).networkError();
    await expect(getAllStartups()).rejects.toThrow();
  });

  it('followStartup: rejects on 401 (unauthenticated)', async () => {
    mock.onPost('/startups/1/follow').reply(401);
    await expect(followStartup(1)).rejects.toThrow();
  });
});
