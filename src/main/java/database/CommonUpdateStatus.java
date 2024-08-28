package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static utils.BurpPrintUtils.*;

public class CommonUpdateStatus {
    /**
     * 更新多个 ID列表 的状态
     */
    public static synchronized int updateStatusByIds(String tableName, List<Integer> ids, String updateStatus) {
        int updatedCount = -1;

        String updateSQL = "UPDATE " + tableName + " SET run_status = ? WHERE id IN $buildInParamList$;"
                .replace("$buildInParamList$", DBService.buildInParamList(ids.size()));

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmtUpdate = conn.prepareStatement(updateSQL)) {
            stmtUpdate.setString(1, updateStatus);

            for (int i = 0; i < ids.size(); i++) {
                stmtUpdate.setInt(i + 2, ids.get(i));
            }

            updatedCount = stmtUpdate.executeUpdate();

            if (updatedCount != ids.size()) {
                stderr_println(LOG_DEBUG, "[!] Number of updated rows does not match number of selected rows.");
            }
        } catch (Exception e) {
            stderr_println(LOG_ERROR, String.format("[-] Error updating [%s] Data Status: %s", tableName, e.getMessage()));
        }
        return updatedCount;
    }

    //修改ID对应的数据状态 为 分析中
    public static int updateStatusRunIngByIds(String tableName, List<Integer> ids) {
        return updateStatusByIds(tableName, ids, Constants.ANALYSE_ING);
    }

    // 修改ID对应的数据状态 为 分析完成
    public static int updateStatusRunEndByIds(String tableName, List<Integer> ids) {
        return updateStatusByIds(tableName, ids, Constants.ANALYSE_END);
    }

    /**
     * 更新多个 msgHash 的状态
     */
    public static synchronized int updateStatusByMsgHashList(String tableName, List<String> msgHashList, String updateStatus) {
        int updatedCount = -1;

        String updateSQL = "UPDATE " + tableName + " SET run_status = ? WHERE msg_hash IN $buildInParamList$;"
                .replace("$buildInParamList$", DBService.buildInParamList(msgHashList.size()));

        try (Connection conn = DBService.getInstance().getNewConn(); PreparedStatement stmtUpdate = conn.prepareStatement(updateSQL)) {
            stmtUpdate.setString(1, updateStatus);

            for (int i = 0; i < msgHashList.size(); i++) {
                stmtUpdate.setString(i + 2, msgHashList.get(i));
            }

            updatedCount = stmtUpdate.executeUpdate();

            if (updatedCount != msgHashList.size()) {
                stderr_println(LOG_DEBUG, "[!] Number of updated rows does not match number of selected rows.");
            }
        } catch (Exception e) {
            stderr_println(LOG_ERROR, String.format("[-] Error updating [%s] Data Status: %s",tableName, e.getMessage()));
        }
        return updatedCount;
    }

    //更新数据对对应状态 updateStatusRunEndByMsgHashList
    public static int updateStatusRunEndByMsgHashList(String tableName, List<String> msgHashList) {
        return updateStatusByMsgHashList(tableName, msgHashList, Constants.ANALYSE_END);
    }

    //更新数据对应状态 updateStatusRunIngByMsgHashList
    public static int updateStatusRunIngByMsgHashList(String tableName, List<String> msgHashList) {
        return updateStatusByMsgHashList(tableName, msgHashList, Constants.ANALYSE_ING);
    }
}
