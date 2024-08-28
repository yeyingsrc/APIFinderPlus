package database;

import model.AccessedUrlInfo;
import model.HttpMsgInfo;
import model.HttpUrlInfo;
import utils.CastUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static utils.BurpPrintUtils.*;

public class RecordUrlTable {
    //数据表名称
    public static String tableName = "RECORD_URL";
    public static String urlHashName = "url_hash";

    //创建用于存储所有 访问成功的 URL的数据库 record_urls
    static String creatTableSQL = "CREATE TABLE IF NOT EXISTS  "+ tableName +"  (\n"
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,\n"  //自增的id
            + "url_hash TEXT UNIQUE,\n"
            + "req_host_port TEXT NOT NULL,\n"
            + "req_url TEXT NOT NULL,\n"  //记录访问过的URL
            + "resp_status_code INTEGER\n" //记录访问过的URL状态
            + ");";


    //插入访问的URl
    public static synchronized int insertOrUpdateAccessedUrl(String reqUrl,String reqHostPort, int respStatusCode, String urlHash) {
        int generatedId = -1;
        String upsertSql = "INSERT INTO "+ tableName +
                " (req_url, req_host_port, resp_status_code, url_hash)" +
                " VALUES (?,?, ?, ?)" +
                " ON CONFLICT(url_hash) DO UPDATE SET resp_status_code = EXCLUDED.resp_status_code;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(upsertSql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, reqUrl);
            stmt.setString(2, reqHostPort);
            stmt.setInt(3, respStatusCode);
            stmt.setString(4, urlHash);

            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    generatedId = generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println(String.format("Error insert Or Update Accessed Url On table [%s] -> Error:[%s]", tableName, e.getMessage()));
        }

        return generatedId;
    }

    //插入访问的URl 复用
    public static synchronized int insertOrUpdateAccessedUrl(String reqUrl,String reqHostPort, int respStatusCode){
        return insertOrUpdateAccessedUrl(reqUrl,reqHostPort, respStatusCode, CastUtils.calcCRC32(reqUrl));
    }

    //插入访问的URl 复用
    public static synchronized int insertOrUpdateAccessedUrl(HttpMsgInfo msgInfo) {
        return insertOrUpdateAccessedUrl(
                msgInfo.getUrlInfo().getRawUrlUsual(),
                msgInfo.getUrlInfo().getHostPort(),
                msgInfo.getRespStatusCode(),
                CastUtils.calcCRC32(msgInfo.getUrlInfo().getRawUrlUsual())
        );
    }

    //插入访问的URl 复用
    public static synchronized int insertOrUpdateAccessedUrl(String reqUrl, int respStatusCode) {
        return insertOrUpdateAccessedUrl(reqUrl, new HttpUrlInfo(reqUrl).getHostPort(), respStatusCode, CastUtils.calcCRC32(reqUrl));
    }

    //实现批量插入访问信息
    public static synchronized int[] batchInsertOrUpdateAccessedUrls(List<AccessedUrlInfo> accessedUrlInfos) {
        int[] generatedIds = null;

        String upsertSql = "INSERT INTO "+ tableName +
                " (req_url, req_host_port, resp_status_code, url_hash)" +
                " VALUES (?, ?, ?, ?)" +
                " ON CONFLICT(url_hash) DO UPDATE SET resp_status_code = EXCLUDED.resp_status_code;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(upsertSql, Statement.RETURN_GENERATED_KEYS)) {
            // 添加到批处理队列
            conn.setAutoCommit(false); // 开启事务
            for (AccessedUrlInfo accessedUrlInfo : accessedUrlInfos) {
                stmt.setString(1, accessedUrlInfo.getReqUrl());
                stmt.setString(2, accessedUrlInfo.getReqHostPort());
                stmt.setInt(3, accessedUrlInfo.getRespStatusCode());
                stmt.setString(4, accessedUrlInfo.getUrlHash());
                stmt.addBatch();
            }
            // 执行批处理
            generatedIds = stmt.executeBatch();
            conn.commit(); // 提交事务

//            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
//                while (generatedKeys.next()) {
//                    generatedIds.add(generatedKeys.getInt(1));
//                }
//            }
        } catch (Exception e) {
            System.err.println(String.format("Error batch insert Or Update Accessed Urls On table [%s] -> Error:[%s]", tableName, e.getMessage()));
        }

        return generatedIds;
    }


    //实现批量插入访问信息 复用
    public static synchronized int[] batchInsertOrUpdateAccessedUrls(List<String> accessedUrls, int respStatusCode){
        List<AccessedUrlInfo> accessedUrlInfos = new ArrayList<>();
        for (String reqUrl : accessedUrls){
            String reqHostPort = new HttpUrlInfo(reqUrl).getHostPort();
            AccessedUrlInfo accessedUrlInfo = new AccessedUrlInfo(reqUrl, reqHostPort,respStatusCode);
            accessedUrlInfos.add(accessedUrlInfo);
        }
        return batchInsertOrUpdateAccessedUrls(accessedUrlInfos);
    }


    //基于host获取获取所有访问过的URL
    public static synchronized List<String> fetchAllAccessedUrls(String reqHostPort) {
        Set<String> uniqueURLs = new HashSet<>();

        String selectSql = "SELECT req_url FROM "+ tableName +"  WHERE req_host_port = ?;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            // 获取所有的URL
            stmt.setString(1, reqHostPort);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String reqUrl = rs.getString("req_url");
                uniqueURLs.add(reqUrl);
            }

    } catch (Exception e) {
            stderr_println(String.format("[-] Error fetch [%s] All Accessed Url By reqHostPort: %s", tableName, e.getMessage()));
            e.printStackTrace();
        }

        return new ArrayList<>(uniqueURLs);
    }


    //获取所有访问过的URL
    public static synchronized List<String> fetchAllAccessedUrls() {
        List<String> uniqueURLs = new ArrayList<>();

        String selectSql = "SELECT req_url FROM "+ tableName +" ;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            // 获取所有的URL
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String reqUrl = rs.getString("req_url");
                uniqueURLs.add(reqUrl);
            }

        } catch (Exception e) {
            stderr_println(String.format("[-] Error fetch [%s] All Accessed Url: %s", tableName, e.getMessage()));
            e.printStackTrace();
        }
        return uniqueURLs;
    }


}
