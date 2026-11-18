<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowDownToLine,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Check,
  ChevronRight,
  Clock3,
  FileText,
  Layers,
  Library,
  LogOut,
  Menu,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  X,
} from '@lucide/vue'
import LoginView from './components/LoginView.vue'
import CollectionPanel from './components/CollectionPanel.vue'
import LanguagePicker from './components/LanguagePicker.vue'
import ThemePicker from './components/ThemePicker.vue'
import ItemDetails from './components/ItemDetails.vue'
import CollectionHistory from './components/CollectionHistory.vue'
import { ApiError, errorMessage, request, waitForRun } from './lib/api'
import { trapFocusOnTab } from './lib/focus'
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
const collectionRuns = ref<CollectionRun[]>([])
const historyBusy = ref(false)
const historyFailure = ref<unknown>(null)
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
const mobileMenuOpen = ref(false)
const menuToggleButton = ref<HTMLButtonElement>()
const mobileDrawer = ref<HTMLElement>()
const mobileCloseButton = ref<HTMLButtonElement>()

let lastDetailId: number | null = null
let lastDetailTrigger: HTMLElement | null = null
let expiryTimer: ReturnType<typeof setTimeout> | undefined
let listController: AbortController | undefined
let detailController: AbortController | undefined
let historyController: AbortController | undefined
let runController: AbortController | undefined

function logout(expired = false) {
  clearTimeout(expiryTimer)
  listController?.abort()
  detailController?.abort()
  historyController?.abort()
  runController?.abort()
  runController = undefined
  token.value = ''
  email.value = ''
  items.value = []
  total.value = 0
  page.value = 0
  result.value = null
  collectionRuns.value = []
  collectionBusy.value = false
  listBusy.value = false
  historyBusy.value = false
  dialogOpen.value = false
  mobileMenuOpen.value = false
  listFailure.value = null
  historyFailure.value = null
  collectionFailure.value = null
  loginFailure.value = null
  filterError.value = ''
  loginErrorKey.value = expired ? 'login.expired' : ''
  Object.assign(filters, { source: '', q: '', from: '', to: '' })
  applied.value = { ...filters }
}

function isExpired(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    logout(true)
    return true
  }
  return false
}

async function login(submittedEmail: string, password: string) {
  if (loginBusy.value) return
  loginBusy.value = true
  loginFailure.value = null
  loginErrorKey.value = ''
  try {
    const response = await request<LoginResponse>('/v1/auth/login', undefined, { email: submittedEmail, password })
    token.value = response.accessToken
    email.value = submittedEmail
    expiryTimer = setTimeout(() => logout(true), response.expiresIn * 1000)
    await Promise.all([loadItems(), loadHistory()])
  } catch (error) {
    if (error instanceof ApiError && [401, 503].includes(error.status)) {
      loginErrorKey.value = error.status === 401 ? 'login.invalid' : 'login.unconfigured'
    } else {
      loginFailure.value = error
    }
  } finally {
    loginBusy.value = false
  }
}

async function loadItems() {
  if (!token.value) return
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const session = token.value
  listBusy.value = true
  listFailure.value = null
  const query = new URLSearchParams({ page: String(page.value), size: String(size.value) })
  for (const [key, value] of Object.entries(applied.value)) {
    if (value) query.set(key, key === 'from' || key === 'to' ? new Date(value).toISOString() : value.trim())
  }
  try {
    const response = await request<ItemPage>(`/v1/items?${query}`, session, undefined, controller.signal)
    if (controller.signal.aborted || token.value !== session) return
    items.value = response.items
    total.value = response.total
  } catch (error) {
    if (controller.signal.aborted || token.value !== session) return
    if (!isExpired(error)) listFailure.value = error
  } finally {
    if (listController === controller) listBusy.value = false
  }
}

async function loadHistory() {
  if (!token.value) return
  historyController?.abort()
  const controller = new AbortController()
  historyController = controller
  const session = token.value
  historyBusy.value = true
  historyFailure.value = null
  try {
    const response = await request<CollectionRun[]>('/v1/collections?page=0&size=10', session, undefined, controller.signal)
    if (!controller.signal.aborted && token.value === session) collectionRuns.value = response
  } catch (error) {
    if (!controller.signal.aborted && token.value === session && !isExpired(error)) historyFailure.value = error
  } finally {
    if (historyController === controller) historyBusy.value = false
  }
}

