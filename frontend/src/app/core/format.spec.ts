import { describe, expect, it } from 'vitest';

import { todayIso, whatsappLink } from './format';

describe('whatsappLink', () => {
  it('limpia el teléfono y codifica el mensaje', () => {
    expect(whatsappLink('+34 611-05 90 57', 'Hola Ana & cía')).toBe('https://wa.me/34611059057?text=Hola%20Ana%20%26%20c%C3%ADa');
  });
  it('devuelve null si no hay teléfono utilizable', () => {
    expect(whatsappLink(undefined, 'x')).toBeNull();
    expect(whatsappLink('12-3', 'x')).toBeNull();
  });
});

describe('todayIso', () => {
  it('tiene formato yyyy-MM-dd', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
