<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, ref, watch } from 'vue'
import { ArrowUpRight, CheckCircle2, Link, Play, Rss, X } from '@lucide/vue'
import type { CollectionRun, CollectionSource } from '../types'
import { safeLink } from '../lib/format'

const props = defineProps<{ busy: boolean; result: CollectionRun | null; error: string }>()
const emit = defineEmits<{ collect: [source: CollectionSource, input: string]; view: [] }>()
const { t, n } = useI18n()
const source = ref<CollectionSource>('RSS')
const input = ref('')
const validation = ref('')
const hiddenResult = ref(false)
const isFixture = computed(() => source.value === 'ALPHA' || source.value === 'BETA')
const isUrl = computed(() => source.value === 'RSS')

const PLACEHOLDER_KEYS: Record<Exclude<CollectionSource, 'ALPHA' | 'BETA'>, string> = {
  RSS: 'collection.feedPlaceholder',
  GOOGLE_NEWS: 'collection.queryPlaceholder',
  WORDPRESS: 'collection.wordpressPlaceholder',
}

const placeholder = computed(() =>
  source.value === 'ALPHA'
    ? '{"posts": […]}'
    : source.value === 'BETA'
      ? '{"items": […]}'
      : t(PLACEHOLDER_KEYS[source.value]),
)

type SourceExample = { label?: string; labelKey?: string; value: string }

const EXAMPLES: Partial<Record<CollectionSource, SourceExample[]>> = {
  RSS: [
    { label: 'BBC News', value: 'https://feeds.bbci.co.uk/news/world/rss.xml' },
    { label: 'XKCD · Atom', value: 'https://xkcd.com/atom.xml' },
    { label: 'TabNews', value: 'https://www.tabnews.com.br/recentes/rss' },
    { label: 'G1', value: 'https://g1.globo.com/rss/g1/' },
    { label: 'Hacker News', value: 'https://hnrss.org/frontpage' },
  ],
  GOOGLE_NEWS: [
    { label: 'Java Spring Boot', value: 'Java Spring Boot' },
    { label: 'Energia renovável', value: 'energia renovável' },
    { label: 'Inteligência artificial', value: 'inteligência artificial' },
  ],
  WORDPRESS: [
    { label: 'java', value: 'java' },
    { label: 'technology', value: 'technology' },
    { label: 'wordpress', value: 'wordpress' },
  ],
  ALPHA: [
    {
      labelKey: 'collection.fixtureExample',
      value: '{"posts":[{"id":"alpha-1","author":{"handle":"@dev"},"text":"Exemplo Alpha","created_at":"2026-11-18T12:00:00Z","url":"https://example.org/alpha/1"}]}',
    },
  ],
  BETA: [
    {
      labelKey: 'collection.fixtureExample',
      value: '{"items":[{"postId":"beta-1","authorName":"Maria","body":"Exemplo Beta","publishedAt":"2026-11-18T12:00:00Z","link":"https://example.org/beta/1"}]}',
    },
  ],
}

const activeExamples = computed(() => EXAMPLES[source.value] ?? [])

watch(source, () => {
  input.value = ''
  validation.value = ''
})
watch(
  () => props.result,
  (result) => {
    if (result) {
      input.value = ''
      hiddenResult.value = false
    }
  },
)

function submit() {
  if (props.busy) return
  validation.value = ''
  const value = input.value.trim()
  if (!value) {
    validation.value = 'validation.required'
    return
  }
  if (isUrl.value && !safeLink(value)) {
    validation.value = 'validation.url'
    return
  }
  if (value.length > 4096) {
    validation.value = 'validation.length'
    return
  }
  emit('collect', source.value, value)
}

function example(value: string) {
  input.value = value
  validation.value = ''
}
</script>

