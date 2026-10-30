
> [!NOTE]
> **本仓库已迁移至 Gitee**：<https://gitee.com/Everett5678/cijian>
> GitHub 端仅作只读归档，新代码请推送到 Gitee。

---
# 词间 CiJian

> 词与词之间，有路可走。
>
> 一个完全离线的中文语义探索安卓应用，基于腾讯 AI Lab 中文词向量（light 版，143,613 词 × 200 维）。

国王 － 男人 ＋ 女人 ≈ **王后**。从「爱情」走到「战争」会路过**婚姻**、**情感**和**世界大战**。这背后不是魔法，只是 14 万个 200 维向量之间的夹角 —— 本应用把这片语义空间装进你的手机，让你随意漫步。

## 功能

| 模块 | 说明 |
|---|---|
| **词语计算器** | 多步加减（＋词/－词），经典题式一键预填，结果按余弦相似度排序 |
| **词义之路** | 任意两词之间线性插值 + 逐步最近邻，力导向图谱可拖拽、逐站停留进度条、生成宣纸风分享卡 |
| **语义星图** | 「墨涟」：以任意词为中心的四圈相似度邻域图，全词标』注可点读；「星海」：14 万词 3D 星空漫游，LOD 疏密自适应，靠近时词语渐次浮现 |
| **语义猜词** | 隐藏一个常用词，输入任意词换算相关度百分比（温度提示），用最少次数猜中；词库为人工精选 800+ 常用词 |
| **词桥** | 给定起点与终点，十步之内沿近邻搭建语义之桥；失败时展示参考走法 |
| **分词工坊** | 双向最大匹配分词，词典直接复用词向量词表；切出的词一键进入其他玩法 |
| **类比测验** | 「A 之于 B，如同 C 之于？」自动出题计分，内置 60+ 关系词对 |
| **收藏与历史** | 收藏词/运算/路径，JSON 本地持久化；历史足迹随时回溯 |
| **随机探索** | 「手气不错」随机一词漫游 |

无网络权限、无第三方 SDK、无任何数据上传。

## 安装

### 方式一：GitHub Releases（推荐）

打开 [Releases](../../releases) 下载 `CiJian-x.y.z.apk` 直接安装。每次推送 `v*` 标签，Actions 会自动：下载模型 → SHA256 校验 → 密钥签名 → 发布 Release。

### 方式二：本地构建

1. 环境要求：Android Studio（含 JDK 17）、Android SDK 35
2. 下载模型放进 assets（约 110MB）：

```bash
curl -L -o app/src/main/assets/models/light_Tencent_AILab_ChineseEmbedding.bin \
  "https://hf-mirror.com/shibing624/text2vec-word2vec-tencent-chinese/resolve/main/light_Tencent_AILab_ChineseEmbedding.bin"
```

3. 用 Android Studio 打开项目，`Run` 即可。命令行构建：

```bash
gradle wrapper --gradle-version 8.9   # 首次生成 wrapper（或直接用 AS 内置 Gradle）
./gradlew :app:assembleRelease         # 未配置签名环境变量时自动回落 debug 签名
```

## 发布流程（版本号管理）

```bash
# 修改代码后，打标签触发自动构建发布；versionName 取自标签，versionCode = git 提交数（单调递增）
git tag v1.0.1 && git push origin v1.0.1
```

CI 签名所需仓库 Secrets（维护者配置一次即可）：

| Secret | 说明 |
|---|---|
| `CI_KEYSTORE_BASE64` | `base64 -w0 cijian-release.jks` 的输出 |
| `CI_KEYSTORE_PASSWORD` | keystore 口令 |
| `CI_KEY_ALIAS` | `cijian` |
| `CI_KEY_PASSWORD` | key 口令（与 keystore 口令相同） |

> 密钥文件不入库（`.gitignore` 已排除 `*.jks`）。请自行备份 keystore，丢失后无法向既有用户推送升级安装包。

## 架构与实现要点

```
app/src/main/java/com/cijian/app/
├── CiJianApplication.kt        # Application：引擎加载状态机（Loading/Ready/Error）
├── core/
│   ├── ModelLoader.kt          # 模型二进制解析 + PCA 资产读取
│   ├── Word2VecEngine.kt       # 核心引擎：归一化/近邻/算术/路径/类比/搜索/常用词
│   ├── Pca.kt                  # 端上 PCA 兜底（协方差 + 幂迭代，多线程）
│   ├── Segmenter.kt            # 双向最大匹配分词
│   └── QuizPairs.kt            # 类比测验种子词对
├── data/Store.kt               # kotlinx.serialization JSON 持久化
├── assets/common_words.txt     # 人工精选 800+ 常用词（猜词/词桥/星图随机定位）
└── ui/                         # Compose Material3 界面（纸墨东方主题）
```

关键技术点：

- **模型格式**：腾讯 light 版为二进制流式布局 —— 文本头 `143613 200` 之后，每条记录为 `词(UTF-8) + 空格 + 200×float32(LE)`，**记录间无分隔符**。词向量字节中可能出现 `0x20/0x0A`，绝不能按行读取，必须按「词结束空格 + 定长向量」推进（`ModelLoader`）。加载 + L2 归一化全程约 1~2 秒。
- **检索**：暴力点积（28.6M 次浮点/次查询）在手机上约 30ms，无需任何向量索引库；以简易选择堆维护 top-k。
- **词义之路**：`p(t) = (1−t)·v_from + t·v_to`，每步取最近邻并排除已访问词，自然形成语义路径。
- **星图渲染**：「墨涟」2D 邻域图不需要 PCA —— 每次以焦点词做一次暴力近邻，涟漪按相似度分带；「星海」3D 用预计算 PCA（`tools/model_tool.py`，随仓库分发 1.1MB+1.7MB），缺失时端上兜底。3D 采用屏幕网格去重 + 分层容量上限的 LOD 剔除：远望疏朗、靠近后词语渐次浮现，全程点数受控不卡帧。标签统一用纸色描边晕圈保证可读性。
- **主题**：思源宋体（Noto Serif SC 可变字体）随包分发；宣纸底、焦墨字、朱砂点缀。运算符统一用全角「＋－＝」，避开个别机型变字体对 U+2212 渲染异常的问题。

## 工具脚本

```bash
python3 tools/model_tool.py inspect   # 校验模型头与向量范数
python3 tools/model_tool.py demo      # 近邻 / 类比 / 词义之路 真实示例
python3 tools/model_tool.py pca       # 重新生成 PCA 投影资产
```

## 真实算法示例（light 版实测）

```
[analogy]  国王 − 男人 + 女人 ≈ 王后(0.705), 爵士(0.656), 王子(0.651)
[path]     爱情 → 感情 → 婚姻 → 情爱 → 情感 → 世界大战 → 局部战争 → 朝鲜战争
[nearest]  苹果 → 苹果公司(0.851), iphone(0.779), apple(0.768), 小米(0.752)
```

## Roadmap

- [ ] 模型热插拔（任意 word2vec 文本/二进制格式导入）
- [ ] 星图聚类着色（按语义簇分色）
- [ ] 词频/年代维度着色
- [ ] 双语界面

## 许可

MIT License。模型权重遵循腾讯 AI Lab 官方许可，仅用于研究/学习用途。
