import request from '@/util/request.js'

const suffix = 'system/'
// 查询可动态修改的系统参数
export const getParamsRequest = (succFun, errFun) => {
  request.send(suffix + 'params', null, succFun, errFun)
}
// 保存系统参数（JSON 提交，空值表示恢复默认）
export const saveParamsRequest = (params, succFun, errFun) => {
  request.getAxios().post(suffix + 'params/save', JSON.stringify(params), {
    headers: { 'Content-Type': 'application/json;charset=UTF-8' },
    // 默认实例会把数据 qs 序列化成表单，这里保持 JSON 原样
    transformRequest: [(data) => data]
  }).then(response => {
    succFun(response.data, response)
  }).catch(error => {
    if (typeof errFun === 'function') {
      errFun(error.response && error.response.data, error.response, error)
    }
  })
}
// 查询系统运行状态
export const getStatusRequest = (succFun, errFun) => {
  request.send(suffix + 'status', null, succFun, errFun)
}
