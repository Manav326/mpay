'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Mic, MicOff, PhoneCall, PhoneOff, ShieldCheck, UsersRound, UserCog, LockKeyhole, CircleHelp, Search } from 'lucide-react';
import {
  createVoiceCall,
  endVoiceCall,
  getAdminApiBaseUrl,
  getVoiceCall,
  getVoiceCallRoleAccess,
  getVoiceCallSignalingToken,
  getVoiceCallUserAccess,
  updateVoiceCallRoleAccess,
  updateVoiceCallUserAccess,
  getCustomerCareAccess,
  updateCustomerCareRolePermission,
  updateCustomerCareUserPermission,
} from '@/lib/api';
import { SupportAccessResponse, VoiceCallResponse, VoiceCallRoleAccess, VoiceCallUserAccess } from '@/lib/types';

function webSocketUrl(token: string, path: string) {
  const base = getAdminApiBaseUrl();
  const socketBase = base.startsWith('https://')
    ? 'wss://' + base.slice('https://'.length)
    : base.startsWith('http://')
      ? 'ws://' + base.slice('http://'.length)
      : base;
  return socketBase + path + '?token=' + encodeURIComponent(token);
}

export function VoiceCallWidget({
  callId,
  customerName,
  onClosed,
}: {
  callId: string;
  customerName: string;
  onClosed: () => void;
}) {
  const [call, setCall] = useState<VoiceCallResponse | null>(null);
  const [status, setStatus] = useState('RINGING');
  const [message, setMessage] = useState('Waiting for the customer to answer…');
  const [muted, setMuted] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [busy, setBusy] = useState(false);
  const [summary, setSummary] = useState<VoiceCallResponse | null>(null);
  const [summaryVisible, setSummaryVisible] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
  const signalingReconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const peerRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const queuedCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const queuedOutgoingSignalsRef = useRef<string[]>([]);
  const remoteDescriptionReadyRef = useRef(false);
  const signalingStartedRef = useRef(false);
  const offerStartedRef = useRef(false);
  const endedRef = useRef(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const iceServersKey = JSON.stringify(call?.iceServers ?? []);
  const iceServers = useMemo(() => {
    return call?.iceServers?.map(server => ({
      urls: server.urls,
      username: server.username || undefined,
      credential: server.credential || undefined,
    })) || [];
  }, [iceServersKey]);
  const mediaActive = call?.status === 'ACCEPTED' || call?.status === 'CONNECTED';

  useEffect(() => {
    let active = true;
    let poll: ReturnType<typeof setInterval> | null = null;

    async function load() {
      try {
        const current = await getVoiceCall(callId);
        if (!active) return;
        setCall(current);
        setStatus(current.status);
        if (
          !endedRef.current &&
          ['DECLINED', 'MISSED', 'CANCELLED', 'ENDED'].includes(current.status)
        ) {
          void finish(
            current.status === 'DECLINED' ? 'The customer declined the call.' : 'The call has ended.',
            current
          );
        }
      } catch (error: any) {
        if (active) setMessage(error?.message || 'Unable to load the call.');
      }
    }

    void load();
    poll = setInterval(() => { void load(); }, 2500);

    return () => {
      active = false;
      if (poll) clearInterval(poll);
    };
  }, [callId]);

  useEffect(() => {
    function endCallOnPageHide(event: PageTransitionEvent) {
      if (event.persisted || endedRef.current) return;
      const token = typeof window !== 'undefined' ? localStorage.getItem('mpay_admin_token') : null;
      if (!token) return;

      void fetch(
        getAdminApiBaseUrl() + '/api/v1/calls/' + encodeURIComponent(callId) + '/end',
        {
          method: 'POST',
          headers: {
            Authorization: 'Bearer ' + token,
            'Content-Type': 'application/json',
          },
          keepalive: true,
        },
      ).catch(() => {});
    }

    window.addEventListener('pagehide', endCallOnPageHide);
    return () => window.removeEventListener('pagehide', endCallOnPageHide);
  }, [callId]);

  useEffect(() => {
    const connectedAt = call?.connectedAt ? new Date(call.connectedAt).getTime() : null;
    const endedAt = call?.endedAt ? new Date(call.endedAt).getTime() : null;
    if (!connectedAt) {
      setElapsed(0);
      return;
    }

    const updateElapsed = () => {
      const end = endedAt ?? Date.now();
      setElapsed(Math.max(0, Math.floor((end - connectedAt) / 1000)));
    };

    updateElapsed();
    if (endedAt) return;
    const timer = setInterval(updateElapsed, 1000);
    return () => clearInterval(timer);
  }, [call?.connectedAt, call?.endedAt]);

  useEffect(() => {
    if (!call || !mediaActive || signalingStartedRef.current) return;
    signalingStartedRef.current = true;
    let cancelled = false;

    async function connect() {
      try {
        const token = await getVoiceCallSignalingToken(callId);
        const pc = new RTCPeerConnection({ iceServers });
        peerRef.current = pc;

        pc.onicecandidate = event => {
          if (!event.candidate) return;
          const message = JSON.stringify({
            type: 'signal',
            callId,
            payload: {
              kind: 'candidate',
              candidate: event.candidate.candidate,
              sdpMid: event.candidate.sdpMid,
              sdpMLineIndex: event.candidate.sdpMLineIndex,
            },
          });
          const socket = socketRef.current;
          if (socket?.readyState === WebSocket.OPEN) {
            socket.send(message);
          } else {
            queuedOutgoingSignalsRef.current.push(message);
          }
        };

        pc.ontrack = event => {
          const stream = event.streams[0] || new MediaStream([event.track]);
          if (audioRef.current && stream) {
            audioRef.current.srcObject = stream;
            void audioRef.current.play().catch(() => {
              setMessage('Browser audio playback is blocked. Click the call window to enable audio.');
            });
          }
        };

        pc.oniceconnectionstatechange = () => {
          const state = pc.iceConnectionState;
          if (state === 'connected' || state === 'completed') {
            setMessage('Connected securely');
          } else if (state === 'failed') {
            setMessage('The secure audio connection could not be established.');
            void endVoiceCall(callId).catch(() => {});
          }
        };

        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        if (cancelled) {
          stream.getTracks().forEach(track => track.stop());
          pc.close();
          return;
        }
        localStreamRef.current = stream;
        // Apply the current admin mute state before the first RTP packet is sent.
        stream.getAudioTracks().forEach(track => {
          track.enabled = !muted;
          pc.addTrack(track, stream);
        });

        async function createOffer() {
          if (endedRef.current || offerStartedRef.current) return;
          offerStartedRef.current = true;
          setMessage('Preparing secure audio…');
          const offer = await pc.createOffer();
          await pc.setLocalDescription(offer);
          const socket = socketRef.current;
          if (socket?.readyState !== WebSocket.OPEN) {
            offerStartedRef.current = false;
            setMessage('Waiting for secure signaling…');
            return;
          }
          socket.send(JSON.stringify({
            type: 'signal',
            callId,
            payload: { kind: 'offer', type: offer.type, sdp: offer.sdp },
          }));
        }

        function scheduleSignalingReconnect() {
          if (
            cancelled ||
            endedRef.current ||
            signalingReconnectTimerRef.current ||
            peerRef.current?.iceConnectionState === 'connected' ||
            peerRef.current?.iceConnectionState === 'completed'
          ) return;

          setMessage('Reconnecting secure signaling…');
          signalingReconnectTimerRef.current = setTimeout(() => {
            signalingReconnectTimerRef.current = null;
            connectSignaling();
          }, 1200);
        }

        function connectSignaling() {
          if (cancelled || endedRef.current) return;
          socketRef.current?.close();
          const socket = new WebSocket(webSocketUrl(token.token, token.websocketPath));
          socketRef.current = socket;

          socket.onopen = () => {
            signalingReconnectTimerRef.current = null;
            setMessage('Connected to call signaling…');
            for (const message of queuedOutgoingSignalsRef.current) {
              socket.send(message);
            }
            queuedOutgoingSignalsRef.current = [];
            socket.send(JSON.stringify({ type: 'ready', callId }));
          };

          socket.onmessage = async event => {
          try {
            const data = JSON.parse(event.data);
            if (data.callId !== callId) return;
            if (data.type === 'ready') {
              setMessage('Customer connected. Preparing secure audio…');
              await createOffer();
              return;
            }
            if (data.type === 'status') {
              if (data.status === 'CONNECTED') {
                const connectedAtEpochMillis = Number(data.connectedAtEpochMillis);
                setStatus('CONNECTED');
                setMessage('Connected securely');
                if (Number.isFinite(connectedAtEpochMillis) && connectedAtEpochMillis > 0) {
                  setCall(current => current ? {
                    ...current,
                    status: 'CONNECTED',
                    connectedAt: new Date(connectedAtEpochMillis).toISOString(),
                  } : current);
                }
                return;
              }
              if (!endedRef.current && ['DECLINED', 'MISSED', 'CANCELLED', 'ENDED'].includes(data.status)) {
                void finish(
                  data.status === 'DECLINED' ? 'The customer declined the call.' : 'The call has ended.'
                );
              }
              return;
            }
            if (data.type !== 'signal' || !data.payload) return;
            const payload = data.payload;
            if (payload.kind === 'answer') {
              const answer = new RTCSessionDescription({ type: payload.type, sdp: payload.sdp });
              await pc.setRemoteDescription(answer);
              remoteDescriptionReadyRef.current = true;
              for (const candidate of queuedCandidatesRef.current) {
                await pc.addIceCandidate(candidate);
              }
              queuedCandidatesRef.current = [];
            } else if (payload.kind === 'candidate') {
              const candidate: RTCIceCandidateInit = {
                candidate: payload.candidate,
                sdpMid: payload.sdpMid ?? null,
                sdpMLineIndex: payload.sdpMLineIndex ?? null,
              };
              if (remoteDescriptionReadyRef.current) await pc.addIceCandidate(candidate);
              else queuedCandidatesRef.current.push(candidate);
            }
          } catch {
            setMessage('Unable to process secure call signaling.');
          }
        };

          socket.onclose = () => {
            if (!endedRef.current) scheduleSignalingReconnect();
          };

          socket.onerror = () => {
            if (!endedRef.current) scheduleSignalingReconnect();
          };
        }

        connectSignaling();
      } catch (error: any) {
        if (!cancelled) {
          setMessage(error?.message || 'Unable to start voice audio.');
          void endVoiceCall(callId).catch(() => {});
        }
      }
    }

    void connect();

    return () => {
      cancelled = true;
      if (signalingReconnectTimerRef.current) {
        clearTimeout(signalingReconnectTimerRef.current);
        signalingReconnectTimerRef.current = null;
      }
      socketRef.current?.close();
      socketRef.current = null;
      queuedOutgoingSignalsRef.current = [];
      offerStartedRef.current = false;
      peerRef.current?.close();
      peerRef.current = null;
      localStreamRef.current?.getTracks().forEach(track => track.stop());
      localStreamRef.current = null;
    };
  }, [mediaActive, callId, iceServers]);

  async function finish(text: string, finalCall?: VoiceCallResponse) {
    if (summaryVisible && endedRef.current) return;

    endedRef.current = true;
    setMessage(text);
    socketRef.current?.close();
    peerRef.current?.close();
    localStreamRef.current?.getTracks().forEach(track => track.stop());
    socketRef.current = null;
    peerRef.current = null;
    localStreamRef.current = null;

    let resolvedCall = finalCall ?? null;
    if (!resolvedCall) {
      try {
        resolvedCall = await getVoiceCall(callId);
      } catch {
        resolvedCall = call;
      }
    }

    if (resolvedCall) {
      setCall(resolvedCall);
      setStatus(resolvedCall.status);
      setSummary(resolvedCall);
    }
    setSummaryVisible(true);
  }

  async function hangUp() {
    if (busy || endedRef.current) return;
    setBusy(true);
    setMessage('Ending call…');

    // WebSocket hang-up is a best-effort low-latency signal. A failure here must
    // never prevent the authoritative REST termination below.
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) {
      try {
        socket.send(JSON.stringify({ type: 'hangup', callId }));
      } catch (error) {
        console.debug('Voice-call signaling hang-up failed; continuing with REST end', error);
      }
    }

    try {
      const endedCall = await endVoiceCall(callId);
      await finish('Call ended', endedCall);
    } catch (error: any) {
      if (!endedRef.current) {
        setMessage(error?.message || 'Unable to end the call. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  function toggleMute() {
    const next = !muted;
    localStreamRef.current?.getAudioTracks().forEach(track => { track.enabled = !next; });
    setMuted(next);
  }

  return (
    <div className="voice-call-modal-backdrop" role="dialog" aria-modal="true" aria-label="mPay voice call">
      <section className="voice-call-modal">
        {summaryVisible ? (
          <>
            <div className="voice-call-topline">
              <span className="voice-call-live"><span />CALL ENDED</span>
              <span className="voice-call-secure"><ShieldCheck size={13} /> Secure</span>
            </div>
            <div className="voice-call-avatar"><PhoneCall size={29} /></div>
            <div className="voice-call-kicker">mPay voice support · call summary</div>
            <h2>{customerName || summary?.calleeName || 'mPay customer'}</h2>
            <p>{summary?.status === 'DECLINED' ? 'Customer declined the call.' : 'The call has ended.'}</p>

            <div className="detail-grid detail-grid-3">
              <div>
                <small>Connected time</small>
                <b>{formatDuration(callDurationSeconds(summary))}</b>
              </div>
              <div>
                <small>Status</small>
                <b>{summary?.status || status}</b>
              </div>
              <div>
                <small>End reason</small>
                <b>{summary?.endedReason || '—'}</b>
              </div>
              <div>
                <small>Started</small>
                <b>{summary?.createdAt ? formatCallDate(summary.createdAt) : '—'}</b>
              </div>
              <div>
                <small>Connected at</small>
                <b>{summary?.connectedAt ? formatCallDate(summary.connectedAt) : 'Not connected'}</b>
              </div>
              <div>
                <small>Ended at</small>
                <b>{summary?.endedAt ? formatCallDate(summary.endedAt) : '—'}</b>
              </div>
            </div>

            <div className="voice-call-footnote">
              Call ID: <span className="mono">{summary?.callId || callId}</span>
            </div>

            <div className="voice-call-summary-actions">
              <button className="secondary" onClick={onClosed}>Close</button>
            </div>
          </>
        ) : (
          <>
            <div className="voice-call-topline">
              <span className="voice-call-live"><span />{status === 'CONNECTED' ? 'LIVE' : 'OUTGOING'}</span>
              <span className="voice-call-secure"><ShieldCheck size={13} /> Secure</span>
            </div>
            <div className="voice-call-avatar"><PhoneCall size={29} /></div>
            <div className="voice-call-kicker">mPay voice support</div>
            <h2>{customerName || 'mPay customer'}</h2>
            <p>
              {status === 'RINGING'
                ? 'Ringing customer…'
                : status === 'CONNECTED'
                  ? 'Call time  ' + formatDuration(elapsed)
                  : message}
            </p>
            <audio ref={audioRef} autoPlay playsInline />
            <div className="voice-call-controls">
              <button className={muted ? 'voice-round active' : 'voice-round'} onClick={toggleMute} aria-label={muted ? 'Unmute microphone' : 'Mute microphone'}>
                {muted ? <MicOff size={19} /> : <Mic size={19} />}
              </button>
              <button className="voice-round hangup" onClick={hangUp} disabled={busy} aria-label="End call">
                <PhoneOff size={19} />
              </button>
            </div>
            <div className="voice-call-footnote">The call is not recorded. The customer must accept before two-way audio starts.</div>
          </>
        )}
      </section>
    </div>
  );
}

function formatDuration(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, '0');
  const seconds = (totalSeconds % 60).toString().padStart(2, '0');
  return minutes + ':' + seconds;
}

function callDurationSeconds(call: VoiceCallResponse | null) {
  if (!call?.connectedAt) return 0;
  const start = new Date(call.connectedAt).getTime();
  const end = call.endedAt ? new Date(call.endedAt).getTime() : Date.now();
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 0;
  return Math.max(0, Math.floor((end - start) / 1000));
}

function formatCallDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function VoiceAccessPanel() {
  const [access, setAccess] = useState<SupportAccessResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState('');
  const [selectedRole, setSelectedRole] = useState('');
  const [selectedEmployeeId, setSelectedEmployeeId] = useState('');
  const [employeeQuery, setEmployeeQuery] = useState('');
  const [notice, setNotice] = useState('');

  async function load() {
    setLoading(true);
    try {
      const data = await getCustomerCareAccess();
      setAccess(data);
      const queryEmployee =
        typeof window !== 'undefined'
          ? new URLSearchParams(window.location.search).get('employee') || ''
          : '';
      const preferredEmployee = queryEmployee || selectedEmployeeId;
      const nextEmployee = data.users.some(item => item.publicUserId === preferredEmployee)
        ? preferredEmployee
        : data.users.find(item => !item.protected)?.publicUserId || data.users[0]?.publicUserId || '';
      setSelectedEmployeeId(nextEmployee);
      if (!selectedRole && data.roles.length) {
        setSelectedRole(data.roles.find(item => !item.protected)?.role || data.roles[0].role);
      }
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load staff access.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  function selectEmployee(publicUserId: string) {
    setSelectedEmployeeId(publicUserId);
    if (typeof window !== 'undefined' && publicUserId) {
      const url = '/admin/voice?employee=' + encodeURIComponent(publicUserId);
      window.history.replaceState({ mpayAdminView: 'voice', employee: publicUserId }, '', url);
    }
  }

  async function saveRolePermission(role: string, permission: string, enabled: boolean) {
    const key = 'role:' + role + ':' + permission;
    setSavingKey(key);
    setNotice('');
    try {
      const updated = await updateCustomerCareRolePermission(role, permission, enabled);
      setAccess(current => current ? {
        ...current,
        roles: current.roles.map(item => item.role === role ? updated : item)
      } : current);
      setNotice(enabled
        ? 'Role capability enabled. Dependent Customer Care access was updated automatically where required.'
        : 'Role capability removed. Any dependent access was adjusted safely by the server.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to change the role access.');
    } finally {
      setSavingKey('');
    }
  }

  async function saveEmployeePermission(
    publicUserId: string,
    permission: string,
    mode: 'DEFAULT' | 'ALLOW' | 'DENY'
  ) {
    const key = 'employee:' + publicUserId + ':' + permission;
    setSavingKey(key);
    setNotice('');
    try {
      const updated = await updateCustomerCareUserPermission(publicUserId, permission, mode);
      setAccess(current => current ? {
        ...current,
        users: current.users.map(item => item.publicUserId === publicUserId ? updated : item)
      } : current);
      const message = mode === 'ALLOW'
        ? 'Employee capability explicitly allowed.'
        : mode === 'DENY'
          ? 'Employee capability explicitly blocked.'
          : 'Employee returned to the normal role setting.';
      setNotice(message);
    } catch (error: any) {
      setNotice(error?.message || 'Unable to change the employee access.');
    } finally {
      setSavingKey('');
    }
  }

  const roleList = access?.roles.filter(role => !role.protected) || [];
  const employees = useMemo(() => {
    const q = employeeQuery.trim().toLowerCase();
    if (!access) return [];
    return access.users
      .filter(employee => !q || [
        employee.name,
        employee.mobile,
        employee.role,
        employee.publicUserId,
      ].some(value => String(value || '').toLowerCase().includes(q)))
      .sort((a, b) => (a.name || a.mobile).localeCompare(b.name || b.mobile));
  }, [access, employeeQuery]);

  const chosenRole = access?.roles.find(item => item.role === selectedRole) || null;
  const chosenEmployee = access?.users.find(item => item.publicUserId === selectedEmployeeId) || null;

  const permissionGroups = ['Customer Care', 'Customer information', 'Voice', 'AI'];

  function friendlyRole(role: string) {
    if (role === 'MANAGER') return 'Manager';
    if (role === 'CUSTOMER_SUPPORT') return 'Customer care';
    return role.replace(/_/g, ' ').toLowerCase().replace(/(^| )\w/g, value => value.toUpperCase());
  }

  function enabledCount(permissions: Array<{ enabled: boolean }>) {
    return permissions.filter(permission => permission.enabled).length;
  }

  function overrideCount(permissions: Array<{ mode: string | null }>) {
    return permissions.filter(permission => permission.mode && permission.mode !== 'DEFAULT').length;
  }

  function initials(name?: string | null, mobile?: string | null) {
    const source = String(name || mobile || 'E').trim();
    return source.split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('').toUpperCase() || 'E';
  }

  function dependencyNote(permission: string) {
    if (permission === 'SUPPORT_MANAGE') return 'Requires Customer Care access. Enabling this also keeps conversation access enabled.';
    if (permission === 'SUPPORT_VIEW') return 'Removing Customer Care access also removes case-management access.';
    if (permission === 'CALL_CUSTOMER') return 'Only employees with this capability can start support voice calls.';
    return null;
  }

  const employeeOverrideTotal = access?.users.reduce((sum, user) => sum + overrideCount(user.permissions), 0) || 0;

  return (
    <div className="content voice-access-page">
      <section className="voice-access-hero">
        <div className="voice-access-hero-copy">
          <div className="eyebrow">Administrator controls</div>
          <h2>Voice & Access</h2>
          <p>Control Customer Care, voice, customer information and AI capabilities by role. Use an individual override only when one employee needs a deliberate exception.</p>
          <div className="voice-access-principles">
            <span><Check size={11} /> Role is the baseline</span>
            <span><Check size={11} /> Employee override is explicit</span>
            <span><LockKeyhole size={11} /> Administrator access stays protected</span>
          </div>
        </div>
        <div className="voice-access-hero-icon"><ShieldCheck size={25} /></div>
      </section>

      {notice && (
        <div className="voice-access-notice">
          <Check size={13} />
          <span>{notice}</span>
        </div>
      )}

      <section className="voice-access-summary">
        <article>
          <span><UserCog size={14} /></span>
          <div><b>{roleList.length}</b><small>Employee roles</small></div>
        </article>
        <article>
          <span><UsersRound size={14} /></span>
          <div><b>{access?.users.length ?? '—'}</b><small>Employees covered</small></div>
        </article>
        <article>
          <span><ShieldCheck size={14} /></span>
          <div><b>{employeeOverrideTotal}</b><small>Explicit overrides</small></div>
        </article>
        <article>
          <span><PhoneCall size={14} /></span>
          <div><b>{access?.permissions.length ?? '—'}</b><small>Managed capabilities</small></div>
        </article>
      </section>

      {access && (
        <section className="voice-access-guide">
          <div className="voice-guide-icon"><CircleHelp size={15} /></div>
          <div>
            <b>How access works</b>
            <span><strong>Role setting</strong> is the normal baseline. <strong>Allow</strong> grants one employee an exception. <strong>Deny</strong> explicitly blocks one capability even when the role allows it.</span>
          </div>
        </section>
      )}

      <section className="panel">
        <div className="panel-head wrap voice-panel-head">
          <div>
            <div className="staff-section-kicker">01 · Role blueprint</div>
            <h2>Role access</h2>
            <p>Set the normal capability baseline for each employee role. Administrator settings stay protected.</p>
          </div>
          <button className="secondary compact" onClick={() => void load()} disabled={loading}>
            <UsersRound size={13} /> Refresh access
          </button>
        </div>

        {loading && !access ? <div className="empty-state">Loading access settings…</div> :
          roleList.length === 0 ? <div className="empty-state">No employee roles are available.</div> :
          <div className="voice-role-grid">
            {roleList.map(role => {
              const activePermissions = role.permissions.filter(permission => permission.enabled);
              const selected = selectedRole === role.role;
              return (
                <button
                  className={'voice-role-card' + (selected ? ' selected' : '')}
                  key={role.role}
                  onClick={() => setSelectedRole(role.role)}
                  aria-pressed={selected}
                >
                  <div className="voice-role-icon"><UserCog size={16} /></div>
                  <div className="voice-role-copy">
                    <div className="voice-role-title"><b>{friendlyRole(role.role)}</b><span>{activePermissions.length}/{role.permissions.length} enabled</span></div>
                    <div className="voice-role-capabilities">
                      {activePermissions.slice(0, 3).map(permission => <span key={permission.permission}>{permission.label}</span>)}
                      {activePermissions.length > 3 && <span className="more">+{activePermissions.length - 3} more</span>}
                    </div>
                  </div>
                  <span className="voice-role-select">{selected ? 'Selected' : 'Review'}</span>
                </button>
              );
            })}
          </div>
        }
      </section>

      {chosenRole && (
        <section className="panel">
          <div className="panel-head wrap voice-panel-head">
            <div>
              <div className="staff-section-kicker">Role baseline</div>
              <h2>{friendlyRole(chosenRole.role)} permissions</h2>
              <p>Changes here affect every employee in this role unless that person has an explicit override.</p>
            </div>
            <span className="voice-access-role-note"><ShieldCheck size={12} /> Administrator control</span>
          </div>

          <div className="voice-friendly-permissions voice-permission-groups">
            {permissionGroups.map(group => {
              const items = chosenRole.permissions.filter(permission => permission.group === group);
              if (!items.length) return null;
              return (
                <div className="voice-friendly-group" key={group}>
                  <div className="voice-friendly-group-title">{group}</div>
                  {items.map(permission => {
                    const busy = savingKey === 'role:' + chosenRole.role + ':' + permission.permission;
                    const dependency = dependencyNote(permission.permission);
                    return (
                      <div className="voice-friendly-row" key={permission.permission}>
                        <div className="voice-permission-copy">
                          <b>{permission.label}</b>
                          <span>{permission.description}</span>
                          {dependency && <small>{dependency}</small>}
                        </div>
                        <button
                          className={permission.enabled ? 'voice-access-toggle enabled' : 'voice-access-toggle'}
                          disabled={!!savingKey}
                          onClick={() => void saveRolePermission(chosenRole.role, permission.permission, !permission.enabled)}
                        >
                          <span className="voice-toggle-dot" />
                          {busy ? 'Saving…' : permission.enabled ? 'Allowed' : 'Not allowed'}
                        </button>
                      </div>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section className="panel voice-staff-panel">
        <div className="panel-head wrap voice-panel-head">
          <div>
            <div className="staff-section-kicker">02 · Individual exception</div>
            <h2>Employee-specific access</h2>
            <p>Search the employee directory, review their effective access and change only the capability that needs an exception.</p>
          </div>
        </div>

        <div className="voice-employee-workspace">
          <aside className="voice-employee-directory">
            <div className="voice-employee-directory-head">
              <b>Employee directory</b>
              <span>{employees.length} shown</span>
            </div>
            <label className="voice-employee-search">
              <Search size={13} />
              <input value={employeeQuery} onChange={event => setEmployeeQuery(event.target.value)} placeholder="Search name, mobile or role" aria-label="Search employees for access" />
            </label>
            <div className="voice-employee-list">
              {employees.map(employee => {
                const selected = employee.publicUserId === selectedEmployeeId;
                const overrides = overrideCount(employee.permissions);
                return (
                  <button
                    key={employee.publicUserId}
                    className={'voice-employee-item' + (selected ? ' selected' : '')}
                    onClick={() => selectEmployee(employee.publicUserId)}
                  >
                    <div className="voice-staff-avatar">{initials(employee.name, employee.mobile)}</div>
                    <div className="voice-employee-item-copy">
                      <b>{employee.name || 'Unnamed employee'}</b>
                      <span>{friendlyRole(employee.role)}</span>
                    </div>
                    <span className={overrides ? 'voice-override-badge' : 'voice-override-badge quiet'}>{overrides ? overrides + ' override' + (overrides === 1 ? '' : 's') : 'Role default'}</span>
                  </button>
                );
              })}
              {employees.length === 0 && <div className="voice-directory-empty">No employee matches the current search.</div>}
            </div>
          </aside>

          <section className="voice-employee-detail">
            {chosenEmployee ? (
              <>
                <div className="voice-selected-employee-head">
                  <div className="voice-staff-main">
                    <div className="voice-staff-avatar large">{initials(chosenEmployee.name, chosenEmployee.mobile)}</div>
                    <div>
                      <div className="voice-selected-name"><b>{chosenEmployee.name || 'Unnamed employee'}</b><span>{friendlyRole(chosenEmployee.role)}</span></div>
                      <span>{chosenEmployee.mobile}</span>
                    </div>
                  </div>
                  <div className="voice-employee-effective">
                    <b>{enabledCount(chosenEmployee.permissions)}</b>
                    <span>effective capabilities</span>
                  </div>
                </div>

                <div className="voice-mode-legend">
                  <span className="default"><b>Role default</b><small>Follow the job role</small></span>
                  <span className="allow"><b>Allow</b><small>Explicitly grant</small></span>
                  <span className="deny"><b>Deny</b><small>Explicitly block</small></span>
                </div>

                <div className="voice-friendly-permissions voice-permission-groups">
                  {permissionGroups.map(group => {
                    const items = chosenEmployee.permissions.filter(permission => permission.group === group);
                    if (!items.length) return null;
                    return (
                      <div className="voice-friendly-group" key={group}>
                        <div className="voice-friendly-group-title">{group}</div>
                        {items.map(permission => {
                          const busy = savingKey === 'employee:' + chosenEmployee.publicUserId + ':' + permission.permission;
                          const locked = chosenEmployee.protected || !permission.editable;
                          const dependency = dependencyNote(permission.permission);
                          return (
                            <div className="voice-friendly-row" key={permission.permission}>
                              <div className="voice-permission-copy">
                                <b>{permission.label}</b>
                                <span>{permission.description}</span>
                                {dependency && <small>{dependency}</small>}
                              </div>
                              <div className="voice-access-choice">
                                <button disabled={locked || !!savingKey} className={permission.mode === 'DEFAULT' ? 'selected' : ''} onClick={() => void saveEmployeePermission(chosenEmployee.publicUserId, permission.permission, 'DEFAULT')}>Default</button>
                                <button disabled={locked || !!savingKey} className={permission.mode === 'ALLOW' ? 'selected allow' : ''} onClick={() => void saveEmployeePermission(chosenEmployee.publicUserId, permission.permission, 'ALLOW')}>Allow</button>
                                <button disabled={locked || !!savingKey} className={permission.mode === 'DENY' ? 'selected deny' : ''} onClick={() => void saveEmployeePermission(chosenEmployee.publicUserId, permission.permission, 'DENY')}>Deny</button>
                                {locked && <span className="voice-locked"><LockKeyhole size={10} /> Protected</span>}
                                {busy && <span className="voice-saving">Saving…</span>}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })}
                </div>
              </>
            ) : (
              <div className="voice-directory-empty large">
                <UsersRound size={20} />
                <b>Choose an employee</b>
                <span>Select a person from the directory to review their effective permissions.</span>
              </div>
            )}
          </section>
        </div>
      </section>

      <section className="voice-access-privacy">
        <ShieldCheck size={17} />
        <div>
          <b>Audit trail</b>
          <span>Access changes are recorded against the employee or role. Routine page reading and searches are not stored as activity events.</span>
        </div>
      </section>
    </div>
  );
}
