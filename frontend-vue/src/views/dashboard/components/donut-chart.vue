<template>
  <div class="donut-chart">
    <svg class="donut-svg" viewBox="0 0 100 100">
      <circle cx="50" cy="50" fill="none" r="40" stroke="#f0f0f0" stroke-width="14"></circle>
      <circle
        v-for="segment in segments"
        :key="segment.name"
        :stroke-dasharray="segment.dash + ' ' + (circumference - segment.dash)"
        :stroke-dashoffset="-segment.offset"
        cx="50"
        cy="50"
        fill="none"
        r="40"
        :stroke="segment.color"
        stroke-width="14"
        transform="rotate(-90 50 50)"
      >
        <title>{{ segment.name }}：{{ segment.value }}（{{ segment.percent }}%）</title>
      </circle>
      <text class="donut-total" x="50" y="48">{{ total }}</text>
      <text class="donut-caption" x="50" y="62" text-anchor="middle">{{ caption }}</text>
    </svg>
    <ul class="donut-legend">
      <li v-for="segment in segments" :key="'legend-' + segment.name">
        <i :style="{ background: segment.color }"></i>
        <span class="legend-name">{{ segment.name }}</span>
        <span class="legend-value">{{ segment.value }}</span>
        <span class="legend-percent">{{ segment.percent }}%</span>
      </li>
    </ul>
  </div>
</template>

<script>
export default {
  name: 'DonutChart',
  props: {
    items: {
      type: Array,
      default() {
        return []
      }
    },
    caption: {
      type: String,
      default: '合计'
    }
  },
  computed: {
    circumference() {
      return 2 * Math.PI * 40
    },
    total() {
      return this.items.reduce((sum, item) => sum + (Number(item.value) || 0), 0)
    },
    segments() {
      const colors = ['#1890ff', '#fa8c16', '#52c41a', '#f5222d', '#722ed1', '#13c2c2']
      let offset = 0
      return this.items.map((item, index) => {
        const value = Number(item.value) || 0
        const percent = this.total === 0 ? 0 : Math.round((value / this.total) * 1000) / 10
        const segment = {
          name: item.name,
          value: value,
          percent: percent,
          dash: (value / (this.total || 1)) * this.circumference,
          offset: offset,
          color: item.color || colors[index % colors.length]
        }
        offset += segment.dash
        return segment
      })
    }
  }
}
</script>

<style scoped>
.donut-svg {
  width: 120px;
  height: 120px;
  display: block;
  margin: 0 auto;
}
.donut-total {
  font-size: 20px;
  fill: #262626;
  text-anchor: middle;
}
.donut-caption {
  font-size: 8px;
  fill: #8c8c8c;
}
.donut-legend {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  font-size: 12px;
  color: #595959;
}
.donut-legend li {
  line-height: 22px;
}
.donut-legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 2px;
}
.legend-name {
  display: inline-block;
  min-width: 70px;
}
.legend-value {
  display: inline-block;
  min-width: 40px;
  text-align: right;
  color: #262626;
}
.legend-percent {
  display: inline-block;
  min-width: 48px;
  text-align: right;
  color: #8c8c8c;
}
</style>
