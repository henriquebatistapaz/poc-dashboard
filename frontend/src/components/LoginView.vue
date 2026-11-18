<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LanguagePicker from './LanguagePicker.vue'
import ThemePicker from './ThemePicker.vue'
import { ref } from 'vue'
import { ArrowRight, Layers, LockKeyhole, Rss, ShieldCheck } from '@lucide/vue'

defineProps<{ busy: boolean; error: string }>()
const emit = defineEmits<{ login: [email: string, password: string] }>()
const { t } = useI18n()
const email = ref('')
const password = ref('')
const localValidation = ref('')

function submit() {
  localValidation.value = ''
  if (!email.value.trim() || !password.value) {
    localValidation.value = 'validation.loginRequired'
    return
  }
  if (!/^\S+$/.test(email.value.trim())) {
    localValidation.value = 'validation.email'
    return
  }
  emit('login', email.value.trim(), password.value)
  password.value = ''
}
</script>

<template>
  <main class="login-shell">
    <section class="login-story">
      <a class="brand" href="/" aria-label="Coleta">
        <span class="brand-icon"><Layers :size="21" aria-hidden="true" /></span>coleta<span class="brand-dot">.</span>
      </a>
      <div class="story-content">
        <span class="eyebrow"><span class="live-dot" aria-hidden="true"></span> {{ t('login.eyebrow') }}</span>
        <p class="story-headline">{{ t('login.headline') }}</p>
        <p>{{ t('login.story') }}</p>
        <div class="story-feed">
          <div class="feed-line">
            <span class="source-icon"><Rss :size="20" aria-hidden="true" /></span>
            <div><strong>{{ t('login.feedTitle') }}</strong><small>{{ t('login.feedSubtitle') }}</small></div>
            <ShieldCheck :size="21" class="muted" aria-hidden="true" />
          </div>
          <div class="feed-skeleton"></div>
          <div class="feed-skeleton short"></div>
          <div class="story-tags">
            <span>{{ t('login.collect') }}</span>
            <ArrowRight :size="14" aria-hidden="true" />
            <span>{{ t('login.organize') }}</span>
            <ArrowRight :size="14" aria-hidden="true" />
            <span>{{ t('login.explore') }}</span>
          </div>
        </div>
      </div>
      <span class="login-footnote"><LockKeyhole :size="14" aria-hidden="true" /> {{ t('login.privateNote') }}</span>
    </section>
    <section class="login-form-side">
      <div class="login-controls">
        <ThemePicker />
        <LanguagePicker />
      </div>
      <form class="login-form" novalidate @submit.prevent="submit">
        <span class="section-kicker">{{ t('login.welcome') }}</span>
        <h1>{{ t('login.title') }}</h1>
        <p class="muted">{{ t('login.subtitle') }}</p>
        <div v-if="localValidation || error" id="login-error" class="notice error" role="alert">
          {{ localValidation ? t(localValidation) : error }}
        </div>
        <label for="login-email">{{ t('login.email') }}</label>
        <input
          id="login-email"
          v-model="email"
          type="text"
          inputmode="email"
          autocapitalize="none"
          autocorrect="off"
          spellcheck="false"
          autocomplete="username"
          :placeholder="t('login.emailPlaceholder')"
          maxlength="254"
          required
          :disabled="busy"
          :aria-invalid="Boolean(localValidation || error)"
          :aria-describedby="localValidation || error ? 'login-error' : undefined"
        />
        <label for="login-password">{{ t('login.password') }}</label>
        <input
          id="login-password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          :placeholder="t('login.passwordPlaceholder')"
          required
          :disabled="busy"
          :aria-invalid="Boolean(localValidation || error)"
          :aria-describedby="localValidation || error ? 'login-error' : undefined"
        />
        <button class="button primary login-submit" type="submit" :disabled="busy">
          <span v-if="busy" class="spinner" aria-hidden="true"></span>
          {{ busy ? t('login.busy') : t('login.submit') }}
          <ArrowRight v-if="!busy" :size="17" aria-hidden="true" />
        </button>
        <p class="login-note"><ShieldCheck :size="16" aria-hidden="true" /> {{ t('login.sessionNote') }}</p>
      </form>
    </section>
  </main>
</template>
