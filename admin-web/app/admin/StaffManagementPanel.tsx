'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Activity, Check, History, KeyRound, Mail, Phone, Plus, ShieldCheck, UserCog, UserPlus, UsersRound, X } from 'lucide-react';
import { createPortalStaff, getPortalRoles, getPortalStaff, getPortalStaffActivity, updatePortalStaffStatus } from '@/lib/api';
import { PortalStaff, PortalStaffActivity } from '@/lib/types';

const roleMeta: Record<string, { label: string; description: string }> = {
  MANAGER: {
    label: 'Manager',
    description: 'A manager who can work with the admin areas allowed for the role.',
  },
  CUSTOMER_SUPPORT: {
    label: 'Customer care employee',
    description: 'A customer care employee who handles customer conversations and can receive voice access when granted.',
  },
};

function roleLabel(role: string) {
  return roleMeta[role]?.label || role.replace(/_/g, ' ').toLowerCase().replace(/(^| )\w/g, value => value.toUpperCase());
}

function initials(name?: string | null, mobile?: string) {
  const source = (name || mobile || 'E').trim();
  return source.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'E';
}

function formatDate(value?: string | null) {
  if (!value) return 'Never signed in';
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export default function StaffManagementPanel({ onManageAccess }: { onManageAccess: () => void }) {
  const [portalRoles, setPortalRoles] = useState<string[]>([]);
  const [employees, setEmployees] = useState<PortalStaff[]>([]);
  const [loading, setLoading] = useState(true);
  const [rolesError, setRolesError] = useState('');
  const [notice, setNotice] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusBusy, setStatusBusy] = useState('');
  const [activityEmployee, setActivityEmployee] = useState<PortalStaff | null>(null);
  const [activity, setActivity] = useState<PortalStaffActivity[]>([]);
  const [activityLoading, setActivityLoading] = useState(false);
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

  const activeCount = employees.filter(employee => employee.active).length;
  const blockedCount = employees.length - activeCount;

  async function load() {
    setLoading(true);
    setRolesError('');
    try {
      const [roles, staff] = await Promise.all([getPortalRoles(), getPortalStaff()]);
      const normalizedRoles = roles.map(role => role.toUpperCase());
      setPortalRoles(normalizedRoles);
      setEmployees(staff);
      setForm(current => ({
        ...current,
        role: current.role || normalizedRoles.find(role => !['ADMIN', 'CLIENT'].includes(role)) || '',
      }));
    } catch (error: any) {
      setRolesError(error?.message || 'Unable to load the employee team.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  function openCreate() {
    setNotice('');
    setForm(current => ({ ...current, role: current.role || staffRoles[0] || '' }));
    setModalOpen(true);
  }

  function closeCreate() {
    if (!saving) setModalOpen(false);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setNotice('');
    if (!form.role) {
      setNotice('Choose the employee role first.');
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
      setNotice((form.name.trim() || 'Employee') + ' has been created. Next, choose what this employee is allowed to do in Voice & Access.');
      setForm({ name: '', mobile: '', email: '', password: '', confirmPassword: '', role: staffRoles[0] || '' });
      setModalOpen(false);
      await load();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to create the employee account.');
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(employee: PortalStaff) {
    setStatusBusy(employee.publicUserId);
    setNotice('');
    try {
      const updated = await updatePortalStaffStatus(employee.publicUserId, !employee.active);
      setEmployees(current => current.map(item => item.publicUserId === employee.publicUserId
        ? { ...item, active: updated.active }
        : item
      ));
      setNotice((employee.name || 'Employee') + (employee.active ? ' is now blocked.' : ' is active again.'));
    } catch (error: any) {
      setNotice(error?.message || 'Unable to change the employee status.');
    } finally {
      setStatusBusy('');
    }
  }

  async function openActivity(employee: PortalStaff) {
    setActivityEmployee(employee);
    setActivity([]);
    setActivityLoading(true);
    try {
      setActivity(await getPortalStaffActivity(employee.publicUserId));
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load employee activity.');
    } finally {
      setActivityLoading(false);
    }
  }

  return (
    <div className="content staff-management-page">
      <section className="staff-management-hero">
        <div>
          <div className="eyebrow">People & access</div>
          <h2>Team & Access</h2>
          <p>Employees are separate from customer accounts. Create the employee first, then decide exactly what that employee may do.</p>
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
        <article className="staff-summary-card"><span><UsersRound size={17} /></span><div><b>{employees.length}</b><small>Employees</small></div></article>
        <article className="staff-summary-card"><span><Check size={17} /></span><div><b>{activeCount}</b><small>Active</small></div></article>
        <article className="staff-summary-card"><span><ShieldCheck size={17} /></span><div><b>{staffRoles.length}</b><small>Employee roles</small></div></article>
        <article className="staff-summary-card"><span><UserCog size={17} /></span><div><b>{blockedCount}</b><small>Blocked</small></div></article>
      </section>

      <section className="panel">
        <div className="panel-head wrap">
          <div>
            <h2>Employee roles</h2>
            <p>A job role tells you who the person is. Access tells you what they can do.</p>
          </div>
          <button className="secondary compact" onClick={onManageAccess}><ShieldCheck size={14} /> Voice & access</button>
        </div>
        <div className="staff-role-grid">
          {loading && staffRoles.length === 0 ? <div className="empty-state">Loading employee roles…</div> :
            staffRoles.length === 0 ? <div className="empty-state">No employee roles are enabled yet.</div> :
            staffRoles.map(role => {
              const roleUsers = employees.filter(employee => employee.role.toUpperCase() === role.toUpperCase());
              return (
                <article className="staff-role-card" key={role}>
                  <div className="staff-role-icon"><UserCog size={17} /></div>
                  <div className="staff-role-copy"><b>{roleLabel(role)}</b><span>{roleMeta[role]?.description || 'Portal employee role.'}</span></div>
                  <div className="staff-role-count">{roleUsers.length} {roleUsers.length === 1 ? 'person' : 'people'}</div>
                </article>
              );
            })}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head wrap">
          <div>
            <h2>Employees</h2>
            <p>Open activity to review important actions performed by an employee. Normal reads/searches are not recorded as noisy events.</p>
          </div>
        </div>
        {loading && employees.length === 0 ? <div className="empty-state">Loading employees…</div> :
          employees.length === 0 ? (
            <div className="staff-empty-state">
              <div><UsersRound size={20} /></div>
              <b>No employees have been created yet.</b>
              <span>Create a Manager or Customer care employee, then grant the access they need.</span>
              <button className="primary compact" onClick={openCreate} disabled={staffRoles.length === 0}><Plus size={14} /> Add first employee</button>
            </div>
          ) : (
            <div className="staff-list">
              {employees.map(employee => (
                <article className="staff-row" key={employee.publicUserId}>
                  <div className="staff-identity">
                    <div className="staff-avatar">{initials(employee.name, employee.mobile)}</div>
                    <div>
                      <b>{employee.name || 'Unnamed employee'}</b>
                      <span>{roleLabel(employee.role)} · {employee.mobile}</span>
                      {employee.email && <small>{employee.email}</small>}
                    </div>
                  </div>
                  <div className="staff-row-meta">
                    <span className={'status ' + (employee.active ? 'active' : 'blocked')}>{employee.active ? 'Active' : 'Blocked'}</span>
                    <button className="secondary compact" onClick={() => void openActivity(employee)}><History size={13} /> Activity</button>
                    <button className="secondary compact" onClick={onManageAccess}><ShieldCheck size={13} /> Access</button>
                    <button
                      className={employee.active ? 'secondary compact danger' : 'secondary compact'}
                      onClick={() => void toggleStatus(employee)}
                      disabled={statusBusy === employee.publicUserId}
                    >
                      {statusBusy === employee.publicUserId ? 'Saving…' : employee.active ? 'Block' : 'Activate'}
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
          <span>Creating an employee does not create a customer wallet or customer account. Calling, customer information, case management and other access are granted separately.</span>
        </div>
      </section>

      {modalOpen && (
        <div className="staff-modal-backdrop" onClick={closeCreate}>
          <section className="staff-modal" onClick={event => event.stopPropagation()}>
            <div className="staff-modal-head">
              <div><span className="eyebrow">New employee</span><h3>Create a team member</h3><p>Choose a plain job role. Access is managed after creation.</p></div>
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
                  <div className="staff-input-with-icon"><Phone size={14} /><input value={form.mobile} onChange={event => setForm({ ...form, mobile: event.target.value.replace(/\D/g, '').slice(0, 10) })} placeholder="10-digit mobile" inputMode="numeric" required /></div>
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
              <div className="staff-form-note"><ShieldCheck size={14} /><span>The account starts active. No customer wallet or customer privileges are created.</span></div>
              <div className="staff-form-actions">
                <button type="button" className="secondary" onClick={closeCreate} disabled={saving}>Cancel</button>
                <button type="submit" className="primary" disabled={saving || !form.role}>{saving ? 'Creating…' : 'Create employee'}</button>
              </div>
            </form>
          </section>
        </div>
      )}

      {activityEmployee && (
        <div className="staff-modal-backdrop" onClick={() => !activityLoading && setActivityEmployee(null)}>
          <section className="staff-modal staff-activity-modal" onClick={event => event.stopPropagation()}>
            <div className="staff-modal-head">
              <div>
                <span className="eyebrow">Employee activity</span>
                <h3>{activityEmployee.name || activityEmployee.mobile}</h3>
                <p>{roleLabel(activityEmployee.role)} · Last sign-in: {formatDate(activityEmployee.lastLoginAt)}</p>
              </div>
              <button className="icon-btn" onClick={() => !activityLoading && setActivityEmployee(null)} aria-label="Close"><X size={18} /></button>
            </div>
            {activityLoading ? <div className="empty-state">Loading activity…</div> :
              activity.length === 0 ? (
                <div className="staff-empty-state"><div><Activity size={20} /></div><b>No recorded activity yet.</b><span>Important account, access and support actions will appear here.</span></div>
              ) : (
                <div className="staff-activity-list">
                  {activity.map((item, index) => (
                    <article className="staff-activity-row" key={item.occurredAt + item.action + index}>
                      <div className="staff-activity-icon"><Activity size={14} /></div>
                      <div><b>{item.summary}</b><span>{item.action.replace(/_/g, ' ').toLowerCase()} · {formatDate(item.occurredAt)}</span></div>
                    </article>
                  ))}
                </div>
              )
            }
          </section>
        </div>
      )}
    </div>
  );
}
