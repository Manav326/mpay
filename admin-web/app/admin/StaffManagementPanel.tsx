'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  Check,
  CheckCircle2,
  Clock3,
  Eye,
  EyeOff,
  History,
  KeyRound,
  Mail,
  Phone,
  Plus,
  Search,
  ShieldCheck,
  UserCog,
  UserPlus,
  UsersRound,
  X,
} from 'lucide-react';
import { createPortalStaff, getPortalRoles, getPortalStaff, getPortalStaffActivity, updatePortalStaffStatus } from '@/lib/api';
import { PortalStaff, PortalStaffActivity } from '@/lib/types';

const roleMeta: Record<string, { label: string; description: string }> = {
  MANAGER: {
    label: 'Manager',
    description: 'Manages the admin areas granted to the role, with individual overrides available when needed.',
  },
  CUSTOMER_SUPPORT: {
    label: 'Customer care employee',
    description: 'Handles customer conversations, cases and voice support when those capabilities are granted.',
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

function shortDate(value?: string | null) {
  if (!value) return 'Never';
  return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(value));
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
  const [confirmBlockId, setConfirmBlockId] = useState('');
  const [employeeQuery, setEmployeeQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'BLOCKED'>('ALL');
  const [activityEmployee, setActivityEmployee] = useState<PortalStaff | null>(null);
  const [activity, setActivity] = useState<PortalStaffActivity[]>([]);
  const [activityLoading, setActivityLoading] = useState(false);
  const [formError, setFormError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
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

  const roleCounts = useMemo(
    () => staffRoles.map(role => {
      const people = employees.filter(employee => employee.role.toUpperCase() === role.toUpperCase());
      return {
        role,
        total: people.length,
        active: people.filter(employee => employee.active).length,
      };
    }),
    [employees, staffRoles]
  );

  const filteredEmployees = useMemo(() => {
    const query = employeeQuery.trim().toLowerCase();
    return employees.filter(employee => {
      if (roleFilter !== 'ALL' && employee.role.toUpperCase() !== roleFilter) return false;
      if (statusFilter === 'ACTIVE' && !employee.active) return false;
      if (statusFilter === 'BLOCKED' && employee.active) return false;
      if (!query) return true;
      return [
        employee.name,
        employee.mobile,
        employee.email,
        employee.role,
        employee.publicUserId,
      ].some(value => String(value || '').toLowerCase().includes(query));
    });
  }, [employees, employeeQuery, roleFilter, statusFilter]);

  const activeCount = employees.filter(employee => employee.active).length;
  const blockedCount = employees.length - activeCount;

  async function load() {
    setLoading(true);
    setRolesError('');
    setConfirmBlockId('');
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
    setFormError('');
    setShowPassword(false);
    setShowConfirmPassword(false);
    setForm(current => ({
      ...current,
      role: current.role || staffRoles[0] || '',
    }));
    setModalOpen(true);
  }

  function closeCreate() {
    if (!saving) {
      setModalOpen(false);
      setFormError('');
    }
  }

  function openAccess(employee?: PortalStaff) {
    onManageAccess();
    if (employee && typeof window !== 'undefined') {
      const url = '/admin/voice?employee=' + encodeURIComponent(employee.publicUserId);
      window.history.replaceState({ mpayAdminView: 'voice', employee: employee.publicUserId }, '', url);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setNotice('');
    setFormError('');

    const name = form.name.trim();
    const mobile = form.mobile.trim();

    if (!form.role) {
      setFormError('Choose the employee role first.');
      return;
    }
    if (name.length === 0 || name.length > 120) {
      setFormError('Enter a valid full name (up to 120 characters).');
      return;
    }
    if (!/^[6-9][0-9]{9}$/.test(mobile)) {
      setFormError('Enter a valid 10-digit Indian mobile number.');
      return;
    }
    if (form.password.length < 8 || form.password.length > 72) {
      setFormError('Password must be between 8 and 72 characters.');
      return;
    }
    if (form.password !== form.confirmPassword) {
      setFormError('The two passwords do not match.');
      return;
    }

    setSaving(true);
    try {
      await createPortalStaff({
        name,
        mobile,
        email: form.email.trim() || undefined,
        password: form.password,
        role: form.role,
      });
      setNotice(name + ' has been created. Review the employee’s access in Voice & Access.');
      setForm({ name: '', mobile: '', email: '', password: '', confirmPassword: '', role: staffRoles[0] || '' });
      setModalOpen(false);
      await load();
    } catch (error: any) {
      setFormError(error?.message || 'Unable to create the employee account.');
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
      setConfirmBlockId('');
      setNotice(employee.name + (employee.active ? ' is now blocked.' : ' is active again.'));
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
        <div className="staff-hero-copy">
          <div className="eyebrow">People & access</div>
          <h2>Team & Access</h2>
          <p>Employees are separate from customer accounts. Create the employee first, then grant only the capabilities their job requires.</p>
        </div>
        <div className="staff-hero-actions">
          <button className="secondary compact" onClick={() => void load()} disabled={loading}>
            <History size={13} /> Refresh
          </button>
          <button className="primary compact" onClick={openCreate} disabled={staffRoles.length === 0}>
            <UserPlus size={15} /> Add employee
          </button>
        </div>
      </section>

      {notice && (
        <div className="staff-management-notice success">
          <CheckCircle2 size={14} />
          <span>{notice}</span>
        </div>
      )}
      {rolesError && (
        <div className="staff-management-notice error">
          <ShieldCheck size={14} />
          <span>{rolesError}</span>
        </div>
      )}

      <section className="staff-summary-grid">
        <article className="staff-summary-card">
          <span><UsersRound size={16} /></span>
          <div><b>{employees.length}</b><small>Total employees</small></div>
        </article>
        <article className="staff-summary-card">
          <span className="success"><Check size={16} /></span>
          <div><b>{activeCount}</b><small>Active</small></div>
        </article>
        <article className="staff-summary-card">
          <span><UserCog size={16} /></span>
          <div><b>{staffRoles.length}</b><small>Employee roles</small></div>
        </article>
        <article className="staff-summary-card">
          <span className="muted"><Clock3 size={16} /></span>
          <div><b>{blockedCount}</b><small>Blocked</small></div>
        </article>
      </section>

      <section className="panel staff-role-panel">
        <div className="panel-head wrap staff-panel-head">
          <div>
            <div className="staff-section-kicker">Role blueprint</div>
            <h2>Employee roles</h2>
            <p>A job role defines the normal access baseline. Individual access can override it for one employee.</p>
          </div>
          <button className="secondary compact" onClick={() => openAccess()}>
            <ShieldCheck size={14} /> Manage access
          </button>
        </div>

        {loading && staffRoles.length === 0 ? <div className="empty-state">Loading employee roles…</div> :
          staffRoles.length === 0 ? <div className="empty-state">No employee roles are enabled yet.</div> :
          <div className="staff-role-grid">
            {roleCounts.map(item => (
              <article className="staff-role-card" key={item.role}>
                <div className="staff-role-icon"><UserCog size={16} /></div>
                <div className="staff-role-copy">
                  <b>{roleLabel(item.role)}</b>
                  <span>{roleMeta[item.role]?.description || 'Portal employee role.'}</span>
                  <div className="staff-role-meta">
                    <span>{item.total} {item.total === 1 ? 'person' : 'people'}</span>
                    <span className="active-dot"><i /> {item.active} active</span>
                  </div>
                </div>
              </article>
            ))}
          </div>
        }
      </section>

      <section className="panel staff-employee-panel">
        <div className="panel-head wrap staff-panel-head">
          <div>
            <div className="staff-section-kicker">People directory</div>
            <h2>Employees</h2>
            <p>Review account status, last sign-in and employee activity. Access changes remain in Voice & Access.</p>
          </div>
        </div>

        <div className="staff-directory-toolbar">
          <label className="staff-search">
            <Search size={14} />
            <input
              value={employeeQuery}
              onChange={event => setEmployeeQuery(event.target.value)}
              placeholder="Search name, mobile, email or employee ID"
              aria-label="Search employees"
            />
          </label>
          <select value={roleFilter} onChange={event => setRoleFilter(event.target.value)} aria-label="Filter employees by role">
            <option value="ALL">All roles</option>
            {staffRoles.map(role => <option key={role} value={role}>{roleLabel(role)}</option>)}
          </select>
          <select value={statusFilter} onChange={event => setStatusFilter(event.target.value as 'ALL' | 'ACTIVE' | 'BLOCKED')} aria-label="Filter employees by status">
            <option value="ALL">All status</option>
            <option value="ACTIVE">Active</option>
            <option value="BLOCKED">Blocked</option>
          </select>
        </div>

        <div className="staff-directory-meta">
          <span>{filteredEmployees.length} {filteredEmployees.length === 1 ? 'employee' : 'employees'} shown</span>
          {(employeeQuery || roleFilter !== 'ALL' || statusFilter !== 'ALL') && (
            <button className="staff-clear-filter" onClick={() => { setEmployeeQuery(''); setRoleFilter('ALL'); setStatusFilter('ALL'); }}>
              Clear filters
            </button>
          )}
        </div>

        {loading && employees.length === 0 ? <div className="empty-state">Loading employees…</div> :
          filteredEmployees.length === 0 ? (
            <div className="staff-empty-state">
              <div><UsersRound size={20} /></div>
              <b>{employees.length === 0 ? 'No employees have been created yet.' : 'No employees match these filters.'}</b>
              <span>{employees.length === 0 ? 'Create a Manager or Customer care employee, then grant the access they need.' : 'Try a different role, status or search term.'}</span>
              {employees.length === 0 && <button className="primary compact" onClick={openCreate} disabled={staffRoles.length === 0}><Plus size={14} /> Add first employee</button>}
            </div>
          ) : (
            <div className="staff-list">
              {filteredEmployees.map(employee => (
                <article className="staff-row" key={employee.publicUserId}>
                  <div className="staff-identity">
                    <div className="staff-avatar">{initials(employee.name, employee.mobile)}</div>
                    <div className="staff-identity-copy">
                      <div className="staff-name-line">
                        <b>{employee.name || 'Unnamed employee'}</b>
                        <span className={'status ' + (employee.active ? 'active' : 'blocked')}>{employee.active ? 'Active' : 'Blocked'}</span>
                      </div>
                      <span>{roleLabel(employee.role)} · {employee.mobile}</span>
                      {employee.email && <small>{employee.email}</small>}
                    </div>
                  </div>

                  <div className="staff-last-login">
                    <small>Last sign-in</small>
                    <b>{employee.lastLoginAt ? shortDate(employee.lastLoginAt) : 'Never'}</b>
                  </div>

                  <div className="staff-row-actions">
                    <button className="secondary compact" onClick={() => void openActivity(employee)}>
                      <History size={13} /> Activity
                    </button>
                    <button className="secondary compact" onClick={() => openAccess(employee)}>
                      <ShieldCheck size={13} /> Access
                    </button>
                    {employee.active ? (
                      confirmBlockId === employee.publicUserId ? (
                        <div className="staff-inline-confirm">
                          <span>Block employee?</span>
                          <button className="danger-confirm" onClick={() => void toggleStatus(employee)} disabled={statusBusy === employee.publicUserId}>Confirm</button>
                          <button className="confirm-cancel" onClick={() => setConfirmBlockId('')} disabled={statusBusy === employee.publicUserId}>Cancel</button>
                        </div>
                      ) : (
                        <button className="secondary compact danger" onClick={() => setConfirmBlockId(employee.publicUserId)}>
                          Block
                        </button>
                      )
                    ) : (
                      <button className="secondary compact" onClick={() => void toggleStatus(employee)} disabled={statusBusy === employee.publicUserId}>
                        {statusBusy === employee.publicUserId ? 'Activating…' : 'Activate'}
                      </button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
      </section>

      <section className="staff-safety-note">
        <KeyRound size={16} />
        <div>
          <b>Account creation and permissions are separate</b>
          <span>Creating an employee does not create a customer wallet. Calling, customer information, case management and AI controls are granted separately and remain auditable.</span>
        </div>
      </section>

      {modalOpen && (
        <div className="staff-modal-backdrop" onClick={closeCreate}>
          <section className="staff-modal" onClick={event => event.stopPropagation()} aria-labelledby="new-employee-title">
            <div className="staff-modal-head">
              <div>
                <span className="eyebrow">New employee</span>
                <h3 id="new-employee-title">Create a team member</h3>
                <p>Choose the job role now. Access is managed after the account is created.</p>
              </div>
              <button className="icon-btn" onClick={closeCreate} disabled={saving} aria-label="Close">
                <X size={17} />
              </button>
            </div>

            {formError && (
              <div className="staff-form-error">
                <ShieldCheck size={14} /> <span>{formError}</span>
              </div>
            )}

            <form onSubmit={submit} className="staff-form">
              <label>Job role
                <select value={form.role} onChange={event => setForm({ ...form, role: event.target.value })} required>
                  <option value="">Choose a role</option>
                  {staffRoles.map(role => <option key={role} value={role}>{roleLabel(role)}</option>)}
                </select>
              </label>

              <label>Full name
                <input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} placeholder="e.g. Ankit Kumar" maxLength={120} autoComplete="name" required />
              </label>

              <div className="staff-form-two">
                <label>Mobile number
                  <div className="staff-input-with-icon"><Phone size={14} /><input value={form.mobile} onChange={event => setForm({ ...form, mobile: event.target.value.replace(/\D/g, '').slice(0, 10) })} placeholder="10-digit mobile" inputMode="numeric" autoComplete="tel" required /></div>
                </label>
                <label>Email <span className="muted">(optional)</span>
                  <div className="staff-input-with-icon"><Mail size={14} /><input type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} placeholder="name@company.com" maxLength={254} autoComplete="email" /></div>
                </label>
              </div>

              <div className="staff-form-two">
                <label>Password
                  <div className="staff-input-with-icon staff-password-field">
                    <KeyRound size={14} />
                    <input type={showPassword ? 'text' : 'password'} value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} placeholder="8–72 characters" minLength={8} maxLength={72} autoComplete="new-password" required />
                    <button type="button" className="staff-password-toggle" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>
                      {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                  </div>
                </label>
                <label>Confirm password
                  <div className="staff-input-with-icon staff-password-field">
                    <KeyRound size={14} />
                    <input type={showConfirmPassword ? 'text' : 'password'} value={form.confirmPassword} onChange={event => setForm({ ...form, confirmPassword: event.target.value })} placeholder="Enter it again" minLength={8} maxLength={72} autoComplete="new-password" required />
                    <button type="button" className="staff-password-toggle" onClick={() => setShowConfirmPassword(value => !value)} aria-label={showConfirmPassword ? 'Hide confirmation password' : 'Show confirmation password'}>
                      {showConfirmPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                  </div>
                </label>
              </div>

              <div className="staff-password-hint">
                <CheckCircle2 size={13} />
                <span>Use 8–72 characters. The backend enforces the same limit.</span>
              </div>

              <div className="staff-form-note">
                <ShieldCheck size={14} />
                <span>The account starts active. No customer wallet or customer privileges are created.</span>
              </div>

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
          <section className="staff-modal staff-activity-modal" onClick={event => event.stopPropagation()} aria-labelledby="employee-activity-title">
            <div className="staff-modal-head">
              <div>
                <span className="eyebrow">Employee activity</span>
                <div className="staff-activity-heading">
                  <div className="staff-avatar small">{initials(activityEmployee.name, activityEmployee.mobile)}</div>
                  <div>
                    <h3 id="employee-activity-title">{activityEmployee.name || activityEmployee.mobile}</h3>
                    <p>{roleLabel(activityEmployee.role)} · {activityEmployee.active ? 'Active account' : 'Blocked account'} · Last sign-in: {formatDate(activityEmployee.lastLoginAt)}</p>
                  </div>
                </div>
              </div>
              <button className="icon-btn" onClick={() => !activityLoading && setActivityEmployee(null)} aria-label="Close"><X size={17} /></button>
            </div>

            {activityLoading ? <div className="empty-state">Loading activity…</div> :
              activity.length === 0 ? (
                <div className="staff-empty-state">
                  <div><Activity size={20} /></div>
                  <b>No recorded activity yet.</b>
                  <span>Important account, access and support actions will appear here. Routine page reading and searches are intentionally not stored.</span>
                </div>
              ) : (
                <div className="staff-activity-list">
                  {activity.map((item, index) => (
                    <article className="staff-activity-row" key={item.occurredAt + item.action + index}>
                      <div className="staff-activity-icon"><Activity size={13} /></div>
                      <div className="staff-activity-copy">
                        <div className="staff-activity-top">
                          <b>{item.summary}</b>
                          <time>{formatDate(item.occurredAt)}</time>
                        </div>
                        <span className="staff-activity-action">{item.action.replace(/_/g, ' ').toLowerCase()}</span>
                        {(item.subjectType || item.subjectId) && (
                          <div className="staff-activity-meta">
                            {item.subjectType && <span>{item.subjectType}</span>}
                            {item.subjectId && <span className="mono">{item.subjectId}</span>}
                          </div>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              )
            }

            <div className="staff-modal-footer">
              <span><Clock3 size={12} /> Latest recorded employee events</span>
              <button className="secondary compact" onClick={() => void openActivity(activityEmployee)} disabled={activityLoading}>
                Refresh activity
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
