<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Globe2 } from '@lucide/vue'
const { t, locale } = useI18n()
</script>
<template>
  <div class="language-picker"><Globe2 :size="15" /><select v-model="locale" :aria-label="t('common.language')"><option value="pt-BR">Português</option><option value="en">English</option><option value="es">Español</option></select></div>
</template>
