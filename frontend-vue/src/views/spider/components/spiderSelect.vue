<template>
  <a-checkbox-group :options="items" :default-value="valueHandle()" @change="valueHandle">
    <template slot="label" slot-scope="option">
      <span>{{ option.text }}<help-tip :name="option.text" /></span>
    </template>
  </a-checkbox-group>
</template>

<script>
import mixins from '../mixins/spider-mixins.js'
/**
 * options:[{
 *        label: '跟随重定向',
 *        value: 'follow-redirect',
 *        defaultVal: '1'
 * }]
 */
export default {
  name: 'tableAndModal',
  mixins: [mixins],
  props: {
    editor: Object,
    cell: Object,
    options: Array
  },
  computed: {
    // label 换成插槽渲染，才能在有说明文案的勾选项后面挂问号图标
    items() {
      return this.options.map(option => ({ value: option.value, text: option.label }))
    }
  },
  methods: {
    // 处理请求设置选中的值和初始化值
    valueHandle(selectValues) {
      let arrs = []
      if (selectValues === undefined) {
        this.options.forEach(element => {
          if (this.getCellValue(element.value, element.defaultVal) === '1') {
            arrs.push(element.value)
            this.setCellValue(element.value, '1')
          } else {
            this.setCellValue(element.value, '0')
          }
        })
      } else {
        this.options.forEach(element => {
          if (selectValues.indexOf(element.value) === -1) {
            this.setCellValue(element.value, '0')
          } else {
            this.setCellValue(element.value, '1')
          }
        })
        this.$emit('change', selectValues)
        this.$emit('input', selectValues)
      }
      return arrs
    }
  },
  mounted() {
    this.bodyTypeValue = this.getCellValue('body-type', 'none')
  }
}
</script>
