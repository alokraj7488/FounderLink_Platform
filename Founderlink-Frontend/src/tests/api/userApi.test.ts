/**
 * userApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  getMyProfile,
  updateProfile,
  getUserById,
  searchUsersBySkill,
  getCoFounderIds,
  getUsersByRole,
  getAuthUserById,
  getProfilesBatch,
} from '../../core/api/userApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockProfile = {
  id: 1,
  name: 'Alice',
  email: 'alice@test.com',
  bio: 'Builder',
  skills: 'React, Node',
  experience: '3 years',
  portfolioLinks: 'https://alice.dev',
};

const mockAuthUser = { id: 1, email: 'alice@test.com', role: 'ROLE_COFOUNDER', name: 'Alice' };

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('userApi – Normal', () => {
  it('getMyProfile: GETs /users/:id and returns profile', async () => {
    mock.onGet('/users/1').reply(200, mockProfile);
    const res = await getMyProfile(1);
    expect(res.status).toBe(200);
    expect(res.data.name).toBe('Alice');
    expect(res.data.email).toBe('alice@test.com');
  });

  it('updateProfile: PUTs updated data and returns updated profile', async () => {
    mock.onPut('/users/1').reply(200, { ...mockProfile, bio: 'Updated bio' });
    const res = await updateProfile(1, { name: 'Alice', bio: 'Updated bio', skills: 'React', experience: '', portfolioLinks: '' });
    expect(res.data.bio).toBe('Updated bio');
  });

  it('getUserById: GETs user by id', async () => {
    mock.onGet('/users/1').reply(200, mockProfile);
    const res = await getUserById(1);
    expect(res.data.id).toBe(1);
  });

  it('searchUsersBySkill: GETs users matching skill', async () => {
    mock.onGet('/users/search?skill=React').reply(200, [mockProfile]);
    const res = await searchUsersBySkill('React');
    expect(res.data).toHaveLength(1);
    expect(res.data[0].skills).toContain('React');
  });

  it('getCoFounderIds: GETs co-founders from by-role endpoint', async () => {
    mock.onGet('/auth/users/by-role?role=ROLE_COFOUNDER').reply(200, [mockAuthUser]);
    const res = await getCoFounderIds();
    expect(res.data[0].role).toBe('ROLE_COFOUNDER');
  });

  it('getUsersByRole: GETs users with specified role', async () => {
    mock.onGet('/auth/users/by-role?role=ROLE_INVESTOR').reply(200, [{ ...mockAuthUser, role: 'ROLE_INVESTOR' }]);
    const res = await getUsersByRole('ROLE_INVESTOR');
    expect(res.data[0].role).toBe('ROLE_INVESTOR');
  });

  it('getAuthUserById: GETs auth user by id', async () => {
    mock.onGet('/auth/users/1').reply(200, mockAuthUser);
    const res = await getAuthUserById(1);
    expect(res.data.email).toBe('alice@test.com');
  });

  it('getProfilesBatch: builds query string and returns profile list', async () => {
    mock.onGet(/\/users\/profiles\/batch/).reply(200, [mockProfile]);
    const res = await getProfilesBatch([1, 2, 3]);
    expect(res.data).toHaveLength(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('userApi – Boundary', () => {
  it('getUserById: fetches user with id 0', async () => {
    mock.onGet('/users/0').reply(200, { ...mockProfile, id: 0 });
    const res = await getUserById(0);
    expect(res.status).toBe(200);
  });

  it('getProfilesBatch: handles single id', async () => {
    mock.onGet(/\/users\/profiles\/batch/).reply(200, [mockProfile]);
    const res = await getProfilesBatch([1]);
    expect(res.data).toHaveLength(1);
  });

  it('getProfilesBatch: handles empty id array', async () => {
    mock.onGet(/\/users\/profiles\/batch/).reply(200, []);
    const res = await getProfilesBatch([]);
    expect(res.data).toHaveLength(0);
  });

  it('getProfilesBatch: appends optional skill param when provided', async () => {
    mock.onGet(/\/users\/profiles\/batch/).reply(200, [mockProfile]);
    const res = await getProfilesBatch([1], 'TypeScript');
    expect(res.status).toBe(200);
  });

  it('searchUsersBySkill: encodes skill with special characters', async () => {
    mock.onGet('/users/search?skill=C%2B%2B').reply(200, []);
    const res = await searchUsersBySkill('C++');
    expect(res.data).toHaveLength(0);
  });

  it('updateProfile: handles all null optional fields', async () => {
    mock.onPut('/users/1').reply(200, mockProfile);
    const res = await updateProfile(1, { name: 'Alice', bio: '', skills: '', experience: '', portfolioLinks: '' });
    expect(res.status).toBe(200);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('userApi – Exception Handling', () => {
  it('getMyProfile: rejects on 404 (user not found)', async () => {
    mock.onGet('/users/999').reply(404, { message: 'User not found' });
    await expect(getMyProfile(999)).rejects.toThrow();
  });

  it('updateProfile: rejects on 400 (validation error)', async () => {
    mock.onPut('/users/1').reply(400, { message: 'Validation failed' });
    await expect(updateProfile(1, { name: '', bio: '', skills: '', experience: '', portfolioLinks: '' })).rejects.toThrow();
  });

  it('getUserById: rejects on 500 server error', async () => {
    mock.onGet('/users/1').reply(500);
    await expect(getUserById(1)).rejects.toThrow();
  });

  it('getAuthUserById: rejects on 403 Forbidden', async () => {
    mock.onGet('/auth/users/1').reply(403);
    await expect(getAuthUserById(1)).rejects.toThrow();
  });

  it('searchUsersBySkill: rejects on network error', async () => {
    mock.onGet(/\/users\/search/).networkError();
    await expect(searchUsersBySkill('React')).rejects.toThrow();
  });

  it('getUsersByRole: rejects on 401 Unauthorized', async () => {
    mock.onGet(/\/auth\/users\/by-role/).reply(401);
    await expect(getUsersByRole('ROLE_ADMIN')).rejects.toThrow();
  });
});
