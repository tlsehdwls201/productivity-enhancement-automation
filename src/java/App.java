package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

public class App implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // 설정 파일 로드
        Properties props = configLoader.getProperties();

        String clientId = props.getProperty("naver.clientId");
        String clientSecret = props.getProperty("naver.clientSecret");
        String jdbcUrl = props.getProperty("db.url");
        String dbUser = props.getProperty("db.user");
        String dbPassword = props.getProperty("db.password");

        String keywordsStr = props.getProperty("search.keywords");
        String[] keywords = keywordsStr.split(",");

        int display = Integer.parseInt(props.getProperty("search.display"));
        String sort = props.getProperty("search.sort");

        // 현재 시간 기준으로 24시간 전 시간 계산
        Calendar calendar = Calendar.getInstance();
        Date currentTime = calendar.getTime();
        calendar.add(Calendar.HOUR_OF_DAY, -24);
        Date twentyFourHoursAgo = calendar.getTime();

        for (String keyword : keywords) {
            keyword = keyword.trim();
            String apiURL = "https://openapi.naver.com/v1/search/news.json?query="
                    + encodeURIComponent(keyword) + "&display=" + display + "&sort=" + sort;

            // HTTP 클라이언트 생성 및 API 요청
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(apiURL);
                request.addHeader("X-Naver-Client-Id", clientId);
                request.addHeader("X-Naver-Client-Secret", clientSecret);

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode == 200) { // 성공
                        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                        StringBuilder responseBody = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBody.append(line);
                        }

                        // JSON 파싱
                        Gson gson = new Gson();
                        JsonObject jsonObject = gson.fromJson(responseBody.toString(), JsonObject.class);
                        JsonArray items = jsonObject.getAsJsonArray("items");

                        // 데이터베이스 연결 및 데이터 삽입
                        insertDataToDB(items, keyword, jdbcUrl, dbUser, dbPassword, twentyFourHoursAgo);
                    } else { // 실패
                        System.err.println("API 요청 실패. 상태 코드: " + statusCode + " | 키워드: " + keyword);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("키워드 '" + keyword + "'에 대한 데이터 수집 중 오류 발생.");
            }
        }
    }

    /**
     * URL 인코딩을 수행합니다.
     *
     * @param s 인코딩할 문자열
     * @return 인코딩된 문자열
     */
    private String encodeURIComponent(String s) {
        String result = null;
        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * JSON 배열의 데이터를 MySQL 데이터베이스에 삽입합니다.
     * 단, pubDate가 24시간 이내인 경우에만 삽입합니다.
     *
     * @param items          네이버 API 응답의 'items' 배열
     * @param keyword        검색에 사용된 키워드
     * @param jdbcUrl        데이터베이스 URL
     * @param dbUser         데이터베이스 사용자명
     * @param dbPassword     데이터베이스 비밀번호
     * @param twentyFourHoursAgo 24시간 전 시간
     */
    private void insertDataToDB(JsonArray items, String keyword, String jdbcUrl, String dbUser, String dbPassword, Date twentyFourHoursAgo) {
        String insertSQL = "INSERT INTO news_search_results (keyword, title, originallink, link, description, pubDate, search_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE title = VALUES(title), originallink = VALUES(originallink), " +
                "description = VALUES(description), pubDate = VALUES(pubDate), search_date = VALUES(search_date)";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            conn.setAutoCommit(false); // 트랜잭션 시작

            java.sql.Date searchDate = new java.sql.Date(System.currentTimeMillis());

            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();

                String title = removeHtmlTags(item.get("title").getAsString());
                String originallink = item.get("originallink").getAsString();
                String link = item.get("link").getAsString();
                String description = removeHtmlTags(item.get("description").getAsString());
                String pubDateStr = item.get("pubDate").getAsString(); // 예: "Wed, 09 Oct 2023 00:00:00 +0900"

                Timestamp pubDate = null;
                try {
                    pubDate = parsePubDate(pubDateStr);
                } catch (ParseException e) {
                    // 날짜 형식이 잘못된 경우 처리
                    System.err.println("잘못된 날짜 형식: " + pubDateStr);
                    continue; // 해당 기사를 건너뜀
                }

                // pubDate가 24시간 이내인지 확인
                if (pubDate.before(new Timestamp(twentyFourHoursAgo.getTime()))) {
                    // 24시간 이전의 기사이므로 삽입하지 않음
                    continue;
                }

                pstmt.setString(1, keyword);
                pstmt.setString(2, title);
                pstmt.setString(3, originallink);
                pstmt.setString(4, link);
                pstmt.setString(5, description);
                pstmt.setTimestamp(6, pubDate);
                pstmt.setDate(7, searchDate);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit(); // 트랜잭션 커밋
            System.out.println("키워드 '" + keyword + "'에 대한 24시간 내 데이터가 성공적으로 삽입되었습니다.");

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("데이터베이스 삽입 중 오류 발생.");
        }
    }

    /**
     * 문자열에서 HTML 태그를 제거합니다.
     *
     * @param htmlString HTML 태그가 포함된 문자열
     * @return 태그가 제거된 순수 텍스트
     */
    private String removeHtmlTags(String htmlString) {
        return htmlString.replaceAll("<[^>]*>", "").replaceAll("&quot;", "\"").replaceAll("&amp;", "&");
    }

    /**
     * pubDate 문자열을 java.sql.Timestamp로 변환합니다.
     *
     * @param pubDateStr 네이버 API의 pubDate 문자열
     * @return 변환된 Timestamp 객체
     * @throws ParseException 날짜 형식이 잘못된 경우
     */
    private Timestamp parsePubDate(String pubDateStr) throws ParseException {
        // 예: "Wed, 09 Oct 2023 00:00:00 +0900"
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH);
        java.util.Date parsedDate = formatter.parse(pubDateStr);
        return new Timestamp(parsedDate.getTime());
    }
}
