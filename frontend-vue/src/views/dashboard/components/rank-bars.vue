<template>
  <ul class="rank-bars">
    <li v-for="(item, index) in items" :key="item.name + '-' + index" class="rank-row">
      <span class="rank-index" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
      <span class="rank-name" :title="item.name">{{ item.name }}</span>
      <span class="rank-track">
        <i :style="{ width: percent(item) }"></i>
      </span>
      <span class="rank-value">{{ item.text }}</span>
    </li>
    <a-empty v-if="!items.length" :image="simpleImage" />
  </ul>
</template>

<script>
import { Empty } from 'ant-design-vue'

export default {
  name: 'RankBars',
  props: {
    items: {
      type: Array,
      default() {
        return []
      }
    }
  },
  computed: {
    simpleImage() {
      return Empty.PRESENTED_IMAGE_SIMPLE
    },
    maxValue() {
      return this.items.reduce((max, item) => Math.max(max, Number(item.value) || 0), 0)
    }
  },
  methods: {
    percent(item) {
      if (!this.maxValue) {
        return '0%'
      }
      // 保留一点底色，方便看清排名条之间的差距
      return Math.max(((Number(item.value) || 0) / this.maxValue) * 100, 2) + '%'
    }
  }
}
</script>

<style scoped>
.rank-bars {
  list-style: none;
  margin: 0;
  padding: 0;
}
.rank-row {
  display: flex;
  align-items: center;
  line-height: 26px;
  font-size: 12px;
}
.rank-index {
  width: 18px;
  color: #bfbfbf;
  text-align: center;
  margin-right: 6px;
}
.rank-index.is-top {
  color: #fa8c16;
  font-weight: 600;
}
.rank-name {
  width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #595959;
}
.rank-track {
  flex: 1;
  height: 10px;
  background: #f5f5f5;
  border-radius: 5px;
  margin: 0 8px;
}
.rank-track i {
  display: block;
  height: 10px;
  background: linear-gradient(90deg, #69c0ff, #1890ff);
  border-radius: 5px;
}
.rank-value {
  width: 82px;
  text-align: right;
  color: #262626;
}
</style>
