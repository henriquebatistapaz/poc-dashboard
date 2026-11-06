<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDownToLine, ArrowLeft, ArrowRight, ArrowUpRight, BookOpen, Check, ChevronRight, FileText, Layers, Library, LogOut, Plus, RefreshCw, Search, ShieldCheck, X } from '@lucide/vue'
import LoginView from './components/LoginView.vue'
import CollectionPanel from './components/CollectionPanel.vue'
import LanguagePicker from './components/LanguagePicker.vue'
import ItemDetails from './components/ItemDetails.vue'
import { ApiError, errorMessage, request, waitForRun } from './lib/api'
import { displayDate, hostLabel, sourceLabel } from './lib/format'
import type { CollectionRun, CollectionSource, Filters, Item, ItemPage, LoginResponse } from './types'

const { t, n } = useI18n()
const token = ref('')
const email = ref('')
const loginBusy = ref(false)
const loginErrorKey = ref('')
const loginFailure = ref<unknown>(null)
const listBusy = ref(false)
const listFailure = ref<unknown>(null)
const collectionFailure = ref<unknown>(null)
const collectionBusy = ref(false)
const result = ref<CollectionRun | null>(null)
const items = ref<Item[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const filters = reactive<Filters>({ source: '', q: '', from: '', to: '' })
const applied = ref<Filters>({ ...filters })
const filterError = ref('')
const sources = ['rss', 'google-news', 'google-alerts', 'wordpress', 'reddit', 'alpha', 'beta']
const pages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const dialogOpen = ref(false)
const detail = ref<Item | null>(null)
const detailBusy = ref(false)
const detailFailure = ref<unknown>(null)
let expiryTimer: ReturnType<typeof setTimeout> | undefined
let listController: AbortController | undefined
let detailController: AbortController | undefined

function logout(expired = false) {
  clearTimeout(expiryTimer)
  listController?.abort(); detailController?.abort()
  token.value = ''; email.value = ''; items.value = []; total.value = 0; page.value = 0
  result.value = null; collectionBusy.value = false; listBusy.value = false; dialogOpen.value = false
  listFailure.value = null; collectionFailure.value = null; loginFailure.value = null; filterError.value = ''
  loginErrorKey.value = expired ? 'login.expired' : ''
  Object.assign(filters, { source: '', q: '', from: '', to: '' }); applied.value = { ...filters }
}
function isExpired(error: unknown) {
  if (error instanceof ApiError && error.status === 401) { logout(true); return true }
  return false
}
async function login(submittedEmail: string, password: string) {
  if (loginBusy.value) return
  loginBusy.value = true; loginFailure.value = null; loginErrorKey.value = ''
  try {
    const response = await request<LoginResponse>('/v1/auth/login', undefined, { email: submittedEmail, password })
    token.value = response.accessToken; email.value = submittedEmail
    expiryTimer = setTimeout(() => logout(true), response.expiresIn * 1000)
    await loadItems()
  } catch (error) {
    if (error instanceof ApiError && [401, 503].includes(error.status)) loginErrorKey.value = error.status === 401 ? 'login.invalid' : 'login.unconfigured'
    else loginFailure.value = error
  } finally { loginBusy.value = false }
}
async function loadItems() {
  if (!token.value) return
  listController?.abort()
  const controller = new AbortController(); listController = controller
  const session = token.value
  listBusy.value = true; listFailure.value = null
  const query = new URLSearchParams({ page: String(page.value), size: String(size.value) })
  for (const [key, value] of Object.entries(applied.value)) {
    if (value) query.set(key, key === 'from' || key === 'to' ? new Date(value).toISOString() : value.trim())
  }
  try {
    const response = await request<ItemPage>(`/v1/items?${query}`, session, undefined, controller.signal)
    if (controller.signal.aborted || token.value !== session) return
    items.value = response.items; total.value = response.total
  } catch (error) {
    if (controller.signal.aborted || token.value !== session) return
    if (!isExpired(error)) listFailure.value = error
  } finally { if (listController === controller) listBusy.value = false }
}
function applyFilters() {
  filterError.value = ''
  if (filters.from && filters.to && new Date(filters.from) > new Date(filters.to)) { filterError.value = 'validation.range'; return }
  applied.value = { ...filters }; page.value = 0; void loadItems()
}
function clearFilters() { Object.assign(filters, { source: '', q: '', from: '', to: '' }); applyFilters() }
function changePage(value: number) { page.value = value; void loadItems() }
async function collect(source: CollectionSource, input: string) {
  if (collectionBusy.value || !token.value) return
  const session = token.value
  collectionBusy.value = true; collectionFailure.value = null; result.value = null
  try {
    const started = await request<CollectionRun>('/v1/collections', session, { source, input })
    if (token.value !== session) return
    const response = await waitForRun(started.id, session)
    if (token.value !== session) return
    result.value = response
    Object.assign(filters, { source: source.toLowerCase().replaceAll('_', '-'), q: '', from: '', to: '' })
    applyFilters()
  } catch (error) {
    if (token.value !== session) return
    if (!isExpired(error)) collectionFailure.value = error
  } finally { if (token.value === session) collectionBusy.value = false }
}
function closeDetail() { detailController?.abort(); dialogOpen.value = false; detail.value = null }
async function openDetail(id: number) {
  detailController?.abort()
  const controller = new AbortController(); detailController = controller
  const session = token.value
  dialogOpen.value = true; detailBusy.value = true; detail.value = null; detailFailure.value = null
  try {
    const response = await request<Item>(`/v1/items/${id}`, session, undefined, controller.signal)
    if (!controller.signal.aborted && token.value === session) detail.value = response
  } catch (error) {
    if (!controller.signal.aborted && token.value === session && !isExpired(error)) detailFailure.value = error
  } finally { if (detailController === controller) detailBusy.value = false }
}
function scrollTo(id: string) { document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
onBeforeUnmount(() => { clearTimeout(expiryTimer); listController?.abort(); detailController?.abort() })
</script>

<template>
  <LoginView v-if="!token" :busy="loginBusy" :error="loginErrorKey ? t(loginErrorKey) : loginFailure ? errorMessage(loginFailure) : ''" @login="login" />
  <div v-else class="workspace-shell">
    <aside class="sidebar">
      <a class="brand" href="#library"><span class="brand-icon"><Layers :size="21" /></span>coleta<span class="brand-dot">.</span></a>
      <div class="workspace-switch"><span class="workspace-avatar">{{ email.charAt(0).toUpperCase() }}</span><div><strong>Dashboard Mini</strong><small><span class="live-dot"></span>{{ t('common.private') }}</small></div></div>
      <span class="nav-label">{{ t('dashboard.workspace') }}</span>
      <nav :aria-label="t('common.workspace')"><a class="nav-item active" href="#library"><Library :size="18" />{{ t('dashboard.contents') }}<span class="nav-count">{{ n(total) }}</span></a><a class="nav-item" href="#collect"><Plus :size="18" />{{ t('dashboard.newCollection') }}</a></nav>
      <div class="sidebar-bottom"><a class="nav-item" href="/swagger-ui.html" target="_blank" rel="noopener noreferrer"><BookOpen :size="17" />{{ t('dashboard.apiDocs') }}<ArrowUpRight :size="13" /></a><div class="private-note"><ShieldCheck :size="15" /><span>{{ t('dashboard.local') }}</span></div></div>
    </aside>
    <div class="workspace-main">
      <header class="topbar"><div class="breadcrumb"><span>{{ t('common.workspace') }}</span><ChevronRight :size="13" /><strong>{{ t('dashboard.contents') }}</strong></div><div class="topbar-right"><LanguagePicker /><span class="user-email">{{ email }}</span><button class="icon-button" :aria-label="t('dashboard.logout')" :title="t('dashboard.logout')" @click="logout()"><LogOut :size="18" /></button></div></header>
      <main class="page-content">
        <div class="page-heading"><div><div class="section-kicker">{{ t('common.private') }}</div><h1>{{ t('dashboard.title') }}</h1><p class="muted">{{ t('dashboard.subtitle') }}</p></div><button class="button secondary" @click="scrollTo('collect')"><Plus :size="17" />{{ t('dashboard.newCollection') }}</button></div>
        <div class="stats-grid"><div class="stat-card"><span class="stat-icon blue"><FileText :size="19" /></span><span class="stat-label">{{ t('dashboard.items') }}</span><strong>{{ n(total) }}</strong><small>{{ t('dashboard.filtered') }}</small></div><div class="stat-card"><span class="stat-icon green"><ArrowDownToLine :size="19" /></span><span class="stat-label">{{ t('dashboard.newItems') }}</span><strong>{{ result ? n(result.inserted ?? 0) : '—' }}</strong><small>{{ t(result ? 'dashboard.lastCollection' : 'dashboard.noCollection') }}</small></div><div class="stat-card"><span class="stat-icon violet"><Check :size="19" /></span><span class="stat-label">{{ t('dashboard.duplicates') }}</span><strong>{{ result ? n(result.duplicates ?? 0) : '—' }}</strong><small>{{ t(result ? 'dashboard.lastCollection' : 'dashboard.noCollection') }}</small></div></div>
        <CollectionPanel :busy="collectionBusy" :result="result" :error="collectionFailure ? errorMessage(collectionFailure) : ''" @collect="collect" @view="scrollTo('library')" />
        <section id="library" class="library-card" aria-labelledby="library-title">
          <div class="library-heading"><div><h2 id="library-title">{{ t('items.title') }}<span class="pill neutral">{{ t('items.count', { count: n(total) }) }}</span></h2><p class="muted">{{ t('items.subtitle') }}</p></div><button class="button quiet" :disabled="listBusy" @click="loadItems"><RefreshCw :size="15" :class="{ spinning: listBusy }" />{{ t('dashboard.refresh') }}</button></div>
          <form class="filters" @submit.prevent="applyFilters"><div class="field search-field"><label for="filter-query">{{ t('filters.search') }}</label><div class="input-with-icon"><Search :size="16" /><input id="filter-query" v-model="filters.q" class="with-icon" :placeholder="t('filters.searchPlaceholder')" maxlength="500" /></div></div><div class="field"><label for="filter-source">{{ t('filters.source') }}</label><select id="filter-source" v-model="filters.source"><option value="">{{ t('filters.allSources') }}</option><option v-for="source in sources" :key="source" :value="source">{{ sourceLabel(source) }}</option></select></div><div class="field date-field"><label for="filter-from">{{ t('filters.from') }}</label><input id="filter-from" v-model="filters.from" type="datetime-local" /></div><div class="field date-field"><label for="filter-to">{{ t('filters.to') }}</label><input id="filter-to" v-model="filters.to" type="datetime-local" /></div><button class="button secondary filter-submit" type="submit" :disabled="listBusy">{{ t('filters.apply') }}</button><button class="icon-button clear-filters" type="button" :title="t('filters.clear')" :aria-label="t('filters.clear')" @click="clearFilters"><X :size="16" /></button><small class="timezone-note">{{ t('filters.timezone') }}</small></form>
          <div v-if="filterError" class="notice error library-notice" role="alert">{{ t(filterError) }}</div>
          <div v-if="listFailure" class="notice error library-notice" role="alert">{{ errorMessage(listFailure) }}<button class="text-button" @click="loadItems">{{ t('common.retry') }}</button></div>
          <div v-if="listBusy" class="list-loading" role="status"><span class="spinner dark"></span>{{ t('common.loading') }}</div>
          <div v-else-if="items.length" class="table-scroll"><table><thead><tr><th>{{ t('items.content') }}</th><th>{{ t('items.source') }}</th><th>{{ t('items.collected') }}</th><th class="actions-column"><span class="sr-only">{{ t('items.actions') }}</span></th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><button class="item-title" @click="openDetail(item.id)">{{ item.title || t('items.untitled') }}</button><div class="item-host">{{ hostLabel(item.link) }}</div></td><td><span class="pill source-pill">{{ sourceLabel(item.source) }}</span></td><td class="date-cell">{{ displayDate(item.collectedAt) }}</td><td><button class="icon-button" :aria-label="t('items.details')" @click="openDetail(item.id)"><ChevronRight :size="18" /></button></td></tr></tbody></table></div>
          <div v-else-if="!listFailure" class="empty-state"><span class="empty-icon"><Library :size="28" /></span><h3>{{ t('items.emptyTitle') }}</h3><p>{{ t('items.emptyText') }}</p><button class="text-button" @click="scrollTo('collect')">{{ t('dashboard.newCollection') }}<ArrowRight :size="16" /></button></div>
          <footer class="table-footer"><div class="page-size"><label for="page-size">{{ t('items.pageSize') }}</label><select id="page-size" v-model.number="size" :disabled="listBusy" @change="changePage(0)"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></div><div class="pagination"><span>{{ t('items.page', { page: n(page + 1), pages: n(pages) }) }}</span><button class="icon-button" :aria-label="t('items.previous')" :disabled="page === 0 || listBusy" @click="changePage(page - 1)"><ArrowLeft :size="16" /></button><button class="icon-button" :aria-label="t('items.next')" :disabled="page + 1 >= pages || listBusy" @click="changePage(page + 1)"><ArrowRight :size="16" /></button></div></footer>
        </section>
        <footer class="page-footer"><span>coleta.</span>{{ t('dashboard.footer') }}<span class="private-footer"><ShieldCheck :size="13" />{{ t('common.private') }}</span></footer>
      </main>
    </div>
    <ItemDetails v-if="dialogOpen" :item="detail" :busy="detailBusy" :error="detailFailure ? errorMessage(detailFailure) : ''" @close="closeDetail" />
  </div>
</template>
