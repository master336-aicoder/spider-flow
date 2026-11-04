import request from '@/util/request.js'

const suffix = 'dashboard/'
// 工作台概览：任务统计、趋势、流程排行、最近任务、系统状态
export const getSummaryRequest = (days, succFun, errFun) => {
  request.send(suffix + 'summary', { days }, succFun, errFun)
}
// 工作台数据库视图：主库信息、表统计、数据源连通性
export const getDatabaseRequest = (succFun, errFun) => {
  request.send(suffix + 'database', null, succFun, errFun)
}