function applyFilters() {
  filterError.value = ''
  if (filters.from && filters.to && new Date(filters.from) > new Date(filters.to)) {
    filterError.value = 'validation.range'
    return
  }
  applied.value = { ...filters }
  page.value = 0
  void loadItems()
}

function clearFilters() {
  Object.assign(filters, { source: '', q: '', from: '', to: '' })
  applyFilters()
}

function changePage(value: number) {
  page.value = value
  void loadItems()
}

async function collect(source: CollectionSource, input: string) {
  if (collectionBusy.value || !token.value) return
  const session = token.value
  collectionBusy.value = true
  collectionFailure.value = null
  result.value = null
  try {
    runController?.abort()
    const controller = new AbortController()
    runController = controller
    const started = await request<CollectionRun>('/v1/collections', session, { source, input }, controller.signal)
    if (token.value !== session) return
    void loadHistory()
    const response = await waitForRun(started.id, session, controller.signal)
    if (token.value !== session) return
    result.value = response
    void loadHistory()
    Object.assign(filters, { source: source.toLowerCase().replaceAll('_', '-'), q: '', from: '', to: '' })
    applyFilters()
  } catch (error) {
    if (runController?.signal.aborted || token.value !== session) return
    if (!isExpired(error)) collectionFailure.value = error
    void loadHistory()
  } finally {
    if (token.value === session) {
      collectionBusy.value = false
      runController = undefined
    }
  }
}

async function resumeRun(run: CollectionRun) {
  if (collectionBusy.value || !token.value || !['PENDING', 'RUNNING'].includes(run.status)) return
  const session = token.value
  const controller = new AbortController()
  runController = controller
  collectionBusy.value = true
  collectionFailure.value = null
  result.value = null
  try {
    const completed = await waitForRun(run.id, session, controller.signal)
    if (token.value !== session) return
    result.value = completed
    Object.assign(filters, { source: completed.source.toLowerCase().replaceAll('_', '-'), q: '', from: '', to: '' })
    applyFilters()
  } catch (error) {
    if (controller.signal.aborted || token.value !== session) return
    if (!isExpired(error)) collectionFailure.value = error
  } finally {
    if (token.value === session) {
      collectionBusy.value = false
      runController = undefined
      void loadHistory()
    }
  }
}

function closeDetail() {
  detailController?.abort()
  dialogOpen.value = false
  detail.value = null
  const trigger = lastDetailTrigger
  lastDetailTrigger = null
  void nextTick(() => trigger?.focus?.())
}

async function openDetail(id: number, event?: Event) {
  if (!dialogOpen.value) {
    lastDetailTrigger =
      event?.currentTarget instanceof HTMLElement
        ? event.currentTarget
        : document.activeElement instanceof HTMLElement
          ? document.activeElement
          : null
  }
  lastDetailId = id
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const session = token.value
  dialogOpen.value = true
  detailBusy.value = true
  detail.value = null
  detailFailure.value = null
  try {
    const response = await request<Item>(`/v1/items/${id}`, session, undefined, controller.signal)
    if (!controller.signal.aborted && token.value === session) detail.value = response
  } catch (error) {
    if (!controller.signal.aborted && token.value === session && !isExpired(error)) detailFailure.value = error
  } finally {
    if (detailController === controller) detailBusy.value = false
  }
}

function retryDetail() {
  if (lastDetailId !== null) void openDetail(lastDetailId)
}

function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

async function openMobileMenu() {
  mobileMenuOpen.value = true
  await nextTick()
  mobileCloseButton.value?.focus()
}

async function closeMobileMenu(restoreFocus = true) {
  mobileMenuOpen.value = false
  if (restoreFocus) {
    await nextTick()
    menuToggleButton.value?.focus()
  }
}

async function navigateFromDrawer(id: string) {
  await closeMobileMenu(false)
  scrollTo(id)
}

function onDrawerKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    void closeMobileMenu(true)
    return
  }
  trapFocusOnTab(mobileDrawer.value, event)
}

onBeforeUnmount(() => {
  clearTimeout(expiryTimer)
  listController?.abort()
  detailController?.abort()
  historyController?.abort()
  runController?.abort()
})
</script>

