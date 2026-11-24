import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// 脚本位于 brands/，仓库根目录是其上一级
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const key = process.env.BRAND

// 未指定品牌时保留仓库内置的中性配置，脚本不做任何写入
if (!key) {
  console.log('[brand] 未设置 BRAND，使用仓库内置的中性配置')
  process.exit(0)
}

const brandFile = join(root, 'brands', key, 'brand.json')

if (!existsSync(brandFile)) {
  console.error(`[brand] 未找到品牌配置：${brandFile}`)
  process.exit(1)
}

let brand
try {
  brand = JSON.parse(readFileSync(brandFile, 'utf8'))
} catch (error) {
  console.error(`[brand] 品牌配置不是合法 JSON：${brandFile}`)
  console.error(error)
  process.exit(1)
}

const variables = brand.variables && typeof brand.variables === 'object' ? brand.variables : {}
const resolveValue = (value) => {
  if (typeof value === 'string') {
    let result = value
    for (let round = 0; round < 10; round += 1) {
      const next = result.replace(/\$\{(\w+)\}/g, (_, name) => {
        const variable = variables[name]
        return variable === undefined || variable === null ? '' : String(variable)
      })
      if (next === result) break
      result = next
    }
    return result
  }
  if (Array.isArray(value)) return value.map(resolveValue)
  if (value && typeof value === 'object') {
    const resolved = {}
    for (const [name, item] of Object.entries(value)) resolved[name] = resolveValue(item)
    return resolved
  }
  return value
}
brand = resolveValue(brand)
delete brand.variables

const required = [
  'appName',
  'merchantAppName',
  'merchantBrand',
  'slogan',
  'merchantSlogan',
  'description',
  'merchantDescription',
  'homeFooter',
  'legalFooter',
  'logoInitial',
  'aboutTitle',
  'aboutContent',
  'version',
  'legal'
]
for (const field of required) {
  if (!brand[field]) {
    console.error(`[brand] 品牌配置缺少字段：${field}`)
    process.exit(1)
  }
}

const apps = [
  {
    dir: join(root, 'frontend/apps/user'),
    appName: brand.appName,
    description: brand.description,
    appid: process.env.WX_USER_APPID || ''
  },
  {
    dir: join(root, 'frontend/apps/merchant'),
    appName: brand.merchantAppName,
    description: brand.merchantDescription,
    appid: process.env.WX_STAFF_APPID || ''
  }
]

for (const app of apps) {
  // 页面配置：只覆盖全局导航标题与用户端首页标题，其余页面标题保持原样
  const pagesFile = join(app.dir, 'src/pages.json')
  const pages = JSON.parse(readFileSync(pagesFile, 'utf8'))
  if (pages.globalStyle) pages.globalStyle.navigationBarTitleText = app.appName
  for (const page of pages.pages || []) {
    if (page.path === 'pages/index/index' && page.style) page.style.navigationBarTitleText = app.appName
  }
  writeFileSync(pagesFile, `${JSON.stringify(pages, null, 2)}\n`, 'utf8')

  // 小程序配置：名称与简介跟随品牌；AppID 仅在显式提供时覆盖
  const manifestFile = join(app.dir, 'src/manifest.json')
  const manifest = JSON.parse(readFileSync(manifestFile, 'utf8'))
  manifest.name = app.appName
  manifest.description = app.description
  manifest['mp-weixin'] = { ...(manifest['mp-weixin'] || {}) }
  if (app.appid) manifest['mp-weixin'].appid = app.appid
  writeFileSync(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')

  // H5 页面标题
  const htmlFile = join(app.dir, 'index.html')
  const html = readFileSync(htmlFile, 'utf8')
  writeFileSync(htmlFile, html.replace(/<title>[\s\S]*?<\/title>/, `<title>${app.appName}</title>`), 'utf8')
}

const generated = `// 由 brands/apply-brand.mjs 根据 brands/${key}/brand.json 生成，请勿手工修改
export interface BrandLegalSection {
  title: string
  items: string[]
}

export interface BrandLegalDoc {
  title: string
  updatedAt: string
  effectiveAt: string
  intro: string
  sections: BrandLegalSection[]
  footer: string
}

export interface BrandConfig {
  key: string
  appName: string
  merchantAppName: string
  merchantBrand: string
  slogan: string
  merchantSlogan: string
  description: string
  merchantDescription: string
  homeFooter: string
  legalFooter: string
  logoInitial: string
  aboutTitle: string
  aboutContent: string
  version: string
  legal: {
    agreement: BrandLegalDoc
    privacy: BrandLegalDoc
  }
}

export const BRAND: BrandConfig = ${JSON.stringify(brand, null, 2)}
`

writeFileSync(join(root, 'frontend/packages/shared/src/brand.generated.ts'), generated, 'utf8')
console.log(`[brand] 已应用品牌配置：${key}`)
