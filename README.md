# DialogYSM

Minecraft 1.20.1 Forge（47.x）的 **NPC 任务对话框** 模组：自带视觉小说（VN）风格的对话框 UI，
对话流程由 JSON 脚本驱动，NPC 由 Citizens 建好之后用命令把「NPC → 脚本」绑起来，再用命令/右键触发。

A visual-novel style NPC dialog mod for Minecraft 1.20.1 Forge. Dialog flow is driven by JSON scripts; bind a script to a Citizens NPC with one command, then trigger it by right-click or command.

## 运行环境

| 项 | 值 |
| --- | --- |
| Minecraft | 1.20.1 |
| 加载器 | Forge 47.x |
| Java | 17 |
| 端 | 客户端 + 服务端（服务端管脚本/绑定/命令，客户端画界面） |
| 可选依赖 | Citizens（NPC 来源）、任意 Bukkit 系插件（`plugin_command` 派发目标） |

## 已实现

- 底部对话框：说话者铭牌、打字机正文（可点击跳过）、呼吸箭头、点击/空格推进
- 立绘：左 / 中 / 右三档位置，`size` 缩放、`brightness` 压暗、入场动画（`SLIDE_UP` / `SLIDE_LEFT` / `SLIDE_RIGHT` / `FADE`）
- 选项按钮：居中悬浮、悬浮高亮、鼠标点选、数字键 1-9 选择、空格=第一项
- 背景大图（可选 CG，`FIT` / `FILL` + 淡入）
- 物品展示（任务奖励预览，支持 NBT）
- `@i` 占位符自动替换为玩家名（文本组件里同样生效，样式保留）
- 脚本热重载：改 JSON 不用重启，`/dialogysm reload`
- 选项条件显示：`visibility_command` 在服务端判定后才下发
- 安全：客户端只能请求「本次脚本里确实出现过的命令」，服务端白名单校验后执行

## 构建

```
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat build
```

产物：`build/libs/dialogysm-<版本>.jar`

## 安装

把 jar 丢进 `mods/`（客户端与服务端都要装：服务端负责脚本/绑定/执行命令，客户端负责画界面）。

首次启动会生成下面这套配置目录（改完 `/dialogysm reload` 即可生效）：

```
config/dialogysm/
├─ settings.json                模组级默认值（见下）
├─ dialogs/                     对话脚本，一个文件一条对话，文件名即脚本 id
│   ├─ quest_intro.json
│   └─ npc_daily.json
└─ textures/
    ├─ portraits/               立绘 PNG（脚本里 "path": "npc.png" 在这里找）
    └─ backgrounds/             背景大图 PNG（background_image.path 在这里找）
```

`settings.json` 字段：

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `defaultCooldown` | 0 | 脚本没写 `cooldown` 时的默认冷却秒数，0 = 无冷却 |
| `defaultBlockMovement` | false | 脚本没写 `blockMovement` 时是否禁止玩家走远 |
| `defaultEndRadius` | 0 | 脚本没写 `endRadius` 时的默认离开半径（0 = 不限） |
| `portraitsDir` | textures/portraits | 立绘目录（相对 config/dialogysm），可改名 |
| `backgroundsDir` | textures/backgrounds | 背景目录，可改名 |
| `maxTextureKB` | 4096 | 单张贴图上限，超出的不下发（防止客户端爆内存） |

### 图片怎么生效

贴图只放在服务端 `config/dialogysm/textures/` 里，客户端不需要放任何文件：

1. 服务端读到命中配置目录的 PNG，把脚本里的 `path` 改写成 `dialogysm:custom/<哈希>`；
2. 图片分块（32KB/包）随对话下发给该玩家；
3. 客户端注册成动态纹理并缓存尺寸，界面直接用。

`path` 三种写法都认：`npc.png`、`portraits/npc.png`、`textures/portraits/npc.png`。
写带命名空间的完整路径（如 `minecraft:textures/block/stone.png`）时不会走配置目录，客户端按资源包规则读。
改图后哈希会变（含文件大小与修改时间），客户端自动重新接收，不用清缓存。

## 命令（需要 OP，权限 2）

