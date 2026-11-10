import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import App from '../App';
import { AnalyticsOverviewPanel } from '../components/AnalyticsOverviewPanel';
import { ApprovalsGovernancePanel } from '../components/ApprovalsGovernancePanel';

describe('SupportFlow AI Console', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

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

  it('renders AnalyticsOverviewPanel metrics, SLA compliance, and agent workloads accurately', () => {
    const onNewTicket = vi.fn();
    render(
      <AnalyticsOverviewPanel
        metrics={{
          totalTickets: 12,
          openTickets: 5,
          inProgressTickets: 4,
          escalatedTickets: 2,
          resolvedTickets: 3,
          slaBreachedTickets: 1,
          slaComplianceRatePercent: 91.7,
          aiTriagedTickets: 10,
          pendingAiApprovals: 2,
          totalKnowledgeArticles: 8,
          totalOrganisations: 3,
          totalCustomers: 6,
          totalAuditLogs: 42,
          ticketsByPriority: { CRITICAL: 2, HIGH: 4, MEDIUM: 4, LOW: 2 },
          agentWorkloads: [
            {
              agentUsername: 'sarah.chen',
              openTickets: 3,
              highOrCriticalTickets: 2,
              escalatedTickets: 1,
            },
          ],
        }}
        onCreateTicketClick={onNewTicket}
      />
    );

    expect(screen.getByText('91.7%')).toBeInTheDocument();
    expect(screen.getByText('sarah.chen')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /New Support Ticket/i }));
    expect(onNewTicket).toHaveBeenCalledTimes(1);
  });

  it('renders ApprovalsGovernancePanel and invokes human decision callback on Approve or Reject', () => {
    const onDecide = vi.fn();
    render(
      <ApprovalsGovernancePanel
        approvals={[
          {
            id: 77,
            ticketId: 10,
            ticketKey: 'SF-1001',
            toolName: 'ESCALATE_TO_TIER3_ENGINEERING',
            actionSummary: 'Escalate ticket SF-1001 to Tier-3 Principal Engineering',
            parametersJson: '{"severity":"P1"}',
            aiReasoning: 'Production SSO outage affecting 1,400 users',
            confidence: 0.94,
            status: 'PENDING',
            requestedBy: 'AI_COPILOT',
            createdAt: new Date().toISOString(),
          },
        ]}
        onDecide={onDecide}
      />
    );

    expect(screen.getByText('SF-1001')).toBeInTheDocument();
    expect(screen.getByText('ESCALATE_TO_TIER3_ENGINEERING')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Approve & Execute Tool/i }));
    expect(onDecide).toHaveBeenCalledWith(
      77,
      true,
      'Verified and approved via Agent Governance Console'
    );
  });

  it('displays error banner when login fails with 401', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('Invalid username or password', { status: 401 })
    );

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    );

    fireEvent.click(screen.getByRole('button', { name: /Sign In to Agent Workspace/i }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Invalid username or password/i);
    });
  });
});
