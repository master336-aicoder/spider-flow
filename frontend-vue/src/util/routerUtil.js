/**
 * @description 获取首页路由
 * @param {Array} routesConfig 路由配置
 * @param {String} homeRouteName 首页路由名称，默认为home
 */
export const getHomeRoute = (routesConfig, homeRouteName = 'home') => {
  let homeRoute
  for (let i = 0; i < routesConfig.length; i++) {
    let item = routesConfig[i]
    if (item.children && item.children.length) {
      let res = getHomeRoute(item.children, homeRouteName)
      if (res.name) return res
    } else if (item.name === homeRouteName) {
      homeRoute = item
    }
  }
  return homeRoute
}
