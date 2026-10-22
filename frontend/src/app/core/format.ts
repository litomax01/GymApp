import { DenyReason, MembershipState, PaymentMethod } from './models';

/** Enlace wa.me con mensaje prellenado, o null si el teléfono no tiene dígitos suficientes. */
export function whatsappLink(phone: string | undefined, text: string): string | null {
  const digits = (phone ?? '').replace(/\D/g, '');
  return digits.length >= 8 ? `https://wa.me/${digits}?text=${encodeURIComponent(text)}` : null;
}

export const STATE_LABEL: Record<MembershipState, string> = {
  CURRENT: 'Vigente',
  UPCOMING: 'Programada',
  EXPIRED: 'Vencida',
  CANCELED: 'Cancelada',
};

export const STATE_BADGE: Record<MembershipState, string> = {
  CURRENT: 'badge-go',
  UPCOMING: 'badge-warn',
  EXPIRED: 'badge-stop',
  CANCELED: 'badge-idle',
};

export const METHOD_LABEL: Record<PaymentMethod, string> = { CASH: 'Efectivo', CARD: 'Tarjeta', TRANSFER: 'Transferencia' };

export const DENY_TEXT: Record<DenyReason, string> = {
  MEMBER_INACTIVE: 'El socio está dado de baja.',
  NO_MEMBERSHIP: 'No tiene ninguna membresía contratada.',
  MEMBERSHIP_EXPIRED: 'Su membresía está vencida.',
};

/** Fecha ISO (yyyy-MM-dd) de hoy en la zona local. */
export function todayIso(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function daysSince(iso: string | undefined): number | null {
  if (!iso) return null;
  return Math.floor((Date.now() - Date.parse(iso)) / 86_400_000);
}
