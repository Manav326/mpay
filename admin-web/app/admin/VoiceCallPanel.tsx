'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Mic, MicOff, PhoneCall, PhoneOff, ShieldCheck } from 'lucide-react';
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
} from '@/lib/api';
import { VoiceCallResponse, VoiceCallRoleAccess, VoiceCallUserAccess } from '@/lib/types';

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
        stream.getAudioTracks().forEach(track => pc.addTrack(track, stream));

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
  const [roles, setRoles] = useState<VoiceCallRoleAccess[]>([]);
  const [users, setUsers] = useState<VoiceCallUserAccess[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState('');
  const [notice, setNotice] = useState('');

  async function load() {
    setLoading(true);
    try {
      const [roleData, userData] = await Promise.all([getVoiceCallRoleAccess(), getVoiceCallUserAccess()]);
      setRoles(roleData);
      setUsers(userData);
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load voice-call access.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  async function saveRole(role: string, enabled: boolean) {
    setSavingKey('role:' + role);
    try {
      const updated = await updateVoiceCallRoleAccess(role, enabled);
      setRoles(current => current.map(item => item.role === role ? updated : item));
      setNotice(role + (enabled ? ' can now call customers.' : ' no longer has calling access.'));
      if (!enabled) setUsers(current => current.map(user => user.role === role && user.mode === 'DEFAULT' ? { ...user, enabled: false } : user));
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update role access.');
    } finally {
      setSavingKey('');
    }
  }

  async function saveUser(publicUserId: string, mode: VoiceCallUserAccess['mode']) {
    setSavingKey('user:' + publicUserId);
    try {
      const updated = await updateVoiceCallUserAccess(publicUserId, mode);
      setUsers(current => current.map(item => item.publicUserId === publicUserId ? updated : item));
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update staff access.');
    } finally {
      setSavingKey('');
    }
  }

  return (
    <div className="content voice-access-page">
      <section className="voice-access-hero">
        <div>
          <div className="eyebrow">Calling & access</div>
          <h2>Voice support controls</h2>
          <p>Allow trusted portal staff to place two-way calls to customer accounts. Every customer explicitly accepts or declines the call.</p>
        </div>
        <div className="voice-access-hero-icon"><PhoneCall size={25} /></div>
      </section>

      {notice && <div className="voice-access-notice">{notice}</div>}

      <section className="panel">
        <div className="panel-head wrap">
          <div><h2>Role access</h2><p>Enable calling for a staff group, then fine-tune individual accounts below.</p></div>
          <button className="secondary compact" onClick={() => void load()} disabled={loading}>Refresh</button>
        </div>
        <div className="voice-role-grid">
          {loading && roles.length === 0 ? <div className="empty-state">Loading call-access roles…</div> :
            roles.map(role => (
              <div className="voice-role-card" key={role.role}>
                <div className="voice-role-icon"><PhoneCall size={17} /></div>
                <div className="voice-role-copy"><b>{role.role.replace(/_/g, ' ')}</b><span>Staff role</span></div>
                <button className={role.enabled ? 'voice-access-toggle enabled' : 'voice-access-toggle'} onClick={() => void saveRole(role.role, !role.enabled)} disabled={savingKey === 'role:' + role.role}>
                  {role.enabled ? <Check size={14} /> : <ChevronDown size={14} />}
                  {savingKey === 'role:' + role.role ? 'Saving…' : role.enabled ? 'Enabled' : 'Off'}
                </button>
              </div>
            ))
          }
        </div>
      </section>

      <section className="panel voice-staff-panel">
        <div className="panel-head wrap">
          <div><h2>Individual staff access</h2><p>Override the role setting for a specific Manager or Team Lead without changing the whole group.</p></div>
        </div>
        {users.length === 0 && !loading ? <div className="empty-state">No portal staff accounts are available.</div> :
          <div className="voice-staff-list">
            {users.map(user => (
              <div className="voice-staff-row" key={user.publicUserId}>
                <div className="voice-staff-main">
                  <div className="voice-staff-avatar">{(user.name || user.role || 'S').charAt(0).toUpperCase()}</div>
                  <div><b>{user.name || 'Unnamed staff'}</b><span>{user.role} · {user.mobile}</span></div>
                </div>
                <div className="voice-staff-access">
                  <label><span>Access</span><select value={user.mode} disabled={savingKey === 'user:' + user.publicUserId} onChange={e => void saveUser(user.publicUserId, e.target.value as VoiceCallUserAccess['mode'])}>
                    <option value="DEFAULT">Role default</option>
                    <option value="ALLOW">Allow</option>
                    <option value="DENY">Deny</option>
                  </select></label>
                </div>
                <span className={user.enabled ? 'status active' : 'status blocked'}>{user.enabled ? 'Can call' : 'No call access'}</span>
              </div>
            ))}
          </div>
        }
      </section>

      <section className="voice-access-privacy">
        <ShieldCheck size={18} />
        <div><b>Privacy by design</b><span>mPay stores call state and access decisions, not call audio. The microphone is only activated after the customer accepts.</span></div>
      </section>
    </div>
  );
}
