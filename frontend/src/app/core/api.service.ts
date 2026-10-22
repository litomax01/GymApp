import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CheckIn, ContractInput, DashboardSummary, Member, MemberInput, Membership, Page, PaymentInput, Plan, PlanInput,
} from './models';

/** Único punto de acceso HTTP a la API; el resto de la app no conoce URLs. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // Socios
  searchMembers(q: string, page = 0, onlyActive = true, size = 20): Observable<Page<Member>> {
    const params = new HttpParams().set('q', q).set('page', page).set('size', size).set('onlyActive', onlyActive);
    return this.http.get<Page<Member>>('/api/members', { params });
  }
  getMember(id: number): Observable<Member> {
    return this.http.get<Member>(`/api/members/${id}`);
  }
  createMember(body: MemberInput): Observable<Member> {
    return this.http.post<Member>('/api/members', body);
  }
  updateMember(id: number, body: MemberInput): Observable<Member> {
    return this.http.put<Member>(`/api/members/${id}`, body);
  }
  setMemberActive(id: number, active: boolean): Observable<Member> {
    return this.http.patch<Member>(`/api/members/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  // Planes
  listPlans(onlyActive = true): Observable<Plan[]> {
    return this.http.get<Plan[]>('/api/plans', { params: new HttpParams().set('onlyActive', onlyActive) });
  }
  createPlan(body: PlanInput): Observable<Plan> {
    return this.http.post<Plan>('/api/plans', body);
  }
  updatePlan(id: number, body: PlanInput): Observable<Plan> {
    return this.http.put<Plan>(`/api/plans/${id}`, body);
  }
  setPlanActive(id: number, active: boolean): Observable<Plan> {
    return this.http.patch<Plan>(`/api/plans/${id}/active/${active}`, {});
  }

  // Membresías y pagos
  listMemberships(memberId: number): Observable<Membership[]> {
    return this.http.get<Membership[]>(`/api/members/${memberId}/memberships`);
  }
  contract(memberId: number, body: ContractInput): Observable<Membership> {
    return this.http.post<Membership>(`/api/members/${memberId}/memberships`, body);
  }
  pay(membershipId: number, body: PaymentInput): Observable<Membership> {
    return this.http.post<Membership>(`/api/memberships/${membershipId}/payments`, body);
  }
  cancelMembership(membershipId: number): Observable<Membership> {
    return this.http.patch<Membership>(`/api/memberships/${membershipId}/cancel`, {});
  }

  // Acceso y panel
  checkIn(memberId: number): Observable<CheckIn> {
    return this.http.post<CheckIn>('/api/check-ins', { memberId });
  }
  recentCheckIns(limit = 12): Observable<CheckIn[]> {
    return this.http.get<CheckIn[]>('/api/check-ins/recent', { params: new HttpParams().set('limit', limit) });
  }
  dashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>('/api/dashboard');
  }
}
