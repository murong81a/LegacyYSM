# 发布不变量（每次发 jar 前逐条过）

> 这份清单是**踩过的坑的固化**。任何一条不过，就不要发版；先修，再重跑。
> 每条都注明"为什么"和"怎么查"，这样上下文被压缩后也能照做。

## 1. 构建必须是 clean build

- **为什么**：注解处理器只在重新编译的源码上生成 refmap；增量构建会产出空的 `{"mappings":{},"data":{}}`，mixin 在正式环境静默失效。
- **怎么查**：`run-build.cmd clean build`；然后 `unzip -p <jar> mixins.yes_steve_model.refmap.json`，确认 `mappings` 非空（历史上正常值 ≈ 2.2 KB）。

## 2. jar 必须是 reobf 后的产物

- **为什么**：`runClient` 会把 `build/libs/*.jar` 覆盖成 dev（未 reobf）版本，发出去会 `NoSuchFieldError: world`。
- **怎么查**：发版前**最后一步**永远是 `build`（不是 `runClient`）；`javap`/`strings` 里应出现 `field_70170_p` 这类 SRG 名，而不是 `world`。

## 3. 文件名带 commit hash，一个 jar 只装一个假设

- **为什么**：一天连发 5 个 jar 且每个都塞了 2~6 个互不相关的改动，导致"回归了但不知道是谁干的"。
- **怎么查**：`git status` 干净、`git log -1 --oneline` 与文件名后缀一致；提交信息只描述一件事。

## 4. 改源码只用编辑工具，禁止 PowerShell 文本重写

- **为什么**：`Get-Content -Raw | ... | Set-Content -Encoding UTF8` 会按 ANSI 读 UTF-8 文件再写成 UTF-8，中文注释乱码 + 加 BOM，javac 报 `illegal character`。本会话已经发生两次（`AnimationRouletteScreen.java`、`ysm_convert.py`）。
- **怎么查**：`git diff` 里若出现整文件重写（几十行删除+新增而逻辑没变），立刻 `git checkout -- <file>` 重来。

## 5. 模型目录在测试期间冻结

- **为什么**：玩家在 `11:34` 进游戏测试，我在 `11:05~11:27` 批量重装了 28 个模型、又原地改了贴图——"稳定版本"因此从未存在过。
- **怎么查**：改 `custom\` 之前先问一句"你现在在测吗"；改完必须让玩家 `/ysm model reload` 并重新确认基线。

## 6. 贴图必须是真 PNG

- **为什么**：作者会把 WebP 改名成 `.png`；Java 8 `ImageIO.read` 返回 null 而不抛异常，模型变白模且日志无声。
- **怎么查**：`ysm_convert.py` 已内置转码；手工排查可读文件头 `89 50 4E 47`。jar 内 `OuterFileTexture` 现在会打 WARN。

## 7. 动画名必须覆盖 canonical

- **为什么**：作者常把真身写在中文名下（`行走动画`/`跑步动画`/`正常_行走`），英文名留 `{"loop":true}` 空壳 → 腿不动。
- **怎么查**：`check_custom.py` 输出 `real/shell/missing`；`ysm_convert.py` 内置别名表（含 `atri`、`kronoa` 的 controller 推导映射）。

## 8. 目录 id 必须合法

- **为什么**：`ResourceUtil.isValidResourceLocation` 只接受 `[a-z0-9_.-]`，中文/大写/空格会被**静默跳过**（本会话绕了最久的一个坑）。
- **怎么查**：`check_custom.py` 会标 `目录名非法`；`ServerModelManager` 现在会打 WARN。

## 9. GL 状态必须还原

- **为什么**：`renderModel` 结束后若把 `OpenGlHelper.lightmapTexUnit` 留在开启状态，OptiFine 光影下整个 GUI 会被染色（绿屏/黑预览）。
- **怎么查**：确认 `RenderUtil.renderModel` 结尾那 5 行清理还在（`disableStandardItemLighting`、`disableRescaleNormal`、`setActiveTexture(lightmap)`、`disableTexture2D`、`setActiveTexture(default)`）。

## 10. 暂停时不能把并行层 STOP

- **为什么**：STOP 会让 parallel 动画停止写 scale，GeckoLib 复位把骨骼 scale 拉回 1，酒狐那种双骨架模型会人形+狐狸同屏。
- **怎么查**：`AnimationManager.predicateParallel` / `predicateElement` 在 `isGamePaused()` 时返回 `CONTINUE`，`AnimationProcessor` 的 reset 长度用 `Double.MAX_VALUE`。

## 11. 发版必须附"三行说明"

```
改了什么（一个主题）：
怎么验证（玩家可执行的具体操作）：
出问题怎么回滚（上一个 jar / git revert 的 hash）：
```
