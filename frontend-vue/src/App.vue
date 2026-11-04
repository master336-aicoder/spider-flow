<template>
  <div id="app">
    <a-config-provider :locale="zhCN">
      <router-view />
    </a-config-provider>

  </div>
</template>

<script>
import zhCN from 'ant-design-vue/es/locale-provider/zh_CN'
export default {
  name: 'App',
  data() {
    return {
      zhCN
    }
  }
}
</script>

<style lang="less">
.size {
  width: 100%;
  height: 100%;
}
html,
body {
  .size;
  overflow: hidden;
  margin: 0;
  padding: 0;
}
#app {
  .size;
}
</style>
