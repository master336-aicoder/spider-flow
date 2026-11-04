<template>
  <div class="dashboard">
    <a-card :bordered="false" class="dashboard-card">
      <template slot="extra">
        <span class="update-tip">{{ summaryTip }}</span>
        <span class="auto-refresh">
          自动刷新
          <a-switch v-model="autoRefresh" />
        </span>
        <a-button :loading="summaryLoading" icon="reload" size="small" @click="loadSummary">刷新</a-button>
      </template>
      <a-row :gutter="16" class="stat-row">
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="流程总数" :value="task.totalFlows || 0" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="已启用定时" :value="task.enabledFlows || 0" :suffix="'/ ' + (task.totalFlows || 0)" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic
            :value-style="task.runningTasks > 0 ? { color: '#fa8c16' } : {}"
            title="进行中任务"
            :value="task.runningTasks || 0"
          />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="今日任务" :value="task.todayTasks || 0" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="累计执行次数" :value="task.totalExecuteCount || 0" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="平均任务耗时" :value="durationText(task.avgDurationMs)" />
        </a-col>
      </a-row>
      <a-divider style="margin:12px 0;" />
      <a-row :gutter="16" class="stat-row">
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="Quartz 已注册" :value="spider.quartzJobCount === undefined ? '-' : spider.quartzJobCount" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="数据源" :value="datasources.length" :suffix="dbConnectedText" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="库内表" :value="main.tableCount || 0" :suffix="' / ' + sizeText(main.sizeBytes)" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="堆内存使用" :value="memPercent" suffix="%" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="线程池占用" :value="poolPercent" suffix="%" />
        </a-col>
        <a-col :md="4" :sm="8" :xs="12">
          <a-statistic title="运行时长" :value="jvm.uptimeText" :value-style="{ fontSize: '16px' }" />
        </a-col>
      </a-row>
    </a-card>

    <a-row :gutter="16">
      <a-col :md="14" :xs="24">
        <a-card class="dashboard-card" title="任务执行趋势">
          <template slot="extra">
            <a-radio-group v-model="days" size="small" @change="loadSummary">
              <a-radio-button :value="7">7 天</a-radio-button>
              <a-radio-button :value="14">14 天</a-radio-button>
              <a-radio-button :value="30">30 天</a-radio-button>
            </a-radio-group>
          </template>
          <a-spin :spinning="summaryLoading">
            <bar-trend :data="trend" />
          </a-spin>
        </a-card>
      </a-col>
      <a-col :md="10" :xs="24">
        <a-card class="dashboard-card" title="流程定时状态">
          <a-spin :spinning="summaryLoading">
            <donut-chart :items="flowStatusItems" caption="流程总数" />
          </a-spin>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="16">
      <a-col :md="14" :xs="24">
        <a-card class="dashboard-card" title="执行次数排行">
          <template slot="extra">
            <router-link :to="{ name: 'spider_list' }">爬虫列表</router-link>
          </template>
          <a-spin :spinning="summaryLoading">
            <rank-bars :items="flowRankItems" />
          </a-spin>
        </a-card>
      </a-col>
      <a-col :md="10" :xs="24">
        <a-card class="dashboard-card" title="系统资源">
          <a-spin :spinning="summaryLoading">
            <div class="meter">
              <span class="meter-label">堆内存</span>
              <div class="meter-bar">
                <a-progress
                  :percent="memPercent"
                  :show-info="false"
                  :stroke-color="memPercent > 85 ? '#f5222d' : '#1890ff'"
                  size="small"
                />
              </div>
              <span class="meter-text">{{ sizeText(memory.used) }} / {{ sizeText(memory.max) }}</span>
            </div>
            <div class="meter">
              <span class="meter-label">线程池</span>
              <div class="meter-bar">
                <a-progress
                  :percent="poolPercent"
                  :show-info="false"
                  :stroke-color="poolPercent > 85 ? '#f5222d' : '#52c41a'"
                  size="small"
                />
              </div>
              <span class="meter-text">{{ poolMeterText }}</span>
            </div>
            <a-descriptions :column="1" size="small" bordered>
              <a-descriptions-item label="定时调度">
                <a-tag :color="spider.jobEnable === 'true' ? 'green' : 'red'">
                  {{ spider.jobEnable === 'true' ? '已开启' : '已关闭' }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="调度中流程">{{ spider.scheduledJobs }} / 总数 {{ spider.totalSpiders }}</a-descriptions-item>
              <a-descriptions-item label="JVM 线程">{{ jvm.threadCount }} / CPU {{ jvm.availableProcessors }}</a-descriptions-item>
              <a-descriptions-item label="Java 版本">{{ jvm.javaVersion }}</a-descriptions-item>
              <a-descriptions-item label="操作系统">{{ jvm.osName }}</a-descriptions-item>
              <a-descriptions-item label="工作目录">{{ spider.workspace }}</a-descriptions-item>
            </a-descriptions>
          </a-spin>
        </a-card>
      </a-col>
    </a-row>

    <a-card class="dashboard-card" title="最近任务">
      <template slot="extra">
        <a-tooltip title="sp_task 只记录起止时间，没有成功/失败字段，因此状态只能区分进行中与已结束">
          <a-icon type="question-circle" />
        </a-tooltip>
      </template>
      <a-table
        :columns="taskColumns"
        :data-source="recentTasks"
        :loading="summaryLoading"
        :pagination="false"
        :scroll="{ x: 860 }"
        rowKey="id"
        size="middle"
      >
        <template slot="status" slot-scope="text">
          <a-tag :color="text === 'running' ? 'orange' : 'green'">
            {{ text === 'running' ? '进行中' : '已结束' }}
          </a-tag>
        </template>
        <template slot="duration" slot-scope="text, record">
          {{ record.durationMs === null ? '-' : durationText(record.durationMs) }}
        </template>
        <template slot="operation" slot-scope="text, record">
          <a @click="openTask(record)">任务详情</a>
        </template>
      </a-table>
    </a-card>

    <a-row :gutter="16">
      <a-col :lg="12" :xs="24">
        <a-card class="dashboard-card" title="数据库表统计">
          <template slot="extra">
            <span class="update-tip">{{ databaseTip }}</span>
            <a-button :loading="databaseLoading" icon="reload" size="small" @click="loadDatabase">刷新</a-button>
          </template>
          <a-descriptions :column="2" size="small" bordered>
            <a-descriptions-item label="版本">{{ main.version }}</a-descriptions-item>
            <a-descriptions-item label="数据库">{{ main.database }}</a-descriptions-item>
            <a-descriptions-item label="地址">{{ main.host }}:{{ main.port }}</a-descriptions-item>
            <a-descriptions-item label="字符集">{{ main.charset }}</a-descriptions-item>
          </a-descriptions>
          <a-table
            :columns="tableColumns"
            :data-source="tables"
            :loading="databaseLoading"
            :pagination="false"
            :scroll="{ x: 460 }"
            class="table-gap"
            rowKey="name"
            size="small"
          >
            <template slot="rows" slot-scope="text">
              {{ text < 0 ? '统计失败' : text }}
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :lg="12" :xs="24">
        <a-card class="dashboard-card" title="数据源连通性">
          <template slot="extra">
            <router-link :to="{ name: 'datasources_list' }">数据源管理</router-link>
          </template>
          <a-table
            :columns="sourceColumns"
            :data-source="datasources"
            :loading="databaseLoading"
            :pagination="false"
            :scroll="{ x: 520 }"
            rowKey="id"
            size="small"
          >
            <template slot="connected" slot-scope="text, record">
              <a-tag :color="record.connected ? 'green' : 'red'">{{ record.connected ? '可连接' : '连接失败' }}</a-tag>
              <span class="cost">{{ record.costMs }} ms</span>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script>
import BarTrend from './components/bar-trend.vue'
import DonutChart from './components/donut-chart.vue'
import RankBars from './components/rank-bars.vue'
import { getSummaryRequest, getDatabaseRequest } from '@/api/dashboard.js'

export default {
  name: 'Dashboard',
  components: {
    BarTrend,
    DonutChart,
    RankBars
  },
  data() {
    return {
      taskColumns: [
        { title: '流程', dataIndex: 'flowName', width: 200 },
        { title: '开始时间', dataIndex: 'beginTime', width: 170 },
        { title: '结束时间', dataIndex: 'endTime', width: 170 },
        { title: '耗时', dataIndex: 'durationMs', width: 120, scopedSlots: { customRender: 'duration' } },
        { title: '状态', dataIndex: 'status', width: 100, scopedSlots: { customRender: 'status' } },
        { title: '操作', dataIndex: 'operation', width: 100, scopedSlots: { customRender: 'operation' } }
      ],
      tableColumns: [
        { title: '表名', dataIndex: 'name', width: 180 },
        { title: '行数', dataIndex: 'rows', width: 90, scopedSlots: { customRender: 'rows' } },
        { title: '体积', dataIndex: 'sizeBytes', width: 90, customRender: text => this.sizeText(text) },
        { title: '说明', dataIndex: 'comment' }
      ],
      sourceColumns: [
        { title: '名称', dataIndex: 'name', width: 120 },
        { title: '类型', dataIndex: 'type', width: 90 },
        { title: '地址', dataIndex: 'target', width: 160 },
        { title: '状态', dataIndex: 'connected', width: 140, scopedSlots: { customRender: 'connected' } }
      ],
      days: 14,
      summary: {},
      database: {},
      summaryLoading: false,
      databaseLoading: false,
      autoRefresh: false,
      summaryTip: '',
      databaseTip: '',
      timer: null
    }
  },
  computed: {
    task() {
      return this.summary.task || {}
    },
    trend() {
      return this.summary.trend || []
    },
    flowRank() {
      return this.summary.flowRank || []
    },
    recentTasks() {
      return this.summary.recentTasks || []
    },
    system() {
      return this.summary.system || {}
    },
    jvm() {
      return this.system.jvm || {}
    },
    memory() {
      return this.system.memory || {}
    },
    spider() {
      return this.system.spider || {}
    },
    main() {
      return this.database.main || {}
    },
    tables() {
      return this.database.tables || []
    },
    datasources() {
      return this.database.datasources || []
    },
    dbConnectedText() {
      if (!this.datasources.length) {
        return ''
      }
      const ok = this.datasources.filter(item => item.connected).length
      return ' / 可连 ' + ok
    },
    flowStatusItems() {
      return [
        { name: '已启用定时', value: this.task.enabledFlows || 0 },
        { name: '未启用定时', value: (this.task.totalFlows || 0) - (this.task.enabledFlows || 0), color: '#d9d9d9' }
      ]
    },
    flowRankItems() {
      return this.flowRank.map(item => ({
        name: item.name || '未命名流程',
        value: item.executeCount,
        text: (item.executeCount || 0) + ' 次'
      }))
    },
    memPercent() {
      if (!this.memory.max) {
        return 0
      }
      return Math.round((this.memory.used / this.memory.max) * 100)
    },
    poolPercent() {
      if (!this.spider.poolMaxThreads || this.spider.poolMaxThreads <= 0) {
        return 0
      }
      return Math.round(((this.spider.poolActiveThreads || 0) / this.spider.poolMaxThreads) * 100)
    },
    poolMeterText() {
      const active = this.spider.poolActiveThreads
      if (active === undefined || active < 0) {
        return '-'
      }
      return active + ' / ' + this.spider.poolMaxThreads + '，排队 ' + (this.spider.poolQueueSize || 0)
    }
  },
  watch: {
    autoRefresh(value) {
      this.setupTimer(value)
    }
  },
  mounted() {
    this.loadSummary()
    this.loadDatabase()
  },
  beforeDestroy() {
    clearInterval(this.timer)
  },
  methods: {
    setupTimer(enabled) {
      clearInterval(this.timer)
      if (enabled) {
        this.timer = setInterval(this.loadSummary, 10000)
      }
    },
    loadSummary() {
      this.summaryLoading = true
      getSummaryRequest(
        this.days,
        data => {
          this.summary = (data && data.data) || {}
          this.summaryTip = '更新于 ' + new Date().toLocaleTimeString()
          this.summaryLoading = false
        },
        () => {
          this.summaryLoading = false
          this.summaryTip = '概览获取失败'
          this.$message.error('工作台概览获取失败')
        }
      )
    },
    loadDatabase() {
      this.databaseLoading = true
      getDatabaseRequest(
        data => {
          this.database = (data && data.data) || {}
          this.databaseTip = '更新于 ' + new Date().toLocaleTimeString()
          this.databaseLoading = false
        },
        () => {
          this.databaseLoading = false
          this.databaseTip = '数据库状态获取失败'
          this.$message.error('数据库状态获取失败')
        }
      )
    },
    openTask(record) {
      this.$router.push({ name: 'task_list', params: { flowId: record.flowId } })
    },
    sizeText(bytes) {
      const value = Number(bytes) || 0
      if (value < 1024) {
        return value + ' B'
      }
      if (value < 1024 * 1024) {
        return (value / 1024).toFixed(1) + ' KB'
      }
      return (value / 1024 / 1024).toFixed(1) + ' MB'
    },
    durationText(ms) {
      const value = Number(ms) || 0
      if (value < 1000) {
        return value + ' 毫秒'
      }
      const seconds = Math.round(value / 1000)
      if (seconds < 60) {
        return seconds + ' 秒'
      }
      return Math.floor(seconds / 60) + ' 分 ' + (seconds % 60) + ' 秒'
    }
  }
}
</script>

<style scoped>
.dashboard {
  /* 左右至少留够 8px，否则 a-row 的负边距会把内容顶出容器产生横向滚动 */
  padding: 8px;
}
.dashboard >>> .ant-descriptions-item-content {
  word-break: break-all;
}
.dashboard-card {
  margin-bottom: 16px;
}
.stat-row .ant-col {
  padding: 4px 8px;
}
.update-tip {
  color: #8c8c8c;
  font-size: 12px;
  margin-right: 12px;
}
.auto-refresh {
  color: #595959;
  font-size: 12px;
  margin-right: 12px;
}
.meter {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}
.meter-label {
  font-size: 12px;
  color: #595959;
  width: 48px;
  flex: none;
}
.meter-bar {
  flex: 1;
  margin: 0 8px;
}
.meter-text {
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
}
.table-gap {
  margin-top: 12px;
}
.cost {
  color: #8c8c8c;
  font-size: 12px;
  margin-left: 6px;
}
</style>
