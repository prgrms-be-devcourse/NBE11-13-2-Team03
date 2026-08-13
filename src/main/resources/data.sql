-- 1. GOODS 테이블 생성 (상품 기본 정보)
CREATE TABLE goods (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(100),
    price       INT NOT NULL,
    image_url   VARCHAR(500),
    status      VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. GOODS_SALES 테이블 생성 (상품 판매/재고 정보)
CREATE TABLE goods_sales (
    id                    BIGSERIAL PRIMARY KEY,
    goods_id              BIGINT NOT NULL,
    created_by            BIGINT NOT NULL,
    initial_stock         INT NOT NULL,
    remaining_stock       INT NOT NULL,
    max_purchase_quantity INT,
    status                VARCHAR(50) NOT NULL,
    start_at              TIMESTAMP NOT NULL,
    end_at                TIMESTAMP NOT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT fk_goods_sales_goods_id FOREIGN KEY (goods_id)
    REFERENCES goods (id) ON DELETE CASCADE
);