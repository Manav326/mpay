'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  BellRing,
  Bot,
  Check,
  CheckCircle2,
  ChevronRight,
  ChevronDown,
  Clock3,
  FileText,
  Headset,
  Inbox,
  LockKeyhole,
  MessageCircle,
  MessageSquareText,
  PhoneCall,
  PhoneOff,
  RefreshCw,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  UserCheck,
  UserRound,
  WalletCards,
  X,
} from 'lucide-react';
import {
  addSupportCaseNote,
  createVoiceCall,
  declineCustomerCareRequest,
  getCustomerCareAccess,
  getCustomerCareChat,
  getCustomerCareCustomer,
  getCustomerCareQueue,
  getCustomerCareRequests,
  getCustomerCallbackAccess,
  getSupportAiSettings,
  getUserDetailById,
  markCustomerCareChatRead,
  releaseSupportCaseOwnership,
  sendCustomerCareChatMessage,
  startCustomerCareCall,
  takeSupportCaseOwnership,
  updateCustomerCallbackAccess,
  updateCustomerCareRolePermission,
  updateCustomerCareUserPermission,
  updateSupportAiSettings,
  updateSupportCase,
} from '@/lib/api';
import {
  SupportAiSettings,
  SupportAccessResponse,
  SupportCallRequest,
  SupportCase,
  SupportCaseEvent,
  SupportChat,
  SupportCustomer,
  SupportInteraction,
  SupportNote,
  SupportQueueResponse,
  SupportUserAccess,
  UserDetail,
  UserSummary,
} from '@/lib/types';
import { VoiceCallWidget } from './VoiceCallPanel';

type Props = {
  canManageSupport: boolean;
  canCallCustomer: boolean;
  canManageCallAccess: boolean;
  canManageSupportAi: boolean;
  canViewCustomerContext: boolean;
  canManageSupportAccess: boolean;
  users?: UserSummary[];
};

type ActivityFilter = 'ALL' | 'VOICE' | 'NOTE' | 'SYSTEM';
type DialogState =
  | { kind: 'decline'; request: SupportCallRequest }
  | { kind: 'note'; caseItem: SupportCase }
  | { kind: 'resolution'; caseItem: SupportCase; status: string }
  | null;

const dateTime = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '—';

const shortDate = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short' }).format(new Date(value))
    : '—';

const money = (value?: number | null) =>
  '₹' + Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const relativeAge = (value?: string | null) => {
  if (!value) return '—';
  const ms = Date.now() - new Date(value).getTime();
  const minutes = Math.max(0, Math.floor(ms / 60000));
  if (minutes < 1) return 'just now';
  if (minutes < 60) return minutes + 'm ago';
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + 'h ago';
  return Math.floor(hours / 24) + 'd ago';
};

const relativeUntil = (value?: string | null) => {
  if (!value) return '—';
  const minutes = Math.floor((new Date(value).getTime() - Date.now()) / 60000);
  if (minutes <= 0) return 'expired';
  if (minutes < 60) return minutes + 'm left';
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return hours + 'h ' + remainder + 'm left';
};

const initials = (name?: string | null, mobile?: string | null) => {
  const text = String(name || '').trim();
  if (!text) return String(mobile || 'mP').replace(/\D/g, '').slice(-2) || 'mP';
  return text
    .split(/\s+/)
    .slice(0, 2)
    .map(part => part.charAt(0).toUpperCase())
    .join('');
};

const statusTone = (status?: string | null) => {
  const value = String(status || '').toUpperCase();
  if (['OPEN', 'IN_PROGRESS', 'ACTIVE', 'ACCEPTED', 'CONNECTED'].includes(value)) return 'active';
  if (['RESOLVED', 'COMPLETED', 'ENDED', 'ANSWERED'].includes(value)) return 'success';
  if (['DECLINED', 'CANCELLED', 'MISSED', 'EXPIRED', 'FAILED'].includes(value)) return 'danger';
  return 'pending';
};

const priorityTone = (priority?: string | null) => {
  const value = String(priority || '').toUpperCase();
  if (value === 'HIGH' || value === 'URGENT') return 'danger';
  if (value === 'LOW') return 'quiet';
  return 'pending';
};

const interactionIcon = (item: SupportInteraction) => {
  if (item.channel === 'VOICE') return <PhoneCall size={14} />;
  return <MessageSquareText size={14} />;
};

