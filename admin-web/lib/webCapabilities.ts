'use client';

import { useEffect, useState } from 'react';

export type WebFormFactor = 'mobile' | 'tablet' | 'desktop';

export type WebCapabilities = {
  formFactor: WebFormFactor;
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  isHandheld: boolean;
  hasTouch: boolean;
  isSecureContext: boolean;
  canUseContactPicker: boolean;
};

type ContactPickerNavigator = Navigator & {
  contacts?: {
    select?: (properties: string[], options?: { multiple?: boolean }) => Promise<Array<{ tel?: string[]; name?: string[] }>>;
    getProperties?: () => Promise<string[]>;
  };
};

function detectWebCapabilities(): WebCapabilities {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return {
      formFactor: 'desktop',
      isMobile: false,
      isTablet: false,
      isDesktop: true,
      isHandheld: false,
      hasTouch: false,
      isSecureContext: false,
      canUseContactPicker: false,
    };
  }

  const userAgent = navigator.userAgent || '';
  const isAndroid = /Android/i.test(userAgent);
  const isAndroidPhone = /Android.*Mobile/i.test(userAgent);
  const isIPhone = /iPhone|iPod/i.test(userAgent);
  const isIPad = /iPad/i.test(userAgent) || (/Macintosh/i.test(userAgent) && navigator.maxTouchPoints > 1);
  const isOtherPhone = /Windows Phone|BlackBerry|BB10|Opera Mini|IEMobile/i.test(userAgent);

  const isMobile = isIPhone || isAndroidPhone || isOtherPhone;
  const isTablet = isIPad || (isAndroid && !isAndroidPhone);
  const formFactor: WebFormFactor = isMobile ? 'mobile' : isTablet ? 'tablet' : 'desktop';
  const hasTouch = navigator.maxTouchPoints > 0 || window.matchMedia('(pointer: coarse)').matches;
  const isSecureContext = window.isSecureContext;
  const contactPicker = (navigator as ContactPickerNavigator).contacts;
  const contactPickerCandidate = isMobile
    && isSecureContext
    && typeof contactPicker?.select === 'function'
    && typeof contactPicker?.getProperties === 'function';

  return {
    formFactor,
    isMobile,
    isTablet,
    isDesktop: formFactor === 'desktop',
    isHandheld: isMobile || isTablet,
    hasTouch,
    isSecureContext,
    // The async hook turns this on only after confirming that "tel" is supported.
    canUseContactPicker: false,
  };
}

function contactPickerCandidate(capabilities: WebCapabilities): boolean {
  if (typeof navigator === 'undefined') return false;
  const contactManager = (navigator as ContactPickerNavigator).contacts;
  return capabilities.isMobile
    && capabilities.isSecureContext
    && typeof contactManager?.select === 'function'
    && typeof contactManager?.getProperties === 'function';
}

export function getWebCapabilities(): WebCapabilities {
  return detectWebCapabilities();
}

export function useWebCapabilities(): WebCapabilities {
  const [capabilities, setCapabilities] = useState<WebCapabilities>(detectWebCapabilities);

  useEffect(() => {
    let cancelled = false;

    const refresh = async () => {
      const detected = detectWebCapabilities();
      const contactManager = (navigator as ContactPickerNavigator).contacts;

      if (contactPickerCandidate(detected) && typeof contactManager?.getProperties === 'function') {
        try {
          const properties = await contactManager.getProperties();
          detected.canUseContactPicker = properties.includes('tel');
        } catch {
          detected.canUseContactPicker = false;
        }
      }

      if (!cancelled) setCapabilities(detected);
    };

    const onViewportChange = () => { void refresh(); };
    void refresh();
    window.addEventListener('resize', onViewportChange);
    window.addEventListener('orientationchange', onViewportChange);

    return () => {
      cancelled = true;
      window.removeEventListener('resize', onViewportChange);
      window.removeEventListener('orientationchange', onViewportChange);
    };
  }, []);

  return capabilities;
}
