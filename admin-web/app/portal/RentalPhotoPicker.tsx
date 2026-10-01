'use client';

import { useEffect, useRef, useState } from 'react';
import { AlertCircle, Camera, CheckCircle2, ImagePlus, Link2, LoaderCircle, X } from 'lucide-react';

type PickerSource = 'device' | 'url' | null;

export type RentalPhotoPickerResult =
  | { source: 'device'; file: File }
  | { source: 'url'; url: string };

type Props = {
  title: string;
  currentPreview?: string;
  onCancel: () => void;
  onUse: (result: RentalPhotoPickerResult) => void;
};

const MAX_SOURCE_BYTES = 15 * 1024 * 1024;
const TARGET_BYTES = 4_750_000;
const MAX_PIXELS = 40_000_000;
const MAX_DIMENSION = 2_560;
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

function isHttpUrl(value: string) {
  return /^https?:\/\//i.test(value);
}

async function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return await new Promise((resolve, reject) => {
    canvas.toBlob(blob => {
      if (blob) resolve(blob);
      else reject(new Error('The browser could not encode this image.'));
    }, 'image/jpeg', quality);
  });
}

async function normalizeDevicePhoto(file: File): Promise<File> {
  if (file.size > MAX_SOURCE_BYTES) throw new Error('Photo is too large. Maximum source size is 15 MB.');
  const sourceUrl = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.decoding = 'async';
    image.src = sourceUrl;
    await image.decode();
    const width = image.naturalWidth;
    const height = image.naturalHeight;
    if (!width || !height) throw new Error('The selected photo could not be read.');
    if (width * height > MAX_PIXELS) throw new Error('Image dimensions are too large. Maximum is 40 megapixels.');
    const longest = Math.max(width, height);
    if (file.type === 'image/jpeg' && file.size <= TARGET_BYTES && longest <= MAX_DIMENSION) return file;
    let lastBlob: Blob | null = null;
    const sizes = [2560, 2304, 2048, 1920, 1800, 1600, 1440, 1280, 1024];
    const qualities = [0.88, 0.82, 0.76, 0.70, 0.64, 0.58, 0.52, 0.46, 0.40];
    for (const maxDimension of sizes) {
      const scale = Math.min(1, maxDimension / longest);
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(width * scale));
      canvas.height = Math.max(1, Math.round(height * scale));
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Your browser could not prepare the photo.');
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      for (const quality of qualities) {
        const blob = await canvasToBlob(canvas, quality);
        lastBlob = blob;
        if (blob.size <= TARGET_BYTES) {
          return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', {
            type: 'image/jpeg',
            lastModified: Date.now()
          });
        }
      }
    }
    if (lastBlob && lastBlob.size <= 5 * 1024 * 1024) {
      return new File([lastBlob], file.name.replace(/\.[^.]+$/, '') + '.jpg', {
        type: 'image/jpeg',
        lastModified: Date.now()
      });
    }
    throw new Error('The selected photo could not be compressed below 5 MB. Please choose another image.');
  } finally {
    URL.revokeObjectURL(sourceUrl);
  }
}

