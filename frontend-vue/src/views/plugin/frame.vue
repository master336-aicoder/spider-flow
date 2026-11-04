<template>
  <iframe
    v-if="src"
    :src="src"
    class="plugin-frame"
    frameborder="0"
  />
  <a-empty v-else description="插件地址不合法，已拒绝加载" />
</template>

<script>
export default {
  name: 'PluginFrame',
  computed: {
    // 插件只允许加载同源页面，避免把外部站点嵌进管理台
    src() {
      const url = this.$route.meta.pluginUrl || ''
      if (/^https?:\/\//i.test(url) || url.indexOf('//') === 0) {
        return ''
      }
      const path = url.replace(/^\/+/, '')
      return '/' + path
    }
  }
}
</script>

<style scoped>
.plugin-frame {
  width: 100%;
  height: 100%;
  min-height: 60vh;
  border: 0;
}
</style>
