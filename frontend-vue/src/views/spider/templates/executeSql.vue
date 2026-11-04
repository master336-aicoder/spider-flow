<template>
  <a-tabs size="small">
    <a-tab-pane key="config" tab="配置">
      <a-form :label-col="formItemLayout.labelCol" :wrapper-col="formItemLayout.wrapperCol">
        <a-row>
          <a-col :span="6">
            <a-form-item><span slot="label">节点名称<help-tip name="节点名称" /></span>
              <a-input placeholder="请输入节点名称" :size="size" :defaultValue="cell.value" @change="e => (cell.value = e.target.value)" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">数据源<help-tip name="数据源" /></span>
              <a-select :size="size" :defaultValue="getCellValue('datasourceId')" @change="val => setCellValue('datasourceId', val)">
                <a-select-option v-for="item in listDatabase" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">语句类型<help-tip name="语句类型" /></span>
              <a-select :size="size" :defaultValue="getCellValue('statementType', 'select')" @change="val => setCellValue('statementType', val)">
                <a-select-option value="select">select</a-select-option>
                <a-select-option value="selectOne">selectOne</a-select-option>
                <a-select-option value="selectInt">selectInt</a-select-option>
                <a-select-option value="insert">insert</a-select-option>
                <a-select-option value="delete">delete</a-select-option>
                <a-select-option value="update">update</a-select-option>
                <a-select-option value="insertofPk">insertofPk</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">其它<help-tip name="其它" /></span>
              <spider-select :editor="editor" :cell="cell" :options="selectConfig" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item :label-col="{ span: 2 }" :wrapper-col="{ span: 22 }"><span slot="label">SQL语句<help-tip name="SQL语句" /></span>
              <code-editor :value="getCellValue('sql')" height="200px" :option="{ language: 'sql' }" @change="val => setCellValue('sql', val)" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-tab-pane>
  </a-tabs>
</template>

<script>
import mixins from '../mixins/spider-mixins.js'
import { allRequest } from '@/api/datasources.js'
import SpiderSelect from '../components/spiderSelect.vue'
import CodeEditor from '@/components/code-editor'
export default {
  mixins: [mixins],
  components: {
    SpiderSelect,
    CodeEditor
  },
  props: {
    editor: Object,
    cell: Object
  },
  data() {
    return {
      listDatabase: [],
      selectConfig: [
        {
          label: '输出到sqlRowSet流',
          value: 'isStream',
          defaultVal: '0'
        }
      ]
    }
  },
  methods: {
    getAllDatabase() {
      allRequest(data => {
        this.listDatabase = data
      })
    }
  },
  mounted() {
    this.getAllDatabase()
  }
}
</script>
