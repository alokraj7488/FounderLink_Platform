import React, { useEffect } from 'react';
import { AxiosError } from 'axios';
import { useForm, type Resolver } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import type { InferType } from 'yup';
import { toast } from 'react-hot-toast';
import { Mail, Shield } from 'lucide-react';
import Layout from '../../shared/components/Layout';
import Button from '../../shared/components/Button';
import useAuth from '../../shared/hooks/useAuth';
import { getMyProfile, updateProfile } from '../../core/api/userApi';
import { profileSchema } from '../../shared/utils/validationSchemas';
import { ProfileFormData } from '../../types';

type ProfileFormValues = InferType<typeof profileSchema>;

const labelStyle: React.CSSProperties = {
  fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)',
  display: 'block', marginBottom: 6, fontFamily: "'DM Sans', system-ui, sans-serif",
};

const Profile: React.FC = () => {
  const { user, userId } = useAuth();
  const { register, handleSubmit, reset, formState: { isSubmitting } } = useForm<ProfileFormValues>({
    resolver: yupResolver(profileSchema) as Resolver<ProfileFormValues>,
  });

  useEffect(() => {
    if (!userId) return;
    getMyProfile(userId).then((res) => reset(res.data)).catch(() => undefined);
  }, [reset, userId]);

  const onSubmit = async (data: ProfileFormValues): Promise<void> => {
    if (!userId) { toast.error('User not found'); return; }
    try {
      await updateProfile(userId, { ...data, email: user?.email } as ProfileFormData & { email?: string });
      toast.success('Profile updated!');
    } catch (error) {
      const err = error as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message || 'Update failed');
    }
  };

  const roleLabel = user?.role?.replace('ROLE_', '') || 'User';
  const initial = user?.email?.[0]?.toUpperCase() || 'U';
  const roleColor = roleLabel === 'FOUNDER' ? 'var(--brand)' : roleLabel === 'INVESTOR' ? 'var(--green)' : roleLabel === 'COFOUNDER' ? 'var(--purple)' : 'var(--amber)';

  return (
    <Layout>
      <div style={{ maxWidth: 840, margin: '0 auto' }}>
        <div style={{ marginBottom: 28 }}>
          <span style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--brand)', display: 'block', marginBottom: 4 }}>Account</span>
          <h1 style={{ fontFamily: "'Syne', system-ui, sans-serif", fontSize: 28, fontWeight: 600, color: 'var(--text-primary)', margin: 0 }}>My profile</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: 14, marginTop: 4 }}>Manage your public profile and account details</p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 280px) 1fr', gap: 20, alignItems: 'start' }}>
          {/* Left — profile card */}
          <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 16, padding: 24, boxShadow: 'var(--shadow-card)', textAlign: 'center' }}>
            <div style={{
              width: 72, height: 72, borderRadius: 20,
              background: `linear-gradient(135deg, ${roleColor}33, ${roleColor}22)`,
              border: `2px solid ${roleColor}44`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              margin: '0 auto 16px',
              fontSize: 28, fontWeight: 700, color: roleColor,
              fontFamily: "'Syne', system-ui, sans-serif",
            }}>
              {initial}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginBottom: 8 }}>
              <Mail size={13} style={{ color: 'var(--text-faint)' }} />
              <p style={{ fontSize: 13, color: 'var(--text-secondary)', fontWeight: 500 }}>{user?.email}</p>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
              <Shield size={12} style={{ color: 'var(--text-faint)' }} />
              <span style={{ fontSize: 11, fontWeight: 600, color: roleColor, background: `${roleColor}1a`, padding: '3px 10px', borderRadius: 20, border: `1px solid ${roleColor}33` }}>
                {roleLabel}
              </span>
            </div>
          </div>

          {/* Right — form */}
          <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 16, padding: 28, boxShadow: 'var(--shadow-card)' }}>
            <form onSubmit={handleSubmit(onSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
              <div>
                <label style={labelStyle}>Full name</label>
                <input className="input-field" placeholder="Your full name" {...register('name')} />
              </div>
              <div>
                <label style={labelStyle}>Bio</label>
                <textarea
                  rows={3}
                  className="input-field"
                  style={{ height: 'auto', resize: 'vertical' }}
                  placeholder="Tell others a bit about yourself…"
                  {...register('bio')}
                />
              </div>
              <div>
                <label style={labelStyle}>Skills</label>
                <input className="input-field" placeholder="e.g. React, Java, Product Management" {...register('skills')} />
              </div>
              <div>
                <label style={labelStyle}>Experience</label>
                <textarea
                  rows={2}
                  className="input-field"
                  style={{ height: 'auto', resize: 'vertical' }}
                  placeholder="Your professional background…"
                  {...register('experience')}
                />
              </div>
              <div>
                <label style={labelStyle}>Portfolio / Links</label>
                <input className="input-field" placeholder="https://github.com/yourname" {...register('portfolioLinks')} />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: 4 }}>
                <Button type="submit" variant="primary" isLoading={isSubmitting}>Save changes</Button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Profile;
