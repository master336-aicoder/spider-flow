<template>
  <a-card :body-style="{ padding: '12px' }">
    <div class="log-toolbar">
      <a-space>
        <a-input-search
          v-model="keywords"
          placeholder="关键字，支持正则"
          style="width: 220px"
          enter-button="搜索"
          @search="search"
        />
        <a-tooltip title="搜索时区分大小写">
          <a-checkbox v-model="matchcase">区分大小写</a-checkbox>
        </a-tooltip>
        <a-tooltip title="把关键字当作正则表达式">
          <a-checkbox v-model="regx">正则</a-checkbox>
        </a-tooltip>
        <a-select v-model="maxLines" style="width: 110px" @change="load">
          <a-select-option v-for="n in [50, 100, 200, 500]" :key="n" :value="n">{{ n }} 行/屏</a-select-option>
        </a-select>
        <a-radio-group v-model="newestFirst" size="small">
          <a-radio-button :value="true">最新在上</a-radio-button>
          <a-radio-button :value="false">最旧在上</a-radio-button>
        </a-radio-group>
        <a-button icon="reload" @click="load">刷新</a-button>
        <a-button @click="goHead">跳到开头</a-button>
        <a-button @click="goTail">跳到最新</a-button>
        <a-button @click="prev">上一页</a-button>
        <a-button @click="next">下一页</a-button>
        <a-tooltip title="每 2 秒读取新写入的日志并追加">
          <span>自动刷新<a-switch v-model="autoRefresh" style="margin-left: 6px" /></span>
        </a-tooltip>
        <a-button icon="download" @click="download">下载日志</a-button>
      </a-space>
      <span class="log-position">{{ positionText }}</span>
    </div>
    <a-spin :spinning="loading">
      <div class="log-body" ref="body">
        <div v-for="(line, i) in visibleLines" :key="line.from + '-' + i" class="log-row">
          <span
            v-for="(part, j) in highlight(line.text)"
            :key="j"
            :class="part.hit ? 'log-hit' : 'log-plain'"
          >{{ part.t }}</span>
        </div>
        <div v-if="!loading && visibleLines.length === 0" class="log-empty">暂无日志内容</div>
      </div>
    </a-spin>
  </a-card>
</template>

<script>
import { logRequest, logDownloadRequest } from '@/api/spider.js'

// 单次向后端取的行数，够翻好几屏，减少请求次数
const BUFFER_FACTOR = 10

