<template>
  <a-tabs size="small" v-model="currentKey">
    <a-tab-pane key="baseConfig" tab="基本配置">
      <a-form :label-col="formItemLayout.labelCol" :wrapper-col="formItemLayout.wrapperCol">
        <a-row>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">节点名称<help-tip name="节点名称" /></span>
              <a-input :size="size" :defaultValue="cell.value" @change="e => (cell.value = e.target.value)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">循环变量<help-tip name="循环变量" /></span>
              <a-input :size="size" :defaultValue="getCellValue('loopVariableName')" @change="e => setCellValue('loopVariableName', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">循环次数<help-tip name="循环次数" /></span>
              <a-input :size="size" :defaultValue="getCellValue('loopCount')" @change="e => setCellValue('loopCount', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">请求方法<help-tip name="请求方法" /></span>
              <a-select :size="size" :defaultValue="getCellValue('method', 'GET')" @change="val => setCellValue('method', val)">
                <a-select-option value="GET">GET</a-select-option>
                <a-select-option value="POST">POST</a-select-option>
                <a-select-option value="PUT">PUT</a-select-option>
                <a-select-option value="DELETE">DELETE</a-select-option>
                <a-select-option value="PATCH">PATCH</a-select-option>
                <a-select-option value="HEAD">HEAD</a-select-option>
                <a-select-option value="OPTIONS">OPTIONS</a-select-option>
                <a-select-option value="TRACE">TRACE</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="16">
            <a-form-item :labelCol="{ span: 4 }" :wrapperCol="{ span: 20 }"><span slot="label">URL<help-tip name="URL" /></span>
              <a-input :size="size" :defaultValue="getCellValue('url')" @change="e => setCellValue('url', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">延迟时间<help-tip name="延迟时间" /></span>
              <a-input :size="size" :defaultValue="getCellValue('sleep')" @change="e => setCellValue('sleep', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">超时时间<help-tip name="超时时间" /></span>
              <a-input :size="size" :defaultValue="getCellValue('timeout')" @change="e => setCellValue('timeout', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">页面编码<help-tip name="页面编码" /></span>
              <a-input :size="size" :defaultValue="getCellValue('response-charset')" @change="e => setCellValue('response-charset', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">重试次数<help-tip name="重试次数" /></span>
              <a-input :size="size" :defaultValue="getCellValue('retryCount')" @change="e => setCellValue('retryCount', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">重试间隔<help-tip name="重试间隔" /></span>
              <a-input :size="size" :defaultValue="getCellValue('retryInterval')" @change="e => setCellValue('retryInterval', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="formItemLayout.colSpan">
            <a-form-item><span slot="label">代理<help-tip name="代理" /></span>
              <a-input :size="size" :defaultValue="getCellValue('proxy')" @change="e => setCellValue('proxy', e)" />
            </a-form-item>
          </a-col>
          <a-col :span="16">
            <a-form-item :labelCol="{ span: 4 }" :wrapperCol="{ span: 20 }"><span slot="label">请求设置<help-tip name="请求设置" /></span>
              <spider-select :editor="editor" :cell="cell" :options="requestConfig" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-tab-pane>
    <a-tab-pane key="paramsConfig" tab="参数">
      <table-and-modal :editor="editor" :cell="cell" :config="paramsConfig"></table-and-modal>
    </a-tab-pane>
    <a-tab-pane key="cookieConfig" tab="Cookie">
      <table-and-modal :editor="editor" :cell="cell" :config="cookieConfig"></table-and-modal>
    </a-tab-pane>
    <a-tab-pane key="headerConfig" tab="Header">
      <table-and-modal :editor="editor" :cell="cell" :config="headerConfig"></table-and-modal>
    </a-tab-pane>
    <a-tab-pane key="bodyConfig" tab="Body">
      <a-form layout="horizontal" :label-col="{ span: 2 }" :wrapper-col="{ span: 22 }">
        <a-form-item><span slot="label">类型<help-tip name="类型" /></span>
          <a-select :size="size" v-model="bodyTypeValue">
            <a-select-option value="none">none</a-select-option>
            <a-select-option value="form-data">form-data</a-select-option>
            <a-select-option value="raw">raw</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="bodyTypeValue === 'raw'"><span slot="label">Content-Type<help-tip name="Content-Type" /></span>
          <a-select :size="size" :defaultValue="getCellValue('body-content-type', 'text/plain')" @change="val => setCellValue('body-content-type', val)">
            <a-select-option value="text/plain">text/plain</a-select-option>
            <a-select-option value="application/json">application/json</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="bodyTypeValue === 'raw'"><span slot="label">内容<help-tip name="内容" /></span>
          <code-editor height="100px" :value="getCellValue('request-body')" @change="val => setCellValue('request-body', val)"></code-editor>
        </a-form-item>
        <table-and-modal v-if="bodyTypeValue === 'form-data'" :batchAddBtn="false" :editor="editor" :cell="cell" :config="bodyParameterConfig"></table-and-modal>
      </a-form>
    </a-tab-pane>
  </a-tabs>
