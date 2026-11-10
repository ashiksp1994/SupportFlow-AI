import React from 'react';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect } from 'vitest';
import App from '../App';

describe('SupportFlow AI Console', () => {
  it('renders the enterprise authentication screen with demo credentials', () => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    );

    expect(screen.getByText('SupportFlow AI')).toBeInTheDocument();
    expect(screen.getByText('Enterprise AI Customer Support & RAG Console')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign In to Agent Workspace/i })).toBeInTheDocument();
  });
});
