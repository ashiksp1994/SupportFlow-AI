import React from 'react';
import { Lock } from 'lucide-react';
import { AuditLog, CustomerProfile, Organisation } from '../lib/api';

export function DirectoryPanel({
  organisations,
  customers,
}: {
  organisations: Organisation[];
  customers: CustomerProfile[];
}) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
        <h3 className="text-sm font-semibold">Organisations & SLA Policies</h3>
        {organisations.map((o) => (
          <div
            key={o.id}
            className="p-3.5 rounded-lg bg-slate-950 border border-slate-800 text-xs flex justify-between"
          >
            <div>
              <div className="font-semibold text-slate-100">{o.name}</div>
              <div className="text-slate-400">{o.domain}</div>
            </div>
            <div className="text-right">
              <span className="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-semibold">
                {o.slaTier}
              </span>
              <div className="text-slate-400 mt-1">
                Response: {o.responseSlaMinutes}m · Resolution: {o.resolutionSlaHours}h
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
        <h3 className="text-sm font-semibold">Customer Contacts</h3>
        {customers.map((c) => (
          <div
            key={c.id}
            className="p-3.5 rounded-lg bg-slate-950 border border-slate-800 text-xs flex justify-between"
          >
            <div>
              <div className="font-semibold text-slate-100">
                {c.fullName} {c.vip && <span className="text-amber-400 ml-1">★ VIP</span>}
              </div>
              <div className="text-slate-400">
                {c.jobTitle} · {c.organisationName}
              </div>
            </div>
            <div className="text-slate-400">{c.email}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function AuditTrailPanel({ auditLogs }: { auditLogs: AuditLog[] }) {
  return (
    <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
      <h2 className="text-sm font-semibold flex items-center gap-2">
        <Lock className="w-4 h-4 text-indigo-400" /> Immutable Security & AI Governance Audit Log
      </h2>
      <div className="space-y-2">
        {auditLogs.map((log) => (
          <div
            key={log.id}
            className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs flex items-center justify-between"
          >
            <div className="space-x-2">
              <span className="font-mono px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300">
                {log.eventType}
              </span>
              <span className="font-medium text-slate-200">{log.details}</span>
            </div>
            <div className="text-slate-400 font-mono text-[11px]">
              Actor: {log.actorUsername} · {new Date(log.createdAt).toLocaleTimeString()}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
