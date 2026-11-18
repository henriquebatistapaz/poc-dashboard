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
const isFixture = computed(() => ['ALPHA', 'BETA'].includes(source.value))
const isUrl = computed(() => ['RSS', 'WORDPRESS', 'GOOGLE_ALERTS'].includes(source.value))
const placeholder = computed(() =>
  source.value === 'ALPHA'
    ? '{"posts": […]}'
    : source.value === 'BETA'
      ? '{"items": […]}'
      : t(
          source.value === 'GOOGLE_NEWS'
            ? 'collection.queryPlaceholder'
            : source.value === 'GOOGLE_ALERTS'
              ? 'collection.alertsPlaceholder'
              : source.value === 'REDDIT'
                ? 'collection.redditPlaceholder'
                : 'collection.feedPlaceholder',
        ),
)

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
          <option value="GOOGLE_ALERTS">{{ t('sources.google-alerts') }}</option>
          <option value="REDDIT">{{ t('sources.redditConditional') }}</option>
          <option value="ALPHA">{{ t('sources.alpha') }}</option>
          <option value="BETA">{{ t('sources.beta') }}</option>
        </select>
      </div>
      <div class="field input-field">
        <label for="collection-input">{{
          t(
            isFixture
              ? 'collection.fixture'
              : isUrl
                ? 'collection.feedUrl'
                : source === 'REDDIT'
                  ? 'collection.subreddit'
                  : 'collection.search',
          )
        }}</label>
        <div class="input-with-icon">
          <Link v-if="isUrl && source !== 'GOOGLE_ALERTS'" :size="17" aria-hidden="true" />
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
            :class="{ 'with-icon': isUrl && source !== 'GOOGLE_ALERTS' }"
            :type="source === 'GOOGLE_ALERTS' ? 'password' : isUrl ? 'url' : 'text'"
            :inputmode="isUrl && source !== 'GOOGLE_ALERTS' ? 'url' : 'text'"
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
    <div v-if="source === 'RSS'" class="feed-examples">
      <span>{{ t('collection.examples') }}</span>
      <button type="button" :disabled="busy" @click="example('https://feeds.bbci.co.uk/news/world/rss.xml')">
        BBC News <ArrowUpRight :size="12" aria-hidden="true" />
      </button>
      <button type="button" :disabled="busy" @click="example('https://xkcd.com/atom.xml')">
        XKCD · Atom <ArrowUpRight :size="12" aria-hidden="true" />
      </button>
      <button type="button" :disabled="busy" @click="example('https://wordpress.org/news/feed/')">
        WordPress <ArrowUpRight :size="12" aria-hidden="true" />
      </button>
    </div>
    <p v-else-if="source === 'GOOGLE_ALERTS'" class="field-help">{{ t('collection.alertsHelp') }}</p>
    <p v-else-if="source === 'REDDIT'" class="field-help">{{ t('collection.redditHelp') }}</p>
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
