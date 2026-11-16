# BAllowlist

Paper サーバー用プラグイン。統合版（Bedrock）プレイヤーを、Xbox ゲーマータグだけでホワイトリストに登録できるようにする。

## 背景

Geyser + Floodgate 構成では、統合版プレイヤーの登録に Floodgate UUID が必要で、サーバーコンソールの `whitelist add` では名前から登録できない。このプラグインは「ゲーマータグ → XUID → Floodgate UUID」の変換を肩代わりする。

## 使い方

```
/allowlist add <ゲーマータグ>     # 登録
/allowlist remove <ゲーマータグ>  # 削除
/allowlist list                   # 一覧
```

- 権限: `allowlist.admin`（既定は OP）
- `add` の流れ: Geyser 公式 API（`https://api.geysermc.org/v2/xbox/xuid/<タグ>`）で XUID を取得 → Floodgate UUID（`new UUID(0, xuid)`）を計算 → 表示名は「.」+ ゲーマータグ（スペースは `_`）で whitelist.json に追記 → 稼働中なら `whitelist reload`
- 注意: Geyser API は接続履歴のないプレイヤーを引けないことがある（キャッシュ方式）。その場合はこのプラグインでも登録できない

## ビルド

Maven:

```sh
mvn package
# 成果物: target/BAllowlist.jar
```

- Java 25、Paper API 26.3（`pom.xml` 参照、paper-api と gson は provided スコープ）
- 成果物の jar はサーバーの `plugins/` に置く

## 構成

- `src/main/java/jp/koro/bwhitelist/BAllowlist.java` — 本体
- `src/main/resources/plugin.yml` — プラグイン定義
