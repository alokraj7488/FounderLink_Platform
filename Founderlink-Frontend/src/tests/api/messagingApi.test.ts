/**
 * messagingApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import {
  sendMessage,
  getConversationMessages,
  getMyConversations,
  startConversation,
} from '../../core/api/messagingApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

const mockMessage = {
  id: 1,
  senderId: 1,
  receiverId: 2,
  content: 'Hello!',
  conversationId: 10,
  createdAt: '2024-03-01T10:00:00Z',
};

const mockConversation = {
  id: 10,
  participant1Id: 1,
  participant2Id: 2,
  lastMessage: 'Hello!',
  updatedAt: '2024-03-01T10:00:00Z',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('messagingApi – Normal', () => {
  it('sendMessage: POSTs a message and returns the created message', async () => {
    mock.onPost('/messages').reply(201, mockMessage);
    const res = await sendMessage({ receiverId: 2, content: 'Hello!', senderId: 1, conversationId: 10 });
    expect(res.status).toBe(201);
    expect(res.data.content).toBe('Hello!');
    expect(res.data.senderId).toBe(1);
  });

  it('getConversationMessages: GETs all messages in a conversation', async () => {
    mock.onGet('/messages/conversation/10').reply(200, [mockMessage]);
    const res = await getConversationMessages(10);
    expect(res.data).toHaveLength(1);
    expect(res.data[0].conversationId).toBe(10);
  });

  it('getMyConversations: GETs all conversations for the current user', async () => {
    mock.onGet('/messages/conversations').reply(200, [mockConversation]);
    const res = await getMyConversations();
    expect(res.data).toHaveLength(1);
    expect(res.data[0].id).toBe(10);
  });

  it('startConversation: POSTs to create a conversation with another user', async () => {
    mock.onPost('/messages/conversations?otherUserId=2').reply(201, mockConversation);
    const res = await startConversation(2);
    expect(res.status).toBe(201);
    expect([res.data.participant1Id, res.data.participant2Id]).toContain(2);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('messagingApi – Boundary', () => {
  it('sendMessage: sends a message with the maximum length content', async () => {
    const longContent = 'a'.repeat(5000);
    mock.onPost('/messages').reply(201, { ...mockMessage, content: longContent });
    const res = await sendMessage({ receiverId: 2, content: longContent });
    expect(res.data.content.length).toBe(5000);
  });

  it('sendMessage: sends a message with empty content string', async () => {
    mock.onPost('/messages').reply(201, { ...mockMessage, content: '' });
    const res = await sendMessage({ receiverId: 2, content: '' });
    expect(res.status).toBe(201);
    expect(res.data.content).toBe('');
  });

  it('getConversationMessages: returns empty array for a new conversation', async () => {
    mock.onGet('/messages/conversation/99').reply(200, []);
    const res = await getConversationMessages(99);
    expect(res.data).toHaveLength(0);
  });

  it('getMyConversations: returns empty array when user has no conversations', async () => {
    mock.onGet('/messages/conversations').reply(200, []);
    const res = await getMyConversations();
    expect(res.data).toHaveLength(0);
  });

  it('sendMessage: works without optional senderId and conversationId', async () => {
    mock.onPost('/messages').reply(201, mockMessage);
    const res = await sendMessage({ receiverId: 2, content: 'Hi!' });
    expect(res.status).toBe(201);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('messagingApi – Exception Handling', () => {
  it('sendMessage: rejects on 403 (blocked user or no permission)', async () => {
    mock.onPost('/messages').reply(403, { message: 'Not allowed to message this user' });
    await expect(sendMessage({ receiverId: 999, content: 'Hi' })).rejects.toThrow();
  });

  it('sendMessage: rejects on 401 (unauthenticated)', async () => {
    mock.onPost('/messages').reply(401);
    await expect(sendMessage({ receiverId: 2, content: 'Hi' })).rejects.toThrow();
  });

  it('getConversationMessages: rejects on 404 (conversation not found)', async () => {
    mock.onGet('/messages/conversation/9999').reply(404);
    await expect(getConversationMessages(9999)).rejects.toThrow();
  });

  it('getConversationMessages: rejects on 403 (not a participant)', async () => {
    mock.onGet('/messages/conversation/10').reply(403, { message: 'Access denied' });
    await expect(getConversationMessages(10)).rejects.toThrow();
  });

  it('getMyConversations: rejects on 403 (session revoked / forbidden)', async () => {
    // Note: 401 on non-auth endpoints triggers the axios refresh interceptor which
    // uses a module-level isRefreshing flag; 403 gives the same "access denied"
    // semantics without the retry cycle and is the correct status for a revoked session.
    mock.onGet('/messages/conversations').reply(403, { message: 'Session revoked' });
    await expect(getMyConversations()).rejects.toThrow();
  });

  it('startConversation: rejects on 400 (invalid user id)', async () => {
    mock.onPost('/messages/conversations?otherUserId=0').reply(400);
    await expect(startConversation(0)).rejects.toThrow();
  });

  it('sendMessage: rejects on network error', async () => {
    mock.onPost('/messages').networkError();
    await expect(sendMessage({ receiverId: 2, content: 'Hello' })).rejects.toThrow();
  });
});
