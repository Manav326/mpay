'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Check, KeyRound, Mail, Phone, Plus, ShieldCheck, UserCog, UserPlus, UsersRound, X } from 'lucide-react';
import { createPortalStaff, getPortalRoles, getUsers, updateUserStatus } from '@/lib/api';
import { PortalStaff, UserSummary } from '@/lib/types';

const roleMeta: Record<string, { label: string; description: string }> = {
  MANAGER: {
    label: 'Manager',
    description: 'A team manager who can work with the parts of the admin portal that you assign to the role.',
  },
  CUSTOMER_SUPPORT: {
    label: 'Customer care employee',
    description: 'A support employee who handles customer conversations and can receive voice-call access when granted.',
  },
};

function roleLabel(role: string) {
  return roleMeta[role]?.label || role.replace(/_/g, ' ').toLowerCase().replace(/(^| )\\w/g, s => s.toUpperCase());
}

function initials(name?: string | null, mobile?: string) {
  const source = (name || mobile || 'S').trim();
  return source.split(/\\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'S';
}

export default function StaffManagementPanel({ onManageAccess }: { onManageAccess: () => void }) {
  const [portalRoles, setPortalRoles] = useState<string[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [rolesError, setRolesError] = useState('');
  const [notice, setNotice] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusBusy, setStatusBusy] = useState('');
  const [form, setForm] = useState({
    name: '',
    mobile: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: '',
  });

  const staffRoles = useMemo(
    () => portalRoles.filter(role => !['ADMIN', 'CLIENT'].includes(role.toUpperCase())),
    [portalRoles]
  );

  const employees = useMemo(
    () => users.filter(user => staffRoles.some(role => role.toUpperCase() === user.role.toUpperCase()))
      .sort((a, b) => (a.name || '').localeCompare(b.name || '')),
    [users, staffRoles]
  );

  const activeCount = employees.filter(user => user.status === 'ACTIVE').length;
  const blockedCount = employees.length - activeCount;

  async function load() {
    setLoading(true);
    setRolesError('');
    try {
      const [roles, allUsers] = await Promise.all([
        getPortalRoles(),
        getUsers('ALL', 'today-high'),
      ]);
      setPortalRoles(roles.map(role => role.toUpperCase()));
      setUsers(allUsers);
      setForm(current => ({
        ...current,
        role: current.role || roles.find(role => !['ADMIN', 'CLIENT'].includes(role.toUpperCase())) || '',
      }));
    } catch (error: any) {
      setRolesError(error?.message || 'Unable to load team information.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  function openCreate() {
    setNotice('');
    setForm(current => ({
      ...current,
      role: current.role || staffRoles[0] || '',
    }));
    setModalOpen(true);
  }

  function closeCreate() {
    if (!saving) setModalOpen(false);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setNotice('');

    if (!form.role) {
      setNotice('Choose the type of employee first.');
      return;
    }
    if (form.password !== form.confirmPassword) {
      setNotice('The two passwords do not match.');
      return;
    }

    setSaving(true);
    try {
      await createPortalStaff({
        name: form.name.trim(),
        mobile: form.mobile.trim(),
        email: form.email.trim() || undefined,
        password: form.password,
        role: form.role,
      });
      setNotice((form.name.trim() || 'Employee') + ' has been created. You can now decide their permissions in Team & Access.');
      setForm({
        name: '',
        mobile: '',
        email: '',
        password: '',
        confirmPassword: '',
        role: staffRoles[0] || '',
      });
      setModalOpen(false);
      await load();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to create the employee account.');
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(user: UserSummary) {
    setStatusBusy(user.publicUserId);
    setNotice('');
    try {
      await updateUserStatus(user.publicUserId, user.status !== 'ACTIVE');
      setUsers(current => current.map(item => item.publicUserId === user.publicUserId
        ? { ...item, status: user.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE' }
        : item
      ));
      setNotice((user.name || 'Employee') + (user.status === 'ACTIVE' ? ' is now blocked.' : ' is active again.'));
    } catch (error: any) {
      setNotice(error?.message || 'Unable to change the employee status.');
    } finally {
      setStatusBusy('');
    }
  }

  return (
    <div className="content staff-management-page">
      <section className="staff-management-hero">
        <div>
          <div className="eyebrow">People & access</div>
          <h2>Team & Access</h2>
          <p>Create the people who work inside mPay. Their job role and their actual portal permissions are managed separately, so nothing is granted by accident.</p>
        </div>
        <div className="staff-hero-actions">
          <button className="secondary compact" onClick={() => void load()} disabled={loading}>Refresh</button>
          <button className="primary compact" onClick={openCreate} disabled={staffRoles.length === 0}>
            <UserPlus size={15} /> Add employee
          </button>
        </div>
      </section>

      {notice && <div className="staff-management-notice">{notice}</div>}
      {rolesError && <div className="staff-management-notice error">{rolesError}</div>}

      <section className="staff-summary-grid">
        <article className="staff-summary-card">
          <span><UsersRound size={17} /></span>
          <div><b>{employees.length}</b><small>Team members</small></div>
        </article>
        <article className="staff-summary-card">
          <span><Check size={17} /></span>
          <div><b>{activeCount}</b><small>Active</small></div>
        </article>
        <article className="staff-summary-card">
          <span><ShieldCheck size={17} /></span>
          <div><b>{staffRoles.length}</b><small>Employee roles</small></div>
        </article>
        <article className="staff-summary-card">
          <span><UserCog size={17} /></span>
          <div><b>{blockedCount}</b><small>Blocked</small></div>
        </article>
      </section>

      <section className="panel">
        <div className="panel-head wrap">
          <div>
            <h2>Employee roles</h2>
            <p>The role describes what kind of employee this is. Permissions are assigned afterwards from one place.</p>
          </div>
          <button className="secondary compact" onClick={onManageAccess}>
            <ShieldCheck size={14} /> Manage access
          </button>
        </div>
        <div className="staff-role-grid">
          {loading && staffRoles.length === 0 ? (
            <div className="empty-state">Loading employee roles…</div>
          ) : staffRoles.length === 0 ? (
            <div className="empty-state">No employee roles are enabled yet.</div>
          ) : staffRoles.map(role => {
            const roleUsers = employees.filter(user => user.role.toUpperCase() === role.toUpperCase());
            return (
              <article className="staff-role-card" key={role}>
                <div className="staff-role-icon"><UserCog size={17} /></div>
                <div className="staff-role-copy">
                  <b>{roleLabel(role)}</b>
                  <span>{roleMeta[role]?.description || 'Portal staff role.'}</span>
                </div>
                <div className="staff-role-count">{roleUsers.length} {roleUsers.length === 1 ? 'person' : 'people'}</div>
              </article>
            );
          })}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head wrap">
          <div>
            <h2>People on the team</h2>
            <p>Block an account when someone should no longer sign in. Blocking a person also prevents them from using active support calling access.</p>
          </div>
        </div>
        {loading && employees.length === 0 ? (
          <div className="empty-state">Loading team members…</div>
        ) : employees.length === 0 ? (
          <div className="staff-empty-state">
            <div><UsersRound size={20} /></div>
            <b>No employees have been created yet.</b>
            <span>Create a Manager or Customer care employee, then grant the access they need.</span>
            <button className="primary compact" onClick={openCreate} disabled={staffRoles.length === 0}><Plus size={14} /> Add first employee</button>
          </div>
        ) : (
          <div className="staff-list">
            {employees.map(user => (
              <article className="staff-row" key={user.publicUserId}>
                <div className="staff-identity">
                  <div className="staff-avatar">{initials(user.name, user.mobile)}</div>
                  <div>
                    <b>{user.name || 'Unnamed employee'}</b>
                    <span>{roleLabel(user.role)} · {user.mobile}</span>
                    {user.email && <small>{user.email}</small>}
                  </div>
                </div>
                <div className="staff-row-meta">
                  <span className={'status ' + (user.status === 'ACTIVE' ? 'active' : 'blocked')}>
                    {user.status === 'ACTIVE' ? 'Active' : 'Blocked'}
                  </span>
                  <button className="secondary compact" onClick={onManageAccess}>Access</button>
                  <button
                    className={user.status === 'ACTIVE' ? 'secondary compact danger' : 'secondary compact'}
                    onClick={() => void toggleStatus(user)}
                    disabled={statusBusy === user.publicUserId}
                  >
                    {statusBusy === user.publicUserId ? 'Saving…' : user.status === 'ACTIVE' ? 'Block' : 'Activate'}
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="staff-safety-note">
        <KeyRound size={17} />
        <div>
          <b>Account creation and permissions are separate</b>
          <span>Creating an employee only creates their sign-in account. Access such as Customer Care, customer financial details and voice calls is controlled separately.</span>
        </div>
      </section>

      {modalOpen && (
        <div className="staff-modal-backdrop" onClick={closeCreate}>
          <section className="staff-modal" onClick={event => event.stopPropagation()}>
            <div className="staff-modal-head">
              <div>
                <span className="eyebrow">New employee</span>
                <h3>Create a team member</h3>
                <p>Use plain job roles here. Permissions are handled after the account is created.</p>
              </div>
              <button className="icon-btn" onClick={closeCreate} disabled={saving} aria-label="Close"><X size={18} /></button>
            </div>

            <form onSubmit={submit} className="staff-form">
              <label>Job role
                <select value={form.role} onChange={event => setForm({ ...form, role: event.target.value })} required>
                  <option value="">Choose a role</option>
                  {staffRoles.map(role => <option key={role} value={role}>{roleLabel(role)}</option>)}
                </select>
              </label>
              <label>Full name
                <input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} placeholder="e.g. Ankit Kumar" maxLength={120} required />
              </label>
              <div className="staff-form-two">
                <label>Mobile number
                  <div className="staff-input-with-icon"><Phone size={14} /><input value={form.mobile} onChange={event => setForm({ ...form, mobile: event.target.value.replace(/\\D/g, '').slice(0, 10) })} placeholder="10-digit mobile" inputMode="numeric" required /></div>
                </label>
                <label>Email <span className="muted">(optional)</span>
                  <div className="staff-input-with-icon"><Mail size={14} /><input type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} placeholder="name@company.com" maxLength={254} /></div>
                </label>
              </div>
              <div className="staff-form-two">
                <label>First password
                  <div className="staff-input-with-icon"><KeyRound size={14} /><input type="password" value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} placeholder="At least 8 characters" minLength={8} maxLength={72} required /></div>
                </label>
                <label>Confirm password
                  <div className="staff-input-with-icon"><KeyRound size={14} /><input type="password" value={form.confirmPassword} onChange={event => setForm({ ...form, confirmPassword: event.target.value })} placeholder="Enter it again" minLength={8} maxLength={72} required /></div>
                </label>
              </div>
              <div className="staff-form-note">
                <ShieldCheck size={14} />
                <span>The account starts active. No extra permission is granted here beyond the role defaults already configured.</span>
              </div>
              <div className="staff-form-actions">
                <button type="button" className="secondary" onClick={closeCreate} disabled={saving}>Cancel</button>
                <button type="submit" className="primary" disabled={saving || !form.role}>{saving ? 'Creating…' : 'Create employee'}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
