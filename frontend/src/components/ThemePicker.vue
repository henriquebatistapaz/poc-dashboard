<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Monitor, Moon, Sun } from '@lucide/vue'
import { resolvedTheme, themePreference } from '../theme'

const { t } = useI18n()
</script>

<template>
  <div class="theme-picker">
    <Moon v-if="themePreference === 'dark'" :size="15" aria-hidden="true" />
    <Sun v-else-if="themePreference === 'light'" :size="15" aria-hidden="true" />
    <Monitor v-else :size="15" aria-hidden="true" :data-resolved="resolvedTheme" />
    <select v-model="themePreference" :aria-label="t('theme.label')">
      <option value="auto">{{ t('theme.auto') }}</option>
      <option value="light">{{ t('theme.light') }}</option>
      <option value="dark">{{ t('theme.dark') }}</option>
    </select>
  </div>
</template>
