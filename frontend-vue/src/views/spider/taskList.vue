<template>
  <a-card>
    <a-table
      :columns="columns"
      :data-source="data.records"
      :pagination="pagination"
      :scroll="{ x: 960 }"
      @change="handleChange"
      rowKey="id"
    >
      <template slot="status" slot-scope="value, row">
        <a-tag v-if="!row.endTime" color="blue">运行中</a-tag>
        <a-tag v-else>已结束</a-tag>
      </template>
      <template slot="operation" slot-scope="value, row">
        <a @click="goLogPage(value)">查看日志</a>
        <a-divider type="vertical" />
        <a-popconfirm
          v-if="!row.endTime"
          @confirm="stopAction(value)"
          cancel-text="取消"
          ok-text="确定"
          placement="topRight"
          title="确定要停止这次执行吗？"
        >
          <a>停止</a>
          <a-divider type="vertical" />
        </a-popconfirm>
        <a @click="logDownloadAction(value)">下载日志</a>
        <a-divider type="vertical" />
        <a-popconfirm
          @confirm="removeAction(value)"
          cancel-text="取消"
          ok-text="确定"
          placement="topRight"
          title="您确定要删除此记录吗？"
        >
          <a>删除记录</a>
        </a-popconfirm>
      </template>
    </a-table>
  </a-card>
</template>

<script>
import { listRequest, removeRequest, stopRequest } from '@/api/task.js'
import { logDownloadRequest } from '@/api/spider.js'

export default {
  data() {
    return {
      columns: [
        {
          title: '序号',
          width: 60,
          customRender: (text, record, index) => `${index + 1}`
        },
        {
          title: '任务开始时间',
          dataIndex: 'beginTime',
          width: 200
        },
        {
          title: '任务结束时间',
          dataIndex: 'endTime',
          width: 200
        },
        {
          title: '状态',
          dataIndex: 'endTime',
          key: 'status',
          width: 100,
          scopedSlots: { customRender: 'status' }
        },
        {
          title: '操作',
          dataIndex: 'id',
          width: 400,
          scopedSlots: { customRender: 'operation' }
        }
      ],
      data: {
        current: 1,
        pages: 1,
        records: [],
        searchCount: true,
        size: 10,
        total: 1
      },
      queryParam: {
        page: 1,
        limit: 10,
        flowId: ''
      },
      pagination: {
        current: 1,
        pageSize: 10,
        pageSizeOptions: ['10', '20', '30', '40', '50'],
        showTotal: (total, range) => {
          return range[0] + '-' + range[1] + ' 共' + total + '条'
        },
        showQuickJumper: true,
        showSizeChanger: true,
        total: 0
      }
    }
  },
  methods: {
    listAction() {
      listRequest(this.queryParam, data => {
        this.data = data
        this.pagination.total = this.data.total
        this.pagination.pageSize = this.data.size
      })
    },
    removeAction(id) {
      removeRequest(
        id,
        data => {
          this.$message.success(data.message)
          this.listAction()
        },
        data => {
          this.$message.error(data.message)
          this.listAction()
        }
      )
    },
    // 分页切换
    handleChange(pagination) {
      this.pagination = pagination
      this.queryParam.page = this.pagination.current
      this.queryParam.limit = this.pagination.pageSize
      this.listAction()
    },
    // 下载日志文件
    logDownloadAction(taskId) {
      logDownloadRequest(this.queryParam.flowId, taskId)
    },
    // 在线查看日志
    goLogPage(taskId) {
      this.$router.push({ name: 'spider_log', params: { flowId: this.queryParam.flowId, taskId: taskId } })
    },
    // 停止正在执行的任务
    stopAction(id) {
      stopRequest(
        id,
        res => {
          if (res.data) {
            this.$message.success('已发送停止指令')
          } else {
            this.$message.warning('该任务已不在执行，可能已经结束')
          }
          this.listAction()
        },
        res => {
          this.$message.error(res.message)
        }
      )
    }
  },
  mounted() {
    this.queryParam.flowId = this.$route.params.flowId
    this.listAction()
  }
}
</script>
