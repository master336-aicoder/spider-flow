<template>
  <a-tabs size="small">
    <a-tab-pane key="config" tab="配置">
      <a-form :label-col="formItemLayout.labelCol" :wrapper-col="formItemLayout.wrapperCol">
        <a-row>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">节点名称<help-tip name="节点名称" /></span>
              <a-input placeholder="请输入节点名称" :size="size" :defaultValue="cell.value" @change="e => (cell.value = e.target.value)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">次数或集合<help-tip name="次数或集合" /></span>
              <a-input :size="size" :defaultValue="getCellValue('loopCount')" @change="e => setCellValue('loopCount', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">循环变量<help-tip name="循环变量" /></span>
              <a-input placeholder="请输入循环变量(默认item)" :size="size" :defaultValue="getCellValue('loopItem')" @change="e => setCellValue('loopItem', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">下标变量<help-tip name="下标变量" /></span>
              <a-input :size="size" :defaultValue="getCellValue('loopVariableName')" @change="e => setCellValue('loopVariableName', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">开始位置<help-tip name="开始位置" /></span>
              <a-input :size="size" :defaultValue="getCellValue('loopStart', '0')" @change="e => setCellValue('loopStart', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">结束位置<help-tip name="结束位置" /></span>
              <a-input :size="size" :defaultValue="getCellValue('loopEnd', '-1')" @change="e => setCellValue('loopEnd', e)" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-tab-pane>
  </a-tabs>
</template>

<script>
import mixins from '../mixins/spider-mixins.js'
export default {
  mixins: [mixins],
  props: {
    editor: Object,
    cell: Object
  }
}
</script>
