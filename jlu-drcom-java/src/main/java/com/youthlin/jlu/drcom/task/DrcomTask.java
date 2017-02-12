package com.youthlin.jlu.drcom.task;

import com.youthlin.jlu.drcom.Drcom;
import com.youthlin.jlu.drcom.bean.HostInfo;
import com.youthlin.jlu.drcom.bean.STATUS;
import com.youthlin.jlu.drcom.controller.AppController;
import com.youthlin.jlu.drcom.exception.DrcomException;
import com.youthlin.jlu.drcom.util.ByteUtil;
import com.youthlin.jlu.drcom.util.Constants;
import com.youthlin.jlu.drcom.util.FxUtil;
import com.youthlin.jlu.drcom.util.MD5;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.youthlin.utils.i18n.Translation.__;

/**
 * Created by lin on 2017-01-11-011.
 * challenge, login, keep_alive, logout
 */
public class DrcomTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DrcomTask.class);
    //region //Drcom 协议若干字段信息
    /**
     * 在 keep38 的回复报文中获得[28:30]
     */
    private static final byte[] keepAliveVer = {(byte) 0xdc, 0x02};
    /**
     * 官方客户端在密码错误后重新登录时，challenge 包的这个数会递增，因此这里设为静态的
     */
    private static int challengeTimes = 0;
    /**
     * 在 challenge 的回复报文中获得 [4:8]
     */
    private final byte[] salt = new byte[4];
    /**
     * 在 challenge 的回复报文中获得 [20:24], 当是有线网时，就是网络中心分配的 IP 地址，当时无线网时，是局域网地址
     */
    private final byte[] clientIp = new byte[4];
    /**
     * 在 login 的回复报文中获得[23:39]
     */
    private final byte[] tail1 = new byte[16];
    /**
     * 在 login 报文计算，计算时需要用到 salt,password
     */
    private final byte[] md5a = new byte[16];
    /**
     * 初始为 {0，0，0，0} , 在 keep40_1 的回复报文更新[16:20]
     */
    private final byte[] tail2 = new byte[4];
    /**
     * 在 keep alive 中计数.
     * 初始在 keep40_extra : 0x00, 之后每次 keep40 都加一
     */
    private int count = 0;
    private int keep38Count = 0;//仅用于日志计数
    //endregion

    private AppController appController;
    private boolean notifyLogout = false;
    private DatagramSocket client;
    private InetAddress serverAddress;
    private String username;
    private String password;
    private HostInfo hostInfo;


    public DrcomTask(AppController appController) {
        this.appController = appController;
        this.username = appController.usernameTextField.getText();
        this.password = appController.passwordField.getText();
        this.hostInfo = appController.macComboBox.getSelectionModel().getSelectedItem();
    }

    @Override
    public void run() {
        boolean exception = false;
        try {
            init();

            Thread.currentThread().setName("Challenge");
            if (!challenge(challengeTimes++)) {
                log.debug("challenge failed...");
                /*TRANSLATORS: 0 Exception code*/
                throw new DrcomException(__("Server refused the request.{0}", 0, DrcomException.CODE.ex_challenge));
            }

            Thread.currentThread().setName("L o g i n");
            if (!login()) {
                log.debug("login failed...");
                /*TRANSLATORS: 0 Exception code*/
                throw new DrcomException(__("Failed to send authentication information.{0}", 0, DrcomException.CODE.ex_login));
            }

            log.debug("登录成功!");
            if (Drcom.getStage() != null) {
                Platform.runLater(() -> Drcom.getStage().hide());//登录后隐藏窗口，弹出通知
            }
            FxUtil.showWebPage(Constants.NOTICE_URL, Constants.NOTICE_W, Constants.NOTICE_H);
            Platform.runLater(() -> appController.setStatus(STATUS.logged));

            //keep alive
            Thread.currentThread().setName("KeepAlive");
            count = 0;
            while (!notifyLogout && alive()) {//收到注销通知则停止
                Thread.sleep(20000);//每 20s 一次
            }
        } catch (SocketTimeoutException e) {
            log.debug("通信超时", e);
            exception = true;
            FxUtil.showAlertWithException(new DrcomException(__("Waisting server response time out.\nIt may caused by Network state changed or your device slept too long.\nPlease try login again.\n"),
                    e, DrcomException.CODE.ex_timeout));
        } catch (IOException e) {
            log.debug("IO 异常", e);
            exception = true;
            FxUtil.showAlertWithException(new DrcomException(__("IO Exception, please check your network."), e, DrcomException.CODE.ex_io));
        } catch (DrcomException e) {
            log.debug("登录异常", e);
            exception = true;
            FxUtil.showAlertWithException(e);
        } catch (InterruptedException e) {
            log.debug("线程异常", e);
            exception = true;
            FxUtil.showAlertWithException(new DrcomException(__("Thread Exception, There is a error."), e, DrcomException.CODE.ex_thread));
        } catch (Exception e) {
            exception = true;
            FxUtil.showAlertWithException(new DrcomException(__("Unknown error"), e));
        } finally {
            if (exception) {//若发生了异常：密码错误等。 则应允许重新登录
                Platform.runLater(() -> {
                    appController.setStatus(STATUS.ready);
                    appController.statusLabel.setText(__("You are off line now."));
                });
            }
            if (client != null) {
                client.close();
                client = null;
            }
        }
    }

    /**
     * 初始化套接字、设置超时时间、设置服务器地址
     */
    private void init() throws DrcomException {
        try {
            //每次使用同一个端口 若有多个客户端运行这里会抛出异常
            client = new DatagramSocket(Constants.PORT);
            client.setSoTimeout(Constants.TIMEOUT);
            serverAddress = InetAddress.getByName(Constants.AUTH_SERVER);
        } catch (SocketException e) {
            throw new DrcomException(__("The port is occupied, do you have any other clients not exited?"), e, DrcomException.CODE.ex_init);
        } catch (UnknownHostException e) {
            throw new DrcomException(__("The server could not be found. (check DNS settings)"), DrcomException.CODE.ex_init);
        }
    }

    /**
     * 在回复报文中取得 salt 和 clientIp
     */
    private boolean challenge(int tryTimes) throws DrcomException {
        try {
            byte[] buf = {0x01, (byte) (0x02 + tryTimes), ByteUtil.randByte(), ByteUtil.randByte(), 0x6a,
                    0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00};
            DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, Constants.PORT);
            client.send(packet);
            log.trace("send challenge data.【{}】", ByteUtil.toHexString(buf));
            buf = new byte[76];
            packet = new DatagramPacket(buf, buf.length);
            client.receive(packet);
            if (buf[0] == 0x02) {
                log.trace("recv challenge data.【{}】", ByteUtil.toHexString(buf));
                // 保存 salt 和 clientIP
                System.arraycopy(buf, 4, salt, 0, 4);
                System.arraycopy(buf, 20, clientIp, 0, 4);
                return true;
            }
            log.debug("challenge fail, unrecognized response.【{}】", ByteUtil.toHexString(buf));
            return false;
        } catch (SocketTimeoutException e) {
            throw new DrcomException(__("Challenge server failed, time out. {0}", 0, DrcomException.CODE.ex_challenge));
        } catch (IOException e) {
            throw new DrcomException(__("Failed to send authentication information. {0}", 0, DrcomException.CODE.ex_challenge));
        }
    }

    private boolean login() throws IOException, DrcomException {
        byte[] buf = makeLoginPacket();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, Constants.PORT);
        client.send(packet);
        log.trace("send login packet.【{}】", ByteUtil.toHexString(buf));
        byte[] recv = new byte[128];//45
        client.receive(new DatagramPacket(recv, recv.length));
        log.trace("recv login packet.【{}】", ByteUtil.toHexString(recv));
        if (recv[0] != 0x04) {
            if (recv[0] == 0x05) {
                if (recv[4] == 0x0B) {
                    throw new DrcomException(__("Invalid Mac Address, please select the address registered in ip.jlu.edu.cn") + "MAC 地址错误, 请选择在网络中心注册的地址.", DrcomException.CODE.ex_login);
                }
                throw new DrcomException(__("Invalid username or password."), DrcomException.CODE.ex_login);
            } else {
                throw new DrcomException(__("Failed to login, unknown error."), DrcomException.CODE.ex_login);
            }
        }
        // 保存 tail1. 构造 keep38 要用 md5a(在mkptk中保存) 和 tail1
        // 注销也要用 tail1
        System.arraycopy(recv, 23, tail1, 0, 16);
        return true;
    }

    /**
     * 需要用来自 challenge 回复报文中的 salt, 构造报文时会保存 md5a keep38 要用
     */
    private byte[] makeLoginPacket() {
        byte code = 0x03;
        byte type = 0x01;
        byte EOF = 0x00;
        byte controlCheck = 0x20;
        byte adapterNum = 0x05;
        byte ipDog = 0x01;
        byte[] primaryDNS = {10, 10, 10, 10};
        byte[] dhcp = {0, 0, 0, 0};
        byte[] md5b;

        int passLen = password.length();
        if (passLen > 16) {
            passLen = 16;
        }
        int dataLen = 334 + (passLen - 1) / 4 * 4;
        byte[] data = new byte[dataLen];

        data[0] = code;
        data[1] = type;
        data[2] = EOF;
        data[3] = (byte) (username.length() + 20);

        System.arraycopy(MD5.md5(new byte[]{code, type}, salt, password.getBytes()),
                0, md5a, 0, 16);//md5a保存起来
        System.arraycopy(md5a, 0, data, 4, md5a.length);//md5a 4+16=20

        byte[] user = ByteUtil.ljust(username.getBytes(), 36);
        System.arraycopy(user, 0, data, 20, user.length);//username 20+36=56

        data[56] = controlCheck;//0x20
        data[57] = adapterNum;//0x05

        //md5a[0:6] xor mac
        System.arraycopy(md5a, 0, data, 58, 6);
        byte[] macBytes = hostInfo.getMacBytes();
        for (int i = 0; i < 6; i++) {
            data[i + 58] ^= macBytes[i];//md5a oxr mac
        }// xor 58+6=64

        md5b = MD5.md5(new byte[]{0x01}, password.getBytes(), salt, new byte[]{0x00, 0x00, 0x00, 0x00});
        System.arraycopy(md5b, 0, data, 64, md5b.length);//md5b 64+16=80

        data[80] = 0x01;//number of ip
        System.arraycopy(clientIp, 0, data, 81, clientIp.length);//ip1 81+4=85
        System.arraycopy(new byte[]{
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
        }, 0, data, 85, 12);//ip2/3/4 85+12=97

        data[97] = 0x14;//临时放，97 ~ 97+8 是 md5c[0:8]
        data[98] = 0x00;
        data[99] = 0x07;
        data[100] = 0x0b;
        byte[] tmp = new byte[101];
        System.arraycopy(data, 0, tmp, 0, tmp.length);//前 97 位 和 0x14_00_07_0b
        byte[] md5c = MD5.md5(tmp);
        System.arraycopy(md5c, 0, data, 97, 8);//md5c 97+8=105

        data[105] = ipDog;//0x01
        //0 106+4=110
        byte[] hostname = ByteUtil.ljust(hostInfo.getHostname().getBytes(), 32);
        System.arraycopy(hostname, 0, data, 110, hostname.length);//hostname 110+32=142
        System.arraycopy(primaryDNS, 0, data, 142, 4);//primaryDNS 142+4=146
        System.arraycopy(dhcp, 0, data, 146, 4);//dhcp 146+4=150

        //second dns 150+4=154
        //delimiter 154+8=162

        data[162] = (byte) 0x94;//unknown 162+4=166
        data[166] = 0x06;       //os major 166+4=170
        data[170] = 0x02;       //os minor 170+4=174
        data[174] = (byte) 0xf0;//os build
        data[175] = 0x23;       //os build 174+4=178
        data[178] = 0x02;       //os unknown 178+4=182

        //DRCOM CHECK
        data[182] = 0x44;//\x44\x72\x43\x4f\x4d\x00\xcf\x07
        data[183] = 0x72;
        data[184] = 0x43;
        data[185] = 0x4f;
        data[186] = 0x4d;
        data[187] = 0x00;
        data[188] = (byte) 0xcf;
        data[189] = 0x07;
        data[190] = 0x6a;

        //0 191+55=246

        System.arraycopy("1c210c99585fd22ad03d35c956911aeec1eb449b".getBytes(),
                0, data, 246, 40);//246+40=286
        //0 286+24=310
        data[310] = 0x6a;//0x6a 0x00 0x00 310+3=313

        data[313] = (byte) passLen;//password length
        byte[] ror = ByteUtil.ror(md5a, password.getBytes());
        System.arraycopy(ror, 0, data, 314, passLen);//314+passlen
        data[314 + passLen] = 0x02;
        data[315 + passLen] = 0x0c;

        //checksum(data+'\x01\x26\x07\x11\x00\x00'+dump(mac))
        //\x01\x26\x07\x11\x00\x00
        data[316 + passLen] = 0x01;//临时放, 稍后被 checksum 覆盖
        data[317 + passLen] = 0x26;
        data[318 + passLen] = 0x07;
        data[319 + passLen] = 0x11;
        data[320 + passLen] = 0x00;
        data[321 + passLen] = 0x00;
        System.arraycopy(macBytes, 0, data, 322 + passLen, 4);
        tmp = new byte[326 + passLen];//data+'\x01\x26\x07\x11\x00\x00'+dump(mac)
        System.arraycopy(data, 0, tmp, 0, tmp.length);
        tmp = ByteUtil.checksum(tmp);
        System.arraycopy(tmp, 0, data, 316 + passLen, 4);//checksum 316+passlen+4=320+passLen

        data[320 + passLen] = 0x00;
        data[321 + passLen] = 0x00;//分割

        System.arraycopy(macBytes, 0, data, 322 + passLen, macBytes.length);
        //mac 322+passLen+6=328+passLen

        // passLen % 4=mod 补0个数  4-mod  (4-mod)%4
        //             0    0        4
        //             1    3        3
        //             2    2        2
        //             3    1        1
        int zeroCount = (4 - passLen % 4) % 4;
        for (int i = 0; i < zeroCount; i++) {
            data[328 + passLen + i] = 0x00;
        }
        data[328 + passLen + zeroCount] = ByteUtil.randByte();
        data[329 + passLen + zeroCount] = ByteUtil.randByte();
        return data;
    }

    private boolean alive() throws IOException {
        boolean needExtra = false;
        log.trace("count = {}, keep38count = {}", count, ++keep38Count);
        if (count % 21 == 0) {//第一个 keep38 后有 keep40_extra, 十个 keep38 后 count 就加了21
            needExtra = true;
        }//每10个keep38

        //-------------- keep38 ----------------------------------------------------
        byte[] packet38 = makeKeepPacket38();
        DatagramPacket packet = new DatagramPacket(packet38, packet38.length, serverAddress, Constants.PORT);
        client.send(packet);
        log.trace("[rand={}|{}]send keep38. 【{}】",
                ByteUtil.toHexString(packet38[36]), ByteUtil.toHexString(packet38[37]),
                ByteUtil.toHexString(packet38));

        byte[] recv = new byte[128];
        client.receive(new DatagramPacket(recv, recv.length));
        log.trace("[rand={}|{}]recv Keep38. [{}.{}.{}.{}] 【{}】",
                ByteUtil.toHexString(recv[6]), ByteUtil.toHexString(recv[7]),
                ByteUtil.toInt(recv[12]), ByteUtil.toInt(recv[13]), ByteUtil.toInt(recv[14]), ByteUtil.toInt(recv[15]),
                ByteUtil.toHexString(recv));
        keepAliveVer[0] = recv[28];//收到keepAliveVer//通常不会变
        keepAliveVer[1] = recv[29];

        if (needExtra) {//每十次keep38都要发一个 keep40_extra
            log.debug("Keep40_extra...");
            //--------------keep40_extra--------------------------------------------
            //先发 keep40_extra 包
            byte[] packet40extra = makeKeepPacket40(1, true);
            packet = new DatagramPacket(packet40extra, packet40extra.length, serverAddress, Constants.PORT);
            client.send(packet);
            log.trace("[seq={}|type={}][rand={}|{}]send Keep40_extra. 【{}】", packet40extra[1], packet40extra[5],
                    ByteUtil.toHexString(packet40extra[8]), ByteUtil.toHexString(packet40extra[9]),
                    ByteUtil.toHexString(packet40extra));
            recv = new byte[512];
            client.receive(new DatagramPacket(recv, recv.length));
            log.trace("[seq={}|type={}][rand={}|{}]recv Keep40_extra. 【{}】", recv[1], recv[5],
                    ByteUtil.toHexString(recv[8]), ByteUtil.toHexString(recv[9]), ByteUtil.toHexString(recv));
            //不理会回复
        }

        //--------------keep40_1----------------------------------------------------
        byte[] packet40_1 = makeKeepPacket40(1, false);
        packet = new DatagramPacket(packet40_1, packet40_1.length, serverAddress, Constants.PORT);
        client.send(packet);
        log.trace("[seq={}|type={}][rand={}|{}]send Keep40_1. 【{}】", packet40_1[1], packet40_1[5],
                ByteUtil.toHexString(packet40_1[8]), ByteUtil.toHexString(packet40_1[9]),
                ByteUtil.toHexString(packet40_1));

        recv = new byte[64];//40
        client.receive(new DatagramPacket(recv, recv.length));
        log.trace("[seq={}|type={}][rand={}|{}]recv Keep40_1. 【{}】", recv[1], recv[5],
                ByteUtil.toHexString(recv[8]), ByteUtil.toHexString(recv[9]), ByteUtil.toHexString(recv));
        //保存 tail2 , 待会儿构造 packet 要用
        System.arraycopy(recv, 16, tail2, 0, 4);

        //--------------keep40_2----------------------------------------------------
        byte[] packet40_2 = makeKeepPacket40(2, false);
        packet = new DatagramPacket(packet40_2, packet40_2.length, serverAddress, Constants.PORT);
        client.send(packet);
        log.trace("[seq={}|type={}][rand={}|{}]send Keep40_2. 【{}】", packet40_2[1], packet40_2[5],
                ByteUtil.toHexString(packet40_2[8]), ByteUtil.toHexString(packet40_2[9]),
                ByteUtil.toHexString(packet40_2));

        client.receive(new DatagramPacket(recv, recv.length));
        log.trace("[seq={}|type={}][rand={}|{}]recv Keep40_2. 【{}】", recv[1], recv[5],
                ByteUtil.toHexString(recv[8]), ByteUtil.toHexString(recv[9]), ByteUtil.toHexString(recv));
        //keep40_2 的回复也不用理会
        return true;
    }

    /**
     * 0xff md5a:16位 0x00 0x00 0x00 tail1:16位 rand rand
     */
    private byte[] makeKeepPacket38() {
        byte[] data = new byte[38];
        data[0] = (byte) 0xff;
        System.arraycopy(md5a, 0, data, 1, md5a.length);//1+16=17
        //17 18 19
        System.arraycopy(tail1, 0, data, 20, tail1.length);//20+16=36
        data[36] = ByteUtil.randByte();
        data[37] = ByteUtil.randByte();
        return data;
    }

    /**
     * keep40_额外的 就是刚登录时, keep38 后发的那个会收到 272 This Program can not run in dos mode
     * keep40_1     每 秒发送
     * keep40_2
     */
    private byte[] makeKeepPacket40(int firstOrSecond, boolean extra) {
        byte[] data = new byte[40];
        data[0] = 0x07;
        data[1] = (byte) count++;//到了 0xff 会回到 0x00
        data[2] = 0x28;
        data[3] = 0x00;
        data[4] = 0x0b;
        //   keep40_1   keep40_2
        //  发送  接收  发送  接收
        //  0x01 0x02 0x03 0xx04
        if (firstOrSecond == 1 || extra) {//keep40_1 keep40_extra 是 0x01
            data[5] = 0x01;
        } else {
            data[5] = 0x03;
        }
        if (extra) {
            data[6] = 0x0f;
            data[7] = 0x27;
        } else {
            data[6] = keepAliveVer[0];
            data[7] = keepAliveVer[1];
        }
        data[8] = ByteUtil.randByte();
        data[9] = ByteUtil.randByte();

        //[10-15]:0

        System.arraycopy(tail2, 0, data, 16, 4);//16+4=20

        //20 21 22 23 : 0

        if (firstOrSecond == 2) {
            System.arraycopy(clientIp, 0, data, 24, 4);
            byte[] tmp = new byte[28];
            System.arraycopy(data, 0, tmp, 0, tmp.length);
            tmp = ByteUtil.crc(tmp);
            System.arraycopy(tmp, 0, data, 24, 4);//crc 24+4=28

            System.arraycopy(clientIp, 0, data, 28, 4);//28+4=32
            //之后 8 个 0
        }
        return data;
    }

    public void notifyLogout() {
        notifyLogout = true;//终止 keep 线程
        //logout
        log.debug("收到注销指令");
        if (STATUS.logged.equals(appController.getStatus())) {//已登录才注销
            boolean succ = true;
            try {
                challenge(challengeTimes++);
                logout();
            } catch (Throwable t) {
                succ = false;
                log.debug("注销异常", t);
                FxUtil.showAlertWithException(new DrcomException(__("Exception when logout."), t));
            } finally {
                //不管怎样重新登录
                Platform.runLater(() -> appController.setStatus(STATUS.ready));
                if (succ) {
                    FxUtil.showAlert(__("Logout success."));
                }
                if (client != null) {
                    client.close();
                    client = null;
                }
            }
        }
    }

    private boolean logout() throws IOException {
        byte[] buf = makeLogoutPacket();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, Constants.PORT);
        client.send(packet);
        log.trace("send logout packet.【{}】", ByteUtil.toHexString(buf));

        byte[] recv = new byte[512];//25
        client.receive(new DatagramPacket(recv, recv.length));
        log.trace("recv logout packet response.【{}】", ByteUtil.toHexString(recv));
        if (recv[0] == 0x04) {
            log.debug("注销成功");
        } else {
            log.debug("注销...失败?");
        }

        return true;
    }

    private byte[] makeLogoutPacket() {
        byte[] data = new byte[80];
        data[0] = 0x06;//code
        data[1] = 0x01;//type
        data[2] = 0x00;//EOF
        data[3] = (byte) (username.length() + 20);
        byte[] md5 = MD5.md5(new byte[]{0x06, 0x01}, salt, password.getBytes());
        System.arraycopy(md5, 0, data, 4, md5.length);//md5 4+16=20
        System.arraycopy(ByteUtil.ljust(username.getBytes(), 36),
                0, data, 20, 36);//username 20+36=56
        data[56] = 0x20;
        data[57] = 0x05;
        byte[] macBytes = hostInfo.getMacBytes();
        for (int i = 0; i < 6; i++) {
            data[58 + i] = (byte) (data[4 + i] ^ macBytes[i]);
        }// mac xor md5 58+6=64
        System.arraycopy(tail1, 0, data, 64, tail1.length);//64+16=80
        return data;
    }

}
