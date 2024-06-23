package burp;

import com.alibaba.fastjson2.JSONObject;
import dataModel.AnalyseDataTable;
import dataModel.MsgDataTable;
import dataModel.RecordUrlsTable;
import dataModel.ReqDataTable;
import model.HttpMsgInfo;
import model.RecordHashMap;
import utils.ElementUtils;
import model.InfoAnalyse;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

import static burp.BurpExtender.*;
import static model.InfoAnalyse.analyseInfoIsNotEmpty;
import static utils.BurpPrintUtils.*;


public class IProxyScanner implements IProxyListener {
    private static final int MaxRespBodyLen = 200000; //最大支持处理的响应
    private static RecordHashMap urlScanRecordMap = new RecordHashMap(); //记录已加入扫描列表的URL Hash
    private static RecordHashMap urlPathRecordMap = new RecordHashMap(); //记录已加入待分析记录的URL Path Dir

    final ThreadPoolExecutor executorService;
    static ScheduledExecutorService monitorExecutor;
    private static int monitorExecutorServiceNumberOfIntervals = 2;

    public IProxyScanner() {
        // 获取操作系统内核数量
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int coreCount = Math.min(availableProcessors, 16);
        int maxPoolSize = coreCount * 2;

        // 高性能模式
        monitorExecutorServiceNumberOfIntervals = (availableProcessors > 6) ? 1 : monitorExecutorServiceNumberOfIntervals;
        long keepAliveTime = 60L;

        // 创建一个足够大的队列来处理您的任务
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(10000);

        executorService = new ThreadPoolExecutor(
                coreCount,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy() // 当任务太多时抛出异常，可以根据需要调整策略
        );
        stdout_println(LOG_INFO,"[+] run executorService maxPoolSize: " + coreCount + " ~ " + maxPoolSize + ", monitorExecutorServiceNumberOfIntervals: " + monitorExecutorServiceNumberOfIntervals);

        monitorExecutor = Executors.newSingleThreadScheduledExecutor();

        startDatabaseMonitor();
    }

