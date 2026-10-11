CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    current_stock INT NOT NULL,
    name VARCHAR(160) NOT NULL,
    opening_balance INT NOT NULL,
    safety_threshold INT NOT NULL,
    sku VARCHAR(80) NOT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku)
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'SUPPLIER') NOT NULL,
    username VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE stock_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_date DATE NOT NULL,
    delivery_date DATE NOT NULL,
    movement_type ENUM('INWARD', 'OUTWARD') NOT NULL,
    quantity INT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_movement_product_date (product_id, bill_date),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB;