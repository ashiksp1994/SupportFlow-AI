import { z } from 'zod';

export const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export interface UserResponse {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string;
  role: 'ADMIN' | 'AGENT' | 'CUSTOMER' | string;
  organisationId?: number | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
  user: UserResponse;
}

export interface Ticket {
  id: number;
  ticketKey: string;
  subject: string;
  description: string;
  status: string;
  priority: string;
  category: string;
  sentiment: string;
  customerId?: number;
  customerName?: string;
  customerEmail?: string;
  organisationId?: number;
  organisationName?: string;
  assignedAgentUsername?: string;
  slaDueAt: string;
  slaBreached: boolean;
  escalated: boolean;
  aiTriaged: boolean;
  aiConfidence?: number;
  aiSummary?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: number;
  ticketId: number;
  senderType: string;
  senderName: string;
  content: string;
  internalNote: boolean;
  aiGenerated: boolean;
  citationsJson?: string;
  createdAt: string;
}

export interface KnowledgeArticle {
  id: number;
  articleKey: string;
  title: string;
  category: string;
  tags: string;
  content: string;
  organisationId?: number | null;
  createdBy: string;
  createdAt: string;
}

export interface KnowledgeSearchResult {
  id: number;
  articleKey: string;
  title: string;
  category: string;
  tags: string;
  relevanceScore: number;
  snippet: string;
  citationSnippet?: string;
  embeddingProvider?: string;
  fallbackUsed?: boolean;
}

export interface RagCitation {
  articleKey: string;
  title: string;
  category: string;
  relevanceScore: number;
  excerpt: string;
  snippet?: string;
}

export interface RagReplySuggestion {
  ticketId: number;
  ticketKey: string;
  suggestedReply: string;
  confidence: number;
  citations: RagCitation[];
  humanReviewRequired: boolean;
  providerUsed?: string;
  modelUsed?: string;
  fallbackUsed?: boolean;
}

export interface AiTriageResult {
  ticketId: number;
  ticketKey: string;
  predictedCategory: string;
  predictedPriority: string;
  predictedSentiment: string;
  confidence: number;
  rationale: string;
  reasoning?: string;
  recommendedQueue: string;
  autoEscalationRecommended: boolean;
  providerUsed?: string;
  modelUsed?: string;
  fallbackUsed?: boolean;
}

export interface ConversationSummaryResult {
  ticketId: number;
  ticketKey: string;
  executiveSummary: string;
  keyPoints: string[];
  customerSentiment?: string;
  recommendedNextStep: string;
  messageCount: number;
  providerUsed?: string;
  modelUsed?: string;
  fallbackUsed?: boolean;
}

export interface AiApprovalRequest {
  id: number;
  ticketId: number;
  ticketKey: string;
  toolName: string;
  actionSummary: string;
  parametersJson: string;
  aiReasoning: string;
  confidence: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedBy: string;
  decidedBy?: string;
  decisionReason?: string;
  executionResult?: string;
  createdAt: string;
  decidedAt?: string;
}

export interface DashboardAnalytics {
  totalTickets: number;
  openTickets: number;
  inProgressTickets: number;
  escalatedTickets: number;
  resolvedTickets: number;
  slaBreachedTickets: number;
  slaComplianceRatePercent: number;
  aiTriagedTickets: number;
  pendingAiApprovals: number;
  totalKnowledgeArticles: number;
  totalOrganisations: number;
  totalCustomers: number;
  totalAuditLogs: number;
  ticketsByPriority: Record<string, number>;
  agentWorkloads: Array<{
    agentUsername: string;
    openTickets: number;
    highOrCriticalTickets: number;
    escalatedTickets: number;
  }>;
}

export interface Organisation {
  id: number;
  name: string;
  domain: string;
  slaTier: string;
  responseSlaMinutes: number;
  resolutionSlaHours: number;
}

export interface CustomerProfile {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  jobTitle?: string;
  organisationId?: number;
  organisationName?: string;
  vip: boolean;
}

export interface AuditLog {
  id: number;
  eventType: string;
  actorUsername: string;
  targetType?: string;
  targetId?: string;
  details?: string;
  createdAt: string;
}

export const createTicketSchema = z.object({
  subject: z.string().min(5, 'Subject must be at least 5 characters'),
  description: z.string().min(10, 'Description must be at least 10 characters'),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  category: z.enum(['SECURITY', 'BILLING', 'INTEGRATION', 'TECHNICAL_ISSUE', 'FEATURE_REQUEST', 'GENERAL']),
  assignedAgentUsername: z.string().optional(),
});

export type CreateTicketFormValues = z.infer<typeof createTicketSchema>;

export const createArticleSchema = z.object({
  title: z.string().min(5, 'Title must be at least 5 characters'),
  category: z.enum(['SECURITY', 'BILLING', 'INTEGRATION', 'TECHNICAL_ISSUE', 'GENERAL']),
  tags: z.string().min(2, 'Provide comma-separated tags'),
  content: z.string().min(20, 'Article content must be at least 20 characters'),
});

export type CreateArticleFormValues = z.infer<typeof createArticleSchema>;

export async function apiRequest<T>(path: string, token: string | null, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });
  if (!res.ok) {
    const errText = await res.text();
    throw new Error(errText || `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export async function logoutSession(accessToken: string | null, refreshToken: string | null): Promise<void> {
  try {
    await apiRequest<{ status: string }>('/api/auth/logout', accessToken, {
      method: 'POST',
      body: JSON.stringify({ refreshToken: refreshToken || '' }),
    });
  } catch {
    // Always clear client session state even if network call fails
  }
}
