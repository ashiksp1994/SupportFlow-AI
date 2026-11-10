import React from 'react';
import { Plus } from 'lucide-react';
import { DashboardAnalytics } from '../lib/api';

interface AnalyticsOverviewPanelProps {
  metrics: DashboardAnalytics;
  onCreateTicketClick: () => void;
}

export function AnalyticsOverviewPanel({ metrics, onCreateTicketClick }: AnalyticsOverviewPanelProps) {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Real-Time Support Operations & AI Observability</h2>
          <p className="text-xs text-slate-400">
            Live metrics across SLA compliance, AI triage coverage, pgvector knowledge grounding, and human governance gates.
          </p>
        </div>
        <button
          onClick={onCreateTicketClick}
          className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-medium text-white cursor-pointer"
        >
          <Plus className="w-4 h-4" /> New Support Ticket
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <span className="text-xs text-slate-400">Active Support Tickets</span>
          <div className="text-2xl font-bold mt-1">{metrics.totalTickets}</div>
          <div className="text-xs text-slate-400 mt-1">
            Open: {metrics.openTickets} · Escalated: {metrics.escalatedTickets}
          </div>
        </div>
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <span className="text-xs text-slate-400">SLA Compliance Rate</span>
          <div className="text-2xl font-bold text-emerald-400 mt-1">
            {metrics.slaComplianceRatePercent}%
          </div>
          <div className="text-xs text-slate-400 mt-1">
            Breached SLAs: {metrics.slaBreachedTickets}
          </div>
        </div>
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <span className="text-xs text-slate-400">AI Triage & RAG Grounding</span>
          <div className="text-2xl font-bold text-indigo-400 mt-1">
            {metrics.aiTriagedTickets} / {metrics.totalTickets}
          </div>
          <div className="text-xs text-slate-400 mt-1">
            Indexed KB Articles: {metrics.totalKnowledgeArticles}
          </div>
        </div>
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <span className="text-xs text-slate-400">Pending Human AI Approvals</span>
          <div className="text-2xl font-bold text-amber-400 mt-1">
            {metrics.pendingAiApprovals}
          </div>
          <div className="text-xs text-slate-400 mt-1">
            Audit Trail Events: {metrics.totalAuditLogs}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <h3 className="text-sm font-semibold mb-3">Agent Workload Distribution</h3>
          <div className="space-y-3">
            {metrics.agentWorkloads.map((w) => (
              <div
                key={w.agentUsername}
                className="flex items-center justify-between p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs"
              >
                <div>
                  <span className="font-medium text-slate-200">{w.agentUsername}</span>
                  <span className="ml-2 text-slate-400">
                    ({w.highOrCriticalTickets} High/Critical)
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300">
                    {w.openTickets} Active
                  </span>
                  {w.escalatedTickets > 0 && (
                    <span className="px-2 py-0.5 rounded bg-rose-500/20 text-rose-300">
                      {w.escalatedTickets} Escalated
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <h3 className="text-sm font-semibold mb-3">Ticket Volume by Priority</h3>
          <div className="grid grid-cols-2 gap-3 text-xs">
            {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((prio) => (
              <div
                key={prio}
                className="p-3.5 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between"
              >
                <span className="font-medium text-slate-300">{prio}</span>
                <span className="text-base font-bold text-indigo-400">
                  {metrics.ticketsByPriority[prio] ?? 0}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
