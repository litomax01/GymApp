import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, tap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/errors';
import { Member, Page } from '../../core/models';

@Component({
  selector: 'app-member-list',
  imports: [RouterLink],
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 1rem; align-items: end; margin-bottom: 1rem; }
    .filters .field { flex: 1 1 18rem; }
    .check { display: flex; gap: 0.5rem; align-items: center; min-height: 2.75rem; }
    .check input { width: 1.25rem; height: 1.25rem; }
    .pager { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-top: 1rem; }
    .sk { height: 3rem; margin-bottom: 0.5rem; }
  `,
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Socios</h1><p>{{ data() ? data()!.totalElements + ' en total' : 'Altas, fichas y cuotas.' }}</p></div>
        <a class="btn" routerLink="/socios/nuevo">Nuevo socio</a>
      </div>

      <div class="filters">
        <div class="field">
          <label for="q">Buscar</label>
          <input id="q" type="search" placeholder="Nombre, apellido o documento" autocomplete="off" (input)="onSearch($any($event.target).value)" />
        </div>
        <label class="check"><input type="checkbox" [checked]="onlyActive()" (change)="toggleActive($any($event.target).checked)" /> Solo socios activos</label>
      </div>

      @if (loading()) {
        <div role="status" aria-label="Cargando socios">
          <div class="skeleton sk"></div><div class="skeleton sk"></div><div class="skeleton sk"></div>
        </div>
      } @else if (error()) {
        <div class="alert-box" role="alert"><span>{{ error() }}</span><button class="btn btn-quiet btn-small" type="button" (click)="fetch()">Reintentar</button></div>
      } @else if (data(); as d) {
        @if (d.content.length === 0) {
          <div class="state">
            @if (q()) { <strong>Sin resultados</strong>Ningún socio coincide con "{{ q() }}". }
            @else { <strong>Aún no hay socios</strong>Da de alta al primero para empezar a registrar cuotas y entradas.
              <br /><a class="btn" routerLink="/socios/nuevo">Dar de alta un socio</a> }
          </div>
        } @else {
          <div class="table-wrap">
            <table>
              <caption class="sr-only">Listado de socios</caption>
              <thead><tr><th>Nombre</th><th>Documento</th><th>Teléfono</th><th>Estado</th></tr></thead>
              <tbody>
                @for (m of d.content; track m.id) {
                  <tr>
                    <td><a [routerLink]="['/socios', m.id]"><strong>{{ m.lastName }}, {{ m.firstName }}</strong></a></td>
                    <td class="num">{{ m.documentNumber }}</td>
                    <td>{{ m.phone || '—' }}</td>
                    <td><span class="badge" [class.badge-go]="m.active" [class.badge-idle]="!m.active">{{ m.active ? 'Activo' : 'De baja' }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (d.totalPages > 1) {
            <nav class="pager" aria-label="Paginación">
              <button class="btn btn-quiet btn-small" type="button" [disabled]="d.page === 0" (click)="go(d.page - 1)">Anterior</button>
              <span class="muted">Página {{ d.page + 1 }} de {{ d.totalPages }}</span>
              <button class="btn btn-quiet btn-small" type="button" [disabled]="d.page + 1 >= d.totalPages" (click)="go(d.page + 1)">Siguiente</button>
            </nav>
          }
        }
      }
    </div>
  `,
})
export class MemberList implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly search$ = new Subject<string>();
  private page = 0;

  protected readonly q = signal('');
  protected readonly onlyActive = signal(true);
  protected readonly data = signal<Page<Member> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.search$.pipe(debounceTime(300), tap((v) => { this.q.set(v); this.page = 0; }), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.fetch());
  }

  ngOnInit(): void {
    this.fetch();
  }

  protected onSearch(v: string): void {
    this.search$.next(v.trim());
  }

  protected toggleActive(v: boolean): void {
    this.onlyActive.set(v);
    this.page = 0;
    this.fetch();
  }

  protected go(page: number): void {
    this.page = page;
    this.fetch();
  }

  protected fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.searchMembers(this.q(), this.page, this.onlyActive()).subscribe({
      next: (p) => { this.data.set(p); this.loading.set(false); },
      error: (e: unknown) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