export default {
  data() {
    return {
      flowId: '',
      taskId: '',
      buffer: [],
      // 已渲染窗口的起始下标，buffer 始终按时间正序
      displayIndex: 0,
      // 读取锚点，-1 表示从文件末尾开始
      index: -1,
      // 本次读取方向：true 从锚点向更旧读，false 从锚点向更新读
      readReversed: true,
      keywords: '',
      matchcase: true,
      regx: false,
      newestFirst: true,
      maxLines: 100,
      loading: false,
      autoRefresh: false,
      timer: null
    }
  },
  computed: {
    visibleLines() {
      const lines = this.buffer.slice(this.displayIndex, this.displayIndex + this.maxLines)
      return this.newestFirst ? lines.slice().reverse() : lines
    },
    positionText() {
      if (this.buffer.length === 0) {
        return '共 0 行'
      }
      const end = Math.min(this.displayIndex + this.maxLines, this.buffer.length)
      return `第 ${this.displayIndex + 1}-${end} 行 / 已加载 ${this.buffer.length} 行`
    }
  },
  watch: {
    autoRefresh(value) {
      this.clearTimer()
      if (value) {
        this.timer = setInterval(this.tail, 2000)
      }
    }
  },
  methods: {
    fetch(count, callback) {
      this.loading = true
      logRequest({
        id: this.flowId,
        taskId: this.taskId,
        index: this.index,
        count: count,
        keywords: this.keywords,
        matchcase: this.matchcase,
        regx: this.regx,
        reversed: this.readReversed
      }, res => {
        this.loading = false
        const lines = (res && res.data) || []
        if (lines.length > 0) {
          this.buffer = lines
        }
        callback(lines.length > 0)
      }, () => {
        this.loading = false
        callback(false)
      })
    },
    // 以当前锚点重新加载一整块
    load() {
      this.fetch(BUFFER_FACTOR * this.maxLines, hasData => {
        if (!hasData) {
          this.$message.info('没有更多日志')
          return
        }
        this.displayIndex = this.readReversed ? Math.max(this.buffer.length - this.maxLines, 0) : 0
      })
    },
    // 关键字命中行由后端定位，锚点取屏幕边缘，使命中行落在屏幕起始处
    search() {
      if (this.buffer.length > 0) {
        if (this.newestFirst) {
          this.index = this.buffer[this.displayIndex].from
          this.readReversed = true
        } else {
          this.index = this.buffer[Math.min(this.displayIndex + this.maxLines, this.buffer.length) - 1].to
          this.readReversed = false
        }
      }
      this.load()
    },
    prev() {
      this.move(this.newestFirst)
    },
    next() {
      this.move(!this.newestFirst)
    },
    // towardsNewer 为真表示翻向更新的日志，翻出已加载范围时以对应边缘为锚点重新取块
    move(towardsNewer) {
      if (this.buffer.length === 0) {
        this.goTail()
        return
      }
      if (towardsNewer) {
        const end = this.displayIndex + this.maxLines
        if (end < this.buffer.length) {
          this.displayIndex = end
          return
        }
        this.index = this.buffer[this.buffer.length - 1].to
        this.readReversed = false
      } else {
        if (this.displayIndex > 0) {
          this.displayIndex = Math.max(this.displayIndex - this.maxLines, 0)
          return
        }
        this.index = this.buffer[0].from
        this.readReversed = true
      }
      this.load()
    },
    goHead() {
      this.index = 0
      this.readReversed = false
      this.load()
    },
    goTail() {
      this.index = -1
      this.readReversed = true
      this.load()
    },
    // 尾部跟随：只取锚点之后的新增内容
    tail() {
      if (this.loading || this.buffer.length === 0) {
        this.load()
        return
      }
      const last = this.buffer[this.buffer.length - 1]
      this.loading = true
      logRequest({
        id: this.flowId,
        taskId: this.taskId,
        index: last.to,
        count: this.maxLines,
        keywords: this.keywords,
        matchcase: this.matchcase,
        regx: this.regx,
        reversed: false
      }, res => {
        this.loading = false
        const lines = (res && res.data) || []
        if (lines.length === 0) {
          return
        }
        this.buffer = this.buffer.concat(lines)
        if (this.buffer.length > BUFFER_FACTOR * this.maxLines) {
          this.buffer = this.buffer.slice(this.buffer.length - BUFFER_FACTOR * this.maxLines)
        }
        this.displayIndex = Math.max(this.buffer.length - this.maxLines, 0)
      }, () => {
        this.loading = false
      })
    },
    escape(text) {
      return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    },
    // 拆成命中/非命中片段渲染，避免用 v-html 插入日志内容
    highlight(text) {
      if (!this.keywords) {
        return [{ t: text, hit: false }]
      }
      let pattern
      try {
        pattern = new RegExp(this.regx ? this.keywords : this.escape(this.keywords), this.matchcase ? 'g' : 'gi')
      } catch (error) {
        return [{ t: text, hit: false }]
      }
      const parts = []
      let cursor = 0
      let matched
      while ((matched = pattern.exec(text)) !== null) {
        if (matched[0].length === 0) {
          pattern.lastIndex++
          continue
        }
        if (matched.index > cursor) {
          parts.push({ t: text.substring(cursor, matched.index), hit: false })
        }
        parts.push({ t: matched[0], hit: true })
        cursor = matched.index + matched[0].length
      }
      parts.push({ t: text.substring(cursor), hit: false })
      return parts
    },
    download() {
      logDownloadRequest(this.flowId, this.taskId)
    },
    clearTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    }
  },
  mounted() {
    this.flowId = this.$route.params.flowId
    this.taskId = this.$route.params.taskId
    this.load()
  },
  beforeDestroy() {
    this.clearTimer()
  }
}
</script>

<style scoped>
.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.log-position {
  color: #999;
  font-size: 12px;
}

.log-body {
  height: 60vh;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 20px;
  padding: 8px;
}

.log-row {
  white-space: pre-wrap;
  word-break: break-all;
}

.log-hit {
  background: #ffd591;
  color: #303133;
}

.log-empty {
  color: #999;
  text-align: center;
  padding: 20px 0;
}
</style>
