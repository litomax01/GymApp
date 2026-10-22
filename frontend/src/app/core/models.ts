export type Role = 'ADMIN' | 'RECEPTION';
export type PaymentMethod = 'CASH' | 'CARD' | 'TRANSFER';
export type MembershipState = 'CURRENT' | 'UPCOMING' | 'EXPIRED' | 'CANCELED';

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

export interface LoginResponse {
  accessToken: string;
  expiresAt: string;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Member {
  id: number;
  documentNumber: string;
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  birthDate?: string;
  notes?: string;
  active: boolean;
  createdAt: string;
}

export interface MemberInput {
  documentNumber: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  birthDate: string | null;
  notes: string | null;
}

export interface Plan {
  id: number;
  name: string;
  price: number;
  durationDays: number;
  active: boolean;
}

export interface PlanInput {
  name: string;
  price: number;
  durationDays: number;
}

export interface PaymentInput {
  amount: number;
  method: PaymentMethod;
  reference?: string | null;
}

export interface Payment {
  id: number;
  amount: number;
  method: PaymentMethod;
  reference?: string;
  paidAt: string;
}

export interface Membership {
  id: number;
  memberId: number;
  planId: number;
  planName: string;
  startDate: string;
  endDate: string;
  price: number;
  paid: number;
  balance: number;
  state: MembershipState;
  payments: Payment[];
}

export interface ContractInput {
  planId: number;
  startDate?: string | null;
  payment?: PaymentInput | null;
}

export type DenyReason = 'MEMBER_INACTIVE' | 'NO_MEMBERSHIP' | 'MEMBERSHIP_EXPIRED';

export interface CheckIn {
  id: number;
  memberId: number;
  memberName: string;
  result: 'ALLOWED' | 'DENIED';
  reason?: DenyReason;
  checkedInAt: string;
  membershipEnd?: string;
  daysLeft?: number;
}

export interface AtRiskMember {
  memberId: number;
  name: string;
  phone?: string;
  reason: 'INACTIVE' | 'EXPIRING';
  lastVisit?: string;
  membershipEnd: string;
}

export interface DashboardSummary {
  activeMembers: number;
  expiringSoon: number;
  checkInsToday: number;
  monthRevenue?: number;
  pendingBalance?: number;
  atRisk: AtRiskMember[];
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fields?: { field: string; message: string }[];
}