| 命令 | 说明 |
| --- | --- |
| `/dialogysm show <脚本> [玩家]` | 直接给玩家弹对话（没有说话实体，说话者由脚本 `speaker` 指定） |
| `/dialogysm npc <实体选择器> <脚本> [玩家]` | 以某个实体为说话者弹对话（说话者名默认取实体显示名） |
| `/dialogysm bind <实体选择器> <脚本>` | 绑定：以后右键该实体就触发这条对话 |
| `/dialogysm bind_uuid <uuid> <脚本>` | 按 uuid 绑定（Citizens 取不到实体选择器时用这个） |
| `/dialogysm bind_tag <tag> <脚本>` | 按 scoreboard tag 绑定（给 NPC 打 tag 即可） |
| `/dialogysm unbind <实体>` / `unbind_uuid <uuid>` | 解除绑定 |
| `/dialogysm list` | 列出已加载脚本 + 当前绑定表 + config 里可用的立绘 |
| `/dialogysm reload` | 重载脚本、绑定表、冷却表、settings.json 与贴图目录 |

别名 `/ysmd`。

### Citizens NPC 怎么接

Citizens 的 NPC 不一定在 Forge 侧暴露成实体，最稳的接法是走 Citizens 自己的点击命令：

```
/npc create 艾琳
/npc command add dialogysm show quest_intro %player%
```

如果这个 NPC 在 Forge 侧能看到（Mohist 混合端常见），也可以直接
`/dialogysm bind <实体选择器> quest_intro` 然后右键触发。

绑定表存在存档里：`<world>/dialogysm/bindings.json`，可以直接手改。

## 脚本格式

文件放在 `config/dialogysm/dialogs/<脚本id>.json`（id 就是文件名去掉 `.json`），
也支持数据包 `data/dialogysm/dialogs/*.json`。

```json
{
  "id": "quest_intro",
  "title": "任务：林间的草药",
  "description": "说明文字",
  "start": "greet",
  "allowClose": false,
  "entries": [ ...节点... ]
}
```

顶层字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 脚本 id（建议与文件名一致） |
| `title` / `description` | 标题、说明，`/dialogysm list` 里会显示 |
| `start` | 起始节点 id；不写就取 `entries[0]` |
| `allowClose` | 是否允许 ESC 关闭。任务对话建议 `false`，强迫走完流程 |
| `cooldown` | 冷却秒数（同一玩家同一脚本）。不写 / `0` = 不限；冷却期内重复触发会提示还剩多少秒 |
| `blockMovement` | 是否限制走动：为 `true` 时对话中持续给 Slowness V，配合 `endRadius` 用 |
| `endRadius` | 允许走动的半径（格）。跑出范围或换维度，服务端会下发关闭包并结束对话；`0` = 不限 |

节点字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 节点 id，供 `next` / `options.target` 跳转 |
| `speaker` | 说话者名，字符串或文本组件，如 `{"text":"艾琳","color":"#E8D8B0"}`；留空则用绑定 NPC 的名字 |
| `text` | 正文。字符串 / 文本组件 / 文本组件数组。支持 `@i` 替换玩家名 |
| `portraits` | 立绘数组，见下 |
| `next` | 下一条节点 id；不写则按数组顺序往后走 |
| `options` | 选项数组，见下 |
| `command` | 进入该节点时执行的命令，字符串或字符串数组（服务端执行） |
| `allowSkip` | 是否允许点击瞬间打完字，默认 `true` |
| `endDialog` | 看完这一条直接结束整段对话 |
| `visibility_command` | 节点可见条件（如 `execute if score @s ysm_quest_herb matches 1`），按 id 的链接会失效，慎用 |
| `display_items` | 对话中展示的物品：`[{"item":"minecraft:emerald","count":6,"nbt":"{...}"}]` |
| `background_image` | 背景 CG：`{"path":"forest.png","renderOption":"FIT","animation_type":"FADE"}` |

立绘字段：

```json
{"path": "npc.png", "position": "CENTER", "size": 1.0, "brightness": 1.0,
 "alpha": 1.0, "animationType": "SLIDE_UP"}
```

`path` 写文件名 → 解析为 `dialogysm:textures/portraits/npc.png`；
写 `modid:textures/xxx/a.png` 则按原样加载（可复用别人的立绘资源）。
`position`：`LEFT` / `CENTER` / `RIGHT`。

