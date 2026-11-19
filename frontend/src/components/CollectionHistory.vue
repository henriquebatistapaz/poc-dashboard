<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Clock3, LoaderCircle, RefreshCw } from '@lucide/vue'
import type { CollectionRun } from '../types'
import { displayDate, sourceLabel } from '../lib/format'

defineProps<{ runs: CollectionRun[]; busy: boolean; tracking: boolean; error: string }>()
const emit = defineEmits<{ refresh: []; resume: [run: CollectionRun] }>()
const { t, n } = useI18n()
const statusKey = (status: CollectionRun['status']) => `history.status.${status.toLowerCase()}`
</script>

<template>
  <section class="history-card" aria-labelledby="history-title">
    <div class="history-heading">
      <div>
        <span class="history-icon"><Clock3 :size="17" aria-hidden="true" /></span>
        <div>
          <h2 id="history-title">{{ t('history.title') }}</h2>
          <p>{{ t('history.subtitle') }}</p>
        </div>
      </div>
      <button class="button quiet" type="button" :disabled="busy" :aria-label="t('dashboard.refresh')" @click="emit('refresh')">
        <RefreshCw :size="14" :class="{ spinning: busy }" aria-hidden="true" />
        <span>{{ t('dashboard.refresh') }}</span>
      </button>
    </div>
    <div v-if="error" class="notice error history-notice" role="alert">
      <span>{{ error }}</span>
      <button class="text-button" type="button" @click="emit('refresh')">{{ t('common.retry') }}</button>
    </div>
    <div v-else-if="busy && !runs.length" class="history-empty" role="status">
      <span class="spinner dark" aria-hidden="true"></span>{{ t('common.loading') }}
    </div>
    <div v-else-if="!runs.length" class="history-empty" role="status">{{ t('history.empty') }}</div>
    <ul v-else class="history-list" aria-live="polite">
      <li v-for="run in runs" :key="run.id" class="history-row">
        <span class="history-run-icon" :class="`status-${run.status.toLowerCase()}`" aria-hidden="true">
          <LoaderCircle v-if="['PENDING', 'RUNNING'].includes(run.status)" :size="16" class="spinning" />
          <Clock3 v-else :size="16" />
        </span>
        <div class="history-run-main">
          <strong>{{ sourceLabel(run.source) }}</strong>
          <small>{{ t('history.run', { id: run.id }) }} · {{ displayDate(run.createdAt ?? null) }}</small>
          <small v-if="run.input" class="history-run-input">
            <span>{{ t('history.input') }}:</span>
            <code :title="run.input">{{ run.input }}</code>
          </small>
        </div>
        <span class="history-status" :class="`status-${run.status.toLowerCase()}`">{{ t(statusKey(run.status)) }}</span>
        <span v-if="run.status === 'SUCCEEDED'" class="history-counts">
          {{
            t('history.counts', {
              fetched: n(run.fetched ?? 0),
              inserted: n(run.inserted ?? 0),
              duplicates: n(run.duplicates ?? 0),
            })
          }}
        </span>
        <span v-else-if="run.status === 'FAILED'" class="history-counts">{{ t('history.failed') }}</span>
        <button v-else class="text-button history-resume" type="button" :disabled="tracking" @click="emit('resume', run)">
          {{ t(tracking ? 'history.tracking' : 'history.resume') }}
        </button>
      </li>
    </ul>
    <p v-if="tracking" class="history-tracking" role="status">
      <span class="spinner dark" aria-hidden="true"></span>{{ t('history.tracking') }}
    </p>
  </section>
</template>