<template>
  <section id="collect" class="collection-card" aria-labelledby="collection-title">
    <div class="collection-heading">
      <span class="source-icon"><Rss :size="22" aria-hidden="true" /></span>
      <div>
        <div class="eyebrow">{{ t('collection.eyebrow') }}</div>
        <h2 id="collection-title">{{ t('collection.title') }}</h2>
      </div>
      <span class="pill neutral">{{ t('collection.manual') }}</span>
    </div>
    <p class="collection-description">{{ t('collection.description') }}</p>
    <form class="collection-form" novalidate @submit.prevent="submit">
      <div class="field source-field">
        <label for="collection-source">{{ t('collection.source') }}</label>
        <select id="collection-source" v-model="source" :disabled="busy">
          <option value="RSS">{{ t('sources.rss') }}</option>
          <option value="GOOGLE_NEWS">{{ t('sources.google-news') }}</option>
          <option value="WORDPRESS">{{ t('sources.wordpress') }}</option>
          <option value="ALPHA">{{ t('sources.alpha') }}</option>
          <option value="BETA">{{ t('sources.beta') }}</option>
        </select>
      </div>
      <div class="field input-field">
        <label for="collection-input">{{
          t(isFixture ? 'collection.fixture' : isUrl ? 'collection.feedUrl' : 'collection.search')
        }}</label>
        <div class="input-with-icon">
          <Link v-if="isUrl" :size="17" aria-hidden="true" />
          <textarea
            v-if="isFixture"
            id="collection-input"
            v-model="input"
            :placeholder="placeholder"
            :disabled="busy"
            maxlength="4096"
            rows="2"
            required
            :aria-invalid="Boolean(validation || error)"
            :aria-describedby="validation || error ? 'collection-error' : undefined"
          ></textarea>
          <input
            v-else
            id="collection-input"
            v-model="input"
            :class="{ 'with-icon': isUrl }"
            :type="isUrl ? 'url' : 'text'"
            :inputmode="isUrl ? 'url' : 'text'"
            autocapitalize="none"
            autocorrect="off"
            :placeholder="placeholder"
            :disabled="busy"
            maxlength="4096"
            autocomplete="off"
            spellcheck="false"
            required
            :aria-invalid="Boolean(validation || error)"
            :aria-describedby="validation || error ? 'collection-error' : undefined"
          />
        </div>
      </div>
      <button class="button primary collect-submit" :disabled="busy" type="submit">
        <span v-if="busy" class="spinner" aria-hidden="true"></span>
        <Play v-else :size="15" aria-hidden="true" />
        {{ t(busy ? 'collection.busy' : 'collection.submit') }}
      </button>
    </form>
    <div v-if="activeExamples.length" class="feed-examples">
      <span>{{ t('collection.examples') }}</span>
      <button
        v-for="item in activeExamples"
        :key="item.labelKey ?? item.label"
        type="button"
        :disabled="busy"
        @click="example(item.value)"
      >
        {{ item.labelKey ? t(item.labelKey) : item.label }} <ArrowUpRight :size="12" aria-hidden="true" />
      </button>
    </div>
    <p v-if="source === 'WORDPRESS'" class="field-help">{{ t('collection.wordpressHelp') }}</p>
    <p v-else-if="isFixture" class="field-help">{{ t('collection.fixtureHelp') }}</p>
    <p v-if="busy" class="field-help" role="status">{{ t('collection.wait') }}</p>
    <div v-if="validation || error" id="collection-error" class="notice error" role="alert">
      {{ validation ? t(validation) : error }}
    </div>
    <div v-if="result && !hiddenResult" class="collection-result" role="status">
      <CheckCircle2 :size="20" aria-hidden="true" />
      <div>
        <strong>{{ t('collection.done') }}</strong>
        <p>
          {{
            t('collection.counts', {
              fetched: n(result.fetched ?? 0),
              inserted: n(result.inserted ?? 0),
              duplicates: n(result.duplicates ?? 0),
            })
          }}
        </p>
        <small v-if="result.fetched === 0">{{ t('collection.empty') }}</small>
        <small v-else-if="result.inserted === 0">{{ t('collection.duplicateNote') }}</small>
      </div>
      <button class="text-button" type="button" @click="emit('view')">
        {{ t('collection.view') }} <ArrowUpRight :size="14" aria-hidden="true" />
      </button>
      <button class="icon-button" type="button" :aria-label="t('collection.dismiss')" @click="hiddenResult = true">
        <X :size="15" aria-hidden="true" />
      </button>
    </div>
  </section>
</template>
