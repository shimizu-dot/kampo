-- 漢方薬データベース schema
-- PostgreSQL想定

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS kampo_products (
    id BIGSERIAL PRIMARY KEY,
    identification_code VARCHAR(50) NOT NULL,
    sales_name VARCHAR(100) NOT NULL,
    reading VARCHAR(100),
    efficacy_condition_text TEXT NOT NULL,
    efficacy_indication_text TEXT NOT NULL,
    dosage_daily_amount NUMERIC(10,3) NOT NULL,
    dosage_instructions_text TEXT NOT NULL,
    source_file_name VARCHAR(255),
    source_document_no VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_kampo_products_sales_name UNIQUE (sales_name)
);

ALTER TABLE kampo_products
    ALTER COLUMN reading TYPE VARCHAR(100);

CREATE TABLE IF NOT EXISTS kampo_ingredients (
    id BIGSERIAL PRIMARY KEY,
    ingredient_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_kampo_ingredients_ingredient_name UNIQUE (ingredient_name)
);

CREATE TABLE IF NOT EXISTS kampo_product_ingredients (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    amount_value NUMERIC(10,3) NOT NULL,
    amount_unit VARCHAR(20) NOT NULL DEFAULT 'g',
    sort_order INT NOT NULL,
    raw_amount_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kampo_product_ingredients_product
        FOREIGN KEY (product_id) REFERENCES kampo_products(id),
    CONSTRAINT fk_kampo_product_ingredients_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES kampo_ingredients(id),
    CONSTRAINT uq_kampo_product_ingredients_product_ingredient
        UNIQUE (product_id, ingredient_id)
);

CREATE INDEX IF NOT EXISTS idx_kampo_product_ingredients_product_id
    ON kampo_product_ingredients(product_id);

CREATE INDEX IF NOT EXISTS idx_kampo_product_ingredients_ingredient_id
    ON kampo_product_ingredients(ingredient_id);

CREATE INDEX IF NOT EXISTS idx_kampo_product_ingredients_product_sort_id
    ON kampo_product_ingredients(product_id, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_kampo_products_identification_code
    ON kampo_products(identification_code);

CREATE INDEX IF NOT EXISTS idx_kampo_products_sales_name_trgm
    ON kampo_products USING gin (sales_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_kampo_products_reading_trgm
    ON kampo_products USING gin (reading gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_kampo_products_summary_trgm
    ON kampo_products USING gin (efficacy_indication_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_kampo_ingredients_name_trgm
    ON kampo_ingredients USING gin (ingredient_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_kampo_products_identification_sort
    ON kampo_products (
        (CASE WHEN identification_code ~ '^[0-9]+$' THEN identification_code::bigint END),
        identification_code,
        id DESC
    );
