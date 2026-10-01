export type RechargeAmountTone = 'success' | 'pending' | 'failed' | 'neutral';

export function rechargeAmountPresentation(status: string | null | undefined, amount: string): { text: string; tone: RechargeAmountTone } {
  const normalized = String(status || '').toUpperCase();
  if (normalized === 'SUCCESS') return { text: `Wallet debited · ${amount}`, tone: 'success' };
  if (normalized === 'PENDING' || normalized === 'PROCESSING' || normalized === 'RESERVED') {
    return { text: `Amount reserved · ${amount}`, tone: 'pending' };
  }
  if (normalized === 'FAILED' || normalized === 'CANCELLED' || normalized === 'REJECTED') {
    return { text: `No wallet debit · ${amount} not charged`, tone: 'failed' };
  }
  return { text: `Wallet impact not confirmed · ${amount}`, tone: 'neutral' };
}

export function rechargeAmountCopy(status: string | null | undefined, amount: string): string {
  return rechargeAmountPresentation(status, amount).text;
}
