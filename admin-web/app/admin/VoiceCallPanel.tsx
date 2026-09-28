'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Mic, MicOff, Phone, PhoneCall, PhoneOff, ShieldCheck, Volume2, VolumeX } from 'lucide-react';
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
  const [speaker, setSpeaker] = useState(true);
  const [elapsed, setElapsed] = useState(0);
  const [busy, setBusy] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
  const peerRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const queuedCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const remoteDescriptionReadyRef = useRef(false);
  const endedRef = useRef(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const iceServers = useMemo(() => {
    return call?.iceServers?.map(server => ({
      urls: server.urls,
      username: server.username || undefined,
      credential: server.credential || undefined,
    })) || [];
  }, [call]);

  useEffect(() => {
    let active = true;
    let poll: ReturnType<typeof setInterval> | null = null;

    async function load() {
      try {
        const current = await getVoiceCall(callId);
        if (!active) return;
        setCall(current);
        setStatus(current.status);
        if (current.status === 'DECLINED' || current.status === 'MISSED' || current.status === 'CANCELLED' || current.status === 'ENDED') {
          finish(current.status === 'DECLINED' ? 'The customer declined the call.' : 'The call has ended.');
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
    const connectedAt = call?.connectedAt ? new Date(call.connectedAt).getTime() : null;
    if (!connectedAt) return;
    setElapsed(Math.max(0, Math.floor((Date.now() - connectedAt) / 1000)));
    const timer = setInterval(() => setElapsed(Math.max(0, Math.floor((Date.now() - connectedAt) / 1000))), 1000);
    return () => clearInterval(timer);
  }, [call?.connectedAt]);

  useEffect(() => {
    if (!call || status !== 'ACCEPTED' && status !== 'CONNECTED') return;
    let cancelled = false;

    async function connect() {
      try {
        const token = await getVoiceCallSignalingToken(callId);
        const pc = new RTCPeerConnection({ iceServers });
        peerRef.current = pc;

        pc.onicecandidate = event => {
          if (!event.candidate) return;
          socketRef.current?.send(JSON.stringify({
            type: 'signal',
            callId,
            payload: {
              kind: 'candidate',
              candidate: event.candidate.candidate,
              sdpMid: event.candidate.sdpMid,
              sdpMLineIndex: event.candidate.sdpMLineIndex,
            },
          }));
        };

        pc.ontrack = event => {
          const stream = event.streams[0];
          if (audioRef.current && stream) {
            audioRef.current.srcObject = stream;
            void audioRef.current.play().catch(() => {});
          }
        };

        pc.oniceconnectionstatechange = () => {
          const state = pc.iceConnectionState;
          if (state === 'connected' || state === 'completed') {
            setMessage('Connected securely');
          } else if (state === 'failed') {
            setMessage('The secure audio connection could not be established.');
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

        const socket = new WebSocket(webSocketUrl(token.token, token.websocketPath));
        socketRef.current = socket;

        socket.onopen = async () => {
          setMessage('Preparing secure audio…');
          const offer = await pc.createOffer();
          await pc.setLocalDescription(offer);
          socket.send(JSON.stringify({
            type: 'signal',
            callId,
            payload: { kind: 'offer', type: offer.type, sdp: offer.sdp },
          }));
        };

        socket.onmessage = async event => {
          try {
            const data = JSON.parse(event.data);
            if (data.type === 'status') {
              if (['DECLINED', 'MISSED', 'CANCELLED', 'ENDED'].includes(data.status)) {
                finish(data.status === 'DECLINED' ? 'The customer declined the call.' : 'The call has ended.');
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
          if (!endedRef.current) setMessage('Call signaling disconnected.');
        };

        socket.onerror = () => {
          if (!endedRef.current) setMessage('Call signaling is unavailable.');
        };
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
      socketRef.current?.close();
      socketRef.current = null;
      peerRef.current?.close();
      peerRef.current = null;
      localStreamRef.current?.getTracks().forEach(track => track.stop());
      localStreamRef.current = null;
    };
  }, [call?.status, callId, iceServers, status]);

  function finish(text: string) {
    endedRef.current = true;
    setMessage(text);
    socketRef.current?.close();
    peerRef.current?.close();
    localStreamRef.current?.getTracks().forEach(track => track.stop());
    socketRef.current = null;
    peerRef.current = null;
    localStreamRef.current = null;
    setTimeout(onClosed, 650);
  }

  async function hangUp() {
    if (busy || endedRef.current) return;
    setBusy(true);
    try {
      await endVoiceCall(callId);
      finish('Call ended');
    } finally {
      setBusy(false);
    }
  }

  function toggleMute() {
    const next = !muted;
    localStreamRef.current?.getAudioTracks().forEach(track => { track.enabled = !next; });
    setMuted(next);
  }

  function toggleSpeaker() {
    setSpeaker(value => !value);
    setMessage('Speaker controls are managed by your browser audio output.');
  }

  return (
    <div className="voice-call-modal-backdrop" role="dialog" aria-modal="true" aria-label="mPay voice call">
      <section className="voice-call-modal">
        <div className="voice-call-topline">
          <span className="voice-call-live"><span />{status === 'CONNECTED' ? 'LIVE' : 'OUTGOING'}</span>
          <span className="voice-call-secure"><ShieldCheck size={13} /> Secure</span>
        </div>
        <div className="voice-call-avatar"><PhoneCall size={29} /></div>
        <div className="voice-call-kicker">mPay voice support</div>
        <h2>{customerName || 'mPay customer'}</h2>
        <p>{status === 'RINGING' ? 'Ringing customer…' : status === 'CONNECTED' ? formatDuration(elapsed) : message}</p>
        <audio ref={audioRef} autoPlay playsInline />
        <div className="voice-call-controls">
          <button className={muted ? 'voice-round active' : 'voice-round'} onClick={toggleMute} aria-label={muted ? 'Unmute microphone' : 'Mute microphone'}>
            {muted ? <MicOff size={19} /> : <Mic size={19} />}
          </button>
          <button className={speaker ? 'voice-round active' : 'voice-round'} onClick={toggleSpeaker} aria-label="Speaker output">
            {speaker ? <Volume2 size={19} /> : <VolumeX size={19} />}
          </button>
          <button className="voice-round hangup" onClick={hangUp} disabled={busy} aria-label="End call">
            <PhoneOff size={19} />
          </button>
        </div>
        <div className="voice-call-footnote">The call is not recorded. The customer must accept before two-way audio starts.</div>
      </section>
    </div>
  );
}

function formatDuration(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, '0');
  const seconds = (totalSeconds % 60).toString().padStart(2, '0');
  return minutes + ':' + seconds;
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
