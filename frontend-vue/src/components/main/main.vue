<template>
  <a-layout class="main">
    <a-layout-sider
      v-model="isCollapsed"
      :collapsed-width="80"
      :collapsible="true"
      :trigger="null"
      :width="200"
      class="side-menu"
    >
      <side-menu :active-name="$route.name" :is-collapsed="isCollapsed" :menu-list="menuList"></side-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header-con">
        <header-bar :is-collapsed="isCollapsed" @on-collapsed-sider="collapsedSider"></header-bar>
      </a-layout-header>
      <a-layout-content class="main-content">
        <div style="height:100%;">
          <!-- 同一路由仅参数不同时（如切换任务日志）必须重建组件，否则会沿用上一次的 data -->
          <router-view :key="$route.fullPath" />
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>
<script>
import SideMenu from './components/side-menu'
import HeaderBar from './components/header-bar'
import { pluginConfigsRequest } from '@/api/spider.js'

const COLLAPSE_STORAGE_KEY = 'spider-flow-sider-collapsed'
let pluginsLoaded = false

export default {
  components: {
    SideMenu,
    HeaderBar
  },
  data() {
    return {
      // 是否收起侧边栏
      isCollapsed: localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1',
      // 插件自带页面，动态挂到菜单上
      pluginMenus: []
    }
  },
  computed: {
    // 导航菜单列表
    menuList() {
      return this.$store.state.routesConfig.concat(this.pluginMenus)
    }
  },
  watch: {
    isCollapsed(value) {
      localStorage.setItem(COLLAPSE_STORAGE_KEY, value ? '1' : '0')
    }
  },
  methods: {
    // 展开/收起侧边栏
    collapsedSider() {
      this.isCollapsed = !this.isCollapsed
    },
    loadPlugins() {
      if (pluginsLoaded) {
        return
      }
      pluginsLoaded = true
      pluginConfigsRequest(data => {
        const plugins = data || []
        plugins.forEach((plugin, index) => {
          if (!plugin || !plugin.name || !plugin.url) {
            return
          }
          const name = 'plugin_' + index
          this.$router.addRoutes([{
            path: '/_' + name,
            name: '_' + name,
            component: () => import('@/components/main'),
            meta: {
              title: '_' + plugin.name,
              icon: 'appstore',
              hideInBread: 1
            },
            children: [{
              path: '/' + name,
              name: name,
              component: () => import('@/views/plugin/frame.vue'),
              meta: {
                title: plugin.name,
                icon: 'appstore',
                pluginUrl: plugin.url
              }
            }]
          }])
          this.pluginMenus.push({
            name: '_' + name,
            meta: { title: plugin.name, icon: 'appstore' },
            children: [{ name: name, meta: { title: plugin.name, icon: 'appstore' } }]
          })
        })
      }, () => {})
    }
  },
  mounted() {
    this.loadPlugins()
  }
}
</script>

<style lang="less">
.main {
  height: 100%;

  // 宽度交给 a-layout-sider 控制，这里再写死 200px 会让收起动画失效
  .side-menu {
    user-select: none;
    overflow: hidden;
    background: #fff;
  }

  .header-con {
    background: #fff;
    padding: 0;
  }

  .main-content {
    height: 100%;
    overflow-y: auto;

    & > * {
      height: 100%;
    }
  }
}
</style>
