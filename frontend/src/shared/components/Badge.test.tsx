import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusBadge } from './Badge';

describe('StatusBadge', () => {
  it('renders text label alongside tone for non-text-only status communication', () => {
    render(<StatusBadge status="LOCKED" label="Terkunci" />);
    expect(screen.getByText('Terkunci')).toBeInTheDocument();
  });

  it('falls back to the raw status when no label is supplied', () => {
    render(<StatusBadge status="IN_REVIEW" />);
    expect(screen.getByText('IN_REVIEW')).toBeInTheDocument();
  });
});
