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
              <a-select :size="size" :defaultValue="getCellValue('datasourceId')" @change="changeDatasource">
                <a-select-option v-for="item in listDatabase" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">数据库<help-tip name="数据库" /></span>
              <a-select :size="size" allowClear :loading="catalogLoading" :defaultValue="getCellValue('catalog')" not-found-content="该数据源没有可选项" @change="changeCatalog">
                <a-select-option v-for="item in listCatalog" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">schema<help-tip name="schema" /></span>
              <a-select :size="size" allowClear :loading="schemaLoading" :defaultValue="getCellValue('schema')" not-found-content="该数据库不支持独立模式" @change="changeSchema">
                <a-select-option v-for="item in listSchema" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">表名<help-tip name="表名" /></span>
              <a-input placeholder="请输入表名，支持 ${} 表达式" :size="size" :defaultValue="getCellValue('tableName')" @change="e => setCellValue('tableName', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">批次大小<help-tip name="批次大小" /></span>
              <a-input placeholder="默认 500" :size="size" :defaultValue="getCellValue('batchSize', '500')" @change="e => setCellValue('batchSize', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item><span slot="label">其它<help-tip name="其它" /></span>
              <spider-select :editor="editor" :cell="cell" :options="selectConfig" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-tab-pane>
  </a-tabs>
</template>

<script>
import mixins from '../mixins/spider-mixins.js'
import { allRequest, catalogRequest, schemaRequest } from '@/api/datasources.js'
import SpiderSelect from '../components/spiderSelect.vue'

export default {
  name: 'autoSQL',
  mixins: [mixins],
  components: {
    SpiderSelect
  },
  props: {
    editor: Object,
    cell: Object
  },
  data() {
    return {
      listDatabase: [],
      listCatalog: [],
      listSchema: [],
      catalogLoading: false,
      schemaLoading: false,
      selectConfig: [
        {
          label: '自动建表',
          value: 'auto-create-table',
          defaultVal: '1'
        }
      ]
    }
  },
  methods: {
    datasourceId() {
      return this.cell.data.get('datasourceId')
    },
    getAllDatabase() {
      allRequest(data => {
        this.listDatabase = data
      })
    },
    loadCatalog() {
      this.listCatalog = []
      const datasourceId = this.datasourceId()
      if (!datasourceId) {
        return
      }
      this.catalogLoading = true
      catalogRequest(
        datasourceId,
        data => {
          this.catalogLoading = false
          this.listCatalog = this.pickList(data)
        },
        () => {
          this.catalogLoading = false
        }
      )
    },
    loadSchema() {
      this.listSchema = []
      const datasourceId = this.datasourceId()
      if (!datasourceId) {
        return
      }
      this.schemaLoading = true
      schemaRequest(
        {
          datasourceId: datasourceId,
          catalog: this.cell.data.get('catalog')
        },
        data => {
          this.schemaLoading = false
          this.listSchema = this.pickList(data)
        },
        () => {
          this.schemaLoading = false
        }
      )
    },
    // 元数据接口失败时返回 code=0，这里统一取列表并提示
    pickList(data) {
      if (data && data.code === 1) {
        return data.data || []
      }
      this.$message.error((data && data.message) || '读取失败')
      return []
    },
    changeDatasource(val) {
      this.setCellValue('datasourceId', val)
      // 换了数据源，原来选的库与模式不一定存在，清掉重选
      this.cell.data.set('catalog', '')
      this.cell.data.set('schema', '')
      this.loadCatalog()
      this.loadSchema()
    },
    changeCatalog(val) {
      this.cell.data.set('catalog', val || '')
      this.cell.data.set('schema', '')
      this.loadSchema()
    },
    changeSchema(val) {
      this.cell.data.set('schema', val || '')
    }
  },
  mounted() {
    this.getAllDatabase()
    this.loadCatalog()
    this.loadSchema()
  }
}
</script>
