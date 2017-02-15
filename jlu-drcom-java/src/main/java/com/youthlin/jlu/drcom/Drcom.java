package com.youthlin.jlu.drcom;

import com.youthlin.jlu.drcom.bean.STATUS;
import com.youthlin.jlu.drcom.controller.AppController;
import com.youthlin.jlu.drcom.util.Constants;
import com.youthlin.jlu.drcom.util.FxUtil;
import com.youthlin.utils.i18n.Translation;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.UUID;

import static com.youthlin.jlu.drcom.util.FxUtil.icon;
import static com.youthlin.utils.i18n.Translation.__;

//Created by lin on 2017-01-08-008.
public class Drcom extends Application {
    public static final String TITLE;

    static {
        //放在 log 之前初始化//前一次commit被IDEA自动格式化放乱了
        System.setProperty("drcom.java.sessionID", UUID.randomUUID().toString().substring(24));
        System.setProperty("drcom.java.data.home", Constants.DATA_HOME);
        Translation.setDft(Translation.getBundle("Drcom"));
        TITLE = __("JLU Drcom Java Version");
    }

    private static final Logger log = LoggerFactory.getLogger(Drcom.class);
    private static AppController appController;
    private static Stage stage;
    private static long lastKey;
    private static long thisKey;
    private TrayIcon trayIcon;

    public static void main(String[] args) {
        Application.launch(Drcom.class, args);
    }

    public static void setAppController(AppController appController) {
        Drcom.appController = appController;
    }

    public static Stage getStage() {
        return stage;
    }

    @Override
    public void start(Stage stage) throws IOException {
        Thread.currentThread().setName("JavaFxApp");
        log.trace("重命名 Fx 线程名称为{}", Thread.currentThread().getName());
        Drcom.stage = stage;
        if (!checkSingleton()) {
            log.debug("已有运行中客户端");
            Alert alert = FxUtil.buildAlert(__("There is a running client, no necessary to start a new client."));
            alert.setHeaderText(__("Note: There is already a running client."));
            alert.setOnHiding(e -> Platform.exit());//关闭对话框后退出
            alert.show();
            return;
        }
        stage.getIcons().add(icon);
        stage.setTitle(TITLE);
        Parent root = FXMLLoader.load(getClass().getResource("/login.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setMinWidth(310);
        stage.centerOnScreen();
        stage.setResizable(false);
        // https://gist.github.com/jewelsea/e231e89e8d36ef4e5d8a#file-javafxtrayiconsample-java-L39
        // instructs the javafx system not to exit implicitly when the last application window is shut.
        // 最后一个窗口关闭也不退出
        Platform.setImplicitExit(false);//仅通过退出按钮或退出菜单退出
        enableTray(stage);//启用托盘
        stage.show();
        appController.init();
    }

    // Java Swing 每次打开只运行一个实例，并激活任务栏里的程序
    // http://blog.csdn.net/lovoo/article/details/52541632
    private boolean checkSingleton() {
        File tmp = new File(Constants.DATA_HOME, Constants.LOCK_FILE_NAME);
        log.trace("lock file = {}", tmp);
        try {
            //noinspection ResultOfMethodCallIgnored
            tmp.createNewFile();
            RandomAccessFile r = new RandomAccessFile(tmp, "rw");
            FileChannel fc = r.getChannel();
            FileLock lock = fc.tryLock();
            if (lock == null || !lock.isValid()) {
                // 如果没有得到锁，则程序退出.
                // 没有必要手动释放锁和关闭流，当程序退出时，他们会被关闭的.
                return false;
            }
            {
                //@since (ver=1) 使用 3DES 加密密码进行存储
                log.trace("lock file len = {}", r.length());
                if (r.length() > 0) {
                    int version = r.readInt();
                    lastKey = r.readLong();//读取上次用于加密的key
                    log.trace("version = {},long = {}", version, lastKey);
                }
                thisKey = System.currentTimeMillis();
                r.seek(0L);//从头开始写（覆盖）
                r.writeInt(Constants.VER_1);
                r.writeLong(thisKey);//保存本次退出时用于加密的 key
            }
        } catch (IOException e) {
            log.debug("IOException", e);
            return false;
        }
        return true;
    }

    @Override
    public void stop() throws Exception {
        SystemTray.getSystemTray().remove(trayIcon);
        if (appController != null) {
            appController.updateConf();//退出时再检查配置文件是否发生改变
        }
        log.debug("stop app.");
    }

    private void enableTray(final Stage stage) {
        PopupMenu popupMenu = new PopupMenu();
        java.awt.MenuItem openItem = new java.awt.MenuItem(__("Show / Hide"));
        java.awt.MenuItem logout = new java.awt.MenuItem(__("Logout"));
        java.awt.MenuItem quitItem = new java.awt.MenuItem(__("Quit"));
        openItem.addActionListener(e -> {
            if (stage.isShowing()) {
                Platform.runLater(stage::hide);
            } else {
                Platform.runLater(stage::show);
            }
        });
        logout.addActionListener(e -> {
            if (appController != null && appController.getStatus().equals(STATUS.logged)) {
                appController.logout();
                Platform.runLater(stage::show);
            }
        });
        quitItem.addActionListener(e -> {
            if (appController != null) {
                appController.logout();
            }
            Platform.exit();
        });
        popupMenu.add(openItem);
        popupMenu.add(logout);
        popupMenu.add(quitItem);
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage image = ImageIO.read(Drcom.class.getResourceAsStream(Constants.LOGO_URL));
            trayIcon = new TrayIcon(image, TITLE, popupMenu);
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {//鼠标左键
                        if (stage.isShowing()) {
                            Platform.runLater(stage::hide);
                        } else {
                            Platform.runLater(stage::show);
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.debug("Exception. 托盘不可用.", e);
        }
    }

    public static long getLastKey() {
        return lastKey;
    }

    public static long getThisKey() {
        return thisKey;
    }
}
