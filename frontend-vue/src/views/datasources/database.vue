<template>
  <a-drawer :visible="visible" :width="1100" placement="right" :destroy-on-close="true" @close="close">
    <template slot="title">
      数据库详情
      <span class="datasource-name">{{ datasourceName }}</span>
    </template>
    <a-alert class="tips" type="warning" show-icon message="SQL 执行直接作用在该数据源上，且当前服务未开启登录；单次最多返回 500 行、30 秒超时。" />
    <div class="filter-bar">
      <a-space>
        <span>数据库</span>
        <a-select
          class="filter-item"
          size="small"
          allowClear
          :loading="catalogLoading"
          v-model="catalog"
          placeholder="连接默认库"
          not-found-content="该数据源没有可选数据库"
          @change="catalogChanged"
        >
          <a-select-option v-for="item in listCatalog" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <span>schema</span>
        <a-select
          class="filter-item"
          size="small"
          allowClear
          :loading="schemaLoading"
          v-model="schema"
          placeholder="连接默认模式"
          not-found-content="该数据库没有独立模式"
          @change="loadTables"
        >
          <a-select-option v-for="item in listSchema" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <span>表名</span>
        <a-input
          class="filter-item"
          size="small"
          placeholder="按名称模糊搜索"
          v-model="tableName"
          @pressEnter="loadTables"
        >
          <a-button slot="addonAfter" size="small" :loading="tableLoading" @click="loadTables">搜索</a-button>
        </a-input>
      </a-space>
    </div>
    <a-row :gutter="16">
      <a-col :span="7">
        <a-table
          size="small"
          rowKey="name"
          :loading="tableLoading"
          :columns="tableColumns"
          :data-source="listTable"
          :customRow="tableRow"
          :rowClassName="tableRowClass"
          :pagination="{ size: 'small', pageSize: 20, showTotal: showTotal }"
          :scroll="{ y: 460 }"
        />
        <div class="table-count">
          共 {{ listTable.length }} 个{{ listTable.length >= 500 ? '（已达上限，请缩小范围）' : '' }}
        </div>
      </a-col>
      <a-col :span="17">
        <a-tabs size="small" v-model="activeTab">
          <a-tab-pane key="columns" tab="表结构">
            <div class="panel-title">{{ selectTable || '请选择左侧的表' }}</div>
            <a-table
              size="small"
              rowKey="name"
              :loading="columnLoading"
              :columns="columnColumns"
              :data-source="detail.columns"
              :pagination="false"
              :scroll="{ y: 460 }"
            />
          </a-tab-pane>
          <a-tab-pane key="indexes" tab="索引">
            <div class="panel-title">{{ selectTable || '请选择左侧的表' }}</div>
            <a-table
              size="small"
              rowKey="rowKey"
              :columns="indexColumns"
              :data-source="indexRows"
              :pagination="false"
              :scroll="{ y: 460 }"
            />
          </a-tab-pane>
          <a-tab-pane key="sql" tab="SQL执行">
            <div class="sql-toolbar">
              <a-button size="small" type="primary" :loading="sqlLoading" @click="executeAction">执行</a-button>
              <span class="sql-scope">{{ scopeText }}</span>
            </div>
            <code-editor height="200px" :value="sql" :option="{ language: 'sql' }" @change="val => (sql = val)" />
            <template v-if="result !== null">
              <div class="result-info">
                <span v-if="result.type === 'query'">返回 {{ result.rows.length }} 行{{ result.truncated ? '（已截断，最多 500 行）' : '' }}</span>
                <span v-else>影响 {{ result.count }} 行</span>
                <span class="elapsed">耗时 {{ elapsed }} ms</span>
              </div>
              <a-table
                v-if="result.type === 'query'"
                size="small"
                :columns="resultColumns"
                :data-source="resultRows"
                :row-key="(record, index) => index"
                :pagination="{ size: 'small', pageSize: 20, showTotal: showTotal }"
                :scroll="{ x: true, y: 300 }"
              />
            </template>
          </a-tab-pane>
        </a-tabs>
      </a-col>
    </a-row>
  </a-drawer>
