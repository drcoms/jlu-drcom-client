package com.youthlin.jlu.drcom.util;

import java.io.File;

/**
 * Created by lin on 2017-01-09-009.
 * 常量
 */
@SuppressWarnings("WeakerAccess")
public class Constants {
    public static final String LOGO_URL = "/dr-logo.png";
    public static final String LOADING_URL = "/loading.gif";
    public static final String AUTH_SERVER = "auth.jlu.edu.cn";//10.100.61.3
    public static final String COPYRIGHT_YEAR_START = "2017";

    public static final int PORT = 61440;
    public static final int TIMEOUT = 10000;//10s

    public static final String ARTICLE_URL = "http://youthlin.com/?p=1391";
    public static final String PROJECT_HOME = "https://github.com/YouthLin/jlu-drcom-client/tree/master/jlu-drcom-java";
    public static final String NOTICE_URL = "http://login.jlu.edu.cn/notice.php";
    public static final double NOTICE_W = 592;
    public static final double NOTICE_H = 450;


    public static final String DATA_HOME = System.getProperty("user.home", ".") + File.separator + ".drcom";
    public static final String CONF_HOME = DATA_HOME + File.separator + "conf";
    public static final String LOCK_FILE_NAME = "drcom.java.lock";
    public static final String CONF_FILE_NAME = "drcom.java.conf";

    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_DASH_MAC = "dash_mac";
    public static final String KEY_HOSTNAME = "hostname";
    public static final String KEY_REMEMBER = "remember";
    public static final String KEY_AUTO_LOGIN = "auto_login";
    public static final String KEY_VERSION = "version";
    public static final int VER_1 = 1;//添加 3DES 加密： conf, lock 文件

}
