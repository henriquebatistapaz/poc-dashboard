<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUpRight, X } from '@lucide/vue'
import type { Item } from '../types'
import { displayDate, safeLink, sourceLabel } from '../lib/format'
defineProps<{ item: Item | null; busy: boolean; error: string }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const dialog = ref<HTMLDialogElement>()
onMounted(() => dialog.value?.showModal())
</script>
<template>
  <dialog ref="dialog" class="detail-dialog" aria-labelledby="detail-label" @cancel.prevent="emit('close')" @click="(event) => { if (event.target === event.currentTarget) emit('close') }">
    <div class="detail-header"><span id="detail-label" class="section-kicker">{{ t('details.title') }}</span><button class="icon-button" :aria-label="t('common.close')" autofocus @click="emit('close')"><X :size="20" /></button></div>
    <div v-if="busy" class="detail-loading" role="status"><span class="spinner dark"></span>{{ t('common.loading') }}</div>
    <div v-else-if="error" class="notice error" role="alert">{{ error }}</div>
    <template v-else-if="item">
      <span class="pill source-pill">{{ sourceLabel(item.source) }}</span>
      <h2 id="detail-title">{{ item.title || t('items.untitled') }}</h2>
      <dl class="detail-fields"><div><dt>{{ t('details.published') }}</dt><dd>{{ displayDate(item.publishedAt) }}</dd></div><div><dt>{{ t('details.collected') }}</dt><dd>{{ displayDate(item.collectedAt) }}</dd></div><div><dt>{{ t('details.externalId') }}</dt><dd class="mono">{{ item.externalId }}</dd></div><div><dt>{{ t('details.original') }}</dt><dd><a v-if="safeLink(item.link)" :href="safeLink(item.link)!" target="_blank" rel="noopener noreferrer">{{ item.link }}</a><span v-else>{{ t('common.unavailableLink') }}</span></dd></div></dl>
      <a v-if="safeLink(item.link)" class="button primary" :href="safeLink(item.link)!" target="_blank" rel="noopener noreferrer">{{ t('common.openOriginal') }}<ArrowUpRight :size="16" /></a>
    </template>
    <h2 v-else id="detail-title">{{ t('details.title') }}</h2>
  </dialog>
</template>
