#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
词间 CiJian — 模型工具
用法:
  python3 tools/model_tool.py inspect [path]   # 查看模型格式与向量范数
  python3 tools/model_tool.py demo [path]      # 近邻 / 类比 / 词义之路 示例
  python3 tools/model_tool.py pca [path]       # 生成 2D/3D PCA 坐标资产 (assets/models/)
默认路径: app/src/main/assets/models/light_Tencent_AILab_ChineseEmbedding.bin
"""
import sys, os, struct, time
import numpy as np

DEFAULT = os.path.join(os.path.dirname(__file__), "..", "app/src/main/assets/models",
                       "light_Tencent_AILab_ChineseEmbedding.bin")

def load(path):
    """腾讯 light 版格式: 文本头行 'n dim'；随后是连续记录流 = 词(UTF-8) + ' ' + 200×float32(LE)，记录间无分隔符"""
    t0 = time.time()
    import mmap
    with open(path, "rb") as f:
        mm = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)
        nl = mm.find(b"\n")
        header = mm[:nl].decode("utf-8").split()
        n, dim = int(header[0]), int(header[1])
        print(f"[header] vocab={n} dim={dim}")
        words = [""] * n
        X = np.empty((n, dim), dtype=np.float32)
        rec = 4 * dim
        pos = nl + 1
        for i in range(n):
            sp = mm.find(b" ", pos)
            words[i] = mm[pos:sp].decode("utf-8")
            X[i] = np.frombuffer(mm, dtype="<f4", count=dim, offset=sp + 1)
            pos = sp + 1 + rec
        tail = len(mm) - pos
        mm.close()
    print(f"[tail-check] 剩余未解析字节 = {tail} (应为 0)")
    norms = np.linalg.norm(X, axis=1)
    print(f"[load] {len(words)} words, dim={X.shape[1]}, {time.time()-t0:.1f}s")
    print(f"[norm] mean={norms.mean():.4f} std={norms.std():.4f} min={norms.min():.4f} max={norms.max():.4f}")
    Xn = X / np.maximum(norms, 1e-9)[:, None]
    return words, X, Xn

def topk(Xn, words, q, k=10, exclude=()):
    scores = Xn @ (q / max(np.linalg.norm(q), 1e-9))
    idx = np.argpartition(-scores, k + len(exclude))[: k + len(exclude) * 2]
    out = [(words[i], float(scores[i])) for i in idx if words[i] not in exclude]
    return sorted(out, key=lambda x: -x[1])[:k]

def cmd_inspect(path):
    load(path)

def cmd_demo(path):
    words, X, Xn = load(path)
    wi = {w: i for i, w in enumerate(words)}
    def has(*ws): return all(w in wi for w in ws)
    def vec(w): return Xn[wi[w]]

    for w in ["国王", "爱情", "苹果", "词间"]:
        if has(w):
            nb = topk(Xn, words, vec(w), 8, exclude=(w,))
            print(f"\n[nearest] {w} → " + ", ".join(f"{x}({s:.3f})" for x, s in nb))

    if has("国王", "男人", "女人", "女王"):
        q = vec("国王") - vec("男人") + vec("女人")
        r = topk(Xn, words, q, 5, exclude=("国王", "男人", "女人"))
        print(f"\n[analogy] 国王 − 男人 + 女人 ≈ " + ", ".join(f"{x}({s:.3f})" for x, s in r))

    if has("爱情", "战争"):
        va, vb = vec("爱情"), vec("战争")
        visited = {"爱情", "战争"}; stops = []
        for s in range(1, 8):
            t = s / 8.0
            p = (1 - t) * va + t * vb
            r = topk(Xn, words, p, 3, exclude=tuple(visited))
            if not r: break
            visited.add(r[0][0])
            stops.append(f"{r[0][0]}({r[0][1]:.3f})")
        print(f"\n[path] 爱情 → 战争: " + " → ".join(stops))

    pairs = [("国王","女王"),("爸爸","妈妈"),("北京","中国"),("巴黎","法国"),
             ("猫","狗"),("春天","夏天"),("红色","蓝色"),("高兴","难过"),
             ("老师","学生"),("星期一","星期二"),("米饭","面条"),("山","水"),
             ("医生","护士"),("白天","黑夜"),("牛","牛奶"),("手机","电脑")]
    ok = [p for p in pairs if has(*p)]
    print(f"\n[quiz-pairs] {len(ok)}/{len(pairs)} 对词在词表中: " + "、".join(f"{a}→{b}" for a, b in ok[:12]) + (" ..." if len(ok) > 12 else ""))

def cmd_pca(path):
    words, X, Xn = load(path)
    n, dim = Xn.shape
    outdir = os.path.join(os.path.dirname(__file__), "..", "app/src/main/assets/models")
    outdir = os.path.abspath(outdir); os.makedirs(outdir, exist_ok=True)
    mu = Xn.mean(axis=0)
    Xc = Xn - mu
    C = (Xc.T @ Xc) / (n - 1)
    evals, evecs = np.linalg.eigh(C)
    V = evecs[:, ::-1]                       # 降序特征向量
    print(f"[pca] top eigenvalues: {evals[::-1][:5]}")
    for k, magic, fname in [(2, b"CJP2", "pca2d.f32"), (3, b"CJP3", "pca3d.f32")]:
        coords = (Xc @ V[:, :k]).astype("<f4")
        with open(os.path.join(outdir, fname), "wb") as f:
            f.write(struct.pack("<4sii", magic, n, k))
            f.write(coords.tobytes())
        print(f"[pca] wrote {fname}: {n}x{k} float32, {os.path.getsize(os.path.join(outdir, fname))/1e6:.1f}MB")

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "demo"
    p = sys.argv[2] if len(sys.argv) > 2 else DEFAULT
    {"inspect": cmd_inspect, "demo": cmd_demo, "pca": cmd_pca}[cmd](os.path.abspath(p))
