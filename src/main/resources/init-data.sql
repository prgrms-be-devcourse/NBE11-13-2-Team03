SET TIME ZONE 'Asia/Seoul';

BEGIN;

-- =========================================================
-- 1. 기존 데이터 초기화
-- =========================================================
-- users는 유지한다.
-- 구매 / 결제 데이터는 실제 서비스 흐름에서 생성한다.
TRUNCATE TABLE
    payments,
    purchases,
    goods_sales,
    goods
RESTART IDENTITY CASCADE;


-- =========================================================
-- 2. 상품 초기 데이터
-- =========================================================
-- 총 9개
-- ACTIVE 8개 + INACTIVE 1개
-- 아티산 G 키캡은 관리자 직접 등록 시연용으로 제외
-- =========================================================
INSERT INTO goods (
    name,
    description,
    price,
    image_url,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'GUDIT 아크릴 키링',
        'GUDIT 캐릭터와 로고를 담은 한정 아크릴 키링',
        8900,
        '/images/products/Acrylic_Keyring.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT 스티커 팩',
        'GUDIT 캐릭터를 다양한 디자인으로 구성한 스티커 팩',
        5900,
        '/images/products/Sticker_Pack.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT 티셔츠',
        'GUDIT 캐릭터 그래픽을 적용한 데일리 티셔츠',
        29000,
        '/images/products/T-Shirt.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT 텀블러',
        '심플한 GUDIT 로고 디자인의 스테인리스 텀블러',
        24000,
        '/images/products/Tumbler.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT 데스크 매트',
        'GUDIT 캐릭터 그래픽을 적용한 와이드 데스크 매트',
        32000,
        '/images/products/Desk_Mat.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT 노트북 파우치',
        '노트북을 안전하게 보관할 수 있는 GUDIT 전용 파우치',
        35000,
        '/images/products/Laptop_Sleeve.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT Alpha 65 기계식 키보드',
        '한정 수량으로 제작된 GUDIT Alpha 65 기계식 키보드',
        159000,
        '/images/products/Alpha_Mechanical_Keyboard.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT Grid 코일드 케이블',
        '기계식 키보드와 함께 사용할 수 있는 GUDIT 코일드 케이블',
        39000,
        '/images/products/Grid_Coiled_Cable.png',
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'GUDIT 머그컵',
        'GUDIT 캐릭터를 담은 데일리 세라믹 머그컵',
        15900,
        '/images/products/Mug.png',
        'INACTIVE',
        NOW(),
        NOW()
    );


-- =========================================================
-- 3. 판매 초기 데이터
-- =========================================================
-- 총 8개
--
-- ON_SALE은 SQL에 직접 넣지 않는다.
-- 시작 시간이 지난 READY 판매는 Spring Scheduler가
-- Redis Warm-up 후 ON_SALE로 전환한다.
-- =========================================================


-- ---------------------------------------------------------
-- Sale 1
-- 아크릴 키링
-- 시작 시간이 이미 지났으므로 앱 실행 후 ON_SALE 전환 대상
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           1,
           NULL,
           100,
           100,
           2,
           'READY',
           NOW() - INTERVAL '10 minutes',
           NOW() + INTERVAL '1 day 6 hours',
           NULL,
           NOW() - INTERVAL '10 minutes',
           NOW()
       );


-- ---------------------------------------------------------
-- Sale 2
-- 스티커 팩
-- 5분 후 판매 시작
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           2,
           NULL,
           150,
           150,
           3,
           'READY',
           NOW() + INTERVAL '5 minutes',
           NOW() + INTERVAL '3 days',
           NULL,
           NOW(),
           NOW()
       );


-- ---------------------------------------------------------
-- Sale 3
-- 티셔츠
-- 과거에 정상 종료된 판매
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           3,
           NULL,
           80,
           12,
           2,
           'CLOSED',
           NOW() - INTERVAL '8 days',
           NOW() - INTERVAL '3 days',
           NOW() - INTERVAL '3 days',
           NOW() - INTERVAL '8 days',
           NOW() - INTERVAL '3 days'
       );


-- ---------------------------------------------------------
-- Sale 4
-- 텀블러
-- 판매 기간은 남아 있지만 재고가 모두 소진된 상태
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           4,
           NULL,
           60,
           0,
           1,
           'SOLD_OUT',
           NOW() - INTERVAL '2 days',
           NOW() + INTERVAL '10 hours',
           NULL,
           NOW() - INTERVAL '2 days',
           NOW()
       );


-- ---------------------------------------------------------
-- Sale 5
-- 데스크 매트
-- 하루 뒤 판매 시작
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           5,
           NULL,
           50,
           50,
           1,
           'READY',
           NOW() + INTERVAL '1 day',
           NOW() + INTERVAL '5 days',
           NULL,
           NOW(),
           NOW()
       );


-- ---------------------------------------------------------
-- Goods 6
-- 노트북 파우치
-- INACTIVE 상품이므로 판매 데이터 없음
-- ---------------------------------------------------------


-- ---------------------------------------------------------
-- Sale 6
-- Alpha 65 기계식 키보드
-- 8분 후 판매 시작
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           7,
           NULL,
           30,
           30,
           1,
           'READY',
           NOW() + INTERVAL '8 minutes',
           NOW() + INTERVAL '2 days 12 hours',
           NULL,
           NOW(),
           NOW()
       );


-- ---------------------------------------------------------
-- Sale 7
-- Grid 코일드 케이블
-- 재고가 모두 소진된 품절 판매
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           8,
           NULL,
           45,
           0,
           1,
           'SOLD_OUT',
           NOW() - INTERVAL '1 day',
           NOW() + INTERVAL '2 days',
           NULL,
           NOW() - INTERVAL '1 day',
           NOW()
       );


-- ---------------------------------------------------------
-- Sale 8
-- 노트북 파우치
-- 시작 시간이 이미 지났으므로 앱 실행 후 ON_SALE 전환 대상
-- ---------------------------------------------------------
INSERT INTO goods_sales (
    goods_id,
    created_by,
    initial_stock,
    remaining_stock,
    max_purchase_quantity,
    status,
    start_at,
    end_at,
    final_stock_synced_at,
    created_at,
    updated_at
)
VALUES (
           6,
           NULL,
           40,
           40,
           1,
           'READY',
           NOW() - INTERVAL '5 minutes',
           NOW() + INTERVAL '4 days',
           NULL,
           NOW() - INTERVAL '5 minutes',
           NOW()
       );


COMMIT;


-- =========================================================
-- 4. 상품 확인
-- =========================================================
SELECT
    id,
    name,
    price,
    status,
    image_url
FROM goods
ORDER BY id;


-- =========================================================
-- 5. 판매 확인
-- =========================================================
SELECT
    s.id AS sale_id,
    g.id AS goods_id,
    g.name AS goods_name,
    g.status AS goods_status,
    s.status AS sale_status,
    s.initial_stock,
    s.remaining_stock,
    s.max_purchase_quantity,
    s.start_at,
    s.end_at,
    s.final_stock_synced_at
FROM goods_sales s
         JOIN goods g
              ON g.id = s.goods_id
ORDER BY s.id;


-- =========================================================
-- 6. 상품 상태별 개수
-- =========================================================
SELECT
    status,
    COUNT(*) AS count
FROM goods
GROUP BY status
ORDER BY status;


-- =========================================================
-- 7. 판매 상태별 개수
-- =========================================================
SELECT
    status,
    COUNT(*) AS count
FROM goods_sales
GROUP BY status
ORDER BY status;