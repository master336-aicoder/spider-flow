<template>
  <a-modal
    :visible="visible"
    title="测试窗口"
    :mask="false"
    :maskClosable="false"
    :width="900"
    @cancel="close"
  >
    <div class="test-toolbar">
      <a-space>
        <a-tag :color="stateColor">{{ stateText }}</a-tag>
        <a-input v-model="logFilter" placeholder="过滤日志" allowClear style="width: 180px" size="small" />
      </a-space>
      <span class="test-count">输出 {{ totalRows }} 行 / 日志 {{ logs.length }} 条</span>
    </div>
    <a-tabs v-model="activeTab">
      <a-tab-pane key="output" tab="输出">
        <a-empty v-if="outputs.length === 0" description="暂无输出，流程里的输出节点执行后在此展示" />
        <a-tabs v-else :active-key="activeOutput" type="card" size="small" @change="key => (activeOutput = key)">
          <a-tab-pane v-for="output in outputs" :key="output.nodeId" :tab="output.name">
            <a-table
              :columns="output.columns"
              :data-source="output.rows"
              :pagination="false"
              size="small"
              :scroll="{ y: 240 }"
              row-key="__index"
            />
          </a-tab-pane>
        </a-tabs>
      </a-tab-pane>
      <a-tab-pane key="log" tab="日志">
        <div class="test-log">
          <div v-for="(item, index) in filteredLogs" :key="index" class="test-log-row" :class="'log-' + item.level">
            <span class="log-level">{{ item.level }}</span>
            <span class="log-time">{{ item.time }}</span>
            <span class="log-text">{{ item.text }}</span>
          </div>
          <div v-if="filteredLogs.length === 0" class="test-log-empty">暂无日志</div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="debug" tab="调试">
        <a-empty v-if="!debugInfo" description="调试模式下，流程会在每个断点处暂停并在此展示变量" />
        <a-descriptions v-else bordered size="small" :column="1">
          <a-descriptions-item label="节点">{{ debugInfo.nodeName }}</a-descriptions-item>
          <a-descriptions-item label="事件">{{ debugInfo.event }}</a-descriptions-item>
          <a-descriptions-item label="变量名">{{ debugInfo.key }}</a-descriptions-item>
          <a-descriptions-item label="变量值">
            <pre class="debug-value">{{ debugInfo.valueText }}</pre>
          </a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>
    </a-tabs>
    <template slot="footer">
      <a-button v-if="state === 'paused'" type="primary" @click="resume">下一步</a-button>
      <a-button v-if="state === 'running'" danger @click="sendStop">停止</a-button>
      <a-button v-if="state === 'finished' || state === 'idle'" type="primary" @click="start(debugMode, true)">重新开始</a-button>
      <a-button @click="close">关闭</a-button>
    </template>
  </a-modal>
</template>

