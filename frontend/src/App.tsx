import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  ShieldCheck,
  Ticket as TicketIcon,
  Sparkles,
  BookOpen,
  AlertTriangle,
  BarChart3,
  Users,
  FileSearch,
  Send,
  Lock,
  Bot,
  Activity,
} from 'lucide-react';
import {
  apiRequest,
  logoutSession,
  AuthResponse,
  DashboardAnalytics,
  Ticket,
  ConversationMessage,
  RagReplySuggestion,
  AiTriageResult,
  ConversationSummaryResult,
  AiApprovalRequest,
  KnowledgeArticle,
  KnowledgeSearchResult,
  Organisation,
  CustomerProfile,
  AuditLog,
  createTicketSchema,
  CreateTicketFormValues,
  createArticleSchema,
  CreateArticleFormValues,
} from './lib/api';
import { AnalyticsOverviewPanel } from './components/AnalyticsOverviewPanel';
import { ApprovalsGovernancePanel } from './components/ApprovalsGovernancePanel';
import { DirectoryPanel, AuditTrailPanel } from './components/DirectoryAndAuditPanels';

type ActiveTab = 'dashboard' | 'tickets' | 'approvals' | 'knowledge' | 'directory' | 'audit';

export default function App() {
  const queryClient = useQueryClient();
  const [auth, setAuth] = useState<AuthResponse | null>(null);
  const [usernameInput, setUsernameInput] = useState('sarah.chen');
  const [passwordInput, setPasswordInput] = useState('SupportFlow!2026');
  const [authError, setAuthError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<ActiveTab>('dashboard');
  const [selectedTicketId, setSelectedTicketId] = useState<number | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [internalNote, setInternalNote] = useState(false);
  const [kbSearchQuery, setKbSearchQuery] = useState('SAML certificate 401 signature');
  const [latestTriage, setLatestTriage] = useState<AiTriageResult | null>(null);
  const [latestRagSuggestion, setLatestRagSuggestion] = useState<RagReplySuggestion | null>(null);
  const [, setLatestSummary] = useState<ConversationSummaryResult | null>(null);
  const [showNewTicketModal, setShowNewTicketModal] = useState(false);

  const token = auth?.accessToken || null;

  const loginMutation = useMutation({
    mutationFn: async () =>
      apiRequest<AuthResponse>('/api/auth/login', null, {
        method: 'POST',
        body: JSON.stringify({ username: usernameInput, password: passwordInput }),
      }),
    onSuccess: (data) => {
      setAuth(data);
      setAuthError(null);
    },
    onError: (err: Error) => {
      setAuthError(err.message || 'Authentication failed');
    },
  });

  const handleSignOut = async () => {
    await logoutSession(auth?.accessToken || null, auth?.refreshToken || null);
    setAuth(null);
    queryClient.clear();
  };

  const analyticsQuery = useQuery({
    queryKey: ['analytics', token],
    queryFn: () => apiRequest<DashboardAnalytics>('/api/analytics/dashboard', token),
    enabled: Boolean(token && auth?.user.role !== 'CUSTOMER'),
  });

  const ticketsQuery = useQuery({
    queryKey: ['tickets', token],
    queryFn: () => apiRequest<Ticket[]>('/api/tickets', token),
    enabled: Boolean(token),
  });

  const activeTicket =
    ticketsQuery.data?.find((t) => t.id === selectedTicketId) || ticketsQuery.data?.[0] || null;

  const messagesQuery = useQuery({
    queryKey: ['messages', activeTicket?.id, token],
    queryFn: () => apiRequest<ConversationMessage[]>(`/api/tickets/${activeTicket!.id}/messages`, token),
    enabled: Boolean(token && activeTicket?.id),
  });

  const approvalsQuery = useQuery({
    queryKey: ['approvals', token],
    queryFn: () => apiRequest<AiApprovalRequest[]>('/api/ai/approvals', token),
    enabled: Boolean(token && auth?.user.role !== 'CUSTOMER'),
  });

  useQuery({
    queryKey: ['kb-articles', token],
    queryFn: () => apiRequest<KnowledgeArticle[]>('/api/knowledge/articles', token),
    enabled: Boolean(token),
  });

  const kbSearchResultsQuery = useQuery({
    queryKey: ['kb-search', kbSearchQuery, token],
    queryFn: () =>
      apiRequest<KnowledgeSearchResult[]>(
        `/api/knowledge/search?query=${encodeURIComponent(kbSearchQuery)}&limit=4`,
        token
      ),
    enabled: Boolean(token),
  });

  const orgsQuery = useQuery({
    queryKey: ['orgs', token],
    queryFn: () => apiRequest<Organisation[]>('/api/organisations', token),
    enabled: Boolean(token && activeTab === 'directory'),
  });

  const customersQuery = useQuery({
    queryKey: ['customers', token],
    queryFn: () => apiRequest<CustomerProfile[]>('/api/customers', token),
    enabled: Boolean(token && activeTab === 'directory'),
  });

  const auditLogsQuery = useQuery({
    queryKey: ['audit-logs', token],
    queryFn: () => apiRequest<AuditLog[]>('/api/audit-logs', token),
    enabled: Boolean(token && activeTab === 'audit'),
  });

  const triageMutation = useMutation({
    mutationFn: (ticketId: number) =>
      apiRequest<AiTriageResult>(`/api/ai/tickets/${ticketId}/triage`, token, { method: 'POST' }),
    onSuccess: (data) => {
      setLatestTriage(data);
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
    },
  });

  const suggestReplyMutation = useMutation({
    mutationFn: (ticketId: number) =>
      apiRequest<RagReplySuggestion>(`/api/ai/tickets/${ticketId}/suggest-reply`, token, { method: 'POST' }),
    onSuccess: (data) => {
      setLatestRagSuggestion(data);
      setReplyContent(data.suggestedReply);
    },
  });

  const summarizeMutation = useMutation({
    mutationFn: (ticketId: number) =>
      apiRequest<ConversationSummaryResult>(`/api/ai/tickets/${ticketId}/summarize`, token, { method: 'POST' }),
    onSuccess: (data) => {
      setLatestSummary(data);
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const sendMessageMutation = useMutation({
    mutationFn: (ticketId: number) =>
      apiRequest<ConversationMessage>(`/api/tickets/${ticketId}/messages`, token, {
        method: 'POST',
        body: JSON.stringify({
          content: replyContent,
          senderType: 'AGENT',
          senderName: auth?.user.fullName || auth?.user.username,
          internalNote,
          aiGenerated: Boolean(latestRagSuggestion),
        }),
      }),
    onSuccess: () => {
      setReplyContent('');
      queryClient.invalidateQueries({ queryKey: ['messages'] });
    },
  });

  const proposeToolMutation = useMutation({
    mutationFn: ({ ticketId, toolName }: { ticketId: number; toolName: string }) =>
      apiRequest<AiApprovalRequest>(`/api/ai/tickets/${ticketId}/approvals`, token, {
        method: 'POST',
        body: JSON.stringify({ toolName }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvals'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
    },
  });

  const decideApprovalMutation = useMutation({
    mutationFn: ({ id, approved, decisionReason }: { id: number; approved: boolean; decisionReason: string }) =>
      apiRequest<AiApprovalRequest>(`/api/ai/approvals/${id}/decide`, token, {
        method: 'POST',
        body: JSON.stringify({ approved, decisionReason }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvals'] });
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
      queryClient.invalidateQueries({ queryKey: ['messages'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
    },
  });

  const ticketForm = useForm<CreateTicketFormValues>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: {
      subject: '',
      description: '',
      priority: 'HIGH',
      category: 'SECURITY',
      assignedAgentUsername: 'sarah.chen',
    },
  });

  const createTicketMutation = useMutation({
    mutationFn: (values: CreateTicketFormValues) =>
      apiRequest<Ticket>('/api/tickets', token, {
        method: 'POST',
        body: JSON.stringify(values),
      }),
    onSuccess: (created) => {
      ticketForm.reset();
      setShowNewTicketModal(false);
      setSelectedTicketId(created.id);
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
    },
  });

  const articleForm = useForm<CreateArticleFormValues>({
    resolver: zodResolver(createArticleSchema),
    defaultValues: {
      title: '',
      category: 'SECURITY',
      tags: 'sso,saml,security',
      content: '',
    },
  });

  const createArticleMutation = useMutation({
    mutationFn: (values: CreateArticleFormValues) =>
      apiRequest<KnowledgeArticle>('/api/knowledge/articles', token, {
        method: 'POST',
        body: JSON.stringify(values),
      }),
    onSuccess: () => {
      articleForm.reset();
      queryClient.invalidateQueries({ queryKey: ['kb-articles'] });
      queryClient.invalidateQueries({ queryKey: ['kb-search'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
    },
  });

  if (!auth) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-center items-center p-6">
        <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2.5 rounded-xl bg-indigo-500/20 border border-indigo-500/40 text-indigo-400">
              <Sparkles className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight">SupportFlow AI</h1>
              <p className="text-xs text-slate-400">Enterprise AI Customer Support & RAG Console</p>
            </div>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              loginMutation.mutate();
            }}
            className="space-y-4"
          >
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1" htmlFor="username-input">
                Agent / Admin Username
              </label>
              <input
                id="username-input"
                value={usernameInput}
                onChange={(e) => setUsernameInput(e.target.value)}
                className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3.5 py-2 text-sm text-slate-100 focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1" htmlFor="password-input">
                Password
              </label>
              <input
                id="password-input"
                type="password"
                value={passwordInput}
                onChange={(e) => setPasswordInput(e.target.value)}
                className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3.5 py-2 text-sm text-slate-100 focus:outline-none focus:border-indigo-500"
              />
            </div>

            {authError && (
              <div role="alert" className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-xs text-rose-300">
                {authError}
              </div>
            )}

            <button
              type="submit"
              disabled={loginMutation.isPending}
              className="w-full py-2.5 px-4 rounded-lg bg-indigo-600 hover:bg-indigo-500 font-medium text-sm text-white transition cursor-pointer"
            >
              {loginMutation.isPending ? 'Authenticating...' : 'Sign In to Agent Workspace'}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-slate-800 text-xs text-slate-400 space-y-1.5">
            <p className="font-semibold text-slate-300">Demo Accounts (Pre-seeded):</p>
            <div className="flex justify-between">
              <span>Principal Agent: <code className="text-indigo-300">sarah.chen</code></span>
              <span>Pass: <code className="text-indigo-300">SupportFlow!2026</code></span>
            </div>
            <div className="flex justify-between">
              <span>Platform Admin: <code className="text-indigo-300">admin</code></span>
              <span>Pass: <code className="text-indigo-300">SupportFlow!2026</code></span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const metrics = analyticsQuery.data;
  const pendingApprovalsCount = metrics?.pendingAiApprovals ?? 0;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Top Navigation Header */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur px-6 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-indigo-500/20 border border-indigo-500/40 text-indigo-400">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <span className="font-bold tracking-tight text-base">SupportFlow AI</span>
              <span className="ml-2 text-[11px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                Java 25 · Spring Boot 4.1 · RAG
              </span>
            </div>
          </div>

          <nav className="flex items-center gap-1" aria-label="Main workspace navigation">
            {[
              { id: 'dashboard', label: 'Analytics Overview', icon: BarChart3 },
              { id: 'tickets', label: 'Ticket & AI Copilot Workspace', icon: TicketIcon },
              {
                id: 'approvals',
                label: `Human Approval Gate (${pendingApprovalsCount})`,
                icon: ShieldCheck,
              },
              { id: 'knowledge', label: 'Knowledge Base & RAG Search', icon: BookOpen },
              { id: 'directory', label: 'Organisations & Customers', icon: Users },
              { id: 'audit', label: 'Security Audit Trail', icon: Lock },
            ].map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id as ActiveTab)}
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer ${
                    isActive
                      ? 'bg-indigo-600 text-white'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </div>

        <div className="flex items-center gap-3 text-xs">
          <span className="px-2.5 py-1 rounded-md bg-slate-800 text-slate-300 border border-slate-700">
            {auth.user.fullName || auth.user.username} ({auth.user.role})
          </span>
          <button
            onClick={handleSignOut}
            className="text-slate-400 hover:text-slate-200 px-2 py-1 cursor-pointer"
          >
            Sign Out
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-6 max-w-7xl w-full mx-auto space-y-6">
        {activeTab === 'dashboard' && metrics && (
          <AnalyticsOverviewPanel
            metrics={metrics}
            onCreateTicketClick={() => {
              setActiveTab('tickets');
              setShowNewTicketModal(true);
            }}
          />
        )}

        {activeTab === 'tickets' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Ticket Queue */}
            <div className="lg:col-span-4 space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold">Support Ticket Queue</h2>
                <button
                  onClick={() => setShowNewTicketModal(!showNewTicketModal)}
                  className="text-xs px-2.5 py-1 rounded bg-indigo-600 hover:bg-indigo-500 text-white cursor-pointer"
                >
                  + New Ticket
                </button>
              </div>

              {showNewTicketModal && (
                <form
                  onSubmit={ticketForm.handleSubmit((v) => createTicketMutation.mutate(v))}
                  className="p-4 rounded-xl bg-slate-900 border border-indigo-500/40 space-y-3 text-xs"
                >
                  <div className="font-semibold text-indigo-300">Create Support Ticket</div>
                  <input
                    placeholder="Ticket Subject"
                    {...ticketForm.register('subject')}
                    className="w-full rounded bg-slate-950 border border-slate-800 px-3 py-1.5"
                  />
                  <textarea
                    placeholder="Detailed customer issue description..."
                    rows={3}
                    {...ticketForm.register('description')}
                    className="w-full rounded bg-slate-950 border border-slate-800 px-3 py-1.5"
                  />
                  <div className="grid grid-cols-2 gap-2">
                    <select
                      {...ticketForm.register('priority')}
                      className="rounded bg-slate-950 border border-slate-800 px-2 py-1.5"
                    >
                      <option value="CRITICAL">CRITICAL</option>
                      <option value="HIGH">HIGH</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="LOW">LOW</option>
                    </select>
                    <select
                      {...ticketForm.register('category')}
                      className="rounded bg-slate-950 border border-slate-800 px-2 py-1.5"
                    >
                      <option value="SECURITY">SECURITY</option>
                      <option value="BILLING">BILLING</option>
                      <option value="INTEGRATION">INTEGRATION</option>
                      <option value="TECHNICAL_ISSUE">TECHNICAL_ISSUE</option>
                    </select>
                  </div>
                  <button
                    type="submit"
                    className="w-full py-1.5 rounded bg-indigo-600 text-white font-medium cursor-pointer"
                  >
                    Create Ticket
                  </button>
                </form>
              )}

              <div className="space-y-2.5">
                {ticketsQuery.data?.map((t) => {
                  const isSelected = activeTicket?.id === t.id;
                  return (
                    <div
                      key={t.id}
                      onClick={() => setSelectedTicketId(t.id)}
                      className={`p-4 rounded-xl border transition cursor-pointer ${
                        isSelected
                          ? 'bg-slate-900 border-indigo-500 shadow-lg'
                          : 'bg-slate-900/50 border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="font-mono font-semibold text-indigo-400">{t.ticketKey}</span>
                        <div className="flex items-center gap-1.5">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                              t.priority === 'CRITICAL'
                                ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                                : 'bg-amber-500/20 text-amber-300'
                            }`}
                          >
                            {t.priority}
                          </span>
                          <span className="px-2 py-0.5 rounded text-[10px] bg-slate-800 text-slate-300">
                            {t.status}
                          </span>
                        </div>
                      </div>
                      <div className="text-sm font-medium text-slate-100 line-clamp-2">{t.subject}</div>
                      <div className="mt-2 flex items-center justify-between text-[11px] text-slate-400">
                        <span>{t.organisationName || 'Standard Customer'}</span>
                        <span>Agent: {t.assignedAgentUsername || 'Unassigned'}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Center Conversation & Right AI Copilot Panel */}
            {activeTicket && (
              <div className="lg:col-span-8 space-y-4">
                <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2 text-xs text-slate-400">
                        <span className="font-mono text-indigo-400 font-semibold">{activeTicket.ticketKey}</span>
                        <span>·</span>
                        <span>{activeTicket.organisationName || 'Customer'}</span>
                        <span>·</span>
                        <span>Category: {activeTicket.category}</span>
                        <span>·</span>
                        <span>Sentiment: {activeTicket.sentiment}</span>
                      </div>
                      <h3 className="text-base font-semibold mt-1">{activeTicket.subject}</h3>
                    </div>

                    {/* AI Copilot Action Toolbar */}
                    <div className="flex flex-wrap items-center gap-2">
                      <button
                        onClick={() => triageMutation.mutate(activeTicket.id)}
                        disabled={triageMutation.isPending}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/40 text-indigo-300 text-xs font-medium cursor-pointer"
                      >
                        <Bot className="w-3.5 h-3.5" /> AI Auto-Triage
                      </button>
                      <button
                        onClick={() => suggestReplyMutation.mutate(activeTicket.id)}
                        disabled={suggestReplyMutation.isPending}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600/20 hover:bg-emerald-600/30 border border-emerald-500/40 text-emerald-300 text-xs font-medium cursor-pointer"
                      >
                        <Sparkles className="w-3.5 h-3.5" /> Suggest RAG Reply
                      </button>
                      <button
                        onClick={() => summarizeMutation.mutate(activeTicket.id)}
                        disabled={summarizeMutation.isPending}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-600/20 hover:bg-amber-600/30 border border-amber-500/40 text-amber-300 text-xs font-medium cursor-pointer"
                      >
                        <Activity className="w-3.5 h-3.5" /> Summarise Thread
                      </button>
                      <button
                        onClick={() =>
                          proposeToolMutation.mutate({
                            ticketId: activeTicket.id,
                            toolName: 'ESCALATE_TO_TIER3_ENGINEERING',
                          })
                        }
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 border border-rose-500/40 text-rose-300 text-xs font-medium cursor-pointer"
                      >
                        <ShieldCheck className="w-3.5 h-3.5" /> Request AI Tool Approval
                      </button>
                    </div>
                  </div>

                  <p className="text-xs text-slate-300 bg-slate-950 p-3.5 rounded-lg border border-slate-800">
                    {activeTicket.description}
                  </p>

                  {activeTicket.aiSummary && (
                    <div className="p-3.5 rounded-lg bg-indigo-950/40 border border-indigo-500/30 text-xs space-y-1">
                      <div className="font-semibold text-indigo-300 flex items-center gap-1.5">
                        <Sparkles className="w-3.5 h-3.5" /> AI Executive Summary
                      </div>
                      <p className="text-slate-300">{activeTicket.aiSummary}</p>
                    </div>
                  )}

                  {latestTriage && latestTriage.ticketId === activeTicket.id && (
                    <div className="p-3.5 rounded-lg bg-slate-950 border border-indigo-500/40 text-xs space-y-1">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-semibold text-indigo-300">
                          AI Triage Result (Confidence: {(latestTriage.confidence * 100).toFixed(0)}%)
                        </span>
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-mono ${
                            latestTriage.fallbackUsed
                              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                              : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                          }`}
                        >
                          {latestTriage.fallbackUsed
                            ? `Mode: Local Deterministic Fallback (${latestTriage.modelUsed || 'supportflow-grounded-v2'})`
                            : `Provider: ${latestTriage.providerUsed} (${latestTriage.modelUsed})`}
                        </span>
                      </div>
                      <p className="text-slate-300">{latestTriage.rationale || latestTriage.reasoning}</p>
                    </div>
                  )}

                  {latestRagSuggestion && latestRagSuggestion.ticketId === activeTicket.id && (
                    <div className="p-3.5 rounded-lg bg-emerald-950/30 border border-emerald-500/30 text-xs space-y-2">
                      <div className="flex items-center justify-between gap-2">
                        <div className="font-semibold text-emerald-300 flex items-center gap-1.5">
                          {latestRagSuggestion.citations.length === 0 ? (
                            <>
                              <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
                              <span className="text-amber-300">
                                Insufficient KB Evidence — Citations Withheld (0 matches above threshold)
                              </span>
                            </>
                          ) : (
                            <span>Grounded Knowledge Base Citations ({latestRagSuggestion.citations.length})</span>
                          )}
                        </div>
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-mono ${
                            latestRagSuggestion.fallbackUsed
                              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                              : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                          }`}
                        >
                          {latestRagSuggestion.fallbackUsed
                            ? `Mode: Local Deterministic Fallback (${latestRagSuggestion.modelUsed || 'supportflow-grounded-v2'})`
                            : `Provider: ${latestRagSuggestion.providerUsed} (${latestRagSuggestion.modelUsed})`}
                        </span>
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                        {latestRagSuggestion.citations.map((c) => (
                          <div key={c.articleKey} className="p-2.5 rounded bg-slate-950 border border-slate-800">
                            <div className="font-mono text-emerald-400 font-semibold">
                              [{c.articleKey}] {c.title} (Score: {c.relevanceScore})
                            </div>
                            <p className="text-slate-400 mt-1 line-clamp-2">{c.excerpt || c.snippet}</p>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {/* Conversation Messages Thread */}
                <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Omnichannel Conversation Thread
                  </h4>
                  <div className="space-y-3 max-h-72 overflow-y-auto">
                    {messagesQuery.data?.map((m) => (
                      <div
                        key={m.id}
                        className={`p-3.5 rounded-lg border text-xs ${
                          m.internalNote
                            ? 'bg-amber-950/20 border-amber-500/30'
                            : m.senderType === 'CUSTOMER'
                            ? 'bg-slate-950 border-slate-800'
                            : 'bg-indigo-950/20 border-indigo-500/30'
                        }`}
                      >
                        <div className="flex items-center justify-between text-[11px] text-slate-400 mb-1">
                          <span className="font-semibold text-slate-200">
                            {m.senderName} ({m.senderType})
                            {m.internalNote && ' · [INTERNAL AGENT NOTE]'}
                            {m.aiGenerated && ' · [AI RAG ASSISTED]'}
                          </span>
                          <span>{new Date(m.createdAt).toLocaleTimeString()}</span>
                        </div>
                        <div className="whitespace-pre-wrap text-slate-200">{m.content}</div>
                      </div>
                    ))}
                  </div>

                  {/* Reply Composer */}
                  <div className="space-y-2 pt-2 border-t border-slate-800">
                    <textarea
                      rows={4}
                      value={replyContent}
                      onChange={(e) => setReplyContent(e.target.value)}
                      placeholder="Write a customer reply or click 'Suggest RAG Reply' above to populate with cited KB steps..."
                      className="w-full rounded-lg bg-slate-950 border border-slate-800 p-3 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                    />
                    <div className="flex items-center justify-between">
                      <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={internalNote}
                          onChange={(e) => setInternalNote(e.target.checked)}
                        />
                        Post as internal agent note
                      </label>
                      <button
                        onClick={() => sendMessageMutation.mutate(activeTicket.id)}
                        disabled={!replyContent.trim() || sendMessageMutation.isPending}
                        className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-medium text-white cursor-pointer"
                      >
                        <Send className="w-3.5 h-3.5" /> Send Message
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {activeTab === 'approvals' && (
          <ApprovalsGovernancePanel
            approvals={approvalsQuery.data || []}
            onDecide={(id, approved, decisionReason) =>
              decideApprovalMutation.mutate({ id, approved, decisionReason })
            }
          />
        )}

        {activeTab === 'knowledge' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div className="lg:col-span-7 space-y-4">
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <FileSearch className="w-4 h-4 text-indigo-400" /> RAG Vector & Hybrid Semantic Search
                </h2>
                <input
                  value={kbSearchQuery}
                  onChange={(e) => setKbSearchQuery(e.target.value)}
                  placeholder="Search knowledge base embeddings (e.g., SAML certificate, webhook 429, SLA refund)..."
                  className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3.5 py-2 text-xs text-slate-100"
                />

                <div className="space-y-2.5 pt-2">
                  {kbSearchResultsQuery.data?.map((res) => (
                    <div
                      key={res.articleKey}
                      className="p-4 rounded-lg bg-slate-950 border border-slate-800 space-y-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono font-semibold text-indigo-400">
                          [{res.articleKey}] {res.title}
                        </span>
                        <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono">
                          Similarity: {(res.relevanceScore * 100).toFixed(0)}%
                        </span>
                      </div>
                      <p className="text-slate-300">{res.snippet || res.citationSnippet}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="lg:col-span-5">
              <form
                onSubmit={articleForm.handleSubmit((v) => createArticleMutation.mutate(v))}
                className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-3 text-xs"
              >
                <h3 className="text-sm font-semibold">Index New Knowledge Base Article</h3>
                <input
                  placeholder="Article Title"
                  {...articleForm.register('title')}
                  className="w-full rounded bg-slate-950 border border-slate-800 px-3 py-2"
                />
                <div className="grid grid-cols-2 gap-2">
                  <select
                    {...articleForm.register('category')}
                    className="rounded bg-slate-950 border border-slate-800 px-3 py-2"
                  >
                    <option value="SECURITY">SECURITY</option>
                    <option value="INTEGRATION">INTEGRATION</option>
                    <option value="BILLING">BILLING</option>
                    <option value="TECHNICAL_ISSUE">TECHNICAL_ISSUE</option>
                  </select>
                  <input
                    placeholder="Comma-separated tags"
                    {...articleForm.register('tags')}
                    className="rounded bg-slate-950 border border-slate-800 px-3 py-2"
                  />
                </div>
                <textarea
                  rows={5}
                  placeholder="Technical resolution documentation to index for RAG citations..."
                  {...articleForm.register('content')}
                  className="w-full rounded bg-slate-950 border border-slate-800 px-3 py-2"
                />
                <button
                  type="submit"
                  className="w-full py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-medium cursor-pointer"
                >
                  Compute Embedding & Publish Article
                </button>
              </form>
            </div>
          </div>
        )}

        {activeTab === 'directory' && (
          <DirectoryPanel
            organisations={orgsQuery.data || []}
            customers={customersQuery.data || []}
          />
        )}

        {activeTab === 'audit' && <AuditTrailPanel auditLogs={auditLogsQuery.data || []} />}
      </main>
    </div>
  );
}
