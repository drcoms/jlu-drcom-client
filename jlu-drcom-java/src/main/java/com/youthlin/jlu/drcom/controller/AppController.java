package com.youthlin.jlu.drcom.controller;

import com.youthlin.jlu.drcom.Drcom;
import com.youthlin.jlu.drcom.bean.HostInfo;
import com.youthlin.jlu.drcom.bean.STATUS;
import com.youthlin.jlu.drcom.task.DrcomTask;
import com.youthlin.jlu.drcom.util.ByteUtil;
import com.youthlin.jlu.drcom.util.Constants;
import com.youthlin.jlu.drcom.util.FxUtil;
import com.youthlin.jlu.drcom.util.IPUtil;
import com.youthlin.jlu.drcom.util.MD5;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;

import static com.youthlin.jlu.drcom.util.FxUtil.icon;
import static com.youthlin.jlu.drcom.util.FxUtil.loading;
import static com.youthlin.utils.i18n.Translation.__;
import static com.youthlin.utils.i18n.Translation._x;


/**
 * Created by lin on 2017-01-08-008.
 * 登录页面
 */
@SuppressWarnings("unused")
public class AppController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(AppController.class);
    public Menu fileMenu;
    public Menu helpMenu;
    public Button logoutButton;
    public Button loginButton;
    public TextField usernameTextField;
    public Label tipLabel;
    public CheckBox rememberCheckBox;
    public PasswordField passwordField;
    public Label statusLabel;
    public ComboBox<HostInfo> macComboBox;
    public CheckBox autoLoginCheckBox;
    public ImageView imageView;
    public Label autoLoginLabel;
    public Label rememberLabel;
    public MenuItem noticeMenuItem;
    public MenuItem quitMenuItem;
    public MenuItem introduceMenuItem;
    public MenuItem aboutMenuItem;
    public Label usernameLabel;
    public Tooltip usernameToolTip;
    public Tooltip passwordToolTip;
    public Tooltip macToolTip;
    public Label passwordLabel;
    public Text welcomeText;
    private STATUS status = STATUS.ready;
    private DrcomTask drcomTask;
    private String savedMac;//保存之前配置的 MAC: 当 ready 之前 init 时就退出时界面中没有 MAC 地址

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.debug("初始化界面...");
        welcomeText.setText(__("Welcome to use JLU Drcom (Java Version)"));
        fileMenu.setText(__("File(F)"));
        noticeMenuItem.setText(_x("Notice", "校园网通知"));
        quitMenuItem.setText(__("Quit"));
        helpMenu.setText(__("Help(H)"));
        introduceMenuItem.setText(__("Introduce"));
        aboutMenuItem.setText(__("About"));
        usernameLabel.setText(__("Username"));
        usernameToolTip.setText(__("Part before @ symbol of your JLU email account"));
        passwordLabel.setText(__("Password"));
        passwordField.setPromptText(__("Password"));
        passwordToolTip.setText(__("Password of your JLU email account"));
        macToolTip.setText(__("MAC address registered in Network Center"));
        rememberLabel.setText(__("Remember"));
        autoLoginLabel.setText(__("Auto Login"));
        loginButton.setText(__("Login(L)"));
        logoutButton.setText(__("Logout(X)"));
        statusLabel.setText(__("Ready"));
        Drcom.setAppController(this);
        imageView.setImage(loading);
        setStatus(STATUS.init);
    }

    public STATUS getStatus() {
        return status;
    }

    public void setStatus(STATUS status) {
        log.debug("Change status to: " + status.name());
        this.status = status;
        switch (status) {
            case init:
                imageView.setVisible(true);
                statusLabel.setText(__("Initialization..."));
                loginButton.setDisable(true);
                setUIDisable(true);
                logoutButton.setFocusTraversable(false);//不能tab到按钮
                break;
            case ready:
                imageView.setVisible(false);
                statusLabel.setText(__("Ready"));
                loginButton.setText(__("Login(L)"));
                loginButton.setDisable(false);
                setUIDisable(false);
                logoutButton.setFocusTraversable(true);//可以tab到按钮
                break;
            case onLogin:
                imageView.setVisible(true);
                statusLabel.setText(__("Logging in..."));
                loginButton.setText(__("Logging in..."));
                loginButton.setDisable(true);
                setUIDisable(true);
                statusLabel.requestFocus();
                break;
            case logged:
                imageView.setVisible(false);
                statusLabel.setText(__("Logout"));
                loginButton.setText(__("Logout(L)"));
                loginButton.setDisable(false);
                setUIDisable(true);
                break;
        }
    }

    public void init() {
        readNamePass();//先把可以立即确定的输入项填充
        readNetWorkInfo();
    }

    private void readNamePass() {
        log.debug("读取配置文件中用户名密码信息...");
        File file = new File(Constants.CONF_HOME, Constants.CONF_FILE_NAME);
        if (file.exists()) {
            try {
                Properties conf = new Properties();
                conf.load(new FileInputStream(file));
                String username = conf.getProperty(Constants.KEY_USERNAME, "");
                usernameTextField.setText(username);
                String pass = conf.getProperty(Constants.KEY_PASSWORD, "");
                try {
                    //@since 1.0.1 使用 3DES 加密密码进行存储
                    String version = conf.getProperty(Constants.KEY_VERSION, "");
                    //上次加密的 key username 可长达25位 因此 lastKey 放在前
                    String key = Drcom.getLastKey() + username;
                    if (version.equals(Constants.VER_1 + "")) {
                        if (pass.length() > 0 && Drcom.getLastKey() != 0L) {
                            byte[] bytes = ByteUtil.fromHex(pass);
                            pass = new String(MD5.decrypt3DES(ByteUtil.ljust(key.getBytes(), MD5.DES_KEY_LEN), bytes));
                            //log.trace("load key = {}, pass = {}", key, pass);
                        }
                    }
                } catch (Exception e) {
                    log.debug("读取保存的密码时出现异常, 可能的原因: 上次未正确关闭软件. {}", e.getMessage(), e);
                    pass = "";
                }//第一个版本是明文
                ///log.trace("load pass = {}", pass);
                passwordField.setText(pass);
                savedMac = conf.getProperty(Constants.KEY_DASH_MAC);
                rememberCheckBox.setSelected(Boolean.valueOf(conf.getProperty(Constants.KEY_REMEMBER, "true")));
                autoLoginCheckBox.setSelected(Boolean.valueOf(conf.getProperty(Constants.KEY_AUTO_LOGIN, "false")));
                // 获取 MAC 后才自动登录
                log.debug("读取配置文件完成");
            } catch (IOException e) {
                log.debug("读取配置文件时 IO 异常", e);
                e.printStackTrace();
            }
        } else {
            log.debug("配置文件不存在");
        }
    }

    private void readNetWorkInfo() {
        log.debug("获取网络接口信息...");
        statusLabel.setText(__("Getting network interface information..."));
        long start = System.currentTimeMillis();
        new Thread(() -> IPUtil.getHostInfo(
                new IPUtil.OnGetHostInfoCallback() {
                    @Override
                    public void update(int current, int total) {
                        FxUtil.updateLabel(statusLabel, __("({0}/{1})Getting network interface information...", 0, current, total));
                    }

                    @Override
                    public void done(List<HostInfo> hostInfoList) {
                        log.debug("获取网络接口信息完成.[用时:{}ms]", System.currentTimeMillis() - start);
                        Platform.runLater(() -> {
                            macComboBox.getItems().addAll(hostInfoList);
                            if (hostInfoList.size() == 1) {
                                macComboBox.getSelectionModel().select(0);
                            } else {
                                for (HostInfo p : hostInfoList) {
                                    if (IPUtil.isPublicIP(p.getAddress4())) {
                                        macComboBox.getSelectionModel().select(p);
                                    }
                                }
                            }
                            log.debug("初始化界面完成.[用时:{}ms]", System.currentTimeMillis() - start);
                            readMac();
                        });
                    }
                }),
                "Host Info"
        ).start();
    }

    private void readMac() {
        log.debug("读取配置文件中 MAC 地址信息...");
        File file = new File(Constants.CONF_HOME, Constants.CONF_FILE_NAME);
        if (file.exists()) {
            try {
                Properties conf = new Properties();
                conf.load(new FileInputStream(file));
                ObservableList<HostInfo> items = macComboBox.getItems();
                String dashMac = conf.getProperty(Constants.KEY_DASH_MAC);
                boolean find = false;
                if (dashMac != null && dashMac.trim().length() > 0) {
                    for (HostInfo info : items) {//保存了 MAC 地址
                        if (info.getMacHexDash().equals(dashMac)) {
                            macComboBox.getSelectionModel().select(info);
                            find = true;
                            break;
                        }
                    }
                }
                //用无线上网会保存无线接口MAC//当改用有线时不能用无线的MAC
                for (HostInfo info : items) {
                    if (IPUtil.isPublicIP(info.getAddress4())) {
                        macComboBox.getSelectionModel().select(info);
                        find = true;
                        break;
                    }
                }
                if (!find && dashMac != null && dashMac.trim().length() > 0) {
                    //保存的MAC不是本机MAC地址，保存的配置也加入选项并设为默认: 比如是用的路由器MAC
                    HostInfo hostInfo = new HostInfo(conf.getProperty(Constants.KEY_HOSTNAME, "Windows-10"),
                            dashMac, __("Saved Mac Address"));
                    macComboBox.getItems().add(hostInfo);
                    macComboBox.getSelectionModel().select(hostInfo);
                }
                log.debug("读取配置文件完成");
            } catch (IOException e) {
                log.debug("读取配置文件时 IO 异常", e);
                e.printStackTrace();
            } finally {
                readConfDone();
            }
        } else {
            log.debug("配置文件不存在");
            readConfDone();
        }
    }

    private void readConfDone() {
        setStatus(STATUS.ready);
        if (usernameTextField.getText() != null && usernameTextField.getText().trim().length() == 0) {
            usernameTextField.requestFocus();//输入用户名
        } else if (passwordField.getText() != null && passwordField.getText().trim().length() == 0) {
            passwordField.requestFocus();//输入密码
        } else if (macComboBox.getSelectionModel().getSelectedItem() == null) {
            macComboBox.requestFocus();//选择 MAC 地址
        } else {
            loginButton.requestFocus();//登录
        }
        // 获取 MAC 后才自动登录
        if (autoLoginCheckBox.isSelected()) {
            onLoginButtonClick(new ActionEvent(autoLoginCheckBox, loginButton));
        }
    }

    public void onLoginButtonClick(ActionEvent actionEvent) {
        log.debug("点击{}按钮", loginButton.getText());
        switch (status) {
            case ready:
                if (!checkInput()) {
                    return;
                }
                updateConf();
                login();
                break;
            case logged:
                logout();
                break;
        }

    }

    private boolean checkInput() {
        log.trace("检查输入项目...");
        tipLabel.setText("");
        String username = usernameTextField.getText();
        String password = passwordField.getText();
        HostInfo item = macComboBox.getSelectionModel().getSelectedItem();
        boolean success = true;
        if (username == null || username.trim().length() == 0) {
            success = false;
            tipLabel.setText(__("{0} Please input username.", 0, tipLabel.getText()));
            usernameTextField.requestFocus();
        }
        if (password == null || password.trim().length() == 0) {
            if (success) {
                passwordField.requestFocus();
            }
            success = false;
            tipLabel.setText(__("{0} Please input password.", 0, tipLabel.getText()));
        }
        if (item == null) {
            if (success) {
                macComboBox.requestFocus();
            }
            success = false;
            tipLabel.setText(__("{0} Please choose Mac.", 0, tipLabel.getText()));
        }
        return success;
    }

    public void updateConf() {
        boolean remember = rememberCheckBox.isSelected();
        boolean auto = autoLoginCheckBox.isSelected();
        String username = usernameTextField.getText();
        String password = passwordField.getText();
        HostInfo hostInfo = macComboBox.getSelectionModel().getSelectedItem();
        String macHexDash = "";
        if (hostInfo != null) {
            macHexDash = hostInfo.getMacHexDash();
        }
        log.debug("username = {}, password = {}, mac = {}, remember = {},auto login = {}",
                username, "*" + password.length() + '*', macHexDash, remember, auto);
        File file = new File(Constants.CONF_HOME, Constants.CONF_FILE_NAME);
        if (remember) {
            log.trace("写入配置文件...");
            Properties conf = new Properties();
            conf.put(Constants.KEY_USERNAME, username);
            {
                //@since (ver=1) 使用 3DES 加密密码进行存储
                conf.put(Constants.KEY_VERSION, Constants.VER_1 + "");
                byte[] bytes = new byte[0];
                String key = Drcom.getThisKey() + username;//本次加密的 key
                if (password.length() > 0 && Drcom.getThisKey() != 0L) {//有密码才加密
                    bytes = MD5.encrypt3DES(ByteUtil.ljust(key.getBytes(), MD5.DES_KEY_LEN), password.getBytes());
                    //log.trace("store key = {}, pass = {}", key, ByteUtil.toHexString(bytes));
                }
                conf.put(Constants.KEY_PASSWORD, ByteUtil.toHexString(bytes));
            }
            if (macHexDash.length() > 0) {
                conf.put(Constants.KEY_DASH_MAC, macHexDash);
            } else if (savedMac != null) {//正在获取 MAC 信息时就退出就保存上次配置的
                conf.put(Constants.KEY_DASH_MAC, savedMac);
            }
            conf.put(Constants.KEY_REMEMBER, Boolean.toString(true));
            conf.put(Constants.KEY_AUTO_LOGIN, Boolean.toString(auto));
            try {
                if (!file.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.getParentFile().mkdirs();
                }
                if (!file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.createNewFile();
                }
                conf.store(new FileWriter(file), "Config file for Dr.Com(Java) By YouthLin");
            } catch (Exception e) {
                log.debug("保存配置文件时发生异常", e);
                FxUtil.showAlertWithException(new Exception("保存配置文件时发生异常", e));
            }
        } else {
            //不勾选记住密码, 删掉原来保存的配置
            if (file.exists()) {
                log.trace("删除配置文件...");
                try {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                } catch (Exception e) {
                    log.debug("删除配置文件异常");
                }
            }
        }
    }

    private void login() {
        setStatus(STATUS.onLogin);//登陆中
        drcomTask = new DrcomTask(this);
        Thread thread = new Thread(drcomTask);
        thread.setDaemon(true);
        thread.start();//登录成功或异常都会改变status
    }

    public void logout() {
        if (drcomTask != null) {
            new Thread(() -> drcomTask.notifyLogout(), "Log   out").start();
        }
    }

    private void setUIDisable(boolean flag) {
        usernameTextField.setDisable(flag);
        passwordField.setDisable(flag);
        rememberCheckBox.setDisable(flag);
        autoLoginCheckBox.setDisable(flag);
        macComboBox.setDisable(flag);
    }

    public void onExitMenuItemClick(ActionEvent actionEvent) {
        log.trace("点击退出菜单项...");
        logout();
        Platform.exit();
    }

    public void onInfoMenuItemClick(ActionEvent actionEvent) {
        log.trace("点击说明菜单项...");
        FxUtil.showAlert(__("Welcome to use JLU Drcom(Java Version, 3rd party)\nPlease do not connect wired network and wireless network at the same time\nYou may need to restart the client when network changed.\nYou can contact me on 'About' menu.\n"));
    }

    public void onAboutMenuItemClick(ActionEvent actionEvent) {
        log.trace("点击关于菜单项...");
        String yearStr;
        if (Year.now().isAfter(Year.parse(Constants.COPYRIGHT_YEAR_START))) {
            yearStr = Constants.COPYRIGHT_YEAR_START + " - " + Year.now().toString();
        } else {
            yearStr = Constants.COPYRIGHT_YEAR_START;
        }
        Alert alert = FxUtil.buildAlert(/*TRANSLATORS: 0 year(eg:2017-2018). 1 Author nickname. 2 QQ Group. 3 CC by-nc-sa. 4 blog url.*/
                __("© {0} {1}\nQQ Group: {2}\nLicense     : {3}\nArticle       : {4}", 0, yearStr, "Youth．霖", "597417651", "CC BY-NC-SA", Constants.ARTICLE_URL)
        );
        ButtonType contact = new ButtonType(__("Contact Author"));
        ButtonType projectHome = new ButtonType(__("Project Home"));
        alert.getButtonTypes().addAll(contact, projectHome);
        alert.setTitle(__("About"));
        alert.setGraphic(new ImageView(icon));
        alert.setHeaderText(__("About this software"));

        Platform.runLater(() -> {
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (contact == result.get()) {
                    Platform.runLater(() -> {
                        log.trace("点击[联系作者]按钮");
                        FxUtil.showWebPage(Constants.ARTICLE_URL);
                    });
                }
                if (projectHome == result.get()) {
                    log.trace("点击[项目主页]按钮");
                    FxUtil.showWebPage(Constants.PROJECT_HOME);
                }
            }
        });
    }

    //region //记住密码与自动登录的关系
    public void onRememberLabelClick(MouseEvent mouseEvent) {
        //记住密码取消后不能自动登录
        rememberCheckBox.setSelected(!rememberCheckBox.isSelected());
        if (!rememberCheckBox.isSelected()) {
            autoLoginCheckBox.setSelected(false);
        }
    }

    public void onRememberAction(ActionEvent event) {
        if (!rememberCheckBox.isSelected()) {
            autoLoginCheckBox.setSelected(false);
        }
    }

    public void onAutoAction(ActionEvent actionEvent) {
        if (autoLoginCheckBox.isSelected()) {
            rememberCheckBox.setSelected(true);
        }
    }

    public void onAutoLabelClick(MouseEvent mouseEvent) {
        //选了自动登录必须勾选记住密码
        autoLoginCheckBox.setSelected(!autoLoginCheckBox.isSelected());
        if (autoLoginCheckBox.isSelected()) {
            rememberCheckBox.setSelected(true);
        }
    }
    //endregion

    public void onKeyReleased(KeyEvent event) {
        KeyCode keyCode = event.getCode();
        EventTarget target = event.getTarget();
        //log.trace("event={},target={}", event, target);
        if (target == usernameTextField && !fileMenu.isShowing() && !helpMenu.isShowing()) {
            String text = usernameTextField.getText();
            if (text != null && text.length() > 25) {
                usernameTextField.setText(text.substring(0, 25));
            }
            if (KeyCode.ENTER.equals(keyCode) || KeyCode.DOWN.equals(keyCode)) {
                passwordField.requestFocus();//用户名回车后定位到密码
            }
        } else if (target == passwordField && !fileMenu.isShowing() && !helpMenu.isShowing()) {
            String text = passwordField.getText();
            if (text != null && text.length() > 25) {
                passwordField.setText(text.substring(0, 25));
            }
            if (KeyCode.ENTER.equals(keyCode)) {
                onLoginButtonClick(new ActionEvent(event.getSource(), loginButton));//密码回车后登录
            }
            if (KeyCode.DOWN.equals(keyCode)) {
                macComboBox.requestFocus();
            }
            if (KeyCode.UP.equals(keyCode)) {
                usernameTextField.requestFocus();
            }
        }
        if (event.isAltDown()) {
            if (KeyCode.L.equals(keyCode)) {//登录注销快捷键 ALT+L
                onLoginButtonClick(new ActionEvent(event.getSource(), loginButton));
            } else if (KeyCode.X.equals(keyCode)) {//退出 ALT+X
                onExitMenuItemClick(new ActionEvent(event.getSource(), logoutButton));
            } else if (KeyCode.F.equals(keyCode)) {
                //文件菜单
                fileMenu.show();
            } else if (KeyCode.H.equals(keyCode)) {
                helpMenu.show();
            }
        }
        if (event.isControlDown()) {
            if (KeyCode.W.equals(keyCode)) {
                Drcom.getStage().hide();
            }
        }
    }

    public void onNoticeMenuItemClick(ActionEvent e) {
        FxUtil.showWebPage(Constants.NOTICE_URL, Constants.NOTICE_W, Constants.NOTICE_H);
    }
}
