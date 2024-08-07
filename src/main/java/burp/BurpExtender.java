package burp;


import com.alibaba.fastjson2.JSON;
import database.DBService;
import model.FingerPrintRule;
import model.FingerPrintRulesWrapper;
import ui.MainPanel;
import ui.Tags;
import utils.BurpFileUtils;
import utils.BurpPrintUtils;
import utils.RegularUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static utils.BurpPrintUtils.*;
import static utils.CastUtils.isEmptyObj;
import static utils.CastUtils.isNotEmptyObj;

public class BurpExtender implements IBurpExtender, IExtensionStateListener, IContextMenuFactory {
    private static IBurpExtenderCallbacks callbacks;
    private static PrintWriter stdout;
    private static PrintWriter stderr;
    private static IExtensionHelpers helpers;

    private static IProxyScanner iProxyScanner;
    private static Tags tags;

    public static boolean extensionIsLoading = false; //记录插件是否处于加载状态

    public static PrintWriter getStdout() {
        return stdout;
    }

    public static PrintWriter getStderr() {
        return stderr;
    }

    public static IBurpExtenderCallbacks getCallbacks() {
        return callbacks;
    }

    public static IExtensionHelpers getHelpers() {
        return helpers;
    }

    public static IProxyScanner getIProxyScanner() {
        return iProxyScanner;
    }

    public static String extensionName = "APIFinderPlus";

    public static List<FingerPrintRule> fingerprintRules;

    //一些需要被排除|允许的情况
    public static List<String> CONF_WHITE_URL_ROOT = new ArrayList<>(); //仅保留的白名单主机,为空时忽略

    public static List<String> CONF_WHITE_RECORD_PATH_STATUS = new ArrayList<>(); //作为正常访问结果的状态码

    public static List<String> CONF_BLACK_URL_EXT = new ArrayList<>(); //不检查的URL后缀
    public static List<String> CONF_BLACK_URL_PATH = new ArrayList<>(); //不检查的URL路径
    public static List<String> CONF_BLACK_URL_ROOT = new ArrayList<>(); //不检查的ROOT URL 关键字
    public static List<String> CONF_BLACK_AUTO_RECORD_PATH = new ArrayList<>(); //不检查的ROOT URL 关键字
    public static List<String> CONF_BLACK_AUTO_RECURSE_SCAN = new ArrayList<>(); //不检查的ROOT URL 关键字

    public static List<String> CONF_BLACK_RECORD_PATH_TITLE = new ArrayList<>(); // 不记录到PATH 的 TITLE 关键字

    public static List<String> CONF_BLACK_EXTRACT_PATH_KEYS = new ArrayList<>();  //需要忽略的响应提取路径 关键字
    public static List<String> CONF_BLACK_EXTRACT_PATH_EQUAL = new ArrayList<>();  //需要忽略的响应提取路径 完整路径

    public static List<String> CONF_BLACK_EXTRACT_INFO_KEYS = new ArrayList<>();  //需要忽略的响应提取信息
    public static List<String> CONF_REGULAR_EXTRACT_URIS = new ArrayList<>();  //URL提取正则表达式

    public static List<Pattern> URI_MATCH_REGULAR_COMPILE = new ArrayList<>();  //存储编译后的正则表达式

    private static DBService dbService;  //数据库实例

    public static int SHOW_MSG_LEVEL = LOG_DEBUG;  //显示消息级别

    public static  String configName = "finger-important.json";

    public static boolean onlyScopeDomain = false; //是否仅显示本主机域名的URL

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.stdout = new PrintWriter(callbacks.getStdout(), true);
        this.stderr = new PrintWriter(callbacks.getStderr(), true);
        new BurpPrintUtils(this.stdout, this.stderr);  //初始化输出类

