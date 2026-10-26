import { TestBed } from '@angular/core/testing';
import { StatusChip } from './status-chip';

describe('StatusChip', () => {
  function render(value: string): HTMLElement {
    const fixture = TestBed.createComponent(StatusChip);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('.chip');
  }

  it('shows stopped machines in the alarm colour', () => {
    const chip = render('STOPPED');
    expect(chip.classList).toContain('bad');
    expect(chip.textContent).toBe('stopped');
  });

  it('formats multi-word values', () => {
    expect(render('ROTATION_SPEED').textContent).toBe('rotation speed');
  });
});
