import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoadingIndicator } from './LoadingIndicator';

describe('LoadingIndicator', () => {
  it('renders default message', () => {
    render(<LoadingIndicator />);
    expect(screen.getByText('Lade...')).toBeInTheDocument();
  });

  it('renders custom message', () => {
    render(<LoadingIndicator message="Bitte warten..." />);
    expect(screen.getByText('Bitte warten...')).toBeInTheDocument();
  });

  it('has correct CSS class', () => {
    const { container } = render(<LoadingIndicator />);
    expect(container.firstChild).toHaveClass('loading-indicator');
  });
});
