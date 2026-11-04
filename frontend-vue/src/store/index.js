import Vue from 'vue'
import Vuex from 'vuex'
import router from '@/router'
import routes from '@/router/routes.js'
import defaultRoutes from '@/router/routes-default.js'
import spiderFlowRoutes from '@/router/routes-spiderflow.js'

Vue.use(Vuex)

// 路由在此一次性注册：入口重定向 -> 404 缺省页 -> 各业务页面
const routesConfig = [...routes, ...defaultRoutes, ...spiderFlowRoutes]
router.addRoutes(routesConfig)

export default new Vuex.Store({
  state: {
    // 路由配置，菜单与面包屑都从这里取
    routesConfig
  }
})
