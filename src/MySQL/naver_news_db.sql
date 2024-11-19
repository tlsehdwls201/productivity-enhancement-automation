-- 데이터베이스 생성
CREATE DATABASE naver_news_db;
use naver_news_db;

CREATE TABLE news_search_results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    originallink VARCHAR(500) NOT NULL,
    link VARCHAR(500) NOT NULL,
    description TEXT,
    pubDate DATETIME,
    search_date DATE,
    UNIQUE KEY unique_link (link)
);

-- 이벤트 스케줄러 활성화
SET GLOBAL event_scheduler = ON;


DELIMITER //

CREATE EVENT IF NOT EXISTS limit_news_search_results_event
ON SCHEDULE EVERY 1 MINUTE
DO
BEGIN
    DECLARE total_rows INT;
    DECLARE rows_to_delete INT;

    -- 현재 테이블의 행 수를 확인
    SELECT COUNT(*) INTO total_rows FROM news_search_results;

    -- 행 수가 100개를 초과하면 오래된 데이터 삭제
    IF total_rows > 100 THEN
        SET rows_to_delete = total_rows - 100;
        DELETE FROM news_search_results
        ORDER BY search_date ASC
        LIMIT rows_to_delete;
    END IF;
END;
//

DELIMITER ;

DROP EVENT IF EXISTS limit_news_search_results_event; #이벤트 제거

SHOW VARIABLES LIKE 'event_scheduler';
GRANT EVENT ON your_database_name.* TO 'root'@'localhost';#이벤트를 생성하고 실행하기 위해서는 MySQL 사용자에게 적절한 권한이 부여
FLUSH PRIVILEGES;


SET SQL_SAFE_UPDATES = 0; #update, delete 기능 사용 제한

select * from news_search_results;
delete from news_search_results;

