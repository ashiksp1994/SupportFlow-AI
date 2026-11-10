import React from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import { AiApprovalRequest } from '../lib/api';

interface ApprovalsGovernancePanelProps {
  approvals: AiApprovalRequest[];
  onDecide: (id: number, approved: boolean, decisionReason: string) => void;
}

export function ApprovalsGovernancePanel({ approvals, onDecide }: ApprovalsGovernancePanelProps) {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Human-in-the-Loop AI Agent Tool Governance</h2>
        <p className="text-xs text-slate-400">
          Consequential AI tool invocations (SLA billing credits, Tier-3 engineering escalations, ticket closures) are held in PENDING status until approved by an authorized Agent or Admin.
        </p>
      </div>

      <div className="space-y-3">
        {approvals.map((req) => (
          <div
            key={req.id}
            className="p-5 rounded-xl bg-slate-900 border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-4"
          >
            <div className="space-y-1.5 text-xs">
              <div className="flex items-center gap-2">
                <span className="font-mono font-bold text-indigo-400">{req.ticketKey}</span>
                <span className="px-2 py-0.5 rounded bg-slate-800 font-mono text-slate-200">
                  {req.toolName}
                </span>
                <span
                  className={`px-2 py-0.5 rounded font-semibold ${
                    req.status === 'PENDING'
                      ? 'bg-amber-500/20 text-amber-300'
                      : req.status === 'APPROVED'
                      ? 'bg-emerald-500/20 text-emerald-300'
                      : 'bg-rose-500/20 text-rose-300'
                  }`}
                >
                  {req.status}
                </span>
              </div>
              <div className="text-sm font-medium text-slate-100">{req.actionSummary}</div>
              <div className="text-slate-400">AI Reasoning: {req.aiReasoning}</div>
              <div className="font-mono text-[11px] text-slate-400">Payload: {req.parametersJson}</div>
              {req.executionResult && (
                <div className="text-emerald-300 font-medium mt-1">
                  Execution Result: {req.executionResult}
                </div>
              )}
            </div>

            {req.status === 'PENDING' && (
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() =>
                    onDecide(req.id, true, 'Verified and approved via Agent Governance Console')
                  }
                  className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-xs font-medium text-white cursor-pointer"
                >
                  <CheckCircle2 className="w-4 h-4" /> Approve & Execute Tool
                </button>
                <button
                  onClick={() =>
                    onDecide(req.id, false, 'Rejected by human supervisor')
                  }
                  className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 border border-rose-500/40 text-xs font-medium text-rose-300 cursor-pointer"
                >
                  <XCircle className="w-4 h-4" /> Reject
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
