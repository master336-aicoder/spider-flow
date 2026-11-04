<template>
  <div class="bar-trend">
    <svg
      :viewBox="'0 0 ' + width + ' ' + height"
      class="bar-trend-svg"
      preserveAspectRatio="xMidYMid meet"
    >
      <!-- 刻度线 -->
      <g v-for="(tick, index) in ticks" :key="'tick-' + index">
        <line
          :x1="paddingLeft"
          :x2="width - paddingRight"
          :y1="yOf(tick)"
          :y2="yOf(tick)"
          stroke="#f0f0f0"
          stroke-width="1"
        ></line>
        <text :x="paddingLeft - 6" :y="yOf(tick) + 4" class="axis-text" text-anchor="end">{{ tickText(tick) }}</text>
      </g>
      <!-- 任务柱：浅色为已结束，深色为进行中 -->
      <g v-for="(item, index) in data" :key="'bar-' + item.date">
        <rect
          :x="barX(index)"
          :y="yOf(item.total)"
          :height="Math.max(baseline - yOf(item.total), item.total > 0 ? 2 : 0)"
          :width="barWidth"
          :fill="item.running > 0 ? '#91d5ff' : '#1890ff'"
          rx="2"
        >
          <title>{{ item.date }}：共 {{ item.total }} 次，进行中 {{ item.running }}</title>
        </rect>
        <rect
          v-if="item.running > 0"
          :x="barX(index)"
          :y="yOf(item.running)"
          :height="Math.max(baseline - yOf(item.running), 2)"
          :width="barWidth"
          fill="#fa8c16"
          rx="2"
        >
          <title>{{ item.date }}：进行中 {{ item.running }}</title>
        </rect>
        <text
          v-if="showLabel(index)"
          :x="barX(index) + barWidth / 2"
          :y="baseline + 16"
          class="axis-text"
          text-anchor="middle"
        >{{ item.date.slice(5) }}</text>
      </g>
      <line :x1="paddingLeft" :x2="width - paddingRight" :y1="baseline" :y2="baseline" stroke="#d9d9d9" stroke-width="1"></line>
    </svg>
    <div class="bar-trend-legend">
      <span><i style="background:#1890ff"></i>已结束</span>
      <span><i style="background:#fa8c16"></i>进行中</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BarTrend',
  props: {
    data: {
      type: Array,
      default() {
        return []
      }
    },
    height: {
      type: Number,
      default: 180
    }
  },
  data() {
    return {
      paddingLeft: 30,
      paddingRight: 10,
      paddingBottom: 24
    }
  },
  computed: {
    width() {
      return Math.max(this.data.length * 34, 320)
    },
    baseline() {
      return this.height - this.paddingBottom
    },
    maxValue() {
      let max = 0
      this.data.forEach(item => {
        max = Math.max(max, Number(item.total) || 0)
      })
      return max === 0 ? 1 : max
    },
    niceMax() {
      const max = this.maxValue
      if (max <= 4) {
        return 4
      }
      // 取到便于阅读的刻度上限
      const half = Math.pow(10, Math.floor(Math.log10(max))) / 2
      return Math.ceil(max / half) * half
    },
    ticks() {
      return [0, this.niceMax / 2, this.niceMax]
    },
    barWidth() {
      return Math.max((this.width - this.paddingLeft - this.paddingRight) / this.data.length - 10, 4)
    }
  },
  methods: {
    tickText(value) {
      return Number.isInteger(value) ? value : value.toFixed(1)
    },
    yOf(value) {
      const ratio = (Number(value) || 0) / this.niceMax
      return this.baseline - ratio * (this.baseline - 10)
    },
    barX(index) {
      const slot = (this.width - this.paddingLeft - this.paddingRight) / this.data.length
      return this.paddingLeft + slot * index + (slot - this.barWidth) / 2
    },
    showLabel(index) {
      // 标签过密时按间隔抽稀，并始终保留最近一天
      const every = Math.ceil(this.data.length / 8)
      return index % every === 0 || index === this.data.length - 1
    }
  }
}
</script>

<style scoped>
.bar-trend-svg {
  width: 100%;
  height: auto;
  display: block;
}
.axis-text {
  font-size: 10px;
  fill: #8c8c8c;
}
.bar-trend-legend {
  text-align: center;
  font-size: 12px;
  color: #8c8c8c;
}
.bar-trend-legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  vertical-align: middle;
}
.bar-trend-legend span {
  margin: 0 8px;
}
</style>
