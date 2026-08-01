import { nextTick } from 'vue'

const invalidControlSelector = [
  '.el-form-item.is-error input',
  '.el-form-item.is-error textarea',
  '.el-form-item.is-error [role="combobox"]',
].join(', ')

export async function focusFirstInvalid(scopeSelector: string): Promise<void> {
  await nextTick()
  document
    .querySelector<HTMLElement>(scopeSelector)
    ?.querySelector<HTMLElement>(invalidControlSelector)
    ?.focus({ preventScroll: false })
}
