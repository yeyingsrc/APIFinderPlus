package utils;

import burp.BurpExtender;
import model.FingerPrintRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static utils.CastUtils.isNotEmptyObj;

public class ConfigUtils {
    /**
     * 自动根据规则类型赋值到配置列
     */
    public static void loadConfigArrayListByRule(FingerPrintRule rule) {
        switch (rule.getType()) {
            case "CONF_WHITE_URL_ROOT":
                BurpExtender.CONF_WHITE_URL_ROOT.addAll(rule.getKeyword());
                break;
            case "CONF_WHITE_RECORD_PATH_STATUS":
                BurpExtender.CONF_WHITE_RECORD_PATH_STATUS.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_RECORD_PATH_TITLE":
                BurpExtender.CONF_BLACK_RECORD_PATH_TITLE.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_AUTO_RECORD_PATH":
                BurpExtender.CONF_BLACK_AUTO_RECORD_PATH.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_AUTO_RECURSE_SCAN":
                BurpExtender.CONF_BLACK_AUTO_RECURSE_SCAN.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_URL_ROOT":
                BurpExtender.CONF_BLACK_URL_ROOT.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_URL_PATH":
                BurpExtender.CONF_BLACK_URL_PATH.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_URL_EXT":
                BurpExtender.CONF_BLACK_URL_EXT.addAll(rule.getKeyword());
                break;

            case "CONF_BLACK_EXTRACT_PATH_KEYS":
                BurpExtender.CONF_BLACK_EXTRACT_PATH_KEYS.addAll(rule.getKeyword());
                break;
            case "CONF_BLACK_EXTRACT_PATH_EQUAL":
                BurpExtender.CONF_BLACK_EXTRACT_PATH_EQUAL.addAll(rule.getKeyword());
                break;

            case "CONF_BLACK_EXTRACT_INFO_KEYS":
                BurpExtender.CONF_BLACK_EXTRACT_INFO_KEYS.addAll(rule.getKeyword());
                break;

            case "CONF_REGULAR_EXTRACT_URIS":
                BurpExtender.CONF_REGULAR_EXTRACT_URIS.addAll(rule.getKeyword());
                break;

            //添加HTTP请求相关参数配置
            case "CONF_BLACK_RECURSE_REQ_PATH_KEYS":
                BurpExtender.CONF_BLACK_RECURSE_REQ_PATH_KEYS.addAll(rule.getKeyword());
                break;

            case "CONF_RECURSE_REQ_HTTP_METHODS":
                BurpExtender.CONF_RECURSE_REQ_HTTP_METHODS.addAll(rule.getKeyword());
                break;

            case "CONF_RECURSE_REQ_HTTP_PARAMS":
                BurpExtender.CONF_RECURSE_REQ_HTTP_PARAMS.addAll(rule.getKeyword());
                break;

            default:
                break;
        }
    }

    /**
     * 清空所有的配置列表
     */
    public static void autoClearAllConfArrayList() {
        BurpExtender.CONF_WHITE_URL_ROOT = new ArrayList<>(); //仅扫描的URL
        BurpExtender.CONF_WHITE_RECORD_PATH_STATUS = new ArrayList<>(); //作为正常访问结果的状态码
        BurpExtender.CONF_BLACK_AUTO_RECORD_PATH = new ArrayList<>(); //不自动记录PATH的URL域名
        BurpExtender.CONF_BLACK_AUTO_RECURSE_SCAN = new ArrayList<>(); //不自动进行递归扫描的URL域名

        BurpExtender.CONF_BLACK_URL_EXT = new ArrayList<>(); //不检查的URL后缀
        BurpExtender.CONF_BLACK_URL_PATH = new ArrayList<>(); //不检查的URL路径
        BurpExtender.CONF_BLACK_URL_ROOT = new ArrayList<>(); //不检查的URL域名
        BurpExtender.CONF_BLACK_EXTRACT_PATH_KEYS = new ArrayList<>();  //需要忽略的响应提取路径 关键字
        BurpExtender.CONF_BLACK_EXTRACT_PATH_EQUAL = new ArrayList<>();  //需要忽略的响应提取路径 完整路径
        BurpExtender.CONF_BLACK_EXTRACT_INFO_KEYS = new ArrayList<>(); //需要忽略的响应提取信息
        BurpExtender.CONF_REGULAR_EXTRACT_URIS = new ArrayList<>(); //URL提取正则表达式
        //添加HTTP请求相关参数配置
        BurpExtender.CONF_BLACK_RECURSE_REQ_PATH_KEYS = new ArrayList<>();  //禁止递归访问的URL路径[包含]此项任一元素
        BurpExtender.CONF_RECURSE_REQ_HTTP_METHODS = new ArrayList<>();  //递归访问URL时的HTTP请求方法
        BurpExtender.CONF_RECURSE_REQ_HTTP_PARAMS = new ArrayList<>();  //递归访问URL时的HTTP请求参数
    }


    /**
     * 从规则里面重新提取全部配置
     */
    public static void reloadConfigArrayListFromRules(List<FingerPrintRule> fingerprintRules) {
        //清空所有配置列表参数
        autoClearAllConfArrayList();

        if (isNotEmptyObj(fingerprintRules)){
            //循环更新全局配置
            for (int i = 0 ; i < fingerprintRules.size(); i ++){
                FingerPrintRule rule = fingerprintRules.get(i);
                loadConfigArrayListByRule(rule);
            }
        }

        //重新编译正则表达式
        BurpExtender.URI_MATCH_REGULAR_COMPILE = RegularUtils.compileUriMatchRegular(BurpExtender.CONF_REGULAR_EXTRACT_URIS);

        //处理必须有内容的列
        //设置默认请求方法
        if (BurpExtender.CONF_RECURSE_REQ_HTTP_METHODS.isEmpty()){BurpExtender.CONF_RECURSE_REQ_HTTP_METHODS =  Collections.singletonList("GET");}
        //设置默认请求参数
        if (BurpExtender.CONF_RECURSE_REQ_HTTP_PARAMS.isEmpty()){BurpExtender.CONF_RECURSE_REQ_HTTP_PARAMS =  Collections.singletonList("");}

    }
}