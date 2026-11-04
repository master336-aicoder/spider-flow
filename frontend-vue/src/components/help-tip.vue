<template>
  <a-tooltip v-if="tipText" :title="tipText" placement="top">
    <a-icon type="question-circle" class="help-tip-icon" />
  </a-tooltip>
  <span v-else />
</template>

<script>
import { HELP } from '@/config/helpTexts.js'

export default {
  name: 'HelpTip',
  props: {
    // helpTexts.js 里的文案键名
    name: {
      type: String,
      default: ''
    },
    // 直接指定文案，优先级高于 name
    text: {
      type: String,
      default: ''
    }
  },
  computed: {
    tipText() {
      return this.text || HELP[this.name] || ''
    }
  }
}
</script>

<style scoped>
.help-tip-icon {
  margin-left: 4px;
  color: #1890ff;
  font-size: 12px;
  cursor: help;
}
</style>
