/**
 * validationSchemas.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect } from 'vitest';
import {
  loginSchema,
  registerSchema,
  startupSchema,
  investmentSchema,
  profileSchema,
} from '../shared/utils/validationSchemas';

// ─────────────────────────────────────────────────────────────────────────────
// 1. loginSchema
// ─────────────────────────────────────────────────────────────────────────────
describe('loginSchema – Normal', () => {
  it('validates correct email and password', async () => {
    await expect(
      loginSchema.validate({ email: 'user@example.com', password: 'secret123' })
    ).resolves.toBeTruthy();
  });
});

describe('loginSchema – Boundary', () => {
  it('rejects password shorter than 6 characters', async () => {
    await expect(
      loginSchema.validate({ email: 'user@example.com', password: '12345' })
    ).rejects.toThrow('at least 6');
  });

  it('accepts password of exactly 6 characters', async () => {
    await expect(
      loginSchema.validate({ email: 'user@example.com', password: '123456' })
    ).resolves.toBeTruthy();
  });
});

describe('loginSchema – Exception Handling', () => {
  it('rejects missing email', async () => {
    await expect(loginSchema.validate({ password: 'secret123' })).rejects.toThrow('required');
  });

  it('rejects invalid email format', async () => {
    await expect(
      loginSchema.validate({ email: 'not-an-email', password: 'secret123' })
    ).rejects.toThrow('Invalid email');
  });

  it('rejects missing password', async () => {
    await expect(loginSchema.validate({ email: 'user@example.com' })).rejects.toThrow('required');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. registerSchema
// ─────────────────────────────────────────────────────────────────────────────
describe('registerSchema – Normal', () => {
  const valid = {
    name: 'Alice Bob',
    email: 'alice@example.com',
    password: 'password1',
    role: 'ROLE_FOUNDER' as const,
  };

  it('validates a complete valid registration object', async () => {
    await expect(registerSchema.validate(valid)).resolves.toBeTruthy();
  });

  it('accepts all valid roles', async () => {
    for (const role of ['ROLE_FOUNDER', 'ROLE_INVESTOR', 'ROLE_COFOUNDER'] as const) {
      await expect(registerSchema.validate({ ...valid, role })).resolves.toBeTruthy();
    }
  });
});

describe('registerSchema – Boundary', () => {
  it('rejects name shorter than 2 characters', async () => {
    await expect(
      registerSchema.validate({ name: 'A', email: 'a@b.com', password: 'pass12', role: 'ROLE_FOUNDER' })
    ).rejects.toThrow('at least 2');
  });

  it('accepts name of exactly 2 characters', async () => {
    await expect(
      registerSchema.validate({ name: 'Al', email: 'a@b.com', password: 'pass12', role: 'ROLE_FOUNDER' })
    ).resolves.toBeTruthy();
  });
});

describe('registerSchema – Exception Handling', () => {
  it('rejects an invalid role', async () => {
    await expect(
      registerSchema.validate({ name: 'Alice', email: 'a@b.com', password: 'pass12', role: 'ROLE_UNKNOWN' })
    ).rejects.toThrow();
  });

  it('rejects missing all fields', async () => {
    await expect(registerSchema.validate({})).rejects.toThrow();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. startupSchema
// ─────────────────────────────────────────────────────────────────────────────
const validStartup = {
  name: 'TechCo',
  industry: 'FinTech',
  description: 'A description that is longer than twenty characters.',
  problemStatement: 'The problem is very real.',
  solution: 'Our solution works.',
  fundingGoal: 100000,
  stage: 'MVP',
  location: 'Mumbai',
};

describe('startupSchema – Normal', () => {
  it('validates a fully valid startup object', async () => {
    await expect(startupSchema.validate(validStartup)).resolves.toBeTruthy();
  });
});

describe('startupSchema – Boundary', () => {
  it('rejects description shorter than 20 chars', async () => {
    await expect(
      startupSchema.validate({ ...validStartup, description: 'Too short' })
    ).rejects.toThrow('at least 20');
  });

  it('rejects negative fundingGoal', async () => {
    await expect(
      startupSchema.validate({ ...validStartup, fundingGoal: -1 })
    ).rejects.toThrow('positive');
  });

  it('rejects fundingGoal of 0', async () => {
    await expect(
      startupSchema.validate({ ...validStartup, fundingGoal: 0 })
    ).rejects.toThrow('positive');
  });

  it('accepts fundingGoal of 1', async () => {
    await expect(
      startupSchema.validate({ ...validStartup, fundingGoal: 1 })
    ).resolves.toBeTruthy();
  });
});

describe('startupSchema – Exception Handling', () => {
  it('rejects a non-numeric fundingGoal string', async () => {
    await expect(
      startupSchema.validate({ ...validStartup, fundingGoal: 'abc' as unknown as number })
    ).rejects.toThrow('number');
  });

  it('rejects missing required name', async () => {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { name: _name, ...rest } = validStartup;
    await expect(startupSchema.validate(rest)).rejects.toThrow('required');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4. investmentSchema
// ─────────────────────────────────────────────────────────────────────────────
describe('investmentSchema – Normal', () => {
  it('validates amount above minimum', async () => {
    await expect(investmentSchema.validate({ amount: 5000 })).resolves.toBeTruthy();
  });
});

describe('investmentSchema – Boundary', () => {
  it('accepts exactly ₹1,000 (minimum)', async () => {
    await expect(investmentSchema.validate({ amount: 1000 })).resolves.toBeTruthy();
  });

  it('rejects ₹999 (below minimum)', async () => {
    await expect(investmentSchema.validate({ amount: 999 })).rejects.toThrow('Minimum');
  });

  it('rejects 0', async () => {
    await expect(investmentSchema.validate({ amount: 0 })).rejects.toThrow();
  });

  it('rejects negative amount', async () => {
    await expect(investmentSchema.validate({ amount: -100 })).rejects.toThrow();
  });
});

describe('investmentSchema – Exception Handling', () => {
  it('rejects missing amount field', async () => {
    await expect(investmentSchema.validate({})).rejects.toThrow('required');
  });

  it('rejects non-numeric string', async () => {
    await expect(investmentSchema.validate({ amount: 'lots' as unknown as number })).rejects.toThrow('number');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5. profileSchema
// ─────────────────────────────────────────────────────────────────────────────
describe('profileSchema – Normal', () => {
  it('validates a full profile object', async () => {
    await expect(
      profileSchema.validate({
        name: 'Alice',
        bio: 'A short bio.',
        skills: 'React, Node',
        experience: '5 years',
        portfolioLinks: 'https://alice.dev',
      })
    ).resolves.toBeTruthy();
  });

  it('validates empty/null fields (all optional)', async () => {
    await expect(
      profileSchema.validate({ name: null, bio: null, skills: null, experience: null, portfolioLinks: null })
    ).resolves.toBeTruthy();
  });
});

describe('profileSchema – Boundary', () => {
  it('rejects bio longer than 500 characters', async () => {
    await expect(
      profileSchema.validate({ bio: 'a'.repeat(501) })
    ).rejects.toThrow('500');
  });

  it('accepts bio of exactly 500 characters', async () => {
    await expect(
      profileSchema.validate({ bio: 'a'.repeat(500) })
    ).resolves.toBeTruthy();
  });
});

describe('profileSchema – Exception Handling', () => {
  it('rejects an invalid URL for portfolioLinks', async () => {
    await expect(
      profileSchema.validate({ portfolioLinks: 'not-a-url' })
    ).rejects.toThrow('valid URL');
  });

  it('empty string portfolioLinks is transformed to null (no error)', async () => {
    await expect(
      profileSchema.validate({ portfolioLinks: '' })
    ).resolves.toBeTruthy();
  });
});