</template>

<script>
import mixins from '../mixins/spider-mixins.js'
import TableAndModal from '../components/tableAndModal.vue'
import SpiderSelect from '../components/spiderSelect.vue'
import data from './root-data.json'
import CodeEditor from '@/components/code-editor'

export default {
  mixins: [mixins],
  components: {
    TableAndModal,
    CodeEditor,
    SpiderSelect
  },
  props: {
    editor: Object,
    cell: Object
  },
  data() {
    return {
      currentKey: 'baseConfig',
      requestConfig: [
        {
          label: '跟随重定向',
          value: 'follow-redirect',
          defaultVal: '1'
        },
        {
          label: 'TLS证书验证',
          value: 'tls-validate',
          defaultVal: '1'
        },
        {
          label: '自动管理Cookie',
          value: 'cookie-auto-set',
          defaultVal: '1'
        },
        {
          label: '自动去重',
          value: 'repeat-enable',
          defaultVal: '0'
        },
        {
          label: '重复不执行后续节点',
          value: 'repeat-ignore-execute',
          defaultVal: '0'
        }
      ],
      paramsConfig: data.globalParamsConfig,
      cookieConfig: data.globalCookieConfig,
      headerConfig: data.globalHeaderConfig,
      bodyTypeValue: '',
      bodyParameterConfig: {
        title: '参数',
        keyPrefix: 'parameter-form',
        keys: ['parameter-form-name', 'parameter-form-type', 'parameter-form-filename', 'parameter-form-value', 'parameter-form-description'],
        columns: [
          {
            title: '参数名',
            dataIndex: 'parameter-form-name',
            width: 150,
            scopedSlots: {
              customRender: 'parameter-form-name'
            }
          },
          {
            title: '参数类型',
            dataIndex: 'parameter-form-type',
            width: 90,
            scopedSlots: {
              customRender: 'parameter-form-type'
            },
            inputType: 'select',
            selectMap: {
              text: 'text',
              file: 'file'
            },
            selectDefault: 'text'
          },
          {
            title: '文件名',
            dataIndex: 'parameter-form-filename',
            width: 200,
            scopedSlots: {
              customRender: 'parameter-form-filename'
            }
          },
          {
            title: '参数值',
            dataIndex: 'parameter-form-value',
            scopedSlots: {
              customRender: 'parameter-form-value'
            }
          },
          {
            title: '参数描述',
            dataIndex: 'parameter-form-description',
            width: 200,
            scopedSlots: {
              customRender: 'parameter-form-description'
            }
          },
          {
            title: '操作',
            dataIndex: 'operation',
            width: 150,
            scopedSlots: {
              customRender: 'operation'
            }
          }
        ]
      }
    }
  },
  watch: {
    bodyTypeValue(newVal, oldVal) {
      this.setCellValue('body-type', newVal)
    }
  },
  mounted() {
    this.bodyTypeValue = this.getCellValue('body-type', 'none')
  }
}
</script>