<template>
  <LoginView
    v-if="!token"
    :busy="loginBusy"
    :error="loginErrorKey ? t(loginErrorKey) : loginFailure ? errorMessage(loginFailure) : ''"
    @login="login"
  />
  <div v-else class="workspace-shell">
    <aside class="sidebar">
      <a class="brand" href="#library">
        <span class="brand-icon"><Layers :size="21" aria-hidden="true" /></span>coleta<span class="brand-dot">.</span>
      </a>
      <div class="workspace-switch">
        <span class="workspace-avatar">{{ email.charAt(0).toUpperCase() }}</span>
        <div>
          <strong>POC Dashboard</strong>
          <small><span class="live-dot" aria-hidden="true"></span>{{ t('common.private') }}</small>
        </div>
      </div>
      <span class="nav-label">{{ t('dashboard.workspace') }}</span>
      <nav :aria-label="t('common.workspace')">
        <a class="nav-item active" href="#library">
          <Library :size="18" aria-hidden="true" />{{ t('dashboard.contents') }}<span class="nav-count">{{ n(total) }}</span>
        </a>
        <a class="nav-item" href="#collect"><Plus :size="18" aria-hidden="true" />{{ t('dashboard.newCollection') }}</a>
        <a class="nav-item" href="#activity"><Clock3 :size="18" aria-hidden="true" />{{ t('history.title') }}</a>
      </nav>
      <div class="sidebar-bottom">
        <a class="nav-item" href="/swagger-ui.html" target="_blank" rel="noopener noreferrer">
          <BookOpen :size="17" aria-hidden="true" />{{ t('dashboard.apiDocs') }}<ArrowUpRight :size="13" aria-hidden="true" />
        </a>
        <div class="private-note"><ShieldCheck :size="15" aria-hidden="true" /><span>{{ t('dashboard.local') }}</span></div>
      </div>
    </aside>

    <div class="workspace-main">
      <header class="topbar">
        <div class="topbar-left">
          <button
            ref="menuToggleButton"
            type="button"
            class="icon-button mobile-menu-toggle"
            data-testid="mobile-menu-toggle"
            :aria-label="t('nav.openMenu')"
            :aria-expanded="mobileMenuOpen"
            aria-controls="mobile-nav-drawer"
            @click="openMobileMenu"
          >
            <Menu :size="20" aria-hidden="true" />
          </button>
          <div class="breadcrumb">
            <span>{{ t('common.workspace') }}</span>
            <ChevronRight :size="13" aria-hidden="true" />
            <strong>{{ t('dashboard.contents') }}</strong>
          </div>
        </div>
        <div class="topbar-right">
          <ThemePicker />
          <LanguagePicker />
          <span class="user-email">{{ email }}</span>
          <button
            class="icon-button"
            type="button"
            :aria-label="t('dashboard.logout')"
            :title="t('dashboard.logout')"
            @click="logout()"
          >
            <LogOut :size="18" aria-hidden="true" />
          </button>
        </div>
      </header>

      <div
        v-if="mobileMenuOpen"
        class="mobile-drawer-backdrop"
        @click.self="closeMobileMenu(true)"
      >
        <div
          id="mobile-nav-drawer"
          ref="mobileDrawer"
          class="mobile-drawer"
          role="dialog"
          aria-modal="true"
          aria-labelledby="mobile-nav-title"
          @keydown="onDrawerKeydown"
        >
          <div class="mobile-drawer-header">
            <a class="brand" href="#library" @click.prevent="navigateFromDrawer('library')">
              <span class="brand-icon"><Layers :size="20" aria-hidden="true" /></span>coleta<span class="brand-dot">.</span>
            </a>
            <button
              ref="mobileCloseButton"
              type="button"
              class="icon-button"
              :aria-label="t('nav.closeMenu')"
              @click="closeMobileMenu(true)"
            >
              <X :size="20" aria-hidden="true" />
            </button>
          </div>
          <h2 id="mobile-nav-title" class="nav-label">{{ t('nav.menuTitle') }}</h2>
          <div class="workspace-switch">
            <span class="workspace-avatar">{{ email.charAt(0).toUpperCase() }}</span>
            <div>
              <strong>POC Dashboard</strong>
              <small><span class="live-dot" aria-hidden="true"></span>{{ email }}</small>
            </div>
          </div>
          <nav :aria-label="t('nav.menuTitle')">
            <a class="nav-item active" href="#library" @click.prevent="navigateFromDrawer('library')">
              <Library :size="18" aria-hidden="true" />{{ t('dashboard.contents') }}<span class="nav-count">{{ n(total) }}</span>
            </a>
            <a class="nav-item" href="#collect" @click.prevent="navigateFromDrawer('collect')">
              <Plus :size="18" aria-hidden="true" />{{ t('dashboard.newCollection') }}
            </a>
            <a class="nav-item" href="#activity" @click.prevent="navigateFromDrawer('activity')">
              <Clock3 :size="18" aria-hidden="true" />{{ t('history.title') }}
            </a>
            <a
              class="nav-item"
              href="/swagger-ui.html"
              target="_blank"
              rel="noopener noreferrer"
              @click="closeMobileMenu(false)"
            >
              <BookOpen :size="17" aria-hidden="true" />{{ t('dashboard.apiDocs') }}<ArrowUpRight :size="13" aria-hidden="true" />
            </a>
          </nav>
          <div class="private-note"><ShieldCheck :size="15" aria-hidden="true" /><span>{{ t('dashboard.local') }}</span></div>
        </div>
      </div>

      <main class="page-content">
        <div class="page-heading">
          <div>
            <div class="section-kicker">{{ t('common.private') }}</div>
            <h1>{{ t('dashboard.title') }}</h1>
            <p class="muted">{{ t('dashboard.subtitle') }}</p>
          </div>
          <button
            class="button secondary"
            type="button"
            :aria-label="t('dashboard.newCollection')"
            @click="scrollTo('collect')"
          >
            <Plus :size="17" aria-hidden="true" /><span>{{ t('dashboard.newCollection') }}</span>
          </button>
        </div>

        <div class="stats-grid">
          <div class="stat-card">
            <span class="stat-icon blue"><FileText :size="19" aria-hidden="true" /></span>
            <span class="stat-label">{{ t('dashboard.items') }}</span>
            <strong>{{ n(total) }}</strong>
            <small>{{ t('dashboard.filtered') }}</small>
          </div>
          <div class="stat-card">
            <span class="stat-icon green"><ArrowDownToLine :size="19" aria-hidden="true" /></span>
            <span class="stat-label">{{ t('dashboard.newItems') }}</span>
            <strong>{{ result ? n(result.inserted ?? 0) : '—' }}</strong>
            <small>{{ t(result ? 'dashboard.lastCollection' : 'dashboard.noCollection') }}</small>
          </div>
          <div class="stat-card">
            <span class="stat-icon violet"><Check :size="19" aria-hidden="true" /></span>
            <span class="stat-label">{{ t('dashboard.duplicates') }}</span>
            <strong>{{ result ? n(result.duplicates ?? 0) : '—' }}</strong>
            <small>{{ t(result ? 'dashboard.lastCollection' : 'dashboard.noCollection') }}</small>
          </div>
        </div>

        <CollectionPanel
          :busy="collectionBusy"
          :result="result"
          :error="collectionFailure ? errorMessage(collectionFailure) : ''"
          @collect="collect"
          @view="scrollTo('library')"
        />

        <CollectionHistory
          id="activity"
          :runs="collectionRuns"
          :busy="historyBusy"
          :error="historyFailure ? errorMessage(historyFailure) : ''"
          :tracking="collectionBusy"
          @refresh="loadHistory"
          @resume="resumeRun"
        />

        <section id="library" class="library-card" aria-labelledby="library-title">
          <div class="library-heading">
            <div>
              <h2 id="library-title">
                {{ t('items.title') }}<span class="pill neutral">{{ t('items.count', { count: n(total) }) }}</span>
              </h2>
              <p class="muted">{{ t('items.subtitle') }}</p>
            </div>
            <button
              class="button quiet"
              type="button"
              :disabled="listBusy"
              :aria-label="t('dashboard.refresh')"
              @click="loadItems"
            >
              <RefreshCw :size="15" :class="{ spinning: listBusy }" aria-hidden="true" />
              <span>{{ t('dashboard.refresh') }}</span>
            </button>
          </div>

          <form class="filters" @submit.prevent="applyFilters">
            <div class="field search-field">
              <label for="filter-query">{{ t('filters.search') }}</label>
              <div class="input-with-icon">
                <Search :size="16" aria-hidden="true" />
                <input
                  id="filter-query"
                  v-model="filters.q"
                  class="with-icon"
                  :placeholder="t('filters.searchPlaceholder')"
                  maxlength="500"
                />
              </div>
            </div>
            <div class="field">
              <label for="filter-source">{{ t('filters.source') }}</label>
              <select id="filter-source" v-model="filters.source">
                <option value="">{{ t('filters.allSources') }}</option>
                <option v-for="source in sources" :key="source" :value="source">{{ sourceLabel(source) }}</option>
              </select>
            </div>
            <div class="field date-field">
              <label for="filter-from">{{ t('filters.from') }}</label>
              <input
                id="filter-from"
                v-model="filters.from"
                type="datetime-local"
                :aria-invalid="Boolean(filterError)"
                :aria-describedby="filterError ? 'filter-error' : undefined"
              />
            </div>
            <div class="field date-field">
              <label for="filter-to">{{ t('filters.to') }}</label>
              <input
                id="filter-to"
                v-model="filters.to"
                type="datetime-local"
                :aria-invalid="Boolean(filterError)"
                :aria-describedby="filterError ? 'filter-error' : undefined"
              />
            </div>
            <button class="button secondary filter-submit" type="submit" :disabled="listBusy">{{ t('filters.apply') }}</button>
            <button
              class="icon-button clear-filters"
              type="button"
              :title="t('filters.clear')"
              :aria-label="t('filters.clear')"
              @click="clearFilters"
            >
              <X :size="16" aria-hidden="true" />
            </button>
            <small class="timezone-note">{{ t('filters.timezone') }}</small>
          </form>

          <div v-if="filterError" id="filter-error" class="notice error library-notice" role="alert">{{ t(filterError) }}</div>
          <div v-if="listFailure" class="notice error library-notice" role="alert">
            <span>{{ errorMessage(listFailure) }}</span>
            <button class="text-button" type="button" @click="loadItems">{{ t('common.retry') }}</button>
          </div>
          <div v-if="listBusy" class="list-loading" role="status">
            <span class="spinner dark" aria-hidden="true"></span>{{ t('common.loading') }}
          </div>
          <div
            v-else-if="items.length"
            class="table-scroll"
            role="region"
            :aria-label="t('items.tableRegion')"
            tabindex="0"
          >
            <table>
              <thead>
                <tr>
                  <th scope="col">{{ t('items.content') }}</th>
                  <th scope="col">{{ t('items.source') }}</th>
                  <th scope="col">{{ t('items.collected') }}</th>
                  <th scope="col" class="actions-column"><span class="sr-only">{{ t('items.actions') }}</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.id">
                  <td>
                    <button class="item-title" type="button" @click="openDetail(item.id, $event)">
                      {{ item.title || t('items.untitled') }}
                    </button>
                    <div class="item-host">{{ hostLabel(item.link) }}</div>
                  </td>
                  <td><span class="pill source-pill">{{ sourceLabel(item.source) }}</span></td>
                  <td class="date-cell">{{ displayDate(item.collectedAt) }}</td>
                  <td>
                    <button
                      class="icon-button"
                      type="button"
                      :aria-label="t('items.details')"
                      @click="openDetail(item.id, $event)"
                    >
                      <ChevronRight :size="18" aria-hidden="true" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else-if="!listFailure" class="empty-state" role="status">
            <span class="empty-icon"><Library :size="28" aria-hidden="true" /></span>
            <h3>{{ t('items.emptyTitle') }}</h3>
            <p>{{ t('items.emptyText') }}</p>
            <button class="text-button" type="button" @click="scrollTo('collect')">
              {{ t('dashboard.newCollection') }}<ArrowRight :size="16" aria-hidden="true" />
            </button>
          </div>

          <footer class="table-footer">
            <div class="page-size">
              <label for="page-size">{{ t('items.pageSize') }}</label>
              <select id="page-size" v-model.number="size" :disabled="listBusy" @change="changePage(0)">
                <option :value="10">10</option>
                <option :value="20">20</option>
                <option :value="50">50</option>
                <option :value="100">100</option>
              </select>
            </div>
            <div class="pagination">
              <span>{{ t('items.page', { page: n(page + 1), pages: n(pages) }) }}</span>
              <button
                class="icon-button"
                type="button"
                :aria-label="t('items.previous')"
                :disabled="page === 0 || listBusy"
                @click="changePage(page - 1)"
              >
                <ArrowLeft :size="16" aria-hidden="true" />
              </button>
              <button
                class="icon-button"
                type="button"
                :aria-label="t('items.next')"
                :disabled="page + 1 >= pages || listBusy"
                @click="changePage(page + 1)"
              >
                <ArrowRight :size="16" aria-hidden="true" />
              </button>
            </div>
          </footer>
        </section>

        <footer class="page-footer">
          <span>coleta.</span>{{ t('dashboard.footer') }}
          <span class="private-footer"><ShieldCheck :size="13" aria-hidden="true" />{{ t('common.private') }}</span>
        </footer>
      </main>
    </div>

    <ItemDetails
      v-if="dialogOpen"
      :item="detail"
      :busy="detailBusy"
      :error="detailFailure ? errorMessage(detailFailure) : ''"
      @close="closeDetail"
      @retry="retryDetail"
    />
  </div>
</template>
