package database;

import model.FindPathModel;
import model.HttpUrlInfo;
import model.TableLineDataModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static utils.BurpPrintUtils.*;
import static utils.CastUtils.isEmptyObj;

public class UnionTableSql {
    //联合 获取一条需要更新的Path数据
    public static synchronized List<FindPathModel> fetchNeedUpdatePathDataList(int limit){
        List<FindPathModel> findPathModels = new ArrayList<>();

        // 首先选取一条记录的ID 状态是已经分析完毕,并且 当前 PathTree 的 基本路径数量 大于 生成分析数据时的 基本路径数量
        String selectSQL = ("SELECT A.id, A.req_url,A.req_host_port, A.find_path " +
                "From $tableName1$ A LEFT JOIN $tableName2$ B ON A.req_host_port = B.req_host_port " +
                "WHERE A.run_status = ? AND B.basic_path_num > A.basic_path_num Limit ?;")
                .replace("$tableName1$", AnalyseUrlResultTable.tableName)
                .replace("$tableName2$", PathTreeTable.tableName);

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setString(1, Constants.ANALYSE_ING);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FindPathModel  findPathModel = new FindPathModel(
                            rs.getInt("id"),
                            rs.getString("req_url"),
                            rs.getString("req_host_port"),
                            rs.getString("find_path")
                    );
                    findPathModels.add(findPathModel);
                }
            }
        } catch (Exception e) {
            stderr_println(LOG_ERROR, String.format("[-]  Error fetch Need Update Path Data List: %s", e.getMessage()));
        }
        return findPathModels;
    }

    //联合 获取所有行数据
    public static synchronized ArrayList<TableLineDataModel> fetchTableLineDataBySQl(String selectSQL){
        ArrayList<TableLineDataModel> apiDataModels = new ArrayList<>();

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TableLineDataModel apiDataModel = new TableLineDataModel(
                            rs.getInt("id"),
                            rs.getString("msg_hash"),
                            rs.getString("req_url"),
                            rs.getString("req_method"),
                            rs.getInt("resp_status_code"),
                            rs.getString("req_source"),
                            rs.getInt("find_url_num"),
                            rs.getInt("find_path_num"),
                            rs.getInt("find_info_num"),
                            rs.getBoolean("has_important"),
                            rs.getInt("find_api_num"),
                            rs.getInt("path_to_url_num"),
                            rs.getInt("unvisited_url_num"),
                            rs.getString("run_status"),
                            rs.getInt("basic_path_num"),
                            rs.getInt("resp_length")
                    );
                    apiDataModels.add(apiDataModel);
                }
            }
        } catch (Exception e) {
            stderr_println(LOG_ERROR, String.format("[-] Error Fetch All ReqData Left Join InfoAnalyse On MsgHash: %s", e.getMessage()));
        }
        return apiDataModels;
    }

    private static String genSqlByWhereCondition(String WhereCondition){
        String selectSQL = ("SELECT A.id,A.msg_hash,A.req_url,A.req_method,A.resp_status_code,A.req_source,A.run_status,A.resp_length," +
                "B.find_url_num,B.find_path_num,B.find_info_num,B.has_important,B.find_api_num,B.path_to_url_num,B.unvisited_url_num,B.basic_path_num " +
                "from $tableName1$ A LEFT JOIN $tableName2$ B ON A.msg_hash = B.msg_hash $WHERE$ order by A.id;")
                .replace("$tableName1$", ReqDataTable.tableName)
                .replace("$tableName2$", AnalyseUrlResultTable.tableName);

        if (WhereCondition == null) WhereCondition="";

        return selectSQL.replace("$WHERE$", WhereCondition);
    }

    // 获取当前所有记录
    public static synchronized ArrayList<TableLineDataModel> fetchTableLineDataAll() {
        String selectSQL = genSqlByWhereCondition(null);
        return  fetchTableLineDataBySQl(selectSQL);
    }

    //获取有效数据的行
    public static synchronized ArrayList<TableLineDataModel> fetchTableLineDataHasData() {
        // 获取当前所有记录的数据
        String WhereCondition = "Where find_url_num>0 or find_path_num>0 or find_info_num>0";
        String selectSQL = genSqlByWhereCondition(WhereCondition);
        return  fetchTableLineDataBySQl(selectSQL);
    }

    //获取还有未访问完毕的URL的行
    public static synchronized ArrayList<TableLineDataModel> fetchTableLineDataHasUnVisitedUrls() {
        // 获取当前所有记录的数据
        String WhereCondition = "where unvisited_url_num>0";
        String selectSQL = genSqlByWhereCondition(WhereCondition);
        return  fetchTableLineDataBySQl(selectSQL);
    }

    //获取存在敏感信息的行
    public static synchronized ArrayList<TableLineDataModel> fetchTableLineDataHasInfo() {
        // 获取当前所有记录的数据
        String WhereCondition = "where find_info_num>0";
        String selectSQL = genSqlByWhereCondition(WhereCondition);
        return  fetchTableLineDataBySQl(selectSQL);
    }

    //获取没有数据的行,备用,用于后续删除数据
    public static synchronized ArrayList<TableLineDataModel> fetchTableLineDataIsNull() {
        // 获取当前所有记录的数据
        String WhereCondition = "where (find_url_num is null and find_path_num is null and find_info_num is null) or (find_url_num <1  and find_path_num <1 and find_info_num <1) ";
        String selectSQL = genSqlByWhereCondition(WhereCondition);
        return  fetchTableLineDataBySQl(selectSQL);
    }

    //获取没有数据的行,备用,用于后续删除数据
    public static synchronized int clearUselessData() {
        int rowsAffected = -1;

        // 获取当前所有记录的数据
        String deleteSQL = ("DELETE FROM $tableName1$ WHERE id IN (" +
                "SELECT A.id FROM $tableName1$ A LEFT JOIN $tableName2$ B ON A.msg_hash=B.msg_hash " +
                "WHERE (find_url_num IS NULL AND find_path_num IS NULL AND find_info_num IS NULL) " +
                "OR (find_url_num < 1 AND find_path_num < 1 AND find_info_num < 1));")
                .replace("$tableName1$", ReqDataTable.tableName)
                .replace("$tableName2$", AnalyseUrlResultTable.tableName);

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {
            rowsAffected = stmt.executeUpdate();
            stdout_println(LOG_DEBUG, String.format(String.format("[-] table [%s] cleared Useless Data [%s] line.", ReqDataTable.tableName, rowsAffected)));
        } catch (Exception e) {
            stderr_println(LOG_ERROR, String.format("[-] Error clear Useless Data On Table [%s] -> Error:[%s]", ReqDataTable.tableName, e.getMessage()));
            e.printStackTrace();
        }

        return rowsAffected;
    }


    /**
     * 基于 host 列表 同时删除多个 行
     */
    public static synchronized int deleteDataByHosts(List<String> reqHostPortList, String tableName) {
        if (isEmptyObj(reqHostPortList)) return 0;

        // 构建SQL语句，使用占位符 ? 来代表每个ID
        String deleteSQL = "DELETE FROM "+ tableName +"  WHERE req_host_port IN $buildInParamList$;"
                .replace("$buildInParamList$", DBService.buildInParamList(reqHostPortList.size()));

        return runDeleteSql(deleteSQL, reqHostPortList, tableName);
    }

    /**
     * 基于 msgHash 列表 同时删除多个 行
     */
    public static synchronized int deleteDataByMsgHashList(List<String> msgHashList, String tableName) {
        if (isEmptyObj(msgHashList)) return 0;

        // 构建SQL语句，使用占位符 ? 来代表每个ID
        String deleteSQL = "DELETE FROM "+ tableName + "  WHERE msg_hash IN $buildInParamList$;"
                .replace("$buildInParamList$", DBService.buildInParamList(msgHashList.size()));

        return runDeleteSql(deleteSQL, msgHashList, tableName);
    }

    private static int runDeleteSql(String deleteSQL, List<String> msgHashList, String tableName) {
        int totalRowsAffected = 0;

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {

            // 设置SQL语句中的参数值
            int index = 1;
            for (String reqHostPort : msgHashList) {
                stmt.setString(index++, reqHostPort);
            }

            // 执行删除操作
            totalRowsAffected = stmt.executeUpdate();

        } catch (Exception e) {
            stderr_println(String.format("[-] Error deleting Data By reqHostPortList On Table [%s] -> Error:[%s]", tableName, e.getMessage()));
            e.printStackTrace();
        }

        return totalRowsAffected;
    }

    public static synchronized int deleteDataByUrlToHosts(List<String> urlList, String tableName) {
        //获取所有URL的HOST列表
        Set<String> set = new HashSet<>();
        for (String url: urlList){
            HttpUrlInfo urlInfo = new HttpUrlInfo(url);
            set.add(urlInfo.getHostPort());
        }
        ArrayList<String> reqHostPortList = new ArrayList<>(set);

        if (isEmptyObj(reqHostPortList)) return 0;
        return deleteDataByHosts(reqHostPortList,  tableName);
    }

    /**
     * 基于 id 列表 同时删除多个 行
     * @param ids
     * @return
     */
    public static synchronized int deleteDataByIds(List<Integer> ids, String tableName) {
        int totalRowsAffected = 0;

        if (ids.isEmpty()) return totalRowsAffected;

        // 构建SQL语句，使用占位符 ? 来代表每个ID
        String deleteSQL = "DELETE FROM "+ tableName + "  WHERE id IN $buildInParamList$;"
                .replace("$buildInParamList$", DBService.buildInParamList(ids.size()));

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {

            // 设置SQL语句中的参数值
            int index = 1;
            for (Integer id : ids) {
                stmt.setInt(index++, id);
            }

            // 执行删除操作
            totalRowsAffected = stmt.executeUpdate();

        } catch (Exception e) {
            stderr_println(String.format("[-] Error deleting Data By Ids On Table [%s] -> Error:[%s]", tableName, e.getMessage()));
            e.printStackTrace();
        }

        return totalRowsAffected;
    }

    /**
     * 统计所有已加入到数据库的URL的数量
     * @return
     */
    public static synchronized int getTableCounts(String tableName) {
        int count = 0;

        String selectSQL = "SELECT COUNT(*) FROM  "+ tableName +" ;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(selectSQL);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getInt(1); // 获取第一列的值，即 COUNT(*) 的结果
            }
        } catch (Exception e) {
            stderr_println(String.format("Counts Table [%s] Error: %s",tableName, e.getMessage() ));
        }
        return count;
    }

    /**
     * 获取任意表的任意列的字符串拼接
     * @param tableName
     * @param columnName
     * @return
     */
    public static synchronized String fetchConcatColumnToString(String tableName, String columnName) {
        String concatenatedURLs = null;

        String concatSQL = "SELECT GROUP_CONCAT($columnName$,',') AS concatenated_urls FROM "+ tableName +";"
                .replace("$columnName$",columnName);

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(concatSQL)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                concatenatedURLs = rs.getString("concatenated_urls");
            }

        } catch (Exception e) {
            stderr_println(LOG_ERROR, String.format("[-] Error fetching and concatenating URLs: %s", e.getMessage()));
            e.printStackTrace();
        }
        return concatenatedURLs;
    }

    /**
     * 基于 url前缀 列表 删除行
     */
    public static synchronized int deleteDataByRootUr(String rootUrl, String tableName) {
        if (isEmptyObj(rootUrl)) return 0;

        int totalRowsAffected = 0;

        // 构建SQL语句，使用占位符 ? 来代表每个ID
        String deleteSQL = "DELETE FROM "+ tableName + "  WHERE req_url like ?;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {
            // 执行删除操作
            stmt.setString(1, rootUrl+"%");
            totalRowsAffected= stmt.executeUpdate();
        } catch (Exception e) {
            stderr_println(String.format("[-] Error deleting Data By rootUrl On Table [%s] -> Error:[%s]", tableName, e.getMessage()));
            e.printStackTrace();
        }
        return totalRowsAffected;
    }

    public static synchronized int batchDeleteDataByRootUrlList(List<String> rootUrlList, String tableName) {
        if (isEmptyObj(rootUrlList)) return 0;

        int totalRowsAffected = 0;
        String deleteSQL = "DELETE FROM "+ tableName + "  WHERE req_url LIKE ?;";

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {
            // 开启批处理
            conn.setAutoCommit(false);
            // 遍历rootUrlList，为每个rootUrl准备并添加到批处理队列
            for (String rootUrl : rootUrlList) {
                stmt.setString(1, rootUrl + "%");
                stmt.addBatch();
            }
            // 执行批处理
            int[] rowsAffected = stmt.executeBatch();
            conn.commit();
            // 计算受影响的总行数
            for (int row : rowsAffected) {
                totalRowsAffected += row;
            }
        } catch (Exception e) {
            System.err.println(String.format("Error deleting data by rootUrlList on table [%s]: %s", tableName, e.getMessage()));
        }

        return totalRowsAffected;
    }
}
