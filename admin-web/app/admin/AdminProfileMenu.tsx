'use client';

import { useEffect, useRef, useState } from 'react';
import { Camera, ChevronDown, LogOut, ShieldCheck, Trash2, UserRound, X } from 'lucide-react';
import { deleteAdminProfileImage, getAdminProfile, getAdminProfileImage, uploadAdminProfileImage } from '@/lib/api';

export default function AdminProfileMenu({
  name,
  role,
  onLogout,
}: {
  name: string;
  role: string;
  onLogout: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [image, setImage] = useState<string | null>(null);
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;
    getAdminProfile().then(profile => {
      if (active) {
        setMobile(profile.mobile || '');
        setEmail(profile.email || '');
      }
    }).catch(() => undefined);
    getAdminProfileImage().then(src => {
      if (active) setImage(src);
    }).catch(() => undefined);
    return () => {
      active = false;
      if (image?.startsWith('blob:')) URL.revokeObjectURL(image);
    };
  }, []);

  useEffect(() => () => {
    if (image?.startsWith('blob:')) URL.revokeObjectURL(image);
  }, [image]);

  useEffect(() => {
    if (!open) return;
    const closeOnOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (!(event.currentTarget as Document).getElementById('admin-profile-menu-root')?.contains(target)) setOpen(false);
    };
    document.addEventListener('click', closeOnOutside);
    return () => document.removeEventListener('click', closeOnOutside);
  }, [open]);

  async function upload(file?: File) {
    if (!file) return;
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type)) {
      setNotice('Use JPG, PNG or WebP.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setNotice('Profile image must be 5 MB or smaller.');
      return;
    }
    setBusy(true);
    setNotice('');
    try {
      await uploadAdminProfileImage(file);
      const next = await getAdminProfileImage();
      setImage(next);
      setNotice('Profile photo updated.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update profile photo.');
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    setBusy(true);
    setNotice('');
    try {
      await deleteAdminProfileImage();
      if (image?.startsWith('blob:')) URL.revokeObjectURL(image);
      setImage(null);
      setNotice('Profile photo removed.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to remove profile photo.');
    } finally {
      setBusy(false);
    }
  }

  const initial = (name || role || 'A').charAt(0).toUpperCase();

  return <div id="admin-profile-menu-root" className="admin-profile-menu">
    <button className="admin-user-chip" onClick={() => setOpen(value => !value)} aria-expanded={open} aria-label="Open admin profile">
      {image ? <img className="admin-avatar-image" src={image} alt={name} /> : <div className="avatar">{initial}</div>}
      <ChevronDown size={13} className={open ? 'profile-chevron open' : 'profile-chevron'} />
    </button>
    {open && <div className="admin-profile-popover">
      <div className="profile-popover-title"><div><span>Administrator profile</span><b>Account details</b></div><button className="icon-btn" onClick={() => setOpen(false)} aria-label="Close"><X size={15}/></button></div>
      <div className="profile-popover-head">
        {image ? <img className="admin-avatar-large" src={image} alt="" /> : <div className="admin-avatar-large avatar">{initial}</div>}
        <div><b>{name}</b><span><ShieldCheck size={12}/> {role}</span><small>{mobile || 'Portal account'}{email ? ' · ' + email : ''}</small></div>
      </div>
      {notice && <div className="alert">{notice}</div>}
      <div className="profile-popover-actions">
        <input ref={inputRef} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={e => upload(e.target.files?.[0])} />
        <button className="secondary" disabled={busy} onClick={() => inputRef.current?.click()}><Camera size={14}/> {busy ? 'Saving…' : 'Change photo'}</button>
        {image && <button className="secondary" disabled={busy} onClick={remove}><Trash2 size={14}/> Remove</button>}
      </div>
      <div className="profile-popover-divider" />
      <button className="profile-logout" onClick={onLogout}><LogOut size={14}/> Sign out</button>
      <div className="profile-popover-security"><UserRound size={12}/> Protected by role-based access controls</div>
    </div>}
  </div>;
}
