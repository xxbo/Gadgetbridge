# Furry Pixel Badge · 像素兽设电子社交卡

把兽设、社交二维码和真实手环数据放进一张会动的像素角色卡，带去漫展认识新朋友。

这是基于 [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) 开发的 Android 个人定制分支。开发重点是漫展场景下的角色展示、社交互动和活动数据展示，同时保留上游的设备连接与数据管理功能。

> 当前处于开发阶段。以下为本地开发版本已实现的功能，功能源码将随后续提交同步；上游发行版不包含本分支的定制页面。

## 已实现功能

| 模块 | 功能 |
| --- | --- |
| 角色社交卡 | 可编辑兽名、相册头像与社交二维码；点击二维码放大扫码 |
| 生命体征 | 实时心率与采样趋势曲线；最近有效的血氧、HRV 值 |
| 今日探索 | 当天已同步步数、自定义目标、进度条、探索称号和像素小兽 |
| 小兽互动 | 牛、龙、狼、狐、猫、犬；眨眼、摇尾、抖耳、蹦跳；连点飘出“好感 +1” |
| 社交状态 | 可集邮、欢迎扩列、休息中、欢迎贴贴、正在逛展 |
| 主题配色 | 霓虹像素、苍蓝鎏金、樱花梦境、森林精灵 |
| 展示模式 | 隐藏操作按钮，保留扫码和小兽互动；长按进入或退出 |

兽名、图片、物种、社交状态、主题和步数目标保存在手机本地。好感飘字目前是互动效果，没有累计等级系统。

## 使用方式

1. 安装本分支构建的应用，完成手环配对与连接。
2. 从侧边菜单进入“生命体征”页面。
3. 点击标题编辑兽名、点击头像选择图片；通过 `MENU +` 设置二维码、物种、主题等。
4. 点击底部社交状态切换状态，点击小兽触发互动。
5. 点击“今日探索”设置步数目标，默认 **15,000 步**。
6. 开启展示模式用于现场展示，长按画面退出后继续编辑。

`SYNC` 请求同步手环活动记录。`DEMO` 用于预览布局，开启后右上角显示 `DEMO`；示例数值不会写入手环数据。

## 数据与设备支持

目前围绕 **HUAWEI Band 11 Pro** 开发并进行实机验证，包含连接稳定性修复和实时心率协议接入。其他设备能否提供相同数据，取决于上游支持和对应协议实现。

- **心率**：来自手环实时上报，过滤无效样本；约 15 秒没有有效更新时显示 `--`。
- **血氧 / HRV**：最近有效测量值，不代表秒级实时测量。
- **今日步数**：按手机本地当天统计已同步活动样本，每约 15 秒重新读取；没有当天有效样本时显示“等待同步”。不保证与手环屏幕同时刷新。
- **曲线与动画**：曲线展示心率采样趋势，并非心电图；爱心跳动是根据心率驱动的装饰动画。

## 构建与开发

使用项目自带的 Gradle Wrapper，配置 JDK 21 和项目所需的 Android SDK。

```sh
# Windows
./gradlew.bat :app:assembleMainlineDebug

# Linux / macOS
./gradlew :app:assembleMainlineDebug
```

Windows 下运行 `gradlew.bat`（PowerShell 中使用 `./gradlew.bat`）。APK 输出：

```text
app/build/outputs/apk/mainline/debug/app-mainline-debug.apk
```

主要实现入口：

- [VitalSignsActivity.java](app/src/main/java/nodomain/freeyourgadget/gadgetbridge/activities/VitalSignsActivity.java)：像素界面、社交卡、角色动画、主题和探索卡片。
- [HrRriTest.java](app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/huawei/packets/HrRriTest.java)：华为实时心率指令与数据解析。
- [HuaweiSupportProvider.java](app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/huawei/HuaweiSupportProvider.java)：测量请求和实时数据处理。

## 后续方向

- 验证步数同步表现，完善漫展活动数据展示。
- 增加探索里程碑反馈和更丰富的角色动作。
- 继续完善不同设备的数据支持。

## 上游与许可

感谢 Gadgetbridge 团队及所有贡献者。本项目是个人定制分支，与官方项目没有隶属关系。

- [Gadgetbridge 官方仓库](https://codeberg.org/Freeyourgadget/Gadgetbridge)
- [Gadgetbridge 官网](https://gadgetbridge.org)
- [保留的上游 README、作者与贡献说明](README.upstream.md)
- [贡献指南](CONTRIBUTING.md)
- [许可证](LICENSE)

代码遵循仓库现有许可及各文件的许可声明。头像、二维码和其他角色素材的权利归各自权利人所有。
