export type CollectionSource = 'RSS' | 'GOOGLE_NEWS' | 'WORDPRESS' | 'ALPHA' | 'BETA' | 'MASTODON' | 'LEMMY' | 'DEV_TO' | 'HACKER_NEWS' | 'STACK_EXCHANGE' | 'GITHUB' | 'TABNEWS' | 'WIKIPEDIA' | 'GITLAB' | 'LOBSTERS'
export interface Item {
  id: number
  source: string
  externalId: string
  title: string | null
  link: string
  publishedAt: string | null
  collectedAt: string
}
export interface ItemPage { items: Item[]; total: number; page: number; size: number }
export interface LoginResponse { accessToken: string; tokenType: string; expiresIn: number }
export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export interface CollectionRun {
  id: number; source: CollectionSource; status: RunStatus
  createdAt?: string; startedAt?: string | null; finishedAt?: string | null
  input?: string | null
  fetched: number | null; inserted: number | null; duplicates: number | null; errorMessage: string | null
}
export interface Filters { source: string; q: string; from: string; to: string }
