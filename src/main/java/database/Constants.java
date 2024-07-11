package database;

public class Constants {
    //所有表状态都使用
    public static final String ANALYSE_WAIT = "等待解析";
    public static final String ANALYSE_ING = "解析中";
    public static final String ANALYSE_END = "解析完成";
    public static final String ANALYSE_SKIP = "无需解析";


    //msg data 使用
    public static final String REQ_URL = "REQ_URL";
    public static final String MSG_HASH = "MSG_HASH";
    public static final String REQ_BYTES = "REQ_BYTES";
    public static final String RESP_BYTES = "RESP_BYTES";

    //AnalyseDataTable 使用
    public static final String FIND_PATH = "FIND_PATH";

    //RecordUrlsTable使用
    public static final String SPLIT_SYMBOL = "<->";
    public static final String REQ_HOST_PORT = "REQ_HOST_PORT";
    public static final String REQ_PATH_DIRS = "REQ_PATH_DIRS";
    public static final String PATH_TREE = "PATH_TREE";


    //联合语句用到的遍历
    public static final String BASIC_PATH_NUM = "BASIC_PATH_NUM";


    static public String TREE_STATUS_EXPAND = "▼";
    static public String TREE_STATUS_COLLAPSE = "▶";

    static public String TAB_COLOR_SELECTED = "0xffc599";
    static public String TAB_COLOR_MAIN_DATA = "0xf2f2f2";
    static public String TAB_COLOR_SUB_DATA = "0xffffff";

    static public String GRAPHQL_SPACE = " ";
    static public String GRAPHQL_NEW_LINE = "\n";
    static public String GRAPHQL_TAB = "    ";

}
