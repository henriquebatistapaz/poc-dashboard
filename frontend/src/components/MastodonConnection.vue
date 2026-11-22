<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, errorMessage, request } from '../lib/api'

interface ConnectionStatus { connected: boolean; instance: string | null }
interface SearchResult { fetched: number; inserted: number; duplicates: number }

const props = defineProps<{ token: string }>()
const { t, n } = useI18n()
const instance = ref('https://mastodon.social')
const query = ref('')
const limit = ref(20)
const status = ref<ConnectionStatus>({ connected: false, instance: null })
const result = ref<SearchResult | null>(null)
const busy = ref(false)
const error = ref('')
const notice = computed(() => new URLSearchParams(window.location.search).get('mastodon'))

function describe(failure: unknown): string {
  return failure instanceof ApiError && failure.status === 409 ? t('mastodon.needConnection') : errorMessage(failure)
}

async function loadStatus() {
  if (!props.token) return
  try { status.value = await request<ConnectionStatus>('/v1/integrations/mastodon/connection', props.token) }
  catch (failure) { error.value = describe(failure) }
}
watch(() => props.token, loadStatus, { immediate: true })

async function connect() {
  busy.value = true
  error.value = ''
  try {
    const { authorizationUrl } = await request<{ authorizationUrl: string }>(
      '/v1/integrations/mastodon/connect', props.token, { instance: instance.value })
    window.location.assign(authorizationUrl)
  } catch (failure) {
    error.value = describe(failure)
    busy.value = false
  }
}

async function disconnect() {
  busy.value = true
  error.value = ''
  try {
    await request<void>('/v1/integrations/mastodon/connection', props.token, undefined, undefined, 'DELETE')
    status.value = { connected: false, instance: null }
    result.value = null
  } catch (failure) {
    error.value = describe(failure)
  } finally {
    busy.value = false
  }
}

async function search() {
  busy.value = true
  error.value = ''
  try {
    result.value = await request<SearchResult>('/v1/integrations/mastodon/search', props.token,
      { query: query.value.trim(), limit: limit.value })
  } catch (failure) {
    error.value = describe(failure)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section id="mastodon" class="library-card" aria-labelledby="mastodon-title">
    <h2 id="mastodon-title">{{ t('mastodon.title') }}</h2>
    <p>{{ t('mastodon.description') }}</p>
    <p v-if="notice === 'connected'" role="status">{{ t('mastodon.connectedNotice') }}</p>
    <p v-if="notice === 'error'" role="alert">{{ t('mastodon.connectError') }}</p>
    <p v-if="status.connected">{{ t('mastodon.connectedTo', { instance: status.instance }) }}</p>
    <p v-else>{{ t('mastodon.notConnected') }}</p>

    <form v-if="!status.connected" @submit.prevent="connect">
      <label for="mastodon-instance">{{ t('mastodon.instance') }}</label>
      <input id="mastodon-instance" v-model="instance" type="url" required :disabled="!token || busy">
      <button type="submit" :disabled="!token || busy">{{ t('mastodon.connect') }}</button>
    </form>

    <form v-else @submit.prevent="search">
      <label for="mastodon-query">{{ t('mastodon.query') }}</label>
      <input id="mastodon-query" v-model="query" maxlength="500" required :disabled="busy">
      <label for="mastodon-limit">{{ t('mastodon.limit') }}</label>
      <input id="mastodon-limit" v-model.number="limit" type="number" min="1" max="40" :disabled="busy">
      <button type="submit" :disabled="!token || busy">{{ busy ? t('mastodon.searching') : t('mastodon.search') }}</button>
      <button type="button" :disabled="!token || busy" @click="disconnect">{{ t('mastodon.disconnect') }}</button>
    </form>

    <p v-if="result" role="status">
      {{ t('mastodon.result', { fetched: n(result.fetched), inserted: n(result.inserted), duplicates: n(result.duplicates) }) }}
    </p>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>
