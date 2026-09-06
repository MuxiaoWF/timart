# map_attic — 地图选点代码停用区

此目录**不参与编译**（AGP 只注册 main/test/androidTest 等已知 source set，`map_attic` 被忽略），
源码保留入库以备日后恢复。

## 停用背景（2026-09）

GPS 条件表单从「地图/经纬网格选点」改为「地址文字解析」（原生 `Geocoder` →
Nominatim → Photon 三级兜底，见 `data/remote/geocode/` 与 `utils/location/GeocodeResolver.kt`）。
百度 SDK（`BaiduMapSDK_Map` 8.2.0.2）四个 ABI 的 `.so` + 地图样式资源约占 release APK 76MB，
移除后 release 回落至 ~3MB。

## 内容

| 文件 | 说明 |
|---|---|
| `java/com/muxiao/timart/ui/components/visual/BaiduMapPickerCanvas.kt` | 百度在线地图选点画布（依赖 `com.baidu.mapapi.*`） |
| `java/com/muxiao/timart/ui/components/visual/LatLngPickerCanvas.kt` | 纯自绘经纬网格画布（无 SDK 依赖，可直接搬回编译） |
| `java/com/muxiao/timart/utils/map/BaiduMapBootstrap.kt` | 百度 SDK 懒初始化（AK 校验、隐私合规） |

## 恢复步骤

1. `gradle/libs.versions.toml` 加回 `baiduMap = "8.2.0.2"` 与
   `baidu-map-sdk = { group = "com.baidu.lbsyun", name = "BaiduMapSDK_Map", version.ref = "baiduMap" }`；
2. `app/build.gradle.kts` 加回 `implementation(libs.baidu.map.sdk)`（LatlngPickerCanvas 无需依赖）；
3. `AndroidManifest.xml` 加回 `com.baidu.lbsapi.API_KEY` meta-data（AK 从 `local.properties` 的
   `BAIDU_MAP_AK` 注入，见历史版本的 `baiduMapAk` placeholder 逻辑）；
4. 把需要的文件按原包名移回 `app/src/main/java/`（注意平行目录结构一致）；
5. UI 侧重新接入（参考 GpsConditionForm 的双模式历史实现：`ensureInitialized` 成功走在线地图，否则回退网格）。

`CoordTransform`（BD-09 转换）仍在 `utils/location/` 编译树内并有单测，未停用。
