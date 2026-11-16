# LegacyYSM → Forge 1.10.2 移植

把 [RuiXuqi/LegacyYSM](https://github.com/RuiXuqi/LegacyYSM)（Yes Steve Model 的 1.12.2 实现）移植到 **Minecraft 1.10.2 / Forge**，让 1.10.2 整合包也能用 YSM 的模型、动画和换装系统。

模型不是原版皮肤，而是自带骨骼、状态机动画和附加动作（表情、待机、坐下、骑乘等）的完整角色。

## 已实现

**模型加载**
- 文件夹模型、`.zip`、YSM v1 / v2 `.ysm`
- 服务端扫描模型库并同步给客户端，客户端也可上传
- 模型 ID 校验、资源包式贴图扫描、WebP/JPEG 贴图自动告警

**渲染**
- 第一人称手臂替换、第三人称骨骼渲染
- GUI 内实时预览（模型选择界面）
- 光影 / OptiFine 下的深度、光照与 GL 状态处理
- F11 全屏崩溃的 coremod 兜底

**动画**
- GeckoLib3 动画运行时 + 作者的状态机（idle / walk / run / jump / swim / sit / sleep / ride …）
- 附加动作：最多 64 个槽位，快捷键 + 轮盘选择（轮盘支持翻页）
- Molang 表达式：`query.*`、`math.*`、玩家变量，另有 `ctrl.*` 绑定（walk / sprint / sneak / fly / swim / ride …）
- 元素层：常驻播放作者的 `隐藏` 动画，压掉背景板、挂件等不该出现的装饰
- 并行动画（多套动画同时驱动不同骨骼）

**其他**
- 服务端指令、配置、模型同步
- 暂停时冻结并行动画，避免 ESC 后骨骼叠加错位
- Mixin 层：聊天可点击文本、箭矢 `shoot_item_id`

## 尚未实现 / 已知限制

- **v3 `.ysm` 不能直接加载**。v3 是 XChaCha20 + 变异 zstd 的加密格式，目前只能离线转换成文件夹模型再使用
- **YSM2 的元素开关没有实现**。作者的 `v.roaming.*`（换装、形态切换、表情档位）依赖 YSM2 的配置面板，移植版只支持作者写在 `隐藏` 里的固定隐藏；带多套衣服的模型会把备用套装一起画出来
- 部分动作语义未逐一验证：拉弓、载具、鞘翅、NPC 等
- 没有自动化测试；构建产物靠 `clean build` + refmap 校验保证可用
- 仅支持 1.10.2，不向上兼容 1.12.2 的存档/网络格式

## 构建

需要 JDK 8（跑 Gradle / ForgeGradle 2.3）和 JDK 17（解析 Java 16 语法，Jabel 再降为 Java 8 字节码）。

```bat
gradle clean build --no-daemon
```

- 必须 `clean build`：增量编译时注解处理器不会再跑一遍，Mixin refmap 会变空，产物在正式环境失效
- JDK 17 的 `javac` 路径可用 `JAVAC17` 环境变量指定
- 产物：`build/libs/yesstevemodel-1.10.2-*.jar`
- 推 `v*` 标签会由 GitHub Actions 构建并发布 Release

## 运行依赖

**MixinBooter 11.18**：把 `!mixinbooter-11.18.jar` 放进 `mods`（`!` 保证它的 coremod 先加载）。

## 许可证

本仓库是 [RuiXuqi/LegacyYSM](https://github.com/RuiXuqi/LegacyYSM)（BSD 3-Clause）的**非官方 Forge 1.10.2 移植**，原始版权归 Yes Steve Model Dev Group；移植部分同样以 BSD 3-Clause 发布，见根目录 LICENSE。

- 内置模型来自上游，随本模组分发
- 玩家自备的 `.ysm` 模型版权归各自作者，不进入本仓库
- Mixin 运行时由 MixinBooter 提供
