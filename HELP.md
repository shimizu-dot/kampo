# 漢方PDF取込アプリ マニュアル

##データベース作成
psql -h localhost -U postgres -d postgres -c "CREATE DATABASE kanpo;"

##スキーマ作成
psql -h localhost -U postgres -d kanpo -f src/main/resources/schema.sql

## 起動方法

1. PostgreSQL を起動する
2. プロジェクト直下で実行する

```bash
.\mvnw.cmd spring-boot:run
```

3. ブラウザで開く

- `http://localhost:8080/kampo/import`
- `http://localhost:8080/kampo/search`

### うまく起動しないとき

- `8080` 番ポートが使用中だと起動できません
- その場合は既存プロセスを停止するか、別ポートで起動してください

例:

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## 画面構成

### 取込

- PDF をアップロードして内容を抽出する
- 読み方は、PDF の `漢方製剤` の直後にあるカタカナ行から抽出し、半角スペースと全角スペースを除去して保存する
- 抽出後は確認・編集画面に進む
- 必要に応じて内容を修正して登録する

### 確認・編集

- 抽出された内容をそのまま確認できる
- 販売名、識別コード、摘要、効能、用法、有効成分などを修正できる
- 修正後に登録する

### 検索

- 対象を 1 つ選んで検索する
- 対象は `コード / 成分 / 摘要`
- キーワードを入れて実行する
- 全件一覧はヘッダーの `一覧` を使う

## 検索の使い方

### 検索対象の選択

- 検索対象は 1 つ選ぶ
- `コード` は完全一致
- `成分` と `摘要` はあいまい検索

### キーワード入力

- 1 つの入力欄に検索語を入れる
- 例: `5`、`ケイヒ`、`腹痛`
- 一致した有効成分名だけを色付きで表示する

### 全件一覧

- ヘッダーの `一覧` を使う
- 一覧は「コード」と「販売名」のみ表示する
- 識別コード順で表示する

### バックアップ

- ヘッダーの `バックアップ` を使う
- 現在の DB を SQL ファイルとして保存する
- 保存先は `src/backup`
- ファイル名に日付と時刻が入る

## 画面の表示仕様

- モバイル対応済み
- 検索結果の詳細は折りたたみ表示
- 一覧は表形式
- 検索結果に件数表示を出す
- ページをリロードすると検索条件はクリアされる
- 検索画面の移動はヘッダーの `取込 / 検索 / 一覧` を使う

## エラーメッセージ

- 保存失敗時は、DB 接続エラー、重複登録、必須項目不足などを画面に具体的に出す
- テーブル未作成の場合は、その旨を表示する
- 詳細情報も補助表示するので、ログ確認の手がかりになる

## データ設計の補足

- 販売名は `kampo_products`
- 有効成分名は `kampo_ingredients`
- 販売名と有効成分の紐付けは `kampo_product_ingredients`

## 参考

- テーブル設計の詳細は [docs/kampo_table_design.md](docs/kampo_table_design.md) を参照

## Render でデプロイする場合

- Render の Web Service では、DB 接続情報を環境変数で渡す
- まずは `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` を設定する
- Render 側で JDBC 形式の接続文字列が用意される場合は `JDBC_DATABASE_URL` / `JDBC_DATABASE_USERNAME` / `JDBC_DATABASE_PASSWORD` でも可
- `spring.sql.init.mode=always` のため、起動時に `src/main/resources/schema.sql` を実行する
- ローカルでは未設定時に `localhost:5432/kanpo` を使う