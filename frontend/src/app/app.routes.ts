import { Routes } from '@angular/router';

import { authGuard } from './core/auth.http';

export const routes: Routes = [
  { path: 'login', title: 'Entrar · GymFlow', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'panel' },
      { path: 'panel', title: 'Panel · GymFlow', loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard) },
      { path: 'acceso', title: 'Check-in · GymFlow', loadComponent: () => import('./features/checkin/checkin').then((m) => m.CheckInPage) },
      { path: 'socios', title: 'Socios · GymFlow', loadComponent: () => import('./features/members/member-list').then((m) => m.MemberList) },
      { path: 'socios/nuevo', title: 'Nuevo socio · GymFlow', loadComponent: () => import('./features/members/member-form').then((m) => m.MemberForm) },
      { path: 'socios/:id', title: 'Ficha de socio · GymFlow', loadComponent: () => import('./features/members/member-detail').then((m) => m.MemberDetail) },
      { path: 'socios/:id/editar', title: 'Editar socio · GymFlow', loadComponent: () => import('./features/members/member-form').then((m) => m.MemberForm) },
      { path: 'planes', title: 'Planes · GymFlow', loadComponent: () => import('./features/plans/plans').then((m) => m.Plans) },
    ],
  },
  { path: '**', redirectTo: '' },
];
