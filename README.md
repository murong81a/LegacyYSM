# LegacyYSM → Forge 1.10.2 移植

`RuiXuqi/LegacyYSM`（Yes Steve Model 的 1.12.2 移植）搬到 Minecraft Forge 1.10.2 的工作仓库。

## 分支说明

| 分支 | 内容 |
|---|---|
| `good-0216` | **当前交付版**。基线 = `2050651`（2026-11-16 00:35，“0:38 之前是好的”那一版），另加两处 GUI 状态修复：预览模型前清深度缓冲、模型渲染后复位 GL 颜色（修光影下界面发绿 + 抬头看不见预览）。 |
| `master` | 2026-11-16 00:54 的版本（`9218e81`，含 GUI 预览全亮等改动）。之后再改出来的现象被反馈为“越改越诡异”，仅作对照保留。 |
| `experiments-post-038` | `master` 之后所有实验性改动（HUD 变身隐藏、变身全亮、`FOX` 骨骼兜底、暂停时不停止并行动画等）。**未验证**，仅供查阅，勿直接合入。 |

## 构建

需要 JDK 8 运行 Gradle（ForgeGradle 2.3 限制），JDK 17 负责 javac + Jabel（源码用了 Java 16 语法）。

```bat
set JAVA_HOME=<jdk8>
set GRADLE_USER_HOME=<gradle home>
gradle build --no-daemon -I init-mirror.gradle
```

产物：`build/libs/yesstevemodel-1.10.2-1.2.0-<mcver>.jar`

注意：
- 必须用 `build`（会 reobf），**不要**用 `jar`/`runClient` 产出的 dev 包；
- 客户端运行期间不要重新构建同一个 jar，否则会 `ZipError: jzentry == 0`。

## 1.10.2 相关要点（踩过的坑）

- `ItemStack.EMPTY`/`isEmpty()` → `null`/`== null`；`getPath()` → `getResourcePath()`；`BufferBuilder` → `VertexBuffer`；GUI 字段 `xPosition`/`yPosition`/`fontRendererObj`。
- 语言文件必须是 `en_US.lang` / `zh_CN.lang`（大写）。
- AccessTransformer 必须放在 `META-INF/` 并在 jar manifest 里声明 `FMLAT`。
- `SimpleNetworkWrapper.registerMessage` 在 1.10.2 会实例化 handler，导致专用服务器加载 `EntityPlayerSP` 崩溃 → 按 side 注册 + `NoopHandler`。
- F11 全屏崩溃：coremod 把 `Minecraft.toggleFullscreen()V` 改成空实现，可用 `-Dysm.blockFullscreen=false` 恢复原版行为。
- GUI 里渲染模型必须自己管好深度：1.10.2 的 GUI 覆盖层在光影下不保证清过深度缓冲，模型会按世界深度被裁掉（“抬头看不见”）。

## Mixin / MixinBooter

- 运行时依赖 **MixinBooter 11.18**（`!mixinbooter-11.18.jar` 放进 `mods`，`!` 保证 coremod 先加载）。`mcmod.info` 已声明 `required-after:mixinbooter`，缺装时 FML 会直接提示，而不是在 coremod 阶段报晦涩错误。
- 生产 refmap 由 `org.spongepowered:mixin:0.8.7` 的 Annotation Processor 在 `compileJava` 时生成，输出到 `build/classes/java/main/mixins.yes_steve_model.refmap.json`，并随 jar 打包。
- **必须 `clean build`**：增量编译时 javac 不会把已有 class 再交给 AP，refmap 会变成 `{"mappings":{},"data":{}}`。`build.gradle` 的 `compileJava.doLast` 会校验 refmap 非空且包含关键映射，空 refmap 不会被打进 jar。
- 原版 AP 0.8.7 只把 `@Shadow` **字段**写进 SRG 输出，不写进 JSON refmap；`compileJava.doLast` 会从 `mcp-srg.srg` 的 `FD:` 行查出 SRG 名并补进 refmap。新增 `@Shadow` 字段时必须在 `build.gradle` 的 `shadowFields` 里登记（mixin 类 → 字段 → [目标类, 描述符]），否则生产包里该字段映射不到。

## 模型侧说明（酒狐 / wine_fox）

- 人形与狐狸在同一 `main.json` 中，狐狸是 `FOX` 骨骼子树；平时由常驻并行动画 `parallel1` 用 `FOX.scale = v.roaming.a`、`AllBody.scale = 1 - v.roaming.a` 来二选一显示。
- `extra0`（变身）的时间轴翻转 `v.roaming.a`。
- 如果并行动画没有运行（动画数据缺失、被暂停拦截等），`FOX` 会以默认缩放 1 和人形同时出现。
