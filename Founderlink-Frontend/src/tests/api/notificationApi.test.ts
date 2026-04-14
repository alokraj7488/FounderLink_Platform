/**
 * notificationApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  getNotifications,
  getUnreadNotifications,
  markAsRead,
} from '../../core/api/notificationApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockNotification = {
  id: 1,
  userId: 1,
  message: 'Your startup has been approved!',
  isRead: false,
  createdAt: '2024-03-01T09:00:00Z',
};

const mockReadNotification = { ...mockNotification, id: 2, isRead: true };

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('notificationApi – Normal', () => {
  it('getNotifications: GETs all notifications for a user', async () => {
    mock.onGet('/notifications/1').reply(200, [mockNotification, mockReadNotification]);
    const res = await getNotifications(1);
    expect(res.status).toBe(200);
    expect(res.data).toHaveLength(2);
    expect(res.data[0].userId).toBe(1);
  });

  it('getUnreadNotifications: GETs only unread notifications', async () => {
    mock.onGet('/notifications/1/unread').reply(200, [mockNotification]);
    const res = await getUnreadNotifications(1);
    expect(res.data).toHaveLength(1);
    expect(res.data[0].isRead).toBe(false);
  });

  it('markAsRead: PUTs read flag and returns 200', async () => {
    mock.onPut('/notifications/1/read').reply(200);
    const res = await markAsRead(1);
    expect(res.status).toBe(200);
  });

  it('getNotifications: returns a mix of read and unread notifications', async () => {
    mock.onGet('/notifications/1').reply(200, [mockNotification, mockReadNotification]);
    const res = await getNotifications(1);
    const unread = res.data.filter((n) => !n.isRead);
    const read = res.data.filter((n) => n.isRead);
    expect(unread).toHaveLength(1);
    expect(read).toHaveLength(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('notificationApi – Boundary', () => {
  it('getNotifications: returns an empty array when user has no notifications', async () => {
    mock.onGet('/notifications/99').reply(200, []);
    const res = await getNotifications(99);
    expect(res.data).toHaveLength(0);
  });

  it('getUnreadNotifications: returns empty array when all notifications are read', async () => {
    mock.onGet('/notifications/1/unread').reply(200, []);
    const res = await getUnreadNotifications(1);
    expect(res.data).toHaveLength(0);
  });

  it('getNotifications: returns a large number of notifications without error', async () => {
    const many = Array.from({ length: 200 }, (_, i) => ({ ...mockNotification, id: i + 1 }));
    mock.onGet('/notifications/1').reply(200, many);
    const res = await getNotifications(1);
    expect(res.data).toHaveLength(200);
  });

  it('markAsRead: works on an already-read notification (idempotent)', async () => {
    mock.onPut('/notifications/2/read').reply(200);
    const res = await markAsRead(2);
    expect(res.status).toBe(200);
  });

  it('getNotifications: handles userId of 0', async () => {
    mock.onGet('/notifications/0').reply(200, []);
    const res = await getNotifications(0);
    expect(res.status).toBe(200);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('notificationApi – Exception Handling', () => {
  it('getNotifications: rejects on 401 (unauthenticated)', async () => {
    mock.onGet('/notifications/1').reply(401);
    await expect(getNotifications(1)).rejects.toThrow();
  });

  it('getNotifications: rejects on 500 server error', async () => {
    mock.onGet('/notifications/1').reply(500, { message: 'Internal error' });
    await expect(getNotifications(1)).rejects.toThrow();
  });

  it('getUnreadNotifications: rejects on 403 (access denied)', async () => {
    mock.onGet('/notifications/1/unread').reply(403);
    await expect(getUnreadNotifications(1)).rejects.toThrow();
  });

  it('markAsRead: rejects on 404 (notification not found)', async () => {
    mock.onPut('/notifications/9999/read').reply(404, { message: 'Notification not found' });
    await expect(markAsRead(9999)).rejects.toThrow();
  });

  it('markAsRead: rejects on network error', async () => {
    mock.onPut('/notifications/1/read').networkError();
    await expect(markAsRead(1)).rejects.toThrow();
  });

  it('getUnreadNotifications: rejects on network timeout', async () => {
    mock.onGet('/notifications/1/unread').timeout();
    await expect(getUnreadNotifications(1)).rejects.toThrow();
  });
});
