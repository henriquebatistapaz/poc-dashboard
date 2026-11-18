<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpRight, X } from '@lucide/vue'
import type { Item } from '../types'
import { displayDate, safeLink, sourceLabel } from '../lib/format'
import { trapFocusOnTab } from '../lib/focus'

defineProps<{ item: Item | null; busy: boolean; error: string }>()
const emit = defineEmits<{ close: []; retry: [] }>()
const { t } = useI18n()
const dialog = ref<HTMLDialogElement>()
const closeButton = ref<HTMLButtonElement>()

onMounted(() => {
  dialog.value?.showModal?.()
  closeButton.value?.focus()
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  trapFocusOnTab(dialog.value, event)
}
</script>

<template>
  <dialog
    ref="dialog"
    class="detail-dialog"
    aria-labelledby="detail-label"
    @cancel.prevent="emit('close')"
    @keydown="onKeydown"
    @click="(event) => { if (event.target === event.currentTarget) emit('close') }"
  >
    <div class="detail-header">
      <span id="detail-label" class="section-kicker">{{ t('details.title') }}</span>
      <button ref="closeButton" class="icon-button" type="button" :aria-label="t('common.close')" @click="emit('close')">
        <X :size="20" aria-hidden="true" />
      </button>
    </div>
    <div v-if="busy" class="detail-loading" role="status">
      <span class="spinner dark" aria-hidden="true"></span>{{ t('common.loading') }}
    </div>
    <div v-else-if="error" class="notice error detail-notice" role="alert">
      <span>{{ error }}</span>
      <button class="text-button" type="button" @click="emit('retry')">{{ t('common.retry') }}</button>
    </div>
    <template v-else-if="item">
      <span class="pill source-pill">{{ sourceLabel(item.source) }}</span>
      <h2 id="detail-title">{{ item.title || t('items.untitled') }}</h2>
      <dl class="detail-fields">
        <div>
          <dt>{{ t('details.published') }}</dt>
          <dd>{{ displayDate(item.publishedAt) }}</dd>
        </div>
        <div>
          <dt>{{ t('details.collected') }}</dt>
          <dd>{{ displayDate(item.collectedAt) }}</dd>
        </div>
        <div>
          <dt>{{ t('details.externalId') }}</dt>
          <dd class="mono">{{ item.externalId }}</dd>
        </div>
        <div>
          <dt>{{ t('details.original') }}</dt>
          <dd>
            <a v-if="safeLink(item.link)" :href="safeLink(item.link)!" target="_blank" rel="noopener noreferrer">{{ item.link }}</a>
            <span v-else>{{ t('common.unavailableLink') }}</span>
          </dd>
        </div>
      </dl>
      <a
        v-if="safeLink(item.link)"
        class="button primary"
        :href="safeLink(item.link)!"
        target="_blank"
        rel="noopener noreferrer"
      >
        {{ t('common.openOriginal') }}<ArrowUpRight :size="16" aria-hidden="true" />
      </a>
    </template>
    <h2 v-else id="detail-title">{{ t('details.title') }}</h2>
  </dialog>
</template>