export default function CustomerCarePanel({
  canManageSupport,
  canCallCustomer,
  canManageCallAccess,
  canManageSupportAi,
  users = [],
}: Props) {
  const [requests, setRequests] = useState<SupportCallRequest[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(true);
  const [queue, setQueue] = useState<SupportQueueResponse | null>(null);
  const [queueFilter, setQueueFilter] = useState<'ALL' | 'MINE' | 'UNASSIGNED' | 'CALLBACKS' | 'MESSAGES' | 'CASES'>('ALL');
  const [customerQuery, setCustomerQuery] = useState('');
  const [selected, setSelected] = useState<SupportCustomer | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<UserDetail | null>(null);
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [supportChat, setSupportChat] = useState<SupportChat | null>(null);
  const [chatLoading, setChatLoading] = useState(false);
  const [chatDraft, setChatDraft] = useState('');
  const [activityFilter, setActivityFilter] = useState<ActivityFilter>('ALL');
  const [activeSection, setActiveSection] = useState<'cases' | 'activity'>('cases');
  const [notice, setNotice] = useState('');
  const [busyKey, setBusyKey] = useState('');
  const [activeCallId, setActiveCallId] = useState<string | null>(null);
  const [activeCallName, setActiveCallName] = useState('');
  const [aiSettings, setAiSettings] = useState<SupportAiSettings | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [dialogText, setDialogText] = useState('');
  const [dialogVisibility, setDialogVisibility] = useState<'INTERNAL' | 'CUSTOMER'>('INTERNAL');
  const [dialogResolutionCode, setDialogResolutionCode] = useState('AGENT_HANDLED');
  const [chatAtBottom, setChatAtBottom] = useState(true);
  const [accessOpen, setAccessOpen] = useState(false);
  const [access, setAccess] = useState<SupportAccessResponse | null>(null);
  const [accessLoading, setAccessLoading] = useState(false);
  const [accessBusy, setAccessBusy] = useState('');
  const [accessStaffQuery, setAccessStaffQuery] = useState('');
  const [accessNotice, setAccessNotice] = useState('');
  const chatMessagesRef = useRef<HTMLDivElement | null>(null);

  const visibleClients = useMemo(() => {
    const query = customerQuery.trim().toLowerCase();
    return users
      .filter(item => String(item.role || '').toUpperCase() === 'CLIENT')
      .filter(item => {
        if (!query) return true;
        return [item.name, item.mobile, item.publicUserId, item.email]
          .some(value => String(value || '').toLowerCase().includes(query));
      })
      .slice(0, query ? 12 : 6);
  }, [users, customerQuery]);

  const selectedActiveCases = selected?.openCases.filter(item => item.status === 'OPEN') || [];
  const selectedResolvedCases = selected?.openCases.filter(item => item.status === 'RESOLVED') || [];
  const selectedOpenCase = selectedActiveCases[0];
  const selectedUnread = supportChat?.unreadForStaff || 0;
  const queueItems = useMemo(() => {
    const items = queue?.items || [];
    const query = customerQuery.trim().toLowerCase();
    return items.filter(item => {
      if (queueFilter === 'MINE' && !item.assignedToViewer) return false;
      if (queueFilter === 'UNASSIGNED' && item.assignedUserPublicId) return false;
      if (queueFilter === 'CALLBACKS' && !item.pendingCallback) return false;
      if (queueFilter === 'MESSAGES' && item.unreadMessages <= 0) return false;
      if (queueFilter === 'CASES' && !item.caseId) return false;
      if (!query) return true;
      return [item.customerName, item.customerMobile, item.customerPublicId, item.caseId, item.subject]
        .some(value => String(value || '').toLowerCase().includes(query));
    });
  }, [queue, queueFilter, customerQuery]);

  const activityItems = useMemo(() => {
    if (!selected) return [];
    const items = [
      ...selected.interactions.map(item => ({ kind: 'VOICE' as const, at: item.startedAt, item })),
      ...selected.notes.map(item => ({ kind: 'NOTE' as const, at: item.createdAt, item })),
      ...selected.events.map(item => ({ kind: 'SYSTEM' as const, at: item.createdAt, item })),
    ].sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime());
    if (activityFilter === 'ALL') return items;
    return items.filter(item => item.kind === activityFilter);
  }, [selected, activityFilter]);

  async function loadQueue() {
    try {
      setQueue(await getCustomerCareQueue());
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load the Customer Care attention queue.');
    }
  }

  async function loadRequests() {
    setRequestsLoading(true);
    try {
      const [requestData, queueData] = await Promise.all([
        getCustomerCareRequests(),
        getCustomerCareQueue(),
      ]);
      setRequests(requestData);
      setQueue(queueData);
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load the Customer Care queue.');
    } finally {
      setRequestsLoading(false);
    }
  }

  async function loadCustomerChat(publicId: string) {
    setChatLoading(true);
    try {
      const chat = await getCustomerCareChat(publicId);
      setSupportChat(chat);
      if (chatAtBottom && chat.unreadForStaff > 0) {
        const read = await markCustomerCareChatRead(publicId);
        setSupportChat(read);
        await loadQueue();
      }
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load the customer conversation.');
    } finally {
      setChatLoading(false);
    }
  }

  async function openCustomer(publicId: string) {
    if (!publicId) return;
    setSelectedLoading(true);
    setNotice('');
    try {
      const supportCustomer = await getCustomerCareCustomer(publicId);
      setSelected(supportCustomer);
      setActiveSection('cases');
      setActivityFilter('ALL');
      const detail = canViewCustomerContext
        ? await getUserDetailById(publicId).catch(() => null)
        : null;
      setSelectedDetail(detail);
      setChatAtBottom(true);
      void loadCustomerChat(publicId);
    } catch (error: any) {
      setNotice(error?.message || 'Unable to open this customer.');
    } finally {
      setSelectedLoading(false);
    }
  }

  async function takeOwnership(caseId?: string | null) {
    if (!caseId || !canManageSupport || busyKey) return;
    setBusyKey('ownership:' + caseId);
    try {
      await takeSupportCaseOwnership(caseId);
      setNotice('Case assigned to you.');
      if (selected) {
        await openCustomer(selected.customerPublicId);
      }
      await loadRequests();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to take ownership of this case.');
    } finally {
      setBusyKey('');
    }
  }

  async function releaseOwnership(caseId?: string | null) {
    if (!caseId || !canManageSupport || busyKey) return;
    setBusyKey('release:' + caseId);
    try {
      await releaseSupportCaseOwnership(caseId);
      setNotice('Case released back to the support queue.');
      if (selected) {
        await openCustomer(selected.customerPublicId);
      }
      await loadRequests();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to release this case.');
    } finally {
      setBusyKey('');
    }
  }

  async function sendSupportChatMessage() {
    if (!selected || !canManageSupport || !chatDraft.trim() || busyKey) return;
    setBusyKey('chat');
    try {
      const saved = await sendCustomerCareChatMessage(selected.customerPublicId, chatDraft.trim());
      setChatDraft('');
      setSupportChat(value => value ? {
        ...value,
        status: 'OPEN',
        messages: [...value.messages, saved],
        unreadForStaff: 0,
      } : value);
      setNotice('Reply sent to the customer.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to send the support reply.');
    } finally {
      setBusyKey('');
    }
  }

  async function startRequestCall(request: SupportCallRequest) {
    if (!canManageSupport || !canCallCustomer || busyKey) return;
    setBusyKey('call:' + request.requestId);
    try {
      const call = await startCustomerCareCall(request.requestId);
      setActiveCallId(call.callId);
      setActiveCallName(request.customerName || request.customerMobile || 'mPay customer');
      setNotice('Callback claimed and started. The customer must accept before audio connects.');
      await loadRequests();
      if (selected?.customerPublicId === request.customerPublicId) {
        await openCustomer(request.customerPublicId || '');
      }
    } catch (error: any) {
      setNotice(error?.message || 'Unable to start the customer callback.');
    } finally {
      setBusyKey('');
    }
  }

  async function directCall() {
    if (!selected || !canCallCustomer || busyKey) return;
    setBusyKey('direct-call');
    try {
      const call = await createVoiceCall(selected.customerPublicId);
      setActiveCallId(call.callId);
      setActiveCallName(selected.customerName || selected.mobile || 'mPay customer');
      setNotice('Voice support call started.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to start the customer call.');
    } finally {
      setBusyKey('');
    }
  }

  async function declineRequest(request: SupportCallRequest) {
    if (!canManageSupport || busyKey) return;
    setDialog({ kind: 'decline', request });
    setDialogText('');
  }

  async function confirmDecline() {
    if (!dialog || dialog.kind !== 'decline' || !canManageSupport || busyKey) return;
    setBusyKey('decline:' + dialog.request.requestId);
    try {
      await declineCustomerCareRequest(dialog.request.requestId, dialogText);
      setDialog(null);
      setDialogText('');
      setNotice('Callback request declined and documented.');
      await loadRequests();
      if (selected?.customerPublicId === dialog.request.customerPublicId) {
        await openCustomer(dialog.request.customerPublicId || '');
      }
    } catch (error: any) {
      setNotice(error?.message || 'Unable to decline the callback request.');
    } finally {
      setBusyKey('');
    }
  }

  async function toggleCallbackAccess() {
    if (!selected || !canManageCallAccess || busyKey) return;
    setBusyKey('callback-access');
    try {
      const current = await getCustomerCallbackAccess(selected.customerPublicId);
      const next = await updateCustomerCallbackAccess(selected.customerPublicId, !current.enabled);
      setSelected(value => value ? { ...value, callbackRequestEnabled: next.enabled } : value);
      setNotice(next.enabled ? 'Customer callback requests enabled.' : 'Customer callback requests disabled.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update callback access.');
    } finally {
      setBusyKey('');
    }
  }

  function requestCaseStatusChange(caseItem: SupportCase, nextStatus: string) {
    if (!canManageSupport || busyKey || nextStatus === caseItem.status) return;
    if (nextStatus === 'RESOLVED' || nextStatus === 'CLOSED') {
      setDialog({
        kind: 'resolution',
        caseItem,
        status: nextStatus,
      });
      setDialogText(caseItem.resolutionNote || '');
      setDialogResolutionCode(caseItem.resolutionCode || 'AGENT_HANDLED');
      return;
    }
    void persistCaseStatus(caseItem, nextStatus, undefined, undefined);
  }

  async function persistCaseStatus(
    caseItem: SupportCase,
    status: string,
    resolutionCode?: string,
    resolutionNote?: string
  ): Promise<boolean> {
    if (!canManageSupport || busyKey) return false;
    setBusyKey('case:' + caseItem.caseId);
    try {
      const updated = await updateSupportCase(caseItem.caseId, status, resolutionCode, resolutionNote);
      setSelected(value => value ? {
        ...value,
        openCases: value.openCases.map(item => item.caseId === updated.caseId ? updated : item),
      } : value);
      setNotice(status === 'OPEN' ? 'Support case reopened.' : 'Support case marked ' + status.toLowerCase() + '.');
      void loadQueue();
      return true;
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update the support case.');
      return false;
    } finally {
      setBusyKey('');
    }
  }

  async function confirmCaseResolution() {
    if (!dialog || dialog.kind !== 'resolution') return;
    const saved = await persistCaseStatus(dialog.caseItem, dialog.status, dialogResolutionCode, dialogText);
    if (!saved) return;
    setDialog(null);
    setDialogText('');
  }

  function openNoteDialog(caseItem: SupportCase) {
    if (!canManageSupport) return;
    setDialog({ kind: 'note', caseItem });
    setDialogText('');
    setDialogVisibility('INTERNAL');
  }

  async function confirmNote() {
    if (!dialog || dialog.kind !== 'note' || !dialogText.trim() || !canManageSupport || busyKey) return;
    setBusyKey('note:' + dialog.caseItem.caseId);
    try {
      const saved = await addSupportCaseNote(dialog.caseItem.caseId, dialogText.trim(), dialogVisibility);
      setSelected(value => value ? {
        ...value,
        notes: [saved, ...value.notes],
      } : value);
      setDialog(null);
      setDialogText('');
      setNotice(dialogVisibility === 'CUSTOMER' ? 'Customer-visible note added.' : 'Internal support note added.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to add the support note.');
    } finally {
      setBusyKey('');
    }
  }

  async function loadAiSettings() {
    try {
      setAiSettings(await getSupportAiSettings());
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load Customer Care AI settings.');
    }
  }

  async function openAccessPanel() {
    if (!canManageSupportAccess) return;
    setAccessOpen(true);
    setAccessLoading(true);
    setAccessNotice('');
    try {
      setAccess(await getCustomerCareAccess());
    } catch (error: any) {
      setAccessNotice(error?.message || 'Unable to load Customer Care permissions.');
    } finally {
      setAccessLoading(false);
    }
  }

  async function saveRolePermission(role: string, permission: string, enabled: boolean) {
    const key = 'role:' + role + ':' + permission;
    setAccessBusy(key);
    setAccessNotice('');
    try {
      const updated = await updateCustomerCareRolePermission(role, permission, enabled);
      setAccess(current => current ? {
        ...current,
        roles: current.roles.map(item => item.role === role ? updated : item),
      } : current);
      setAccessNotice((enabled ? 'Granted ' : 'Revoked ') + permission.replaceAll('_', ' ').toLowerCase() + ' for the ' + role + ' role.');
    } catch (error: any) {
      setAccessNotice(error?.message || 'Unable to update the role permission.');
    } finally {
      setAccessBusy('');
    }
  }

  async function saveUserPermission(publicUserId: string, permission: string, mode: 'DEFAULT' | 'ALLOW' | 'DENY') {
    const key = 'user:' + publicUserId + ':' + permission;
    setAccessBusy(key);
    setAccessNotice('');
    try {
      const updated = await updateCustomerCareUserPermission(publicUserId, permission, mode);
      setAccess(current => current ? {
        ...current,
        users: current.users.map(item => item.publicUserId === publicUserId ? updated : item),
      } : current);
      const label = permission.replaceAll('_', ' ').toLowerCase();
      setAccessNotice(
        mode === 'ALLOW' ? 'Granted ' + label + ' to ' + (updated.name || updated.mobile) + '.' :
        mode === 'DENY' ? 'Revoked ' + label + ' from ' + (updated.name || updated.mobile) + '.' :
        'Returned ' + label + ' to the role default for ' + (updated.name || updated.mobile) + '.'
      );
    } catch (error: any) {
      setAccessNotice(error?.message || 'Unable to update staff permission.');
    } finally {
      setAccessBusy('');
    }
  }

  async function toggleAi() {
    if (!canManageSupportAi || !aiSettings || aiBusy) return;
    setAiBusy(true);
    try {
      const next = await updateSupportAiSettings(!aiSettings.enabled);
      setAiSettings(next);
      setNotice(next.enabled ? 'Customer Care AI is available to customers.' : 'Customer Care AI is disabled.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update Customer Care AI.');
    } finally {
      setAiBusy(false);
    }
  }

  useEffect(() => {
    void loadAiSettings();
    void loadRequests();
    const timer = window.setInterval(() => { void loadRequests(); }, 5000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!selected?.customerPublicId) {
      setSupportChat(null);
      return;
    }
    setChatAtBottom(true);
    void loadCustomerChat(selected.customerPublicId);
    const timer = window.setInterval(() => { void loadCustomerChat(selected.customerPublicId); }, 5000);
    return () => window.clearInterval(timer);
  }, [selected?.customerPublicId]);

  useEffect(() => {
    const node = chatMessagesRef.current;
    if (node && chatAtBottom) node.scrollTop = node.scrollHeight;
  }, [supportChat?.messages.length, chatAtBottom]);

  async function handleChatScroll() {
    const node = chatMessagesRef.current;
    if (!node || !selected) return;
    const nearBottom = node.scrollHeight - node.scrollTop - node.clientHeight < 32;
    setChatAtBottom(nearBottom);
    if (nearBottom && supportChat?.unreadForStaff) {
      try {
        const read = await markCustomerCareChatRead(selected.customerPublicId);
        setSupportChat(read);
        void loadQueue();
      } catch {}
    }
  }

  return (
    <div className="customer-care-console">
      <section className="customer-care-topbar">
        <div>
          <div className="customer-care-kicker"><Headset size={13} /> SUPPORT OPERATIONS</div>
          <h2>Customer Care</h2>
          <p>Resolve customer issues from one compact workspace — conversation, case state, history and customer context stay connected.</p>
        </div>
        <div className="customer-care-top-actions">
          <div className="care-metric"><span>Callbacks waiting</span><strong>{requests.length}</strong></div>
          <div className="care-metric"><span>{selected ? 'Selected cases' : 'Active cases'}</span><strong>{selected ? selected.openCases.length : '—'}</strong></div>
          <div className={'care-metric ' + (selectedUnread ? 'attention' : '')}><span>Chat {selected ? 'unread' : 'status'}</span><strong>{selected ? selectedUnread : 'Ready'}</strong></div>
          <div className={'care-ai-badge ' + (aiSettings?.enabled ? 'on' : 'off')}>
            <Sparkles size={13} />
            <span>AI {aiSettings?.enabled ? 'On' : 'Off'}</span>
          </div>
          <button className="secondary compact" onClick={() => void loadRequests()} disabled={requestsLoading} title="Refresh queue">
            <RefreshCw size={14} className={requestsLoading ? 'spin' : ''} />
          </button>
        </div>
      </section>

      {notice && (
        <div className="customer-care-notice">
          <CheckCircle2 size={14} />
          <span>{notice}</span>
          <button onClick={() => setNotice('')} aria-label="Dismiss"><X size={14} /></button>
        </div>
      )}

      <section className="customer-care-workspace">
        <aside className="customer-care-queue">
          <div className="care-pane-head">
            <div>
              <span className="care-pane-eyebrow">WORK QUEUE</span>
              <h3>Needs your attention</h3>
            </div>
            <span className="care-count">{queue?.total ?? 0}</span>
          </div>

          <div className="care-search-box">
            <Search size={14} />
            <input
              value={customerQuery}
              onChange={event => setCustomerQuery(event.target.value)}
              placeholder="Search name, mobile or ID"
            />
            {customerQuery && <button onClick={() => setCustomerQuery('')} aria-label="Clear search"><X size={13} /></button>}
          </div>

          <div className="care-queue-section">
            <div className="care-section-label">
              <span><Inbox size={12} /> Attention queue</span>
              <small>{queue?.total ?? 0}</small>
            </div>
            <div className="care-queue-filters">
              {([
                ['ALL', 'All', queue?.total ?? 0],
                ['MINE', 'Mine', queue?.assignedToViewer ?? 0],
                ['UNASSIGNED', 'Unassigned', queue?.unassigned ?? 0],
                ['CALLBACKS', 'Callbacks', queue?.callbacks ?? 0],
                ['MESSAGES', 'New chats', queue?.unreadChats ?? 0],
                ['CASES', 'Cases', queue?.total ?? 0],
              ] as Array<[typeof queueFilter, string, number]>).map(([filter, label, count]) => (
                <button
                  key={filter}
                  className={queueFilter === filter ? 'active' : ''}
                  onClick={() => setQueueFilter(filter)}
                >
                  {label}<span>{count}</span>
                </button>
              ))}
            </div>

            {requestsLoading && !queue ? (
              <div className="care-empty">Loading support attention…</div>
            ) : queueItems.length ? (
              <div className="care-request-list">
                {queueItems
                  .filter(item => {
                    const query = customerQuery.trim().toLowerCase();
                    if (!query) return true;
                    return [item.customerName, item.customerMobile, item.customerPublicId, item.caseId, item.subject]
                      .some(value => String(value || '').toLowerCase().includes(query));
                  })
                  .map(item => (
                    <article
                      key={(item.caseId || item.customerPublicId) + ':' + item.source}
                      className={'care-request-card ' + (selected?.customerPublicId === item.customerPublicId ? 'selected' : '')}
                      onClick={() => void openCustomer(item.customerPublicId)}
                    >
                      <div className="care-request-main">
                        <div className="care-avatar small">{initials(item.customerName, item.customerMobile)}</div>
                        <div className="care-request-copy">
                          <b>{item.customerName || 'mPay customer'}</b>
                          <span>{item.customerMobile || item.customerPublicId || '—'}</span>
                        </div>
                        <span className={'care-status ' + priorityTone(item.priority)}>{item.priority || item.source}</span>
                      </div>
                      <div className="care-request-subline">
                        <span>{item.attentionReason}</span>
                        <span>{item.lastActivityAt ? relativeAge(item.lastActivityAt) : '—'}</span>
                      </div>
                      {item.assignedUserName ? (
                        <div className="care-queue-assignment">
                          <UserCheck size={11} /> {item.assignedToViewer ? 'Assigned to you' : 'Assigned to ' + item.assignedUserName}
                        </div>
                      ) : (
                        <div className="care-queue-assignment unassigned"><UserRound size={11} /> Unassigned</div>
                      )}
                      {item.pendingCallback?.reason && <p>{item.pendingCallback.reason}</p>}
                      <div className="care-request-actions">
                        {item.pendingCallback && canCallCustomer && (
                          <button
                            className="care-action primary"
                            disabled={!!busyKey}
                            onClick={event => { event.stopPropagation(); void startRequestCall(item.pendingCallback!); }}
                          >
                            <PhoneCall size={13} /> {busyKey === 'call:' + item.pendingCallback.requestId ? 'Calling…' : 'Call'}
                          </button>
                        )}
                        {item.pendingCallback && canManageSupport && (
                          <button
                            className="care-action"
                            disabled={!!busyKey}
                            onClick={event => { event.stopPropagation(); void declineRequest(item.pendingCallback!); }}
                          >
                            <PhoneOff size={13} /> Decline
                          </button>
                        )}
                        {item.caseId && canManageSupport && (
                          <button
                            className="care-action"
                            disabled={!!busyKey}
                            onClick={event => { event.stopPropagation(); void takeOwnership(item.caseId); }}
                          >
                            <UserCheck size={13} /> {item.assignedToViewer ? 'Owned' : 'Take over'}
                          </button>
                        )}
                      </div>
                    </article>
                  ))}
              </div>
            ) : (
              <div className="care-empty compact-empty">
                <CheckCircle2 size={16} />
                <b>Attention queue is clear</b>
                <span>Nothing currently needs active support attention.</span>
              </div>
            )}
          </div>

          <div className="care-queue-section directory">
            <div className="care-section-label">
              <span><UserRound size={12} /> Customer directory</span>
              <small>{visibleClients.length}</small>
            </div>
            {!users.length ? (
              <div className="care-empty">Customer directory is unavailable.</div>
            ) : visibleClients.length ? (
              <div className="care-directory-list">
                {visibleClients.map(customer => (
                  <button
                    key={customer.publicUserId}
                    className={'care-directory-row ' + (selected?.customerPublicId === customer.publicUserId ? 'selected' : '')}
                    onClick={() => void openCustomer(customer.publicUserId)}
                  >
                    <span className="care-avatar tiny">{initials(customer.name, customer.mobile)}</span>
                    <span className="care-directory-copy">
                      <b>{customer.name || 'mPay customer'}</b>
                      <small>{customer.mobile} · {customer.publicUserId}</small>
                    </span>
                    <ChevronRight size={13} />
                  </button>
                ))}
              </div>
            ) : (
              <div className="care-empty">No customers match this search.</div>
            )}
          </div>
        </aside>

        <main className="customer-care-main">
          {!selected ? (
            <div className="care-welcome">
              <div className="care-welcome-icon"><Headset size={24} /></div>
              <span className="care-pane-eyebrow">READY FOR THE NEXT CUSTOMER</span>
              <h3>Choose a callback or search for a customer</h3>
              <p>Start from the queue when a customer requests a call, or search the directory by name, mobile or mPay customer ID.</p>
              <div className="care-welcome-steps">
                <span><b>1</b> Identify the customer</span>
                <span><b>2</b> Read the conversation & case</span>
                <span><b>3</b> Resolve, reply or escalate</span>
              </div>
            </div>
          ) : (
            <>
              <section className="care-customer-header">
                <div className="care-customer-identity">
                  <div className="care-avatar large">
                    {selectedDetail?.profileImageUrl ? (
                      <img src={selectedDetail.profileImageUrl} alt="" />
                    ) : (
                      <span>{initials(selected.customerName, selected.mobile)}</span>
                    )}
                  </div>
                  <div className="care-customer-name">
                    <div className="care-name-line">
                      <h3>{selected.customerName || 'mPay customer'}</h3>
                      <span className={'care-status ' + statusTone(selectedDetail?.status)}>{selectedDetail?.status || 'ACTIVE'}</span>
                    </div>
                    <div className="care-customer-meta">
                      <span>{selected.mobile}</span>
                      <span>{selected.customerPublicId}</span>
                      {selectedDetail?.email && <span>{selectedDetail.email}</span>}
                    </div>
                  </div>
                </div>
                <div className="care-customer-actions">
                  {selected.pendingRequest && (
                    <button
                      className="primary compact"
                      disabled={!canCallCustomer || !!busyKey || !!activeCallId}
                      onClick={() => void startRequestCall(selected.pendingRequest!)}
                    >
                      <PhoneCall size={14} /> Call requested
                    </button>
                  )}
                  {canCallCustomer && !selected.pendingRequest && (
                    <button className="secondary compact" disabled={!!busyKey || !!activeCallId} onClick={() => void directCall()}>
                      <PhoneCall size={14} /> Call customer
                    </button>
                  )}
                  <button className="secondary compact" onClick={() => void openCustomer(selected.customerPublicId)} disabled={selectedLoading}>
                    <RefreshCw size={14} className={selectedLoading ? 'spin' : ''} />
                  </button>
                </div>
              </section>

              {selected.pendingRequest && (
                <section className="care-alert-strip">
                  <div className="care-alert-icon"><BellRing size={15} /></div>
                  <div>
                    <b>Customer is waiting for a callback</b>
                    <span>{selected.pendingRequest.reason || 'No reason provided'} · requested {relativeAge(selected.pendingRequest.requestedAt)} · {relativeUntil(selected.pendingRequest.expiresAt)}</span>
                  </div>
                  {canManageSupport && (
                    <button className="care-text-button danger" onClick={() => void declineRequest(selected.pendingRequest!)}>Decline request</button>
                  )}
                </section>
              )}

              <section className="care-conversation-card">
                <div className="care-card-head">
                  <div>
                    <div className="care-title-line">
                      <MessageCircle size={15} />
                      <h3>Customer conversation</h3>
                      {supportChat?.status && <span className={'care-status ' + statusTone(supportChat.status)}>{supportChat.status}</span>}
                      {supportChat?.unreadForStaff ? <span className="care-unread-badge">{supportChat.unreadForStaff} new</span> : null}
                    </div>
                    <p>AI and staff replies share the same authenticated support conversation.</p>
                  </div>
                  <button className="care-icon-button" onClick={() => void loadCustomerChat(selected.customerPublicId)} disabled={chatLoading} title="Refresh conversation">
                    <RefreshCw size={14} className={chatLoading ? 'spin' : ''} />
                  </button>
                </div>

                <div className="care-chat-window" ref={chatMessagesRef} onScroll={() => void handleChatScroll()}>
                  {chatLoading && !supportChat ? (
                    <div className="care-empty">Loading conversation…</div>
                  ) : supportChat?.messages.length ? (
                    supportChat.messages.map(message => (
                      <div key={message.messageId} className={'care-chat-row ' + (message.senderType === 'CUSTOMER' ? 'customer' : 'support')}>
                        <div className={'care-chat-bubble ' + (message.senderType === 'CUSTOMER' ? 'customer' : message.senderType === 'AI' ? 'ai' : 'support')}>
                          <div className="care-chat-author">
                            {message.senderType === 'AI' ? <Bot size={12} /> : message.senderType === 'CUSTOMER' ? <UserRound size={12} /> : <Headset size={12} />}
                            <b>{message.senderType === 'AI' ? 'mPay AI' : message.senderType === 'CUSTOMER' ? 'Customer' : 'mPay Support'}</b>
                            <time>{dateTime(message.createdAt)}</time>
                          </div>
                          <div className="care-chat-text">{message.message}</div>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="care-chat-empty">
                      <MessageSquareText size={19} />
                      <b>No customer message yet</b>
                      <span>The conversation will appear here when the customer starts chatting.</span>
                    </div>
                  )}
                </div>
                {supportChat?.unreadForStaff && !chatAtBottom && (
                  <button className="care-new-message-bar" onClick={() => {
                    const node = chatMessagesRef.current;
                    if (node) {
                      node.scrollTop = node.scrollHeight;
                      setChatAtBottom(true);
                    }
                    if (selected) void markCustomerCareChatRead(selected.customerPublicId).then(setSupportChat).catch(() => {});
                  }}>
                    <Inbox size={13} /> {supportChat.unreadForStaff} new message{supportChat.unreadForStaff === 1 ? '' : 's'} <ChevronDown size={12} />
                  </button>
                )}

                {canManageSupport && (
                  <div className="care-composer">
                    <textarea
                      value={chatDraft}
                      maxLength={4000}
                      onChange={event => setChatDraft(event.target.value)}
                      placeholder="Write a clear, customer-ready reply…"
                      rows={2}
                      onKeyDown={event => {
                        if (event.key === 'Enter' && !event.shiftKey) {
                          event.preventDefault();
                          void sendSupportChatMessage();
                        }
                      }}
                    />
                    <button className="primary compact" disabled={!chatDraft.trim() || busyKey === 'chat'} onClick={() => void sendSupportChatMessage()}>
                      <MessageSquareText size={14} /> {busyKey === 'chat' ? 'Sending…' : 'Send'}
                    </button>
                  </div>
                )}
              </section>

              <section className="care-tab-card">
                <div className="care-tab-bar">
                  <button className={activeSection === 'cases' ? 'active' : ''} onClick={() => setActiveSection('cases')}>
                    <FileText size={14} /> Cases <span>{selected.openCases.length}</span>
                  </button>
                  <button className={activeSection === 'activity' ? 'active' : ''} onClick={() => setActiveSection('activity')}>
                    <Clock3 size={14} /> Activity <span>{selected.interactions.length + selected.notes.length + selected.events.length}</span>
                  </button>
                </div>

                {activeSection === 'cases' ? (
                  <div className="care-case-panel">
                    <div className="care-case-toolbar">
                      <div>
                        <b>Cases requiring support action</b>
                        <span>Resolve or close only after the customer-facing outcome is documented.</span>
                      </div>
                      {selectedOpenCase && <span className={'care-status ' + priorityTone(selectedOpenCase.priority)}>{selectedOpenCase.priority}</span>}
                    </div>

                    {selected.openCases.length ? (
                      <div className="care-case-list">
                        {selected.openCases.map(caseItem => (
                          <article className="care-case-card" key={caseItem.caseId}>
                            <div className="care-case-top">
                              <div>
                                <div className="care-case-title">
                                  <b>{caseItem.subject}</b>
                                  <span className={'care-status ' + statusTone(caseItem.status)}>{caseItem.status}</span>
                                </div>
                                <span>{caseItem.category} · {caseItem.source} · updated {shortDate(caseItem.updatedAt)}</span>
                              </div>
                              <span className={'care-status ' + priorityTone(caseItem.priority)}>{caseItem.priority}</span>
                            </div>

                            <div className="care-case-facts">
                              <span><small>Case</small><b>{caseItem.caseId}</b></span>
                              <span><small>Assigned</small><b>{caseItem.assignedUserPublicId || 'Unassigned'}</b></span>
                              <span><small>Opened</small><b>{shortDate(caseItem.createdAt)}</b></span>
                              <span><small>Source</small><b>{caseItem.source.replaceAll('_', ' ')}</b></span>
                            </div>

                            {caseItem.resolutionNote && (
                              <div className="care-resolution-note">
                                <CheckCircle2 size={13} />
                                <span>{caseItem.resolutionNote}</span>
                              </div>
                            )}

                            <div className="care-case-actions">
                              <select
                                value={caseItem.status}
                                disabled={!canManageSupport || busyKey === 'case:' + caseItem.caseId}
                                onChange={event => requestCaseStatusChange(caseItem, event.target.value)}
                                aria-label="Case status"
                              >
                                <option value="OPEN">Open</option>
                                <option value="RESOLVED">Resolved</option>
                                <option value="CLOSED">Closed</option>
                              </select>
                              {canManageSupport && (
                                <button className="secondary compact" onClick={() => openNoteDialog(caseItem)} disabled={!!busyKey}>
                                  <MessageSquareText size={13} /> Add note
                                </button>
                              )}
                            </div>
                          </article>
                        ))}
                      </div>
                    ) : (
                      <div className="care-empty compact-empty">
                        <CheckCircle2 size={16} />
                        <b>No open support cases</b>
                        <span>The customer account has no unresolved case right now.</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="care-activity-panel">
                    <div className="care-activity-toolbar">
                      <div>
                        <b>Support activity</b>
                        <span>Calls, notes and system events in one chronological trail.</span>
                      </div>
                      <div className="care-filter-pills">
                        {(['ALL', 'VOICE', 'NOTE', 'SYSTEM'] as ActivityFilter[]).map(filter => (
                          <button key={filter} className={activityFilter === filter ? 'active' : ''} onClick={() => setActivityFilter(filter)}>
                            {filter === 'ALL' ? 'All' : filter === 'VOICE' ? 'Calls' : filter === 'NOTE' ? 'Notes' : 'System'}
                          </button>
                        ))}
                      </div>
                    </div>
                    {activityItems.length ? (
                      <div className="care-activity-list">
                        {activityItems.map(entry => {
                          if (entry.kind === 'VOICE') {
                            const item = entry.item as SupportInteraction;
                            return (
                              <article className="care-activity-item" key={item.interactionId}>
                                <div className="care-activity-icon voice"><PhoneCall size={13} /></div>
                                <div className="care-activity-copy">
                                  <div className="care-activity-title">
                                    <b>Voice call</b>
                                    <span className={'care-status ' + statusTone(item.status)}>{item.status}</span>
                                  </div>
                                  <span>{item.actorName || 'mPay Support'} · {dateTime(item.startedAt)} · {item.outcome || 'No outcome recorded'}</span>
                                  <div className="care-activity-meta">
                                    <span>Ring {item.ringDurationLabel || '—'}</span>
                                    <span>Talk {item.durationLabel || '—'}</span>
                                    <span>Handling {item.handlingDurationLabel || '—'}</span>
                                  </div>
                                </div>
                              </article>
                            );
                          }
                          if (entry.kind === 'NOTE') {
                            const item = entry.item as SupportNote;
                            return (
                              <article className="care-activity-item" key={'note-' + item.id}>
                                <div className="care-activity-icon note"><MessageSquareText size={13} /></div>
                                <div className="care-activity-copy">
                                  <div className="care-activity-title">
                                    <b>{item.visibility === 'CUSTOMER' ? 'Customer-visible note' : 'Internal note'}</b>
                                    <span>{item.authorName || 'Support'}</span>
                                  </div>
                                  <span>{dateTime(item.createdAt)}</span>
                                  <p>{item.note}</p>
                                </div>
                              </article>
                            );
                          }
                          const item = entry.item as SupportCaseEvent;
                          return (
                            <article className="care-activity-item" key={'event-' + item.eventId}>
                              <div className="care-activity-icon system"><Clock3 size={13} /></div>
                              <div className="care-activity-copy">
                                <div className="care-activity-title">
                                  <b>{item.eventType.replaceAll('_', ' ')}</b>
                                  <span>{item.actorName || 'System'}</span>
                                </div>
                                <span>{dateTime(item.createdAt)}</span>
                                <p>{item.summary}</p>
                              </div>
                            </article>
                          );
                        })}
                      </div>
                    ) : (
                      <div className="care-empty">No activity matches this filter.</div>
                    )}
                  </div>
                )}
              </section>
            </>
          )}
        </main>

        <aside className="customer-care-context">
          {selected ? (
            <>
              <section className="care-context-card identity-card">
                <div className="care-context-head">
                  <span>ACCOUNT SNAPSHOT</span>
                  <WalletCards size={14} />
                </div>
                <div className="care-wallet-balance">{money(selectedDetail?.availableBalance)}</div>
                <div className="care-wallet-label">available wallet balance</div>
                <div className="care-wallet-grid">
                  <span><small>Total</small><b>{money(selectedDetail?.balance)}</b></span>
                  <span><small>Reserved</small><b>{money(selectedDetail?.reservedBalance)}</b></span>
                </div>
                <div className="care-divider" />
                <div className="care-context-list">
                  <div><span>Recharge volume</span><b>{selectedDetail?.rechargeCount ?? '—'}</b></div>
                  <div><span>Add money</span><b>{selectedDetail ? money(selectedDetail.addMoneyTotal) : '—'}</b></div>
                  <div><span>Withdrawn</span><b>{selectedDetail ? money(selectedDetail.withdrawalTotal) : '—'}</b></div>
                </div>
              </section>

              <section className="care-context-card">
                <div className="care-context-head">
                  <span>RECENT ACTIVITY</span>
                  <Clock3 size={14} />
                </div>
                {selectedDetail?.latestRecharge ? (
                  <div className="care-mini-activity">
                    <div className="care-mini-icon recharge"><MessageSquareText size={13} /></div>
                    <div>
                      <b>Latest recharge</b>
                      <span>{selectedDetail.latestRecharge.operator} · {money(selectedDetail.latestRecharge.amount)}</span>
                      <small>{selectedDetail.latestRecharge.status} · {relativeAge(selectedDetail.latestRecharge.createdAt)}</small>
                    </div>
                  </div>
                ) : (
                  <div className="care-muted">No recent recharge found.</div>
                )}
                {selectedDetail?.recentWalletEntries?.slice(0, 3).map(entry => (
                  <div className="care-mini-activity" key={entry.id}>
                    <div className="care-mini-icon wallet"><WalletCards size={13} /></div>
                    <div>
                      <b>{entry.type.replaceAll('_', ' ')}</b>
                      <span>{money(entry.amount)} · {entry.reference}</span>
                      <small>{relativeAge(entry.createdAt)}</small>
                    </div>
                  </div>
                ))}
              </section>

              <section className="care-context-card">
                <div className="care-context-head">
                  <span>ESCALATION & ACCESS</span>
                  <ShieldCheck size={14} />
                </div>
                <div className="care-access-row">
                  <div>
                    <b>Callback permission</b>
                    <span>Customer can request a voice callback.</span>
                  </div>
                  {canManageCallAccess ? (
                    <button
                      className={'care-toggle ' + (selected.callbackRequestEnabled ? 'on' : '')}
                      disabled={!!busyKey}
                      onClick={() => void toggleCallbackAccess()}
                      aria-label="Toggle callback permission"
                    >
                      <span />
                    </button>
                  ) : (
                    <span className={'care-status ' + (selected.callbackRequestEnabled ? 'success' : 'quiet')}>
                      {selected.callbackRequestEnabled ? 'ON' : 'OFF'}
                    </span>
                  )}
                </div>
                <div className="care-access-row">
                  <div>
                    <b>Callback status</b>
                    <span>{selected.pendingRequest ? 'Customer is waiting for support.' : 'No callback pending.'}</span>
                  </div>
                  <span className={'care-status ' + statusTone(selected.pendingRequest?.status)}>
                    {selected.pendingRequest?.status || 'CLEAR'}
                  </span>
                </div>
              </section>

              <section className="care-context-card">
                <div className="care-context-head">
                  <span>AI CUSTOMER CARE</span>
                  <Sparkles size={14} />
                </div>
                <div className="care-ai-context">
                  <div className={'care-ai-dot ' + (aiSettings?.enabled ? 'on' : 'off')} />
                  <div>
                    <b>{aiSettings?.enabled ? 'AI replies enabled' : 'Human support only'}</b>
                    <span>{aiSettings?.providerConfigured ? aiSettings.model : 'Provider not configured'}</span>
                  </div>
                </div>
                {canManageSupportAi && (
                  <button
                    className={'care-ai-toggle ' + (aiSettings?.enabled ? 'on' : '')}
                    disabled={!aiSettings?.providerConfigured || aiBusy}
                    onClick={() => void toggleAi()}
                  >
                    <Bot size={13} />
                    {aiBusy ? 'Updating…' : aiSettings?.enabled ? 'Disable AI' : 'Enable AI'}
                  </button>
                )}
              </section>
            </>
          ) : (
            <section className="care-context-card care-context-placeholder">
              <div className="care-context-head"><span>CUSTOMER CONTEXT</span><UserRound size={14} /></div>
              <div className="care-context-placeholder-icon"><UserRound size={20} /></div>
              <b>Select a customer</b>
              <span>The customer's wallet, support history and escalation controls will appear here.</span>
            </section>
          )}
        </aside>
      </section>

      {activeCallId && (
        <VoiceCallWidget
          callId={activeCallId}
          customerName={activeCallName}
          onClosed={() => {
            setActiveCallId(null);
            setActiveCallName('');
            if (selected) void openCustomer(selected.customerPublicId);
            void loadRequests();
          }}
        />
      )}

      {dialog && (
        <div className="care-dialog-backdrop" onClick={() => !busyKey && setDialog(null)}>
          <div className="care-dialog" onClick={event => event.stopPropagation()}>
            <div className="care-dialog-head">
              <div>
                <span className="care-pane-eyebrow">
                  {dialog.kind === 'decline' ? 'CALLBACK DECISION' : dialog.kind === 'note' ? 'SUPPORT NOTE' : 'CASE RESOLUTION'}
                </span>
                <h3>
                  {dialog.kind === 'decline'
                    ? 'Decline callback request'
                    : dialog.kind === 'note'
                      ? 'Add support note'
                      : dialog.status === 'RESOLVED'
                        ? 'Resolve support case'
                        : 'Close support case'}
                </h3>
              </div>
              <button className="care-icon-button" onClick={() => !busyKey && setDialog(null)} aria-label="Close dialog"><X size={15} /></button>
            </div>

            {dialog.kind === 'decline' && (
              <>
                <p className="care-dialog-copy">Add a brief reason so the next support agent and the audit trail can understand why the request was declined.</p>
                <textarea value={dialogText} onChange={event => setDialogText(event.target.value.slice(0, 1000))} placeholder="Optional decline reason" rows={4} />
                <div className="care-dialog-actions">
                  <button className="secondary compact" onClick={() => setDialog(null)} disabled={!!busyKey}>Cancel</button>
                  <button className="primary compact" onClick={() => void confirmDecline()} disabled={!!busyKey}>
                    <PhoneOff size={13} /> {busyKey ? 'Saving…' : 'Decline request'}
                  </button>
                </div>
              </>
            )}

            {dialog.kind === 'note' && (
              <>
                <p className="care-dialog-copy">Keep internal context private by default. Choose customer-visible only when the note is explicitly meant to be shown in the support record.</p>
                <div className="care-segment">
                  <button className={dialogVisibility === 'INTERNAL' ? 'active' : ''} onClick={() => setDialogVisibility('INTERNAL')}>Internal</button>
                  <button className={dialogVisibility === 'CUSTOMER' ? 'active' : ''} onClick={() => setDialogVisibility('CUSTOMER')}>Customer-visible</button>
                </div>
                <textarea value={dialogText} onChange={event => setDialogText(event.target.value.slice(0, 2000))} placeholder="Write the support note…" rows={5} />
                <div className="care-dialog-actions">
                  <button className="secondary compact" onClick={() => setDialog(null)} disabled={!!busyKey}>Cancel</button>
                  <button className="primary compact" onClick={() => void confirmNote()} disabled={!dialogText.trim() || !!busyKey}>
                    <MessageSquareText size={13} /> {busyKey ? 'Saving…' : 'Save note'}
                  </button>
                </div>
              </>
            )}

            {dialog.kind === 'resolution' && (
              <>
                <p className="care-dialog-copy">Capture the customer-facing outcome before resolving or closing the case. This is the hand-off record for the next support agent.</p>
                <label className="care-dialog-field">
                  <span>Resolution</span>
                  <select value={dialogResolutionCode} onChange={event => setDialogResolutionCode(event.target.value)}>
                    <option value="AGENT_HANDLED">Handled by support</option>
                    <option value="CUSTOMER_GUIDANCE">Customer guidance provided</option>
                    <option value="PAYMENT_VERIFIED">Payment / wallet status verified</option>
                    <option value="RECHARGE_RESOLVED">Recharge issue resolved</option>
                    <option value="RENTAL_RESOLVED">Rental issue resolved</option>
                    <option value="CALLBACK_COMPLETED">Callback completed</option>
                    <option value="NO_ACTION_REQUIRED">No action required</option>
                    <option value="OTHER">Other</option>
                  </select>
                </label>
                <label className="care-dialog-field">
                  <span>Outcome note</span>
                  <textarea value={dialogText} onChange={event => setDialogText(event.target.value.slice(0, 1200))} placeholder="What was resolved, verified or communicated?" rows={5} />
                </label>
                <div className="care-dialog-actions">
                  <button className="secondary compact" onClick={() => setDialog(null)} disabled={!!busyKey}>Cancel</button>
                  <button className="primary compact" onClick={() => void confirmCaseResolution()} disabled={!!busyKey}>
                    <CheckCircle2 size={13} /> {busyKey ? 'Saving…' : dialog.status === 'RESOLVED' ? 'Resolve case' : 'Close case'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
