# 街景校验台

一个静态单页 Google 街景产品原型：先调用 Street View Metadata API 校验坐标是否有街景，再用 Maps Embed API 加载可交互街景。

## 运行

直接用浏览器打开 `index.html` 即可。首次使用需要在页面里填写 Google Maps API key，点击“保存到本机”后会写入当前浏览器的 `localStorage`。

## 需要启用的 Google API

- Maps Embed API：用于 `https://www.google.com/maps/embed/v1/streetview`
- Street View Static API：metadata 端点属于 Street View Static API 文档体系，用于 `https://maps.googleapis.com/maps/api/streetview/metadata`

建议在 Google Cloud Console 中限制 API key：

- 客户端 Embed key：限制 HTTP referrer，只允许你的域名。
- 生产环境 metadata key：优先放在后端代理里，限制为服务器来源，避免把更高权限 key 暴露到浏览器。

## 已实现功能

- 支持输入经纬度、地址文本、Google Maps 链接中的 `@lat,lng`。
- Metadata 预校验，展示状态、实际命中的坐标、拍摄日期和 `pano_id`。
- 校验成功后使用 `pano_id` 加载 Embed 街景，降低漂移和空白结果。
- 方向、俯仰、视野控制，快速切换东南西北。
- 附近 8 点探测，帮助从目标坐标周边找到可用街景。
- 历史、收藏、复制坐标、复制分享链接、复制 iframe。

## 产品扩展方向

- 后端代理：把 metadata 请求迁到 `/api/streetview/metadata`，统一处理 CORS、签名、限流和 key 安全。
- 批量校验：导入 CSV，经纬度批量返回 `OK/ZERO_RESULTS`，导出结果。
- 采集任务：给每个点添加审核状态、备注、标签、负责人。
- 路线模式：输入起点终点后沿路线抽样校验街景覆盖。
- 对比视图：同屏显示地图、街景、历史收藏或多个候选 pano。
- 质量评分：根据拍摄日期、距离偏移、是否命中原始点，为街景可用性打分。
- 权限与团队：把收藏点、审核记录、批量任务同步到后端数据库。
