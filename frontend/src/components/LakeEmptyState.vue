<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    compact?: boolean
    inverted?: boolean
  }>(),
  {
    description: '',
    compact: false,
    inverted: false,
  },
)
</script>

<template>
  <section
    class="lake-empty"
    :class="{
      'lake-empty--compact': compact,
      'lake-empty--inverted': inverted,
    }"
    role="status"
    aria-live="polite"
  >
    <div class="lake-empty__mark" aria-hidden="true"><span>湖</span></div>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.default" class="lake-empty__action">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.lake-empty {
  display: grid;
  min-height: 230px;
  place-items: center;
  align-content: center;
  padding: 30px 18px;
  color: var(--ink-700);
  text-align: center;
}

.lake-empty__mark {
  position: relative;
  display: grid;
  width: 62px;
  height: 46px;
  margin-bottom: 17px;
  place-items: center;
  border: 1px solid var(--lake-100);
  border-radius: 50%;
  color: var(--lake-800);
}

.lake-empty__mark::before,
.lake-empty__mark::after {
  position: absolute;
  border: 1px solid var(--paper-400);
  border-radius: 50%;
  content: "";
}

.lake-empty__mark::before {
  inset: 6px -8px;
}

.lake-empty__mark::after {
  inset: 12px -15px;
  opacity: 0.62;
}

.lake-empty__mark span {
  position: relative;
  z-index: 1;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 9px;
  color: var(--paper-50);
  background: var(--lake-800);
  font-family: "STSong", "Songti SC", serif;
  font-size: 13px;
  font-weight: 700;
}

.lake-empty > strong {
  color: var(--ink-900);
  font-size: 13px;
}

.lake-empty > p {
  max-width: 36ch;
  margin: 7px 0 0;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.65;
}

.lake-empty__action {
  margin-top: 15px;
}

.lake-empty--compact {
  min-height: 150px;
  padding-block: 18px;
}

.lake-empty--compact .lake-empty__mark {
  width: 50px;
  height: 36px;
  margin-bottom: 13px;
}

.lake-empty--compact .lake-empty__mark span {
  width: 24px;
  height: 24px;
  font-size: 11px;
}

.lake-empty--inverted {
  color: rgb(255 255 255 / 78%);
}

.lake-empty--inverted .lake-empty__mark {
  border-color: rgb(255 255 255 / 24%);
  color: var(--paper-50);
}

.lake-empty--inverted .lake-empty__mark::before,
.lake-empty--inverted .lake-empty__mark::after {
  border-color: rgb(255 255 255 / 18%);
}

.lake-empty--inverted .lake-empty__mark span {
  color: var(--lake-950);
  background: var(--paper-100);
}

.lake-empty--inverted > strong {
  color: var(--paper-50);
}

.lake-empty--inverted > p {
  color: rgb(255 255 255 / 66%);
}
</style>
