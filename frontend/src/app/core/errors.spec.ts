import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { applyFieldErrors, controlError, errorMessage } from './errors';

describe('errorMessage', () => {
  it('avisa de falta de conexión', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('No hay conexión');
  });
  it('usa el mensaje del servidor en errores de negocio', () => {
    const e = new HttpErrorResponse({ status: 409, error: { message: 'El periodo se solapa' } });
    expect(errorMessage(e)).toBe('El periodo se solapa');
  });
  it('no filtra detalles en errores 500', () => {
    const e = new HttpErrorResponse({ status: 500, error: { message: 'NullPointerException en X' } });
    expect(errorMessage(e)).not.toContain('NullPointer');
  });
});

describe('formularios', () => {
  it('traslada errores del servidor al campo', () => {
    const form = new FormGroup({ email: new FormControl('x') });
    const err = new HttpErrorResponse({ status: 400, error: { fields: [{ field: 'email', message: 'Email inválido' }] } });
    expect(applyFieldErrors(err, form)).toBe(true);
    expect(controlError(form, 'email')).toBe('Email inválido');
  });
  it('solo muestra errores en campos tocados', () => {
    const form = new FormGroup({ name: new FormControl('', Validators.required) });
    expect(controlError(form, 'name')).toBeNull();
    form.get('name')!.markAsTouched();
    expect(controlError(form, 'name')).toBe('Este campo es obligatorio.');
  });
});
