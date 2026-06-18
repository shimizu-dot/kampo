# 漢方薬データベース テーブル設計書

## 1. 目的

添付PDFの記載内容から、漢方薬ごとの以下の情報を管理する。

- 販売名
- 読み方
- 有効成分名
- 成分ごとの配合量
- 4. 効能又は効果
- 6. 用法及び用量

本設計では、販売名を主軸にしつつ、有効成分はマスタ化し、販売名と有効成分の関係は中間テーブルで保持する。

## 2. 設計方針

1. 販売名は1レコードで管理する。
2. 有効成分名は再利用できるようにマスタ化する。
3. 1つの販売名に複数の有効成分が紐付くため、中間テーブルを設ける。
4. 配合量は「販売名 - 有効成分」の組み合わせに属する属性として保持する。
5. 「効能又は効果」「用法及び用量」は、PDF記載をそのまま保持できるようテキスト型で持つ。
6. 販売名の読み方は、検索補助や表示補助のため主テーブルで保持する。

## 3. テーブル一覧

### 3.1 `kampo_products`

漢方薬の販売名を管理する主テーブル。

| 項目名 | 論理名 | 型 | 必須 | 主キー/外部キー | 説明 |
|---|---|---:|---|---|---|
| `id` | ID | BIGSERIAL | Yes | PK | 主キー |
| `identification_code` | 識別コード | VARCHAR(50) | Yes |  | PDF内の識別コード。例: `ツムラ／5` の `5` |
| `sales_name` | 販売名 | VARCHAR(100) | Yes |  | 例: ツムラ安中散エキス顆粒（医療用） |
| `reading` | 読み方 | VARCHAR(255) | No |  | 販売名の読み。手動補正も可能 |
| `efficacy_condition_text` | 効能又は効果（前段） | TEXT | Yes |  | 症状や適応条件の記載 |
| `efficacy_indication_text` | 摘要 | TEXT | Yes |  | 疾患名や適応の記載 |
| `dosage_daily_amount` | 1日量 | NUMERIC(10,3) | Yes |  | 例: 7.5 |
| `dosage_instructions_text` | 用法 | TEXT | Yes |  | 例: 2～3回に分割し、食前又は食間に経口投与 |
| `source_file_name` | 元ファイル名 | VARCHAR(255) | No |  | 取り込み元PDF名 |
| `source_document_no` | 文書番号 | VARCHAR(100) | No |  | 例: No.005 |
| `created_at` | 作成日時 | TIMESTAMP | Yes |  | 作成日時 |
| `updated_at` | 更新日時 | TIMESTAMP | Yes |  | 更新日時 |

#### 補足

- `sales_name` は一意制約を付与することを推奨する。
- `identification_code` は、PDF内の識別表示から抽出したコードを保存する。
- `reading` は PDF から自動抽出できない場合があるため、取り込み後の編集画面で補正できるようにする。
- PDF本文が将来改訂される可能性があるため、元ファイル名や文書番号を残せるようにする。

### 3.2 `kampo_ingredients`

有効成分名を管理するマスタテーブル。

| 項目名 | 論理名 | 型 | 必須 | 主キー/外部キー | 説明 |
|---|---|---:|---|---|---|
| `id` | ID | BIGSERIAL | Yes | PK | 主キー |
| `ingredient_name` | 有効成分名 | VARCHAR(255) | Yes |  | 例: ケイヒ |
| `created_at` | 作成日時 | TIMESTAMP | Yes |  | 作成日時 |
| `updated_at` | 更新日時 | TIMESTAMP | Yes |  | 更新日時 |

#### 補足

- 同一成分を複数製品で使う前提のため、成分は独立マスタとする。
- 例示PDFでは、`日局` などの接頭語を含めるかは運用ルールで統一する。
  - 推奨: 保存値は正規化した成分名のみとし、必要に応じて原文は別属性で保持する。

### 3.3 `kampo_product_ingredients`

販売名と有効成分名の紐付け、および配合量を管理する中間テーブル。

| 項目名 | 論理名 | 型 | 必須 | 主キー/外部キー | 説明 |
|---|---|---:|---|---|---|
| `id` | ID | BIGSERIAL | Yes | PK | 主キー |
| `product_id` | 販売名ID | BIGINT | Yes | FK | `kampo_products.id` 参照 |
| `ingredient_id` | 有効成分ID | BIGINT | Yes | FK | `kampo_ingredients.id` 参照 |
| `amount_value` | 配合量数値 | NUMERIC(10,3) | Yes |  | 例: 4.000 |
| `amount_unit` | 単位 | VARCHAR(20) | Yes |  | 例: g |
| `sort_order` | 表示順 | INT | Yes |  | PDFの記載順を保持する |
| `raw_amount_text` | 原文配合量 | TEXT | No |  | 原文をそのまま残したい場合 |
| `created_at` | 作成日時 | TIMESTAMP | Yes |  | 作成日時 |
| `updated_at` | 更新日時 | TIMESTAMP | Yes |  | 更新日時 |

#### 補足

- 配合量は販売名ごとの成分属性であるため、中間テーブルに持たせる。
- 1つの販売名に複数の成分がある前提のため、`sort_order` で表示順を復元できるようにする。
- 同一販売名に同一成分が重複登録されないよう、`(product_id, ingredient_id)` に一意制約を推奨する。

## 4. リレーション

- `kampo_products` 1 - N `kampo_product_ingredients`
- `kampo_ingredients` 1 - N `kampo_product_ingredients`

### 図示

```text
kampo_products
  1
  └── N kampo_product_ingredients N ── 1 kampo_ingredients
```

## 5. PDF取り込み時の格納イメージ

対象PDF: ツムラ安中散エキス顆粒（医療用）

### 5.1 `kampo_products`

- `sales_name`
  - `ツムラ安中散エキス顆粒（医療用）`
- `reading`
  - `つむらあんちゅうさんえきすかりゅう`
- `identification_code`
  - `5`
- `efficacy_condition_text`
  - `やせ型で腹部筋肉が弛緩する傾向にあり、胃痛または腹痛があって、ときに胸やけ、げっぷ、食欲不振、はきけなど`
- `efficacy_indication_text`
  - `神経性胃炎、慢性胃炎、胃アトニー`
- `dosage_daily_amount`
  - `7.5`
- `dosage_instructions_text`
  - `2～3回に分割し、食前又は食間に経口投与`

### 5.2 `kampo_ingredients` / `kampo_product_ingredients`

| 順序 | 有効成分名 | 配合量 | 単位 |
|---|---|---:|---|
| 1 | ケイヒ | 4.0 | g |
| 2 | エンゴサク | 3.0 | g |
| 3 | ボレイ | 3.0 | g |
| 4 | ウイキョウ | 1.5 | g |
| 5 | カンゾウ | 1.0 | g |
| 6 | シュクシャ | 1.0 | g |
| 7 | リョウキョウ | 0.5 | g |

## 6. 実装上の注意

1. PDFの表記ゆれに備え、成分名の正規化ルールを別途定義する。
2. 「日局」などの接頭語を保持するかは、検索要件に応じて決める。
3. 「効能又は効果」「用法及び用量」は改行を含む可能性があるため、テキスト型で保持する。
4. 将来的に承認番号、販売開始日、製造販売元、包装情報なども必要になる可能性があるため、拡張しやすいように主情報と補助情報を分けて設計する。

## 7. 推奨する最小スコープ

初期実装では、以下の3テーブルで十分に要件を満たす。

- `kampo_products`
- `kampo_ingredients`
- `kampo_product_ingredients`