<script>
export default {
  name: 'TestModal',
  props: {
    editor: Object
  },
  data() {
    return {
      visible: false,
      // idle / running / paused / finished
      state: 'idle',
      debugMode: false,
      activeTab: 'output',
      activeOutput: '',
      outputs: [],
      logs: [],
      logFilter: '',
      debugInfo: null,
      socket: null
    }
  },
  computed: {
    stateText() {
      return { idle: '未开始', running: '执行中', paused: '等待下一步', finished: '已结束' }[this.state]
    },
    stateColor() {
      return { idle: '', running: 'blue', paused: 'orange', finished: 'green' }[this.state]
    },
    totalRows() {
      return this.outputs.reduce((sum, item) => sum + item.rows.length, 0)
    },
    filteredLogs() {
      if (!this.logFilter) {
        return this.logs
      }
      const keyword = this.logFilter.toLowerCase()
      return this.logs.filter(item => item.text.toLowerCase().indexOf(keyword) !== -1)
    }
  },
  methods: {
    // detail.vue 校验通过后才调用，debug=true 时流程会在每个节点前暂停
    start(debug, restart) {
      const xml = this.editor.getXML()
      // 后端 loadXMLFromString 解析的是 mxCell 结构，不是 <spider> 根节点
      if (!xml || xml.indexOf('<mxCell') === -1) {
        this.$message.error('流程内容为空，无法测试')
        return
      }
      this.debugMode = debug
      if (restart || this.outputs.length === 0) {
        this.reset()
      }
      this.visible = true
      this.connect(xml, debug)
    },
    reset() {
      this.outputs = []
      this.logs = []
      this.debugInfo = null
      this.activeOutput = ''
      this.activeTab = 'output'
    },
    connect(xml, debug) {
      this.closeSocket()
      const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://'
      this.socket = new WebSocket(protocol + location.host + '/ws')
      this.socket.onopen = () => {
        this.state = 'running'
        this.socket.send(JSON.stringify({ eventType: debug ? 'debug' : 'test', message: xml }))
      }
      this.socket.onerror = () => {
        this.state = 'finished'
        this.$message.error('无法连接测试服务，请确认服务已启动')
      }
      this.socket.onmessage = event => {
        let body
        try {
          body = JSON.parse(event.data)
        } catch (error) {
          return
        }
        this.handle(body)
      }
    },
    handle(event) {
      const type = event.eventType
      const message = event.message
      if (type === 'output') {
        this.appendOutput(message)
      } else if (type === 'log') {
        this.logs.push({ time: event.timestamp, level: (message.level || 'info').toLowerCase(), text: this.formatLog(message) })
        if (this.logs.length > 2000) {
          this.logs.splice(0, this.logs.length - 2000)
        }
      } else if (type === 'debug') {
        this.state = 'paused'
        this.activeTab = 'debug'
        const cell = this.editor.graph.model.cells[message.nodeId]
        this.debugInfo = {
          nodeName: cell ? cell.value : message.nodeId,
          event: message.event,
          key: message.key,
          valueText: this.toText(message.value)
        }
        if (cell) {
          this.editor.selectCell(cell)
        }
      } else if (type === 'finish') {
        this.state = 'finished'
      } else if (type === 'error') {
        this.state = 'finished'
        this.$message.error(typeof message === 'string' ? message : '测试执行出错')
      }
    },
    appendOutput(message) {
      const names = message.outputNames || []
      let output = this.outputs.find(item => item.nodeId === message.nodeId)
      if (!output) {
        const cell = this.editor.graph.model.cells[message.nodeId]
        output = {
          nodeId: message.nodeId,
          name: cell ? cell.value : '输出-' + message.nodeId,
          columns: [{ title: '序号', dataIndex: '__index', width: 60 }].concat(
            names.map((name, i) => ({ title: name, dataIndex: 'c' + i, ellipsis: true }))
          ),
          rows: []
        }
        this.outputs.push(output)
        if (!this.activeOutput) {
          this.activeOutput = output.nodeId
        }
      }
      const row = { __index: output.rows.length + 1 }
      names.forEach((name, i) => {
        row['c' + i] = this.toText(message.values[i])
      })
      output.rows.push(row)
      if (output.rows.length > 500) {
        output.rows.splice(0, output.rows.length - 500)
      }
    },
    // 日志正文里的 {} 由后端按顺序填充变量
    formatLog(message) {
      const parts = (message.message || '').split('{}')
      const variables = message.variables || []
      let text = ''
      parts.forEach((part, i) => {
        text += part
        if (i < parts.length - 1) {
          text += this.toText(variables[i])
        }
      })
      return text
    },
    toText(value) {
      if (value === null || value === undefined) {
        return ''
      }
      if (typeof value === 'object') {
        try {
          return JSON.stringify(value)
        } catch (error) {
          return String(value)
        }
      }
      return String(value)
    },
    resume() {
      this.send({ eventType: 'resume' })
      this.state = 'running'
    },
    sendStop() {
      this.send({ eventType: 'stop' })
      this.state = 'finished'
    },
    send(body) {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.socket.send(JSON.stringify(body))
      }
    },
    closeSocket() {
      if (this.socket) {
        this.socket.onclose = null
        this.socket.close()
        this.socket = null
      }
    },
    close() {
      // 关闭时后端 onClose 会停掉正在跑的测试线程
      this.closeSocket()
      this.visible = false
      this.state = 'idle'
    }
  },
  beforeDestroy() {
    this.closeSocket()
  }
}
</script>

<style scoped>
.test-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.test-count {
  color: #999;
  font-size: 12px;
}

.test-log {
  height: 260px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 20px;
  padding: 8px;
}

.test-log-row {
  white-space: pre-wrap;
  word-break: break-all;
}

.log-level {
  display: inline-block;
  width: 48px;
  color: #858585;
}

.log-time {
  display: inline-block;
  margin-right: 8px;
  color: #858585;
}

.log-error {
  color: #f5222d;
}

.log-warn {
  color: #faad14;
}

.test-log-empty {
  color: #999;
  text-align: center;
  padding: 20px 0;
}

.debug-value {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
</style>
