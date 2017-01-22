package com.youthlin.jlu.drcom.controller;

import com.youthlin.jlu.drcom.Drcom;
import com.youthlin.jlu.drcom.bean.HostInfo;
import com.youthlin.jlu.drcom.bean.STATUS;
import com.youthlin.jlu.drcom.task.DrcomTask;
import com.youthlin.jlu.drcom.util.Constants;
import com.youthlin.jlu.drcom.util.FxUtil;
import com.youthlin.jlu.drcom.util.IPUtil;
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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
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


/**
 * Created by lin on 2017-01-08-008.
 * 登录页面
 */
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
    private STATUS status = STATUS.ready;
    private DrcomTask drcomTask;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.debug("初始化界面...");
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
                statusLabel.setText("初始化...");
                loginButton.setDisable(true);
                setUIDisable(true);
                break;
            case ready:
                imageView.setVisible(false);
                statusLabel.setText("就绪");
                loginButton.setText("登录(L)");
                loginButton.setDisable(false);
                setUIDisable(false);
                break;
            case onLogin:
                imageView.setVisible(true);
                statusLabel.setText("登录中...");
                loginButton.setText("登录中...");
                loginButton.setDisable(true);
                setUIDisable(true);
                break;
            case logged:
                imageView.setVisible(false);
                statusLabel.setText("就绪");
                loginButton.setText("注销(L)");
                loginButton.setDisable(false);
                setUIDisable(true);
                break;
        }
    }

    public void init() {
        readNetWorkInfo();
    }

    private void readNetWorkInfo() {
        log.debug("获取网络接口信息...");
        statusLabel.setText("获取网络接口信息...");
        long start = System.currentTimeMillis();
        new Thread(() -> IPUtil.getHostInfo(
                new IPUtil.OnGetHostInfoCallback() {
                    @Override
                    public void update(int current, int total) {
                        FxUtil.updateLabel(statusLabel, "(" + current + '/' + total + ")获取网络接口信息...");
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
                            readConf();
                        });
                    }
                }),
                "Host Info"
        ).start();
    }

    private void readConf() {
        log.debug("读取配置文件...");
        File file = new File(Constants.CONF_HOME, Constants.CONF_FILE_NAME);
        if (file.exists()) {
            try {
                Properties conf = new Properties();
                conf.load(new FileInputStream(file));
                usernameTextField.setText(conf.getProperty(Constants.KEY_USERNAME, ""));
                passwordField.setText(conf.getProperty(Constants.KEY_PASSWORD, ""));
                String dashMac = conf.getProperty(Constants.KEY_DASH_MAC, "00-11-22-33-44-55");
                ObservableList<HostInfo> items = macComboBox.getItems();
                boolean find = false;
                for (HostInfo info : items) {
                    if (info.getMacHexDash().equals(dashMac)) {
                        macComboBox.getSelectionModel().select(info);
                        find = true;
                        break;
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
                if (!find) {
                    //保存的MAC不是本机MAC地址，保存的配置也加入选项并设为默认: 比如是用的路由器MAC
                    HostInfo hostInfo = new HostInfo(conf.getProperty(Constants.KEY_HOSTNAME, "Windows-10"),
                            dashMac, "保存的 MAC 地址");
                    macComboBox.getItems().add(hostInfo);
                    macComboBox.getSelectionModel().select(hostInfo);
                }
                rememberCheckBox.setSelected(Boolean.valueOf(conf.getProperty(Constants.KEY_REMEMBER, "true")));
                autoLoginCheckBox.setSelected(Boolean.valueOf(conf.getProperty(Constants.KEY_AUTO_LOGIN, "false")));
                if (autoLoginCheckBox.isSelected()) {
                    onLoginButtonClick(new ActionEvent(autoLoginCheckBox, loginButton));
                }
                log.debug("读取配置文件完成");
            } catch (IOException e) {
                log.debug("读取配置文件时 IO 异常", e);
                e.printStackTrace();
            } finally {
                setStatus(STATUS.ready);
            }
        } else {
            log.debug("配置文件不存在");
            setStatus(STATUS.ready);
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
            tipLabel.setText(tipLabel.getText() + "请输入用户名. ");
        }
        if (password == null || password.trim().length() == 0) {
            success = false;
            tipLabel.setText(tipLabel.getText() + "请输入密码. ");
        }
        if (item == null) {
            success = false;
            tipLabel.setText(tipLabel.getText() + "请选择 MAC 地址. ");
        }
        return success;
    }

    public void updateConf() {
        boolean remember = rememberCheckBox.isSelected();
        boolean auto = autoLoginCheckBox.isSelected();
        String username = usernameTextField.getText();
        String password = passwordField.getText();
        HostInfo hostInfo = macComboBox.getSelectionModel().getSelectedItem();
        String macHexDash = hostInfo.getMacHexDash();
        if (macHexDash == null) {
            return;//还在获取网络接口信息时就退出 那就不管配置不配置了
        }
        log.debug("username = {}, password = {}, mac = {}, remember = {},auto login = {}",
                username, "*" + password.length() + '*', macHexDash, remember, auto);
        File file = new File(Constants.CONF_HOME, Constants.CONF_FILE_NAME);
        if (remember) {
            log.trace("写入配置文件...");
            Properties conf = new Properties();
            conf.put(Constants.KEY_USERNAME, username);
            conf.put(Constants.KEY_PASSWORD, password);
            conf.put(Constants.KEY_DASH_MAC, macHexDash);
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
        FxUtil.showAlert("欢迎使用吉林大学校园网登录客户端 Java 版(非官方)\n"
                + "请不要同时连接有线网络与无线网络\n"
                + "网络变更有可能需要重启程序\n"
                + "如有更多问题您可打开[关于]菜单与作者取得联系\n");
    }

    public void onAboutMenuItemClick(ActionEvent actionEvent) {
        log.trace("点击关于菜单项...");
        String yearStr;
        if (Year.now().isAfter(Year.parse(Constants.COPYRIGHT_YEAR_START))) {
            yearStr = Constants.COPYRIGHT_YEAR_START + " - " + Year.now().toString();
        } else {
            yearStr = Constants.COPYRIGHT_YEAR_START;
        }
        Alert alert = FxUtil.buildAlert("CC BY-NC-SA\n© " + yearStr + " Youth．霖\n" + Constants.ARTICLE_URL);
        ButtonType contact = new ButtonType("联系作者");
        ButtonType projectHome = new ButtonType("项目主页");
        alert.getButtonTypes().addAll(contact, projectHome);
        alert.setTitle(Constants.ABOUT_TITLE);
        alert.setGraphic(new ImageView(icon));
        alert.setHeaderText("关于本软件");

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
