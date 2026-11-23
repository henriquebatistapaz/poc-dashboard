<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Share2 } from '@lucide/vue'
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
const postText = ref('')
const visibility = ref<'public' | 'unlisted'>('unlisted')
const postedUrl = ref('')
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

async function publish() {
  busy.value = true
  error.value = ''
  postedUrl.value = ''
  try {
    const { url } = await request<{ url: string }>('/v1/integrations/mastodon/post', props.token,
      { text: postText.value.trim(), visibility: visibility.value })
    postedUrl.value = url
    postText.value = ''
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
  <section id="mastodon" class="collection-card" aria-labelledby="mastodon-title">
    <div class="collection-heading">
      <span class="source-icon"><Share2 :size="22" aria-hidden="true" /></span>
      <div>
        <div class="eyebrow">{{ t('common.private') }}</div>
        <h2 id="mastodon-title">{{ t('mastodon.title') }}</h2>
      </div>
    </div>
    <p class="collection-description">{{ t('mastodon.description') }}</p>
    <p v-if="notice === 'connected'" class="notice" role="status">{{ t('mastodon.connectedNotice') }}</p>
    <p v-if="notice === 'error'" class="notice error" role="alert">{{ t('mastodon.connectError') }}</p>
    <p class="field-help">{{ status.connected ? t('mastodon.connectedTo', { instance: status.instance }) : t('mastodon.notConnected') }}</p>

    <form v-if="!status.connected" class="collection-form" novalidate @submit.prevent="connect">
      <div class="field input-field">
        <label for="mastodon-instance">{{ t('mastodon.instance') }}</label>
        <input id="mastodon-instance" v-model="instance" type="url" required :disabled="!token || busy">
      </div>
      <button class="button primary" type="submit" :disabled="!token || busy">{{ t('mastodon.connect') }}</button>
    </form>

    <form v-else class="collection-form" novalidate @submit.prevent="search">
      <div class="field input-field">
        <label for="mastodon-query">{{ t('mastodon.query') }}</label>
        <input id="mastodon-query" v-model="query" maxlength="500" required :disabled="busy">
      </div>
      <div class="field source-field">
        <label for="mastodon-limit">{{ t('mastodon.limit') }}</label>
        <input id="mastodon-limit" v-model.number="limit" type="number" min="1" max="40" :disabled="busy">
      </div>
      <button class="button primary" type="submit" :disabled="!token || busy">{{ busy ? t('mastodon.searching') : t('mastodon.search') }}</button>
      <button class="button secondary" type="button" :disabled="!token || busy" @click="disconnect">{{ t('mastodon.disconnect') }}</button>
    </form>

    <p v-if="result" class="collection-result" role="status">
      {{ t('mastodon.result', { fetched: n(result.fetched), inserted: n(result.inserted), duplicates: n(result.duplicates) }) }}
    </p>

    <form v-if="status.connected" class="collection-form" novalidate @submit.prevent="publish">
      <div class="field input-field">
        <label for="mastodon-post-text">{{ t('mastodon.postText') }}</label>
        <textarea id="mastodon-post-text" v-model="postText" maxlength="500" required :disabled="busy" rows="3"></textarea>
      </div>
      <div class="field source-field">
        <label for="mastodon-visibility">{{ t('mastodon.postVisibility') }}</label>
        <select id="mastodon-visibility" v-model="visibility" :disabled="busy">
          <option value="unlisted">{{ t('mastodon.visibilityUnlisted') }}</option>
          <option value="public">{{ t('mastodon.visibilityPublic') }}</option>
        </select>
      </div>
      <button class="button primary" type="submit" :disabled="!token || busy">{{ busy ? t('mastodon.posting') : t('mastodon.post') }}</button>
    </form>

    <p v-if="postedUrl" class="collection-result" role="status">{{ t('mastodon.posted') }} <a :href="postedUrl" target="_blank" rel="noopener">{{ postedUrl }}</a></p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
/* Alvos de toque com pelo menos 40px de altura em telas pequenas. */
#mastodon button {
  min-height: 40px;
}
</style>