    @Override
    public void processProxyMessage(boolean messageIsRequest, final IInterceptedProxyMessage iInterceptedProxyMessage) {
        if (!messageIsRequest) {
//            totalScanCount += 1;
//            ConfigPanel.lbRequestCount.setText(Integer.toString(totalScanCount));

            HttpMsgInfo msgInfo = new HttpMsgInfo(iInterceptedProxyMessage);
            //判断是否是正常的响应 //返回结果为空则退出
            if (msgInfo.getRespBytes() == null || msgInfo.getRespBytes().length == 0) {
                stdout_println(LOG_DEBUG,"[-] 没有响应内容 跳过插件处理：" + msgInfo.getReqUrl());
                return;
            }

            //看URL识别是否报错
            if (msgInfo.getReqBaseUrl() == null ||msgInfo.getReqBaseUrl().equals("-")){
                stdout_println(LOG_ERROR,"[-] URL转化失败 跳过url识别：" + msgInfo.getReqUrl());
                return;
            }

            //匹配黑名单域名
            if(ElementUtils.isContainOneKey(msgInfo.getReqHost(), CONF_BLACK_URL_HOSTS, false)){
                stdout_println(LOG_DEBUG,"[-] 匹配黑名单域名 跳过url识别：" + msgInfo.getReqUrl());
                return;
            }

            //保存网站相关的所有 PATH, 便于后续path反查的使用
            //当响应状态 In [200 | 403 | 405] 说明路径存在 此时可以将URL存储已存在字典
            if(urlPathRecordMap.get(msgInfo.getReqBasePath()) <= 0 && ElementUtils.isEqualsOneKey(msgInfo.getRespStatus(), CONF_NEED_RECORD_STATUS, true)){
                urlPathRecordMap.add(msgInfo.getReqBasePath());
                stdout_println(LOG_INFO, String.format("[+] Record ReqBasePath: %s -> %s", msgInfo.getReqBasePath(), msgInfo.getRespStatus()));
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        RecordUrlsTable.insertOrUpdateSuccessUrl(msgInfo);
                    }
                });
            }

            // 排除黑名单后缀
            if(ElementUtils.isEqualsOneKey(msgInfo.getReqPathExt(), CONF_BLACK_URL_EXT, false)){
                stdout_println(LOG_DEBUG, "[-] 匹配黑名单后缀 跳过url识别：" + msgInfo.getReqUrl());
                return;
            }

            //排除黑名单路径 这些JS文件是通用的、无价值的、
            //String blackPaths = "jquery.js|xxx.js";
            if(ElementUtils.isContainOneKey(msgInfo.getReqPath(), CONF_BLACK_URL_PATH, false)){
                stdout_println(LOG_DEBUG, "[-] 匹配黑名单路径 跳过url识别：" + msgInfo.getReqUrl());
                return;
            }

            // 看status是否为30开头
            if (msgInfo.getRespStatus().startsWith("3")){
                stdout_println(LOG_DEBUG,"[-] URL的响应包状态码3XX 跳过url识别：" + msgInfo.getReqUrl());
                return;
            }

            if (msgInfo.getRespStatus().equals("404")){
                stdout_println(LOG_DEBUG, "[-] URL的响应包状态码404 跳过url识别：" + msgInfo.getReqUrl());
                return;
            }

            //判断URL是否已经扫描过
            if (urlScanRecordMap.get(msgInfo.getMsgHash()) > 0) {
                stdout_println(LOG_DEBUG, String.format("[-] 已添加过URL: %s -> %s", msgInfo.getReqUrl(), msgInfo.getMsgHash()));
                return;
            }

            //记录准备加入的请求
            urlScanRecordMap.add(msgInfo.getMsgHash());
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    //防止响应体过大
                    byte[] respBytes = msgInfo.getRespBytes().length > MaxRespBodyLen ? Arrays.copyOf(msgInfo.getRespBytes(), MaxRespBodyLen) : msgInfo.getRespBytes();
                    msgInfo.setRespBytes(respBytes);
                    //加入请求列表
                    int msgId = iInterceptedProxyMessage.getMessageReference();
                    storeReqData(msgInfo, msgId, "ProxyMessage");
                }
            });
        }
    }

    /**
     * 合并添加请求数据和请求信息为一个函数
     * @param msgInfo
     * @param msgId
     */
    private void storeReqData(HttpMsgInfo msgInfo, int msgId, String reqSource) {
        //存储请求体|响应体数据
        int msgDataIndex = MsgDataTable.insertOrUpdateMsgData(msgInfo);
        if (msgDataIndex > 0){
            // 存储到URL表
            int insertOrUpdateOriginalDataIndex = ReqDataTable.insertOrUpdateReqData(msgInfo, msgId, msgDataIndex, reqSource);
            if (insertOrUpdateOriginalDataIndex > 0)
                stdout_println(LOG_INFO, String.format("[+] Success Add Task: %s -> msgHash: %s -> reqSource:%s",
                        msgInfo.getReqUrl(), msgInfo.getMsgHash(), reqSource));
        }
    }

    /**
     * 启动动态监听的数据处理
     */
    private void startDatabaseMonitor() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            executorService.submit(() -> {
                try {
                    //当添加进程还比较多的时候,暂时不进行响应数据处理
                    if (executorService.getActiveCount() >= 6)
                        return;

                    //任务1、获取需要解析的响应体数据并进行解析响
                    Integer needAnalyseDataIndex = ReqDataTable.fetchAndMarkReqData(true);
                    if (needAnalyseDataIndex > 0){
                        // 1 获取 msgDataIndex 对应的数据
                        Map<String, Object> needAnalyseData = MsgDataTable.selectMsgDataById(needAnalyseDataIndex);

                        String msgHash = (String) needAnalyseData.get("msg_hash");
                        String reqUrl = (String) needAnalyseData.get("req_url");
                        stdout_println(LOG_INFO, String.format("[*] 分析数据信息: %s %s", reqUrl, msgHash));

                        //2.2 将请求响应数据整理出新的 MsgInfo 数据 并 分析
                        HttpMsgInfo msgInfo =  new HttpMsgInfo(
                                reqUrl,
                                (byte[]) needAnalyseData.get("req_bytes"),
                                (byte[]) needAnalyseData.get("resp_bytes"),
                                msgHash);

                        //2.3 进行数据分析
                        JSONObject analyseResult = InfoAnalyse.analysisMsgInfo(msgInfo);

                        //2.3 将分析结果写入数据库
                        if(analyseInfoIsNotEmpty(analyseResult)){
                            int analyseDataIndex = AnalyseDataTable.insertAnalyseData(msgInfo, analyseResult);
                            if (analyseDataIndex > 0)
                                stdout_println(LOG_INFO, String.format("[+] 数据分析完成: %s -> msgHash: %s", msgInfo.getReqUrl(), msgInfo.getMsgHash()));
                        }
                    }

                    //判断是否还有需要分析的数据,如果没有的话，就可以考虑计算结果
                    needAnalyseDataIndex = ReqDataTable.fetchAndMarkReqData(false);
                    if (needAnalyseDataIndex <= 0){
                        //开始基于已有数据计算
                        stdout_println(LOG_INFO, "[*] 暂无需要分析数据, 开始进行动态API计算...");

                        //todo 从数据查询一条数据, 获取 id|msg_hash、PATHS列表 限制

                        //todo 如果根树不存在,就开始计算根树 || 或者存在需要没有添加到根树中的记录URL

                        //todo 从数据库获取最终的根树数据

                        //todo 基于根树和paths列表计算新的字典

                        //基于 根树 和 pathList 计算 URLs, 如果计算过的，先判断根数是否更新过
                        //已完成 实现基于根数的路径计算函数
                    }


                    //todo: 提取的PATH需要进一步过滤处理
                    // 考虑增加后缀过滤功能 static/image/k8-2.png
                    // 考虑增加已有URL过滤 /bbs/login
                    // 考虑增加 参数处理 plugin.php?id=qidou_assign


                    //todo: 增加自动递归查询功能
                } catch (Exception e) {
                    stderr_println(String.format("[!] scheduleAtFixedRate error: %s", e.getMessage()));
                    e.printStackTrace();
                }
            });
        }, 0, monitorExecutorServiceNumberOfIntervals, TimeUnit.SECONDS);
    }


    /**
     * 监听线程关闭函数
     */
    public static void shutdownMonitorExecutor() {
        // 关闭监控线程池
        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdown();
            try {
                // 等待线程池终止，设置一个合理的超时时间
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    // 如果线程池没有在规定时间内终止，则强制关闭
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                // 如果等待期线程被中断，恢复中断状态
                Thread.currentThread().interrupt();
                // 强制关闭
                monitorExecutor.shutdownNow();
            }
        }
    }
}
