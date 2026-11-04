import { grammerRequest } from '@/api/spider.js'

let registered = false
let grammers = null
let loading = null

// 与老版 codemirror 提示保持一致：getXxx 作为属性提示，其余带括号
function normalize(grammer) {
  const method = grammer.method
  if (method) {
    if (method.length > 3 && method.indexOf('get') === 0) {
      return method.substring(3, 4).toLowerCase() + method.substring(4)
    }
    return method + '()'
  }
  return grammer.function
}

function documentation(grammer) {
  const lines = []
  if (grammer.owner) {
    lines.push('所属类：' + grammer.owner)
  }
  if (grammer.comment) {
    lines.push('说明：' + grammer.comment)
  }
  if (grammer.example) {
    lines.push('示例：' + grammer.example)
  }
  if (grammer.returns && grammer.returns.length > 0) {
    lines.push('返回值：' + grammer.returns.join(' / '))
  }
  return lines.join('\n')
}

function load() {
  if (grammers) {
    return Promise.resolve(grammers)
  }
  if (!loading) {
    loading = new Promise(resolve => {
      grammerRequest(
        res => {
          grammers = (res && res.data) || []
          resolve(grammers)
        },
        () => resolve([])
      )
    })
  }
  return loading
}

/**
 * 给 Monaco 注册 spiderflow 表达式补全，数据来自后端 /spider/grammers
 */
export function registerSpiderFlowHint(monaco) {
  if (registered) {
    return
  }
  registered = true
  const provider = {
    triggerCharacters: ['.', '$', '{'],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      }
      return load().then(list => {
        const keyword = word.word.toLowerCase()
        const seen = {}
        const suggestions = []
        list.forEach(grammer => {
          const label = normalize(grammer)
          if (!label || seen[label]) {
            return
          }
          if (keyword && label.toLowerCase().indexOf(keyword) === -1) {
            return
          }
          seen[label] = true
          suggestions.push({
            label: label,
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: label,
            detail: grammer.owner || '',
            documentation: documentation(grammer),
            range: range
          })
        })
        return { suggestions: suggestions }
      })
    }
  }
  monaco.languages.registerCompletionItemProvider('javascript', provider)
  monaco.languages.registerCompletionItemProvider('sql', provider)
}