        SwingUtilities.invokeLater(new Runnable() { public void run() {
            // 读取配置文件参数
            String configJson = BurpFileUtils.ReadPluginConfFile(callbacks, configName, StandardCharsets.UTF_8);
            // 加载配置规则
            if(isNotEmptyObj(configJson)){
                FingerPrintRulesWrapper rulesWrapper;
                try{
                    rulesWrapper = JSON.parseObject(configJson, FingerPrintRulesWrapper.class);
                } catch (Exception e){
                    stderr_println(LOG_ERROR, String.format("[!] JSON.parseObject Config Error:[%s]", e.getMessage()));
                    configJson = BurpFileUtils.ReadPluginConfFile(callbacks, configName, Charset.forName("GBK"));
                    rulesWrapper = JSON.parseObject(configJson, FingerPrintRulesWrapper.class);
                }
                // 使用Fastjson的parseObject方法将JSON字符串转换为Rule对象
                fingerprintRules = rulesWrapper.getFingerprint();
                // 从规则json内获取黑名单设置 //此处后续可能需要修改,修改配置类型
                if (isNotEmptyObj(fingerprintRules)){
                    for (int i = 0 ; i < fingerprintRules.size(); i ++){
                        FingerPrintRule rule = fingerprintRules.get(i);
                        if (rule.getIsOpen()) {
                            setActionByRuleInfo(rule);
                        }
                    }
                    stdout_println(LOG_INFO, String.format("[*] Load Config Rules Size: %s", fingerprintRules.size()));
                }

                //编译正则表达式
                URI_MATCH_REGULAR_COMPILE = RegularUtils.compileUriMatchRegular(CONF_REGULAR_EXTRACT_URIS);
            }

            if (isEmptyObj(fingerprintRules)){
                stderr_println(LOG_ERROR, "[!] 配置文件加载出错!!!");
                return;
            }

            //加载UI 标签界面
            tags = new Tags(callbacks, extensionName);

            //初始化数据配置
            dbService = DBService.getInstance();

            //注册监听操作
            iProxyScanner = new IProxyScanner();
            callbacks.registerProxyListener(iProxyScanner);

            // 注册插件状态监听操作
            callbacks.registerExtensionStateListener(BurpExtender.this);

            callbacks.registerContextMenuFactory(BurpExtender.this); //注册右键菜单Factory

            //设置插件已加载完成
            extensionIsLoading = true;
            stdout_println(LOG_INFO, String.format("[+] %s Load success ...", extensionName));
        }});
    }


    public static void setActionByRuleInfo(FingerPrintRule rule) {
        switch (rule.getType()) {
            case "CONF_WHITE_URL_ROOT":
                CONF_WHITE_URL_ROOT.addAll(rule.getKeyword());
                break;
            case "CONF_WHITE_RECORD_PATH_STATUS":
                CONF_WHITE_RECORD_PATH_STATUS.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_RECORD_PATH_TITLE":
                CONF_BLACK_RECORD_PATH_TITLE.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_AUTO_RECORD_PATH":
                CONF_BLACK_AUTO_RECORD_PATH.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_AUTO_RECURSE_SCAN":
                CONF_BLACK_AUTO_RECURSE_SCAN.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_URL_ROOT":
                CONF_BLACK_URL_ROOT.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_URL_PATH":
                CONF_BLACK_URL_PATH.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_URL_EXT":
                CONF_BLACK_URL_EXT.addAll(rule.getKeyword());
                break;

            case "CONF_BLACK_EXTRACT_PATH_KEYS":
                CONF_BLACK_EXTRACT_PATH_KEYS.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_EXTRACT_PATH_EQUAL":
                CONF_BLACK_EXTRACT_PATH_EQUAL.addAll(rule.getKeyword());
                break;

            case "CONF_BLACK_EXTRACT_INFO_KEYS":
                CONF_BLACK_EXTRACT_INFO_KEYS.addAll(rule.getKeyword());
                break;

            case "CONF_REGULAR_EXTRACT_URIS":
                CONF_REGULAR_EXTRACT_URIS.addAll(rule.getKeyword());
                break;

            default:
                break;
        }
    }

    @Override
    public void extensionUnloaded() {
        // 扩展卸载时，立刻关闭线程池
        stdout_println(LOG_DEBUG, "[+] Plugin will unloaded, cleaning resources...");


        // 立刻关闭线程池
        if (iProxyScanner.executorService != null) {
            // 尝试立即关闭所有正在执行的任务
            List<Runnable> notExecutedTasks = iProxyScanner.executorService.shutdownNow();
            stdout_println(LOG_DEBUG, "[+] 尝试停止所有任务, 未执行的任务数量：" + notExecutedTasks.size());
        }

        //更新插件状态
        extensionIsLoading = false;

        // 停止面板更新器
        MainPanel.timer.stop();

        // 关闭计划任务
        IProxyScanner.shutdownMonitorExecutor();
        stdout_println(LOG_DEBUG, "[+] 定时任务断开成功.");

        // 关闭数据库连接
        if (dbService != null) {
            dbService.closeConnection();
            stdout_println(LOG_DEBUG, "[+] 断开数据连接成功.");
        }

        stdout_println(LOG_INFO, String.format("[-] %s Unloaded ...", this.extensionName));
    }

    //callbacks.registerContextMenuFactory(this);//必须注册右键菜单Factory
    //实现右键 感谢原作者Conanjun
    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        final IHttpRequestResponse[] messages = invocation.getSelectedMessages();
        JMenuItem menuItem = new JMenuItem(String.format("Send to %s", BurpExtender.extensionName));
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (final IHttpRequestResponse message : messages) {
                    IProxyScanner.addRightScanTask(message);
                }

            }
        });

        return Arrays.asList(menuItem);
    }

}