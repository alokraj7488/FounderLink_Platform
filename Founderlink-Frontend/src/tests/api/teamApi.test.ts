/**
 * teamApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  inviteCoFounder,
  acceptInvitation,
  rejectInvitation,
  getTeamByStartup,
  getMyInvitations,
  updateMemberRole,
} from '../../core/api/teamApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockMember = {
  id: 1,
  userId: 2,
  startupId: 1,
  role: 'DEVELOPER',
  joinedAt: '2024-03-01T00:00:00Z',
};

const mockInvitation = {
  id: 5,
  invitedUserId: 2,
  startupId: 1,
  role: 'DEVELOPER',
  status: 'PENDING',
  createdAt: '2024-03-01T00:00:00Z',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('teamApi – Normal', () => {
  it('inviteCoFounder: POSTs invite data and returns the created invitation', async () => {
    mock.onPost('/teams/invite').reply(201, mockInvitation);
    const res = await inviteCoFounder({ invitedUserId: 2, startupId: 1, role: 'DEVELOPER' });
    expect(res.status).toBe(201);
    expect(res.data.status).toBe('PENDING');
    expect(res.data.invitedUserId).toBe(2);
  });

  it('acceptInvitation: POSTs to /join/:id and returns the team member', async () => {
    mock.onPost('/teams/join/5').reply(200, mockMember);
    const res = await acceptInvitation(5);
    expect(res.status).toBe(200);
    expect(res.data.role).toBe('DEVELOPER');
  });

  it('rejectInvitation: PUTs to /reject/:id and returns 200', async () => {
    mock.onPut('/teams/reject/5').reply(200);
    const res = await rejectInvitation(5);
    expect(res.status).toBe(200);
  });

  it('getTeamByStartup: GETs team members for a startup', async () => {
    mock.onGet('/teams/startup/1').reply(200, [mockMember]);
    const res = await getTeamByStartup(1);
    expect(res.data).toHaveLength(1);
    expect(res.data[0].startupId).toBe(1);
  });

  it('getMyInvitations: GETs the co-founder\'s pending invitations', async () => {
    mock.onGet('/teams/invitations/my').reply(200, [mockInvitation]);
    const res = await getMyInvitations();
    expect(res.data).toHaveLength(1);
    expect(res.data[0].status).toBe('PENDING');
  });

  it('updateMemberRole: PUTs new role and returns updated member', async () => {
    mock.onPut('/teams/1/role').reply(200, { ...mockMember, role: 'TECH_LEAD' });
    const res = await updateMemberRole(1, { role: 'TECH_LEAD' });
    expect(res.data.role).toBe('TECH_LEAD');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('teamApi – Boundary', () => {
  it('getTeamByStartup: returns empty array for a startup with no team', async () => {
    mock.onGet('/teams/startup/99').reply(200, []);
    const res = await getTeamByStartup(99);
    expect(res.data).toHaveLength(0);
  });

  it('getMyInvitations: returns empty array when no invitations exist', async () => {
    mock.onGet('/teams/invitations/my').reply(200, []);
    const res = await getMyInvitations();
    expect(res.data).toHaveLength(0);
  });

  it('getTeamByStartup: returns many members for a large team', async () => {
    const manyMembers = Array.from({ length: 50 }, (_, i) => ({ ...mockMember, id: i + 1, userId: i + 10 }));
    mock.onGet('/teams/startup/1').reply(200, manyMembers);
    const res = await getTeamByStartup(1);
    expect(res.data).toHaveLength(50);
  });

  it('updateMemberRole: accepts an empty role string without throwing at HTTP level', async () => {
    mock.onPut('/teams/1/role').reply(200, { ...mockMember, role: '' });
    const res = await updateMemberRole(1, { role: '' });
    expect(res.status).toBe(200);
  });

  it('inviteCoFounder: invite with a very long role string', async () => {
    mock.onPost('/teams/invite').reply(201, { ...mockInvitation, role: 'A'.repeat(200) });
    const res = await inviteCoFounder({ invitedUserId: 2, startupId: 1, role: 'A'.repeat(200) });
    expect(res.status).toBe(201);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('teamApi – Exception Handling', () => {
  it('inviteCoFounder: rejects on 409 (already invited)', async () => {
    mock.onPost('/teams/invite').reply(409, { message: 'User already invited' });
    await expect(inviteCoFounder({ invitedUserId: 2, startupId: 1, role: 'DEVELOPER' })).rejects.toThrow();
  });

  it('inviteCoFounder: rejects on 403 (not the startup founder)', async () => {
    mock.onPost('/teams/invite').reply(403, { message: 'Access denied' });
    await expect(inviteCoFounder({ invitedUserId: 2, startupId: 1, role: 'DEV' })).rejects.toThrow();
  });

  it('acceptInvitation: rejects on 404 (invitation not found)', async () => {
    mock.onPost('/teams/join/9999').reply(404, { message: 'Invitation not found' });
    await expect(acceptInvitation(9999)).rejects.toThrow();
  });

  it('acceptInvitation: rejects on 400 (invitation already actioned)', async () => {
    mock.onPost('/teams/join/5').reply(400, { message: 'Invitation already accepted' });
    await expect(acceptInvitation(5)).rejects.toThrow();
  });

  it('rejectInvitation: rejects on 404 (invitation not found)', async () => {
    mock.onPut('/teams/reject/9999').reply(404);
    await expect(rejectInvitation(9999)).rejects.toThrow();
  });

  it('updateMemberRole: rejects on 403 (not authorised)', async () => {
    mock.onPut('/teams/1/role').reply(403);
    await expect(updateMemberRole(1, { role: 'ADMIN' })).rejects.toThrow();
  });

  it('getTeamByStartup: rejects on network error', async () => {
    mock.onGet('/teams/startup/1').networkError();
    await expect(getTeamByStartup(1)).rejects.toThrow();
  });

  it('getMyInvitations: rejects on 401 (session expired)', async () => {
    mock.onGet('/teams/invitations/my').reply(401);
    await expect(getMyInvitations()).rejects.toThrow();
  });
});
