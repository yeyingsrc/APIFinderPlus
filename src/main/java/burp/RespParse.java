package burp;

import model.HttpMsgInfo;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static burp.BurpExtender.*;
import static utils.ElementUtils.isContainKeys;
import static utils.ElementUtils.isEqualsKeys;

public class RespParse {
    private static PrintWriter stdout = BurpExtender.getStdout();
    private static PrintWriter stderr = BurpExtender.getStderr();
    private static IExtensionHelpers helpers = BurpExtender.getHelpers();;

    static final int CHUNK_SIZE = 20000; // 分割大小
    private static final Pattern FIND_URL_FROM_HTML_PATTERN = Pattern.compile("(http|https)://([\\w_-]+(?:(?:\\.[\\w_-]+)+))([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?");

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4E00-\u9FA5]");
    private static final Pattern FIND_PAHT_FROM_JS_PATTERN = Pattern.compile("(?:\"|')(((?:[a-zA-Z]{1,10}://|//)[^\"'/]{1,}\\.[a-zA-Z]{2,}[^\"']{0,})|((?:/|\\.\\./|\\./)[^\"'><,;|*()(%%$^/\\\\\\[\\]][^\"'><,;|()]{1,})|([a-zA-Z0-9_\\-/]{1,}/[a-zA-Z0-9_\\-/]{1,}\\.(?:[a-zA-Z]{1,4}|action)(?:[\\?|/|;][^\"|']{0,}|))|([a-zA-Z0-9_\\-]{1,}\\.(?:php|asp|aspx|jsp|json|action|html|js|txt|xml)(?:\\?[^\"|']{0,}|)))(?:\"|')");
    private static final Pattern FIND_PATH_FROM_JS_PATTERN2 = Pattern.compile("\"(/[^\"\\s,@\\[\\]\\(\\)<>{}，%\\+：:/-]*)\"|'(/[^'\\\\s,@\\[\\]\\(\\)<>{}，%\\+：:/-]*?)'");

    public static final String URL_KEY = "url";
    public static final String PATH_KEY = "path";

    public static void analysisReqData(HttpMsgInfo msgInfo) {
        //存储所有提取的URL/URI
        Set<String> urlSet = new HashSet<>();
        Set<String> pathSet = new HashSet<>();

        //转换响应体,后续可能需要解决编码问题
        String respBody = new String(HttpMsgInfo.getBodyBytes(msgInfo.getRespBytes(), msgInfo.getRespBodyOffset()));

        // 针对html页面提取 直接的URL 已完成
        List<String> extractUrlsFromHtml = extractDirectUrls(msgInfo.getReqUrl(), respBody);
        stdout.println(String.format("[+] 初步提取的URL数量: %s -> %s", msgInfo.getReqUrl(), extractUrlsFromHtml.size()));
        urlSet.addAll(extractUrlsFromHtml);

        // 针对JS页面提取
        if (isEqualsKeys(msgInfo.getReqPathExt(), NEED_EXTRACT_SUFFIX, true) || msgInfo.getInferredMimeType().contains("script")) {
            Set<String> extractUriFromJs = extractUriFromJs(respBody);
            stdout.println(String.format("[+] 初步提取的URI数量: %s -> %s", msgInfo.getReqUrl(), extractUriFromJs.size()));
            urlSet.addAll(extractUriFromJs);
        }

        //拆分提取的URL和PATH为两个set 用于进一步处理操作
        Map<String, Set> setMap = SplitExtractUrlOrPath(urlSet);
        urlSet = setMap.get(URL_KEY);
        pathSet = setMap.get(PATH_KEY);

        //过滤无用的请求URL
        //根据用户配置文件信息过滤其他无用的URL
        urlSet = filterUrlByGlobalConfig(urlSet);
        //保留本域名的URL,会检测格式 Todo: 优化思路 可选择关闭|改为主域名 增加攻击面
        urlSet = filterUrlByHost(msgInfo.getReqHost(),  urlSet);
        if (urlSet.size() > 0)
            stdout.println(String.format("[+] 有效URL数量: %s -> %s", msgInfo.getReqUrl(), urlSet.size()));
            for (String s : urlSet)
                stdout.println(String.format("[*] INFO URL: %s", s));

        //过滤无用的PATH内容
        pathSet = filterUselessPathsByKey(pathSet);
        pathSet = filterUselessPathsByEqual(pathSet);
        if (pathSet.size() > 0)
            stdout.println(String.format("[+] 有效URL数量: %s -> %s", msgInfo.getReqUrl(), pathSet.size()));
            for (String s : pathSet)
                stdout.println(String.format("[*] INFO PATH: %s", s));

        //todo: 实现响应敏感信息提取

        //TODO: 输出已提取的结果信息

        //Todo: 对PATH进行计算,计算出真实的URL路径

        //TODO: 排除已经访问的URL  (可选|非必要, 再次访问时都会过滤掉的,不会加入进程列表)
        //TODO: 初始化时,给已提取URL PATH和 已添加URL赋值 (可选|非必要,不会加入进程列表)

        //TODO: 递归探测已提取的URL (使用burp内置的库,流量需要在logger在logger中显示)
        //TODO: 实现UI显示
    }


    /**
     * 从html内容中提取url信息
     * @param reqUrl
     * @param htmlText
     * @return
     */
    public static List<String> extractDirectUrls(String reqUrl, String htmlText) {
        // 使用正则表达式提取文本内容中的 URL
        List<String> urlList = new ArrayList<>();

        //直接跳过没有http关键字的场景
        if (!htmlText.contains("http")){
            return urlList;
        }

        // html文件内容长度
        int htmlLength = htmlText.length();

        // 处理每个 CHUNK_SIZE 大小的片段
        for (int start = 0; start < htmlLength; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, htmlLength);
            String htmlChunk = htmlText.substring(start, end);

            htmlChunk = htmlChunk.replace("\\/","/");
            Matcher matcher = FIND_URL_FROM_HTML_PATTERN.matcher(htmlChunk);
            while (matcher.find()) {
                String matchUrl = matcher.group();
                //stdout.println(String.format("[*] 初步提取信息:%s", matchUrl));
                //识别相对于网站根目录的URL路径 //不包含http 并且以/开头的（可能是一个相对URL）
                if (!matchUrl.contains("http") && matchUrl.startsWith("/")) {
                    try {
                        //使用当前请求的reqUrl创建URI对象
                        URI baseUrl = new URI(reqUrl);
                        //计算出新的绝对URL//如果baseUrl是http://example.com/，而url是/about 计算结果就是 http://example.com/about。
                        matchUrl = baseUrl.resolve(matchUrl).toString();
                    } catch (URISyntaxException e) {
                        continue;
                    }
                }
                urlList.add(matchUrl);
            }
        }
        return urlList;
    }

    /**
     * 从Js内容中提取uri/url信息
     * @param jsText
     * @return
     */
    public static Set<String> extractUriFromJs(String jsText){
        // 方式一：原有的正则提取js中的url的逻辑
        int jsLength = jsText.length(); // JavaScript 文件内容长度
        Set<String> findUris = new LinkedHashSet<>();

        // 处理每个 CHUNK_SIZE 大小的片段
        for (int start = 0; start < jsLength; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, jsLength);
            String jsChunk = jsText.substring(start, end);
            Matcher m = FIND_PAHT_FROM_JS_PATTERN.matcher(jsChunk);
            int matcher_start = 0;
            while (m.find(matcher_start)){
                String matchGroup = m.group(1);
                if (matchGroup != null){
                    findUris.add(formatExtractUri(matchGroup));
                }
                matcher_start = m.end();
            }

            // 方式二：
            Matcher matcher_result = FIND_PATH_FROM_JS_PATTERN2.matcher(jsChunk);
            while (matcher_result.find()){
                // 检查第一个捕获组
                String group1 = matcher_result.group(1);
                if (group1 != null) {
                    findUris.add(formatExtractUri(group1));
                }
                // 检查第二个捕获组
                String group2 = matcher_result.group(2);
                if (group2 != null) {
                    findUris.add(formatExtractUri(group2));
                }
            }
        }

        //需要排除非本域名的URL  已实现
        //需要排除非黑名单域名、黑名单路径、黑名单的API

        return findUris;
    }

    /**
     * 对提取的URL进行简单的格式处理
     * @param extractUri
     * @return
     */
    public static String formatExtractUri(String extractUri){
        return  extractUri
                .replaceAll("\"", "")
                .replaceAll("'", "")
                .replaceAll("\n", "")
                .replaceAll("\t", "")
                .trim();
    }

    /**
     * 通过new Url功能过滤提取出来的URl
     * @param matchUrlSet
     * @return
     */
    public static Set<String> filterUrlByNew(Set<String> matchUrlSet){
        if (matchUrlSet == null || matchUrlSet.isEmpty()) return matchUrlSet;

        Set<String> newUrlSet = new HashSet<>();
        for (String matchUrl : matchUrlSet){
            try {
                URL url = new URL(matchUrl);
                newUrlSet.add(url.toString());
            } catch (Exception e) {
                stderr.println(String.format("[!] new URL(%s) -> Error: %s", matchUrl, e.getMessage()));
                continue;
            }
        }
        return newUrlSet;
    }

    /**
     * 过滤提取出的URL列表 仅保留指定域名的
     * @param rawDomain
     * @param matchUrlSet
     * @return
     */
    public static Set<String> filterUrlByHost(String rawDomain,  Set<String> matchUrlSet){
        if (rawDomain == null || rawDomain == "" || matchUrlSet == null || matchUrlSet.isEmpty()) return matchUrlSet;

        Set<String> newUrlSet = new HashSet<>();
        for (String matchUrl : matchUrlSet){
            //对比提取出来的URL和请求URL的域名部分是否相同，不相同的一般不是
            try {
                String newDomain = (new URL(matchUrl)).getHost();
                if (!rawDomain.equalsIgnoreCase(newDomain)) {
                    continue;
                }
            } catch (Exception e) {
                stderr.println(String.format("[!] new URL(%s) -> Error: %s", matchUrl, e.getMessage()));
                continue;
            }
            newUrlSet.add(matchUrl);
        }
        return newUrlSet;
    }

    /**
     * 拆分提取出来的Url集合中的URl和Path
     * @param matchUrlSet
     * @return
     */
    public static Map<String, Set> SplitExtractUrlOrPath(Set<String> matchUrlSet) {
        Map<String, Set> setMap = new HashMap<>();
        Set<String> urlSet = new HashSet<>();
        Set<String> pathSet = new HashSet<>();

        for (String matchUrl : matchUrlSet){
            if (matchUrl.contains("://")){
                urlSet.add(matchUrl);
            }else {
                pathSet.add(matchUrl);
            }
        }

        setMap.put(URL_KEY, urlSet);
        setMap.put(PATH_KEY, pathSet);
        return setMap;
    }

    /**
     * 过滤无用的提取路径 通过判断和指定的路径相等
     * @param matchPathSet
     * @return
     */
    private static Set<String> filterUselessPathsByEqual(Set<String> matchPathSet) {
        Set<String> newPathSet = new HashSet<>();
        for (String path : matchPathSet){
            if(!isEqualsKeys(path, USELESS_PATH_EQUAL, false)){
                newPathSet.add(path);
            }
        }
        return newPathSet;
    }

    /**
     * 过滤无用的提取路径 通过判断是否包含无用关键字
     * @param matchPathSet
     * @return
     */
    private static Set<String> filterUselessPathsByKey(Set<String> matchPathSet) {
        Set<String> newPathSet = new HashSet<>();
        for (String path : matchPathSet){
            if(!isContainKeys(path, USELESS_PATH_KEYS, false)){
                newPathSet.add(path);
            }
        }
        return newPathSet;
    }

    /**
     * 基于配置信息过滤提取的请求URL
     * @param matchUrlSet
     * @return
     */
    private static Set<String> filterUrlByGlobalConfig(Set<String> matchUrlSet) {
        if (matchUrlSet == null || matchUrlSet.isEmpty()) return matchUrlSet;

        Set<String> newUrlSet = new HashSet<>();
        for (String matchUrl : matchUrlSet){
            try {
                URL url = new URL(matchUrl);
                //匹配黑名单域名 //排除被禁止的域名URL, baidu.com等常被应用的域名, 这些js是一般是没用的, 为空时不操作
                if(isContainKeys(url.getHost(), UN_CHECKED_URL_DOMAIN, false)){
                    continue;
                }

                // 排除黑名单后缀 jpg、mp3等等
                if(isEqualsKeys(HttpMsgInfo.parseUrlExt(matchUrl), UN_CHECKED_URL_EXT, false)){
                    continue;
                }

                //排除黑名单路径 这些JS文件是通用的、无价值的、
                if(isContainKeys(url.getPath(), UN_CHECKED_URL_PATH, false)){
                    continue;
                }

                newUrlSet.add(matchUrl);
            } catch (Exception e) {
                stderr.println(String.format("[!] new URL(%s) -> Error: %s", matchUrl, e.getMessage()));
                continue;
            }


            newUrlSet.add(matchUrl);
        }
        return newUrlSet;
    }
}