选项字段：

```json
{"text": "交给我吧。", "target": "accept",
 "command": ["scoreboard players set @s ysm_quest_herb 1"],
 "plugin_command": ["cmi menu open %player%"],
 "close": true,
 "visibility_command": "execute unless score @s ysm_quest_herb matches 1"}
```

- `target`：跳转节点 id；写 `"end"` 直接结束
- `command`：选中后执行的原版/Forge 命令（服务端，玩家身份）
- `plugin_command`：选中后执行的 **插件命令**（Bukkit 侧、以玩家身份派发，菜单类插件用它）
- `close`：执行完命令直接结束对话（不写则按 `target` 走）
- `visibility_command`：条件不满足时不显示该选项

节点也有 `plugin_command`，进入该节点时就会执行。

### 用选项开菜单 / 发奖励

一条选项可以同时挂 `command`（原版）和 `plugin_command`（插件），执行顺序是先原版后插件，最后按 `close` / `target` 处理：

```json
{
  "text": "打开每日商店",
  "plugin_command": ["chestcommands open dailyshop %player%"],
  "close": true
}
```

```json
{
  "text": "领取任务奖励",
  "command": ["give @s minecraft:diamond 3"],
  "plugin_command": ["cmi money add %player% 500"],
  "close": true
}
```

说明：

- `plugin_command` 会先尝试以**玩家本人**为 sender 从 Bukkit 派发（`Bukkit.dispatchCommand`），
  这样 CMI 菜单、ChestCommands、Shopkeepers 这类需要玩家上下文的命令才能正常打开；
  纯 Forge 端没有 Bukkit，会自动回退成 Forge 派发器（原版命令照常可用）。
- 支持 `%player%`（玩家名）、`%uuid%` 占位。
- 服务端只执行**本次脚本里出现过的**命令（原版、插件各一套白名单），客户端伪造会被拒。
- 命令以玩家权限 2 执行，插件命令按插件自己的权限规则判定。

## 代码结构

```
com.dialogysm
├── DialogYSMMod.java        模组入口（网络注册、服务端启动加载脚本）
├── DialogEvents.java        右键绑定实体触发对话
├── command/DialogCommands.java   /dialogysm 命令树
├── dialog/
│   ├── DialogScript.java     脚本模型 + 节点查找/推进
│   ├── DialogNode.java       单节点
│   ├── DialogOption.java     选项
│   ├── PortraitSpec.java / BackgroundSpec.java / ItemSpec.java
│   ├── TextUtil.java         文本组件解析 + @i 替换
│   ├── DialogRegistry.java   服务端脚本仓库（config 目录 + 数据包，选项条件过滤）
│   ├── DialogBindings.java   绑定表持久化（存档内 JSON）
│   ├── DialogSessions.java   会话 + 命令白名单
│   └── DialogOpener.java     开对话的统一入口
├── net/                      S2C 打开对话 / C2S 命令回报
└── client/DialogScreen.java  对话框 UI（立绘、打字机、选项、CG、物品）
```

## 已知限制

- 只适配 1.20.1 + Forge；Fabric / 更高版本未做。
- 对话框 UI 是纯自绘（不依赖其他 UI 库），字体沿用原版，做花字需要自己写文本组件。
- `bind <实体选择器>` 依赖该 NPC 在 Forge 侧可见；纯 Bukkit 端的 Citizens NPC 请走 `bind_uuid` / `bind_tag` 或 Citizens 的命令点击。
- `visibility_command` 用在节点上会导致按 id 跳转失效，条件判断建议优先写在选项上。
- 服务端的命令白名单只保存"本次脚本出现过的命令"，所以脚本热重载后**已经在对话中的玩家**需要重新开一次对话才能用新命令。
- `cooldown` 与 `blockMovement / endRadius` 由服务端强制；客户端与服务端都要装同一版本（网络协议已升到 1.1，版本不一致会被拒绝连接）。

## 致谢与声明

- 结构思路参考 `VNDialog-forge-1.20.1`，全部代码按本工程需求重写，未复制其源码。
- 示例立绘 `assets/dialogysm/textures/portraits/npc.png` 为本项目自建素材，可自由替换。

## 许可证

MIT License，见根目录 `LICENSE`。
