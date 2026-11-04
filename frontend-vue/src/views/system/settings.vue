<template>
  <div class="system-settings">
    <a-card title="运行参数" style="margin-bottom:16px;">
      <template slot="extra">
        <a-button icon="reload" size="small" @click="loadParams">重新加载</a-button>
      </template>
      <a-alert
        message="修改保存后立即生效（下次调度/执行时读取新值），并持久化到数据库，重启后保留。"
        banner
        type="info"
      />
      <a-table
        :columns="columns"
        :data-source="params"
        :loading="paramLoading"
        :pagination="false"
        :scroll="{ x: 900 }"
        rowKey="key"
        size="middle"
      >
        <template slot="description" slot-scope="text">
          <span v-if="Array.isArray(text)">{{ text.join('、') }}</span>
          <span v-else>{{ text }}</span>
        </template>
        <template slot="value" slot-scope="text, record">
          <a-select
            v-if="record.options && record.options.length"
            v-model="record.value"
            size="small"
            style="width:120px;"
          >
            <a-select-option v-for="opt in record.options" :key="opt" :value="opt">{{ opt }}</a-select-option>
          </a-select>
          <a-input v-else v-model="record.value" size="small" />
        </template>
        <template slot="operation" slot-scope="text, record">
          <a-popconfirm
            cancel-text="取消"
            ok-text="确定"
            placement="topRight"
            title="确定将该参数恢复为配置文件默认值？"
            @confirm="resetParam(record)"
          >
            <a>{{ record.customized ? '重置' : '' }}</a>
          </a-popconfirm>
        </template>
      </a-table>
      <div style="margin-top:16px;text-align:right;">
        <a-button :loading="saveLoading" icon="save" type="primary" @click="saveParams">保存参数</a-button>
      </div>
    </a-card>

    <a-card title="系统状态" :bordered="true">
      <template slot="extra">
        <span style="margin-right:12px;color:#999;">{{ statusTip }}</span>
        <a-button icon="reload" size="small" @click="loadStatus">刷新</a-button>
      </template>
      <a-row :gutter="16" v-if="status.jvm">
        <a-col :span="12">
          <a-descriptions title="JVM 信息" :column="1" size="small" bordered>
            <a-descriptions-item label="Java 版本">{{ status.jvm.javaVersion }}</a-descriptions-item>
            <a-descriptions-item label="JVM 名称">{{ status.jvm.jvmName }}</a-descriptions-item>
            <a-descriptions-item label="操作系统">{{ status.jvm.osName }}</a-descriptions-item>
            <a-descriptions-item label="CPU 核心数">{{ status.jvm.availableProcessors }}</a-descriptions-item>
            <a-descriptions-item label="已运行">{{ status.jvm.uptimeText }}</a-descriptions-item>
            <a-descriptions-item label="线程数">{{ status.jvm.threadCount }}</a-descriptions-item>
          </a-descriptions>
        </a-col>
        <a-col :span="12">
          <a-descriptions title="内存 / 运行状态" :column="1" size="small" bordered>
            <a-descriptions-item label="堆内存">
              <div class="mem-meter">
                <a-progress
                  :percent="memPercent"
                  :show-info="false"
                  :stroke-color="memPercent > 85 ? '#f5222d' : '#1890ff'"
                  size="small"
                />
                <span class="mem-text">{{ mbText(status.memory.used, status.memory.max) }}</span>
              </div>
            </a-descriptions-item>
            <a-descriptions-item label="非堆内存">{{ mbText(status.memory.nonUsed, -1) }}</a-descriptions-item>
            <a-descriptions-item label="运行中任务">{{ status.spider.runningJobs }}</a-descriptions-item>
            <a-descriptions-item label="调度任务">{{ status.spider.scheduledJobs }}</a-descriptions-item>
            <a-descriptions-item label="爬虫总数">{{ status.spider.totalSpiders }}</a-descriptions-item>
            <a-descriptions-item label="定时调度">{{ status.spider.jobEnable === 'true' ? '已开启' : '已关闭' }}</a-descriptions-item>
            <a-descriptions-item label="线程池实际上限">{{ status.spider.poolMaxThreads }}</a-descriptions-item>
            <a-descriptions-item label="工作目录">{{ status.spider.workspace }}</a-descriptions-item>
          </a-descriptions>
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script>
import { getParamsRequest, saveParamsRequest, getStatusRequest } from '@/api/system.js'

export default {
  name: 'SystemSettings',
  data() {
    return {
      columns: [
        { title: '参数', dataIndex: 'key', width: 200 },
        { title: '说明', dataIndex: 'description', width: 320, scopedSlots: { customRender: 'description' } },
        { title: '当前值', dataIndex: 'value', width: 200, scopedSlots: { customRender: 'value' } },
        { title: '默认值', dataIndex: 'defaultValue', width: 120 },
        { title: '操作', dataIndex: 'operation', width: 60, scopedSlots: { customRender: 'operation' } }
      ],
      params: [],
      original: {},
      status: {},
      paramLoading: false,
      saveLoading: false,
      statusTip: ''
    }
  },
  computed: {
    memPercent() {
      if (!this.status.memory || !this.status.memory.max) return 0
      return Math.round((this.status.memory.used / this.status.memory.max) * 100)
    }
  },
  mounted() {
    this.loadParams()
    this.loadStatus()
    this.timer = setInterval(this.loadStatus, 5000)
  },
  beforeDestroy() {
    clearInterval(this.timer)
  },
  methods: {
    mbText(used, max) {
      if (used == null) return '-'
      const text = (used / 1024 / 1024).toFixed(1) + ' MB'
      return max > 0 ? text + ' / ' + (max / 1024 / 1024).toFixed(0) + ' MB' : text
    },
    loadParams() {
      this.paramLoading = true
      getParamsRequest(
        data => {
          this.params = (data && data.data) || []
          // 记录加载时的值，保存时只提交被改过的参数，避免把默认值写成覆盖项
          this.original = {}
          this.params.forEach(item => {
            this.original[item.key] = item.value
          })
          this.paramLoading = false
        },
        () => {
          this.paramLoading = false
          this.$message.error('获取系统参数失败')
        }
      )
    },
    saveParams() {
      const body = {}
      this.params.forEach(item => {
        if (this.original[item.key] !== item.value) {
          body[item.key] = item.value
        }
      })
      if (Object.keys(body).length === 0) {
        this.$message.info('没有需要保存的修改')
        return
      }
      this.saveLoading = true
      saveParamsRequest(
        body,
        data => {
          this.saveLoading = false
          this.$message.success((data && data.message) || '保存成功，已立即生效')
          this.loadParams()
        },
        data => {
          this.saveLoading = false
          this.$message.error((data && data.message) || '保存失败')
        }
      )
    },
    resetParam(record) {
      record.value = record.defaultValue
      saveParamsRequest(
        { [record.key]: '' },
        data => {
          this.$message.success((data && data.message) || '已重置')
          this.loadParams()
        },
        () => this.$message.error('重置失败')
      )
    },
    loadStatus() {
      getStatusRequest(
        data => {
          this.status = (data && data.data) || {}
          this.statusTip = '更新于 ' + new Date().toLocaleTimeString()
        },
        () => {
          this.statusTip = '状态获取失败'
        }
      )
    }
  }
}
</script>

<style scoped>
.system-settings {
  /* 左右至少留够 8px，否则 a-row 的负边距会把内容顶出容器产生横向滚动 */
  padding: 8px;
}
.mem-meter {
  display: flex;
  align-items: center;
}
.mem-meter .ant-progress {
  flex: 1;
  margin-right: 8px;
}
.mem-text {
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
}
</style>
