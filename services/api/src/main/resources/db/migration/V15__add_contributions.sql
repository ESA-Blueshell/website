CREATE TABLE contribution_periods
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    start_date    DATE           NOT NULL,
    end_date      DATE           NOT NULL,
    half_year_fee DECIMAL(10, 2) NOT NULL,
    full_year_fee DECIMAL(10, 2) NOT NULL,
    alumni_fee    DECIMAL(10, 2) NOT NULL,
    list_id       BIGINT         NOT NULL
);

CREATE TABLE contributions
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT NOT NULL,
    contribution_period_id BIGINT NOT NULL,
    paid                   BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (contribution_period_id) REFERENCES contribution_periods (id)
);