export default function RentalPhotoPicker({
  title,
  currentPreview,
  onCancel,
  onUse
}: Props) {
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [source, setSource] = useState<PickerSource>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [devicePreview, setDevicePreview] = useState('');
  const [url, setUrl] = useState('');
  const [checkedUrl, setCheckedUrl] = useState('');
  const [urlState, setUrlState] = useState<'idle'|'checking'|'loading'|'ready'|'error'>('idle');
  const [urlPreview, setUrlPreview] = useState('');
  const [fileError, setFileError] = useState('');
  const [fileBusy, setFileBusy] = useState(false);
  const requestId = useRef(0);

  useEffect(() => {
    return () => {
      if (devicePreview.startsWith('blob:')) URL.revokeObjectURL(devicePreview);
    };
  }, [devicePreview]);

  useEffect(() => {
    if (source !== 'url') {
      requestId.current += 1;
      setCheckedUrl('');
      setUrlPreview('');
      setUrlState('idle');
      return;
    }

    const normalized = url.trim();
    requestId.current += 1;
    const request = requestId.current;

    setCheckedUrl('');
    setUrlPreview('');
    if (!normalized) {
      setUrlState('idle');
      return;
    }

    if (!isHttpUrl(normalized)) {
      setUrlState('error');
      return;
    }

    setUrlState('checking');
    const timer = window.setTimeout(() => {
      const image = new Image();
      image.decoding = 'async';
      image.onload = () => {
        if (requestId.current !== request) return;
        setCheckedUrl(normalized);
        setUrlPreview(normalized);
        setUrlState('ready');
      };
      image.onerror = () => {
        if (requestId.current !== request) return;
        setUrlState('error');
      };
      image.src = normalized;
    }, 250);

    return () => window.clearTimeout(timer);
  }, [url, source]);

  function chooseSource(next: Exclude<PickerSource, null>) {
    if (next === 'device') {
      setUrl('');
      setCheckedUrl('');
      setUrlPreview('');
      setUrlState('idle');
      // Keep the native file chooser inside the original user gesture.
      fileInput.current?.click();
    } else {
      if (devicePreview.startsWith('blob:')) URL.revokeObjectURL(devicePreview);
      setSelectedFile(null);
      setDevicePreview('');
    }
    setSource(next);
  }

  async function handleFile(file?: File) {
    if (!file) return;

    if (!ALLOWED_TYPES.has(file.type)) {
      setFileError('Please choose a JPG, PNG or WebP image.');
      setSelectedFile(null);
      setDevicePreview('');
      return;
    }

    setFileBusy(true);
    setFileError('');
    setSelectedFile(null);
    if (devicePreview.startsWith('blob:')) URL.revokeObjectURL(devicePreview);
    setDevicePreview('');

    try {
      const normalized = await normalizeDevicePhoto(file);
      setSelectedFile(normalized);
      setDevicePreview(URL.createObjectURL(normalized));
      setSource('device');
    } catch (error: any) {
      setFileError(error?.message || 'Unable to prepare this photo.');
    } finally {
      setFileBusy(false);
    }
  }

  const candidatePreview = source === 'device' ? devicePreview : urlPreview;

  return (
    <div className="photo-picker-backdrop" onClick={onCancel}>
      <section className="photo-picker-modal" onClick={event => event.stopPropagation()}>
        <button className="photo-picker-close" type="button" onClick={onCancel} aria-label="Close">
          <X size={17} />
        </button>

        <div className="photo-picker-header">
          <span className="photo-picker-kicker">PHOTO</span>
          <h2>{title}</h2>
          <p>Choose a replacement. Your current photo stays unchanged until you save.</p>
        </div>

        {currentPreview && (
          <div className="photo-picker-current">
            <div>
              <span>Current photo</span>
              <small>The saved mPay photo</small>
            </div>
            <img src={currentPreview} alt="Current vehicle photo" />
          </div>
        )}

        <div className="photo-picker-source-grid">
          <button
            type="button"
            className={'photo-picker-source ' + (source === 'device' ? 'selected' : '')}
            onClick={() => chooseSource('device')}
          >
            <span className="photo-picker-source-icon"><Camera size={18} /></span>
            <span>
              <b>From device</b>
              <small>JPG, PNG or WebP · large photos optimized automatically</small>
            </span>
          </button>

          <button
            type="button"
            className={'photo-picker-source ' + (source === 'url' ? 'selected' : '')}
            onClick={() => chooseSource('url')}
          >
            <span className="photo-picker-source-icon"><Link2 size={18} /></span>
            <span>
              <b>Image URL</b>
              <small>Direct JPG, PNG or WebP · server optimizes larger images</small>
            </span>
          </button>
        </div>

        <input
          ref={fileInput}
          type="file"
          hidden
          accept="image/jpeg,image/png,image/webp"
          onChange={event => {
            void handleFile(event.target.files?.[0]);
            event.target.value = '';
          }}
        />

        {source === null && (
          <div className="photo-picker-empty">
            <ImagePlus size={21} />
            <div>
              <b>Choose a source</b>
              <span>Select a photo from your device or paste an image URL.</span>
            </div>
          </div>
        )}

        {source === 'device' && (
          <div className="photo-picker-candidate">
            {fileBusy && (<div className="photo-picker-result loading"><LoaderCircle size={16} className="photo-picker-spin" /><span>Optimizing photo before upload…</span></div>)}
            {fileError && (
              <div className="photo-picker-result error">
                <AlertCircle size={16} />
                <span>{fileError}</span>
              </div>
            )}
            {candidatePreview ? (
              <>
                <img src={candidatePreview} alt="Selected replacement" />
                <div className="photo-picker-result ready">
                  <CheckCircle2 size={16} />
                  <span>Photo selected and ready.</span>
                </div>
              </>
            ) : (
              <button type="button" className="photo-picker-dropzone" onClick={() => fileInput.current?.click()}>
                <Camera size={22} />
                <b>Choose a photo</b>
                <span>Tap to open your device photo picker.</span>
              </button>
            )}
          </div>
        )}

        {source === 'url' && (
          <div className="photo-picker-candidate">
            <label className="photo-picker-url-field">
              <span>Image URL</span>
              <input
                value={url}
                onChange={event => setUrl(event.target.value.slice(0, 2048))}
                placeholder="https://example.com/photo.jpg"
                inputMode="url"
                autoFocus
              />
            </label>

            {urlState === 'checking' && (
              <div className="photo-picker-result loading">
                <LoaderCircle size={16} className="photo-picker-spin" />
                <span>Checking image…</span>
              </div>
            )}

            {urlState === 'ready' && checkedUrl && (
              <>
                <img src={urlPreview} alt="URL replacement preview" className="photo-picker-url-preview" />
                <div className="photo-picker-result ready">
                  <CheckCircle2 size={16} />
                  <span>Image loaded and ready to use.</span>
                </div>
              </>
            )}

            {urlState === 'error' && (
              <div className="photo-picker-result error">
                <AlertCircle size={16} />
                <span>
                  {url.trim() && !isHttpUrl(url.trim())
                    ? 'Use an HTTP or HTTPS image URL.'
                    : 'Unable to load this image. Use a direct public image URL or choose a photo from your device.'}
                </span>
              </div>
            )}

            {urlState === 'idle' && (
              <div className="photo-picker-url-help">
                Paste a direct public image URL. Web pages and private links are not accepted. The server validates, resizes and compresses the downloaded image before storing it.
              </div>
            )}
          </div>
        )}

        <div className="photo-picker-actions">
          <button type="button" className="landing-secondary" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className="landing-primary"
            disabled={
              source === null ||
              (source === 'device' && (!selectedFile || fileBusy)) ||
              (source === 'url' && urlState !== 'ready')
            }
            onClick={() => {
              if (source === 'device' && selectedFile) {
                onUse({ source: 'device', file: selectedFile });
              } else if (source === 'url' && checkedUrl && urlState === 'ready') {
                onUse({ source: 'url', url: checkedUrl });
              }
            }}
          >
            Use this photo
          </button>
        </div>

        <p className="photo-picker-footnote">
          Images supplied by URL are copied into mPay storage when you save the vehicle. The source URL is not shown after selection.
        </p>
      </section>
    </div>
  );
}
