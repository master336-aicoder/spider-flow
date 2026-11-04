import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)
const router = new Router({
  base: process.env.BASE_URL,
  mode: 'hash'
})

router.afterEach(to => {
  // 浏览器标题跟随页面名称
  window.document.title = router.app.$config.appName + (!to.meta.title ? '' : '-' + to.meta.title)
})

export default router
