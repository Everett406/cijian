此目录在本地构建时需要放置模型文件：

light_Tencent_AILab_ChineseEmbedding.bin  (约 110MB)
SHA256: 5515923c7e67cdc7eb42996546e0bad273c8452f3bfad6db0794e51c848d151b

下载（任选其一）：
  curl -L -o app/src/main/assets/models/light_Tencent_AILab_ChineseEmbedding.bin \
    "https://hf-mirror.com/shibing624/text2vec-word2vec-tencent-chinese/resolve/main/light_Tencent_AILab_ChineseEmbedding.bin"

或: huggingface-cli download shibing624/text2vec-word2vec-tencent-chinese light_Tencent_AILab_ChineseEmbedding.bin --local-dir app/src/main/assets/models

该文件超过 GitHub 100MB 限制，已被 .gitignore 排除；
GitHub Actions 构建时会自动按上述地址下载并做 SHA256 校验。
pca2d.f32 / pca3d.f32 为预计算的 PCA 投影坐标（tools/model_tool.py 生成），随仓库分发。
