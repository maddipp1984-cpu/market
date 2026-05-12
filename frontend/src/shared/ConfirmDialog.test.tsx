import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { ConfirmDialog } from './ConfirmDialog';

describe('ConfirmDialog', () => {
  it('renders title and message', () => {
    render(
      <ConfirmDialog
        onConfirm={() => {}}
        onCancel={() => {}}
      />
    );
    expect(screen.getByText('Wirklich loeschen?')).toBeInTheDocument();
    expect(screen.getByText('Dieser Vorgang kann nicht rueckgaengig gemacht werden.')).toBeInTheDocument();
  });

  it('renders custom title and message', () => {
    render(
      <ConfirmDialog
        title="Sicher?"
        message="Wirklich fortfahren?"
        onConfirm={() => {}}
        onCancel={() => {}}
      />
    );
    expect(screen.getByText('Sicher?')).toBeInTheDocument();
    expect(screen.getByText('Wirklich fortfahren?')).toBeInTheDocument();
  });

  it('calls onConfirm when confirm button is clicked', async () => {
    const onConfirm = vi.fn();
    render(<ConfirmDialog onConfirm={onConfirm} onCancel={() => {}} />);

    await userEvent.click(screen.getByText('Ja, loeschen'));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('calls onCancel when cancel button is clicked', async () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog onConfirm={() => {}} onCancel={onCancel} />);

    await userEvent.click(screen.getByText('Abbrechen'));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('disables buttons when loading', () => {
    render(
      <ConfirmDialog loading onConfirm={() => {}} onCancel={() => {}} />
    );
    expect(screen.getByText('Loeschen...')).toBeDisabled();
    expect(screen.getByText('Abbrechen')).toBeDisabled();
  });
});
