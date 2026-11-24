# 品牌配置

一套品牌配置驱动小程序的名称、页面标题、文案与法务文本，与业务代码解耦。切换品牌只需替换配置并重新构建，不改动页面代码。

## 目录结构

```
brands/
  apply-brand.mjs     生成脚本：读取品牌配置并写入前端
  <brand-key>/
    brand.json        品牌文案与法务文本
```

`brands/sample` 是示例品牌配置（丽刻约），仅作字段参考，不参与缺省构建。`brands/apply-brand.mjs` 是生成脚本；实际品牌目录（`brands/<brand-key>/`）由交付或部署方维护，是否入库按交付要求决定。

仓库中的页面配置（`pages.json`、`manifest.json`、`index.html`）与 `brand.generated.ts` 自带中性缺省值并随仓库提交，不设置 `BRAND` 时直接使用这些缺省值。

## brand.json 字段

| 字段 | 用途 |
|---|---|
| `key` | 品牌标识，与目录名一致 |
| `appName` | 用户端小程序名称、导航标题 |
| `merchantAppName` | 商家端小程序名称 |
| `merchantBrand` | 商家端登录页品牌名 |
| `slogan` / `merchantSlogan` | 用户端首页 / 商家端登录页副标题 |
| `description` / `merchantDescription` | 小程序简介 |
| `homeFooter` | 用户端首页页脚 |
| `legalFooter` | 协议与登录页页脚 |
| `logoInitial` | 无头像时的占位文字 |
| `aboutTitle` / `aboutContent` | 「关于我们」弹窗内容 |
| `version` | 页面底部版本号 |
| `legal.agreement` / `legal.privacy` | 用户协议、隐私政策全文（标题、日期、导语、分节、页脚） |

法务文本涉及经营主体信息，切换品牌时必须由品牌方提供，不要沿用其他主体的文本。

## 变量引用

品牌名、简称等重复出现的内容在 `variables` 中定义一次，其他字段用 `${变量名}` 引用，生成时统一替换；`variables` 本身不会写入生成结果。

```json
{
  "variables": {
    "brand": "丽刻约"
  },
  "appName": "${brand}",
  "aboutTitle": "关于${brand}",
  "legal": {
    "agreement": {
      "title": "${brand}用户协议"
    }
  }
}
```

修改品牌名时只需改 `variables.brand` 一处。

## 应用配置

页面配置与品牌模块自带中性缺省值并随仓库提交；未设置 `BRAND` 时脚本不做任何写入，直接构建即为缺省内容。指定品牌后，脚本按配置覆盖名称、导航标题与品牌模块：

```powershell
$env:BRAND = "<brand-key>"
pnpm --filter @reservation/user build:mp-weixin
pnpm --filter @reservation/merchant build:mp-weixin
```

各应用的 `dev` / `build` 会在执行前自动调用 `brands/apply-brand.mjs`，也可手动执行：

```powershell
node brands/apply-brand.mjs
```

脚本覆盖以下文件：

- `frontend/packages/shared/src/brand.generated.ts`
- `frontend/apps/user/src/pages.json`、`src/manifest.json`、`index.html`
- `frontend/apps/merchant/src/pages.json`、`src/manifest.json`、`index.html`

覆盖后内容即对应品牌；需要恢复缺省时使用 `git checkout -- <path>`。

## 构建期变量

| 变量 | 用途 |
|---|---|
| `WX_USER_APPID` / `WX_STAFF_APPID` | 写入两端 `manifest.json` 的小程序 AppID |
| `VITE_API_BASE_URL` | 后端接口地址，默认 `http://localhost:8080/api/v1` |
| `VITE_WX_TPL_SUCCESS` / `VITE_WX_TPL_CHANGE` / `VITE_WX_TPL_REMINDER` | 用户端订阅消息模板 ID |
| `VITE_WX_TPL_NEW_RESERVATION` | 商家端新预约通知模板 ID |

## 新增品牌

1. 复制 `brands/sample` 为 `brands/<brand-key>`，修改 `key` 与全部文案字段，然后设 `BRAND=<brand-key>` 构建。该目录是否随仓库交付按项目要求决定。
2. 由品牌方提供用户协议与隐私政策文本，填入 `legal`。
3. 准备该品牌的门店、分类、技师、服务、排班数据（商家端录入或部署时导入 SQL）。
4. 准备平台侧资质：小程序 AppID/Secret、类目、订阅消息模板、request 合法域名、隐私保护指引。
5. 设置 `BRAND` 与构建期变量后重新构建。

平台主体、类目、备案域名、手机号能力等由微信侧审核决定，无法通过配置自动获得。
