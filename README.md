# KugouMusicAPI Mod

一个为 Minecraft 打造的酷狗音乐客户端模组，支持搜索、播放、歌词、登录、VIP 等全部功能。

## 特性
- 🎵 **音乐播放**：搜索并播放酷狗海量曲库
- 🎤 **歌词显示**：实时滚动双语歌词，可自由开关
- 🔐 **扫码登录**：内置二维码，手机酷狗 App 扫一扫即可登录
- 💎 **VIP 领取**：概念版（Lite）自动领取、升级 VIP
- ☁️ **云盘播放**：直接播放你的酷狗云盘音乐
- 📋 **歌单同步**：浏览并播放你创建/收藏的歌单
- ⚙️ **高度可配置**：凭证自动保存，音质随心切换

## 安装
1. 确保已安装 Fabric Loader 和 Fabric API。
2. 下载本模组的 `.jar` 文件放入 `mods` 文件夹。
3. 启动游戏，模组将自动生成配置文件（`config/kugou_config.json`）。

## 使用指令
### 搜索与播放
- `/kugou search <关键词>` – 搜索歌曲，点击结果播放
- `/kugou play <hash>,<albumId>,<albumAudioId>,<quality>` – 直接播放
- `/kugou pause` – 暂停
- `/kugou resume` – 继续
- `/kugou stop` – 停止
- `/kugou quality <128|320|flac|...>` – 设置默认音质

### 歌词
- `/kugou lyric off` – 关闭歌词（默认）
- `/kugou lyric single` – 单语歌词
- `/kugou lyric bilingual` – 双语歌词

### 登录
- `/kugou captcha <手机号>` – 发送验证码
- `/kugou login <手机号> <验证码>` – 验证码登录
- `/kugou qrlogin` – 扫码登录（屏幕显示二维码）
- `/kugou refreshlogin` – 刷新登录态

### VIP（仅概念版 Lite，需登录）
- `/kugou vip status` – VIP 状态
- `/kugou vip day` – 领取一天 VIP
- `/kugou vip upgrade` – 升级畅听 VIP
- `/kugou vip month` – 本月领取天数

### 歌单与云盘
- `/kugou playlist list` – 我的歌单
- `/kugou playlist songs <global_collection_id>` – 歌单歌曲
- `/kugou cloud list` – 云盘音乐列表
- `/kugou cloud play <hash>,<name>,<audio_id>` – 播放云盘歌曲

### 用户信息
- `/kugou user` – 查询用户详情

### 帮助
- `/kugou help` – 查看帮助
## 配置文件
首次启动后模组会在 `config/kugou_config.json` 生成凭证。内容示例：
```json
{
  "dfid": "1J9DwV4dx2VG3xAQvf3Kg7Eh",
  "mid": "278724566976874840157720746984515822090",
  "token": "sgrwgwsgwsgrswg...",
  "userid": 0
}
```

**⚠️警告：因技术原因，凭证暂时为明文存储，请勿泄露该文件，以免发生盗号风险！若发觉凭据泄露，请立即登出设备并修改密码，建议仅在可信计算机上使用本模组，后续将采用更安全的方式存储如`SQLite`。**

### ✅ 已实现功能总结

本模组已完整移植酷狗音乐的核心 API，实现了以下所有功能，且全部基于纯 Java 原生实现，无需 Node 后端。

### 音乐播放
- 搜索歌曲（支持关键词、歌单、歌词、专辑、歌手等多种类型）
- 播放/暂停/继续/停止
- 自定义音质（128kbps、320kbps、flac 等）
- 播放列表（搜索结果、歌单、云盘歌曲均可点击播放）
- 播放结束自动提示

### 歌词显示
- 自动获取并解析 LRC 歌词
- 可切换单语/双语/关闭歌词（默认关闭）
- 按时间轴自动滚动，暂停/继续同步

### 登录与账号
- 手机验证码登录
- 扫码登录（内置二维码显示，手机酷狗App扫描即可）
- Token 刷新登录（延长登录态）
- 设备注册（自动获取 dfid）
- 凭证持久化保存（`config/kugou_config.json`）

### 高级功能
- VIP 状态查询、领取、升级、月度统计（概念版 Lite）
- 云盘音乐列表与播放
- 用户歌单列表与歌曲浏览
- 用户信息查询

### 技术特性
- 纯 Java 实现，所有加密（AES、RSA、MD5）与签名算法与官方客户端一致
- 使用 Fabric Loom + JLayer 播放 MP3
- 支持服务端/客户端双端加载（环境分离）

---

## 常见问题
- **为什么搜索不到歌曲？**

  请确保已登录（扫码或验证码），并等待设备注册完成（首次启动会自动注册 dfid）。

- **歌词不显示？**

  默认关闭，请使用 `/kugou lyric single` 或 `/kugou lyric bilingual` 开启。

- **云盘音乐无法播放？**

  部分 MP3 链接可能需要有效 User-Agent 或已过期，我们正在持续优化。

## 开发与贡献
本模组使用纯 Java 实现了酷狗音乐的全部签名加密算法，欢迎下载体验。若发现问题或者修复已有问题，欢迎提交 Issue 或 Pull Request。

## 许可证
本项目基于 MIT 协议开源，仅供学习研究使用。**严禁**用于商业用途，否则后果自负。

## 致谢
- [KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) Node 版本项目 BY **MakcRe**
- Fabric 社区与所有贡献者
- Deepseek v4 Pro