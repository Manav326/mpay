'use client';

import { useEffect, useRef, useState } from 'react';
import { Camera, ChevronDown, LogOut, Mail, Smartphone, Upload, UserRound, X } from 'lucide-react';
import { getCurrentAdminProfile, getCurrentAdminProfileImage, uploadCurrentAdminProfileImage } from '@/lib/api';
import { CurrentAdminProfile } from '@/lib/types';

export default function AdminProfileMenu({
  sessionName,
  role,
  onLogout,
}: {
  sessionName: string;
  role: string;
  onLogout: () => void;
}) {
  const [profile, setProfile] = useState<CurrentAdminProfile>();
  const [image, setImage] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;
    Promise.all([getCurrentAdminProfile(), getCurrentAdminProfileImage().catch(() => null)]).then(([p, img]) => {
      if (!active) return;
      setProfile(p);
      setImage(img);
    }).catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => () => {
    if (image?.startsWith('blob:')) URL.revokeObjectURL(image);
  }, [image]);

  async function chooseFile(file?: File) {
    if (!file) return;
    setBusy(true);
    setNotice('');
    try {
      const p = await uploadCurrentAdminProfileImage(file);
      const nextImage = await getCurrentAdminProfileImage();
      if (image?.startsWith('blob:')) URL.revokeObjectURL(image);
      setProfile(p);
      setImage(nextImage);
      setNotice('Profile photo updated.');
    } catch (err: any) {
      setNotice(err.message || 'Unable to update profile photo.');
    } finally {
      setBusy(false);
    }
  }

  const name = profile?.name?.trim() || sessionName || role;
  const initial = name.charAt(0).toUpperCase();

  return (
    <div className="admin-profile-menu">
      <button className="admin-user-chip" onClick={() => setOpen(v => !v)} aria-expanded={open}>
        {image ? <img src={image} alt={name} className="admin-user-avatar-image" /> : <span className="avatar">{initial}</span>}
        <span className="admin-user-name">{name}</span>
        <ChevronDown size={14}/>
      </button>
      {open && (
        <div className="admin-profile-popover">
          <div className="admin-profile-head">
            {image ? <img src={image} alt={name} className="admin-profile-large-image" /> : <div className="drawer-avatar">{initial}</div>}
            <div>
              <b>{name}</b>
              <span>{profile?.email || 'No email configured'}</span>
              <em>{profile?.mobile || '—'} · {role}</em>
            </div>
            <button className="icon-btn" onClick={() => setOpen(false)}><X size={15}/></button>
          </div>
          <div className="admin-profile-facts">
            <div><Mail size={13}/><span>{profile?.email || 'No email'}</span></div>
            <div><Smartphone size={13}/><span>{profile?.mobile || 'No mobile'}</span></div>
            <div><UserRound size={13}/><span>{role} portal account</span></div>
          </div>
          {notice && <div className="admin-profile-notice">{notice}</div>}
          <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={e => chooseFile(e.target.files?.[0])} />
          <button className="secondary profile-action" disabled={busy} onClick={() => fileRef.current?.click()}>
            {busy ? <span>Updating…</span> : <><Camera size={15}/> <span>{image ? 'Change profile photo' : 'Upload profile photo'}</span></>}
          </button>
          <button className="profile-logout" onClick={onLogout}><LogOut size={15}/> Sign out</button>
        </div>
      )}
    </div>
  );
}
