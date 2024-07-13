package model;

import com.alibaba.fastjson2.JSONObject;
import utils.CastUtils;

import java.util.List;

public class AnalyseResultModel {
    private int respLength;
    private List<JSONObject> infoList;
    private List<String> urlList;
    private List<String> pathList;
    private List<String> apiList;
    private List<String> unvisitedUrl;

    public AnalyseResultModel(int respLength, List<JSONObject> infoList, List<String> urlList, List<String> pathList, List<String> apiList) {
        this.respLength = respLength;
        this.infoList = infoList;
        this.urlList = urlList;
        this.pathList = pathList;
        this.apiList = apiList;
        this.unvisitedUrl = CastUtils.listAddList(urlList, apiList);
    }

    public int getRespLength() {
        return respLength;
    }

    public List<JSONObject> getInfoList() {
        return infoList;
    }

    public List<String> getUrlList() {
        return urlList;
    }

    public List<String> getPathList() {
        return pathList;
    }

    public List<String> getApiList() {
        return apiList;
    }

    public List<String> getUnvisitedUrl() {
        return unvisitedUrl;
    }

    public void setUnvisitedUrl(List<String> unvisitedUrl) {
        this.unvisitedUrl = unvisitedUrl;
    }
}
