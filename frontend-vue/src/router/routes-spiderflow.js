/**
 * meta 除 vue-router 原生参数外的可配置项：
 *  title: 显示在侧边栏、面包屑处的文字
 *  icon: 左侧菜单与面包屑显示的 antd 图标名
 *  hideInBread: 设为1后此级路由不出现在面包屑中
 *  hideInMenu: 设为1后在左侧菜单不显示该页面选项
 */
export default [
  {
    path: '/_dashboard',
    name: '_dashboard',
    component: () => import('@/components/main'),
    meta: {
      title: '_工作台',
      icon: 'dashboard',
      hideInBread: 1
    },
    children: [
      {
        path: '/dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '工作台',
          icon: 'dashboard'
        }
      }
    ]
  },
  {
    path: '/_spider_list',
    name: '_spider_list',
    component: () => import('@/components/main'),
    meta: {
      title: '_爬虫列表',
      icon: 'container',
      hideInBread: 1
    },
    children: [
      {
        path: '/spider_list',
        name: 'spider_list',
        component: () => import('@/views/spider/list.vue'),
        meta: {
          title: '爬虫列表',
          icon: 'container'
        }
      },
      {
        path: '/spider_detail/:flowId',
        name: 'spider_detail',
        component: () => import('@/views/spider/detail.vue'),
        meta: {
          title: '爬虫详情-编辑',
          icon: 'container',
          hideInMenu: 1
        }
      },
      {
        path: '/spider_detail',
        name: 'spider_detail_add',
        component: () => import('@/views/spider/detail.vue'),
        meta: {
          title: '爬虫详情-新增',
          icon: 'container',
          hideInMenu: 1
        }
      },
      {
        path: '/task_list/:flowId',
        name: 'task_list',
        component: () => import('@/views/spider/taskList.vue'),
        meta: {
          title: '任务详情',
          icon: 'container',
          hideInMenu: 1
        }
      },
      {
        path: '/spider_log/:flowId/:taskId',
        name: 'spider_log',
        component: () => import('@/views/spider/logView.vue'),
        meta: {
          title: '执行日志',
          icon: 'container',
          hideInMenu: 1
        }
      }
    ]
  },
  {
    path: '/_variables_list',
    name: '_variables_list',
    component: () => import('@/components/main'),
    meta: {
      title: '_全局变量',
      icon: 'tablet',
      hideInBread: 1
    },
    children: [
      {
        path: '/variables_list',
        name: 'variables_list',
        component: () => import('@/views/variables/list.vue'),
        meta: {
          title: '全局变量',
          icon: 'tablet'
        }
      }
    ]
  },
  {
    path: '/_functions_list',
    name: '_functions_list',
    component: () => import('@/components/main'),
    meta: {
      title: '_自定义函数',
      icon: 'play-square',
      hideInBread: 1
    },
    children: [
      {
        path: '/functions_list',
        name: 'functions_list',
        component: () => import('@/views/functions/list.vue'),
        meta: {
          title: '自定义函数',
          icon: 'play-square'
        }
      },
      {
        path: '/functions_detail/:id',
        name: 'functions_detail',
        component: () => import('@/views/functions/detail.vue'),
        meta: {
          title: '自定义函数详情页',
          icon: 'database',
          hideInMenu: 1
        }
      }
    ]
  },
  {
    path: '/_datasources_list',
    name: '_datasources_list',
    component: () => import('@/components/main'),
    meta: {
      title: '_数据源管理',
      icon: 'database',
      hideInBread: 1
    },
    children: [
      {
        path: '/datasources_list',
        name: 'datasources_list',
        component: () => import('@/views/datasources/list.vue'),
        meta: {
          title: '数据源管理',
          icon: 'database'
        }
      }
    ]
  },
  {
    path: '/_system',
    name: '_system',
    component: () => import('@/components/main'),
    meta: {
      title: '_系统设置',
      icon: 'setting',
      hideInBread: 1
    },
    children: [
      {
        path: '/system_settings',
        name: 'system_settings',
        component: () => import('@/views/system/settings.vue'),
        meta: {
          title: '系统设置',
          icon: 'setting'
        }
      }
    ]
  },
  {
    path: '/_scripts',
    name: '_scripts',
    component: () => import('@/components/main'),
    meta: {
      title: '_脚本管理',
      icon: 'laptop',
      hideInBread: 1,
      // 0.5.0 后端未提供 script/* 接口，先隐藏入口
      hideInMenu: 1
    },
    children: [
      {
        path: '/scripts',
        name: 'scripts',
        component: () => import('@/views/scripts/index.vue'),
        meta: {
          title: '脚本管理',
          icon: 'laptop'
        }
      }
    ]
  }
]