</template>

<script>
import { catalogRequest, schemaRequest, tableRequest, columnRequest, executeSqlRequest } from '@/api/datasources.js'
import CodeEditor from '@/components/code-editor'

export default {
  name: 'databaseDetail',
  components: {
    CodeEditor
  },
  data() {
    return {
      visible: false,
      datasourceId: null,
      datasourceName: '',
      catalog: undefined,
      schema: undefined,
      tableName: '',
      catalogLoading: false,
      schemaLoading: false,
      tableLoading: false,
      columnLoading: false,
      sqlLoading: false,
      listCatalog: [],
      listSchema: [],
      listTable: [],
      selectTable: '',
      activeTab: 'columns',
      detail: {
        columns: [],
        indexes: []
      },
      sql: '',
      result: null,
      elapsed: 0,
      tableColumns: [
        {
          title: '名称',
          dataIndex: 'name',
          ellipsis: true
        },
        {
          title: '类型',
          dataIndex: 'type',
          width: 80
        }
      ],
      columnColumns: [
        {
          title: '列名',
          dataIndex: 'name',
          width: 160,
          ellipsis: true
        },
        {
          title: '类型',
          dataIndex: 'typeName',
          width: 110
        },
        {
          title: '长度',
          dataIndex: 'size',
          width: 70
        },
        {
          title: '可空',
          dataIndex: 'nullable',
          width: 60,
          customRender: value => (value ? '是' : '否')
        },
        {
          title: '主键',
          dataIndex: 'primaryKey',
          width: 60,
          customRender: value => (value ? '是' : '')
        },
        {
          title: '默认值',
          dataIndex: 'defaultValue',
          width: 110,
          ellipsis: true
        },
        {
          title: '说明',
          dataIndex: 'remarks',
          ellipsis: true
        }
      ],
      indexColumns: [
        {
          title: '索引名',
          dataIndex: 'name',
          width: 200,
          ellipsis: true
        },
        {
          title: '唯一',
          dataIndex: 'unique',
          width: 60,
          customRender: value => (value ? '是' : '否')
        },
        {
          title: '列',
          dataIndex: 'column',
          width: 200,
          ellipsis: true
        },
        {
          title: '排序',
          dataIndex: 'ascending',
          width: 80,
          customRender: value => (value === null ? '' : value ? '升序' : '降序')
        }
      ]
    }
  },
  computed: {
    indexRows() {
      return (this.detail.indexes || []).map((index, position) => {
        return Object.assign({ rowKey: String(position) }, index)
      })
    },
    // 结果集的列名可能重复，用 c0/c1 作为稳定的 dataIndex
    resultColumns() {
      if (!this.result || this.result.type !== 'query') {
        return []
      }
      return this.result.columns.map((label, position) => {
        return {
          title: label,
          dataIndex: 'c' + position,
          key: 'c' + position,
          width: 160,
          ellipsis: true
        }
      })
    },
    resultRows() {
      if (!this.result || this.result.type !== 'query') {
        return []
      }
      return this.result.rows.map(row => {
        const record = {}
        row.forEach((value, position) => {
          record['c' + position] = value
        })
        return record
      })
    },
    scopeText() {
      const scope = [this.catalog, this.schema].filter(item => item).join('.')
      return '执行范围：' + (scope || '数据源默认库')
    }
  },
  methods: {
    showDatabase(id, name) {
      this.datasourceId = id
      this.datasourceName = name || ''
      this.visible = true
      this.loadCatalog()
      this.loadSchema()
      this.loadTables()
    },
    close() {
      this.visible = false
      this.catalog = undefined
      this.schema = undefined
      this.tableName = ''
      this.listCatalog = []
      this.listSchema = []
      this.listTable = []
      this.selectTable = ''
      this.activeTab = 'columns'
      this.detail = { columns: [], indexes: [] }
      this.sql = ''
      this.result = null
    },
    params() {
      return {
        datasourceId: this.datasourceId,
        catalog: this.catalog || '',
        schema: this.schema || '',
        tableName: this.tableName || ''
      }
    },
    // 元数据接口失败时返回 code=0，这里统一取值并提示
    pickData(data, defaultValue) {
      if (data && data.code === 1) {
        return data.data || defaultValue
      }
      this.$message.error((data && data.message) || '读取失败')
      return defaultValue
    },
    loadCatalog() {
      this.catalogLoading = true
      catalogRequest(
        this.datasourceId,
        data => {
          this.catalogLoading = false
          this.listCatalog = this.pickData(data, [])
        },
        () => {
          this.catalogLoading = false
        }
      )
    },
    loadSchema() {
      this.schemaLoading = true
      schemaRequest(
        {
          datasourceId: this.datasourceId,
          catalog: this.catalog || ''
        },
        data => {
          this.schemaLoading = false
          this.listSchema = this.pickData(data, [])
        },
        () => {
          this.schemaLoading = false
        }
      )
    },
    loadTables() {
      this.tableLoading = true
      tableRequest(
        this.params(),
        data => {
          this.tableLoading = false
          this.listTable = this.pickData(data, [])
        },
        () => {
          this.tableLoading = false
        }
      )
    },
    loadColumns(tableName) {
      const params = this.params()
      params.tableName = tableName
      this.columnLoading = true
      columnRequest(
        params,
        data => {
          this.columnLoading = false
          this.detail = Object.assign({ columns: [], primaryKeys: [], indexes: [] }, this.pickData(data, {}))
        },
        () => {
          this.columnLoading = false
        }
      )
    },
    // 换了库，原来的模式不一定存在；表列表也要跟着重查
    catalogChanged() {
      this.schema = undefined
      this.loadSchema()
      this.loadTables()
    },
    tableRow(record) {
      return {
        on: {
          click: () => {
            this.selectTable = record.name
            this.activeTab = 'columns'
            this.loadColumns(record.name)
            // 空白SQL框才填入，避免覆盖正在编辑的语句
            if (!this.sql) {
              this.sql = 'select * from ' + record.name + ' limit 100'
            }
          }
        }
      }
    },
    tableRowClass(record) {
      return record.name === this.selectTable ? 'table-row-active' : ''
    },
    executeAction() {
      const sql = (this.sql || '').trim()
      if (!sql) {
        this.$message.warning('请先输入SQL')
        return
      }
      const params = this.params()
      params.sql = sql
      const start = new Date().getTime()
      this.sqlLoading = true
      executeSqlRequest(
        params,
        data => {
          this.sqlLoading = false
          this.elapsed = new Date().getTime() - start
          if (data && data.code === 1) {
            this.result = data.data
            return
          }
          this.result = null
          this.$message.error((data && data.message) || '执行失败')
        },
        () => {
          this.sqlLoading = false
        }
      )
    },
    showTotal(total, range) {
      return range[0] + '-' + range[1] + ' 共 ' + total + ' 条'
    }
  }
}
</script>

<style lang="less" scoped>
.datasource-name {
  margin-left: 8px;
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  font-weight: normal;
}
.tips {
  margin-bottom: 12px;
}
.filter-bar {
  margin-bottom: 12px;
}
.filter-item {
  width: 200px;
}
.panel-title {
  margin-bottom: 8px;
  font-weight: bold;
}
.table-count {
  margin-top: 8px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
.sql-toolbar {
  margin-bottom: 8px;
}
.sql-scope {
  margin-left: 12px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
.result-info {
  margin: 8px 0;
  font-size: 12px;
}
.elapsed {
  margin-left: 12px;
  color: rgba(0, 0, 0, 0.45);
}
/deep/ .table-row-active td {
  background: #e6f7ff;
}
</style>
