package com.example.drcom;

import android.os.AsyncTask;
import android.util.Log;

import com.example.service.KeepListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by lin on 2017-01-11-011.
 * challenge, login, keep_alive, logout
 */
public class DrcomTask extends AsyncTask<Void, String, Void> {
//    private static final Logger log = LoggerFactory.getLogger(DrcomTask.class);
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
     * 在 challenge 的回复报文中获得 [20:24], 当是有线网时，就是网络中心分配的 IP 地址，当是无线网时，是局域网地址
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

    private boolean notifyLogout = false;
    private DatagramSocket client;
    private InetAddress serverAddress;
    private String username;
    private String password;
    private String mac;
    private HostInfo hostInfo;
    private static final String TAG = "DrcomTask";
    private KeepListener keepListener;
    private STATUS status = STATUS.offline;
    public boolean canReconnect = false;

    public DrcomTask(KeepListener listener) {
        keepListener = listener;
    }
    public void setNamePassMAC(String username, String password, String macAddr) {
        this.username = username;
        this.password = password;
        this.mac = macAddr;
        hostInfo = new HostInfo("windows", macAddr);
    }
    public String[] getNamePassMAC() {
        String[] strings = new String[3];
        strings[0] = username;
        strings[1] = password;
        strings[2] = mac;
        return strings;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        Log.d(TAG, "onProgressUpdate: 要更新的进度值为" + values[0]);
        if ("保持在线".equals(values[0])) {
            keepListener.keepingAlive("保持在线");
        } else if ("登录成功".equals(values[0])) {
            keepListener.informLogStatus(STATUS.online, getNamePassMAC());
        } else if ("已离线".equals(values[0])) {
            keepListener.informLogStatus(STATUS.offline, getNamePassMAC());
        }
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            init();
            Thread.currentThread().setName("Challenge");
            if (!challenge(challengeTimes++)) {
                throw new DrcomException("Server refused the request.{0}", DrcomException.CODE.ex_challenge);
            }
            Thread.currentThread().setName("L o g i n");
            if (!login()) {
                Log.d(TAG, "doInBackground: 登录失败");
                throw new DrcomException("Failed to send authentication information.{0}", DrcomException.CODE.ex_login);
            }
            status = STATUS.online;
            canReconnect = true;
            Log.d(TAG, "doInBackground: 登录成功");
            publishProgress("登录成功");
            //keep alive
            Thread.currentThread().setName("KeepAlive");
            count = 0;
            while (!notifyLogout && alive() && !isCancelled()) {//收到注销通知则停止
                status = STATUS.online;
                Log.d(TAG, "doInBackground: 保持在线");
                publishProgress("保持在线");
                Thread.sleep(20000);//每 20s 一次
            }
            status = STATUS.offline;
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "doInBackground: 超时异常", e);
        } catch (IOException e) {
            Log.e(TAG, "doInBackground: IO异常", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "doInBackground: 睡眠被打断异常", e);
        } catch (DrcomException e) {
            Log.e(TAG, "doInBackground: 登录失败", e);
            Log.d(TAG, "doInBackground: 当前是否可登录" + canReconnect);
            // 提醒用户输入有误
            if ("[ex_login] Invalid Mac Address".equals(e.getMessage())) {
                keepListener.informInvalidMAC();
            } else if ("[ex_login] Invalid username or password".equals(e.getMessage())) {
                keepListener.informInvalidNameOrPass();
            }
        } catch (Exception e) {
            Log.e(TAG, "doInBackground: 其他异常", e);
        } finally {
            if (client != null) {
                client.close();
                client = null;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Log.d(TAG, "onPostExecute: AsyncTask DrClient已停止");
        status = STATUS.offline;
        publishProgress("已离线");
        keepListener.informCanLoginNow(canReconnect, getNamePassMAC());
    }

    @Override
    protected void onCancelled(Void aVoid) {
        Log.d(TAG, "onCancelled(Void aVoid): 任务取消");
        status = STATUS.offline;
        publishProgress("已离线");
        keepListener.informCanLoginNow(canReconnect, getNamePassMAC());
    }

    @Override
    protected void onCancelled() {
        Log.d(TAG, "onCancelled(): 任务取消");
    }

    public boolean isOnline() {
        Log.d(TAG, "isOnline: 在异步任务中查看是否在线");
        return status == STATUS.online;
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
            throw new DrcomException("The port is occupied, do you have any other clients not exited?", e, DrcomException.CODE.ex_init);
        } catch (UnknownHostException e) {
            throw new DrcomException("The server could not be found. (check DNS settings)", DrcomException.CODE.ex_init);
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
//            log.trace("send challenge data.【{}】", ByteUtil.toHexString(buf));
            buf = new byte[76];
            packet = new DatagramPacket(buf, buf.length);
            client.receive(packet);
            if (buf[0] == 0x02) {
//                log.trace("recv challenge data.【{}】", ByteUtil.toHexString(buf));
                // 保存 salt 和 clientIP
                System.arraycopy(buf, 4, salt, 0, 4);
                System.arraycopy(buf, 20, clientIp, 0, 4);
                return true;
            }
//            log.warn("challenge fail, unrecognized response.【{}】", ByteUtil.toHexString(buf));
            return false;
        } catch (SocketTimeoutException e) {
            throw new DrcomException("Challenge server failed, time out. {0}", DrcomException.CODE.ex_challenge);
        } catch (IOException e) {
            throw new DrcomException("Failed to send authentication information. {0}", DrcomException.CODE.ex_challenge);
        }
    }

    private boolean login() throws IOException, DrcomException {
        byte[] buf = makeLoginPacket();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, Constants.PORT);
        client.send(packet);
//        log.trace("send login packet.【{}】", ByteUtil.toHexString(buf));
        byte[] recv = new byte[128];//45
        client.receive(new DatagramPacket(recv, recv.length));
//        log.trace("recv login packet.【{}】", ByteUtil.toHexString(recv));
        if (recv[0] != 0x04) {
            if (recv[0] == 0x05) {
                if (recv[4] == 0x0B) {
                    canReconnect = false;
                    throw new DrcomException("Invalid Mac Address", DrcomException.CODE.ex_login);
                }
                canReconnect = false;
                throw new DrcomException("Invalid username or password", DrcomException.CODE.ex_login);
            } else {
                throw new DrcomException("Failed to login, unknown error", DrcomException.CODE.ex_login);
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
        Log.d(TAG, "alive: " + "count = " + count + ", keep38count = " + (++keep38Count) + "\n");
//        log.trace("count = {}, keep38count = {}", count, ++keep38Count);
        if (count % 21 == 0) {//第一个 keep38 后有 keep40_extra, 十个 keep38 后 count 就加了21
            needExtra = true;
        }//每10个keep38

        //-------------- keep38 ----------------------------------------------------
        byte[] packet38 = makeKeepPacket38();
        DatagramPacket packet = new DatagramPacket(packet38, packet38.length, serverAddress, Constants.PORT);
        client.send(packet);
//        log.trace("[rand={}|{}]send keep38. 【{}】",
//                ByteUtil.toHexString(packet38[36]), ByteUtil.toHexString(packet38[37]),
//                ByteUtil.toHexString(packet38));
        Log.d(TAG, "alive: " + "[rand=" + ByteUtil.toHexString(packet38[36]) + "|" + ByteUtil.toHexString(packet38[37])
                    + "]send keep38. 【" + ByteUtil.toHexString(packet38) + "】");

        byte[] recv = new byte[128];
        client.receive(new DatagramPacket(recv, recv.length));
//        log.trace("[rand={}|{}]recv Keep38. [{}.{}.{}.{}] 【{}】",
//                ByteUtil.toHexString(recv[6]), ByteUtil.toHexString(recv[7]),
//                ByteUtil.toInt(recv[12]), ByteUtil.toInt(recv[13]), ByteUtil.toInt(recv[14]), ByteUtil.toInt(recv[15]),
//                ByteUtil.toHexString(recv));
        Log.d(TAG, "alive: " + "[rand=" + ByteUtil.toHexString(recv[6]) + "|" + ByteUtil.toHexString(recv[7]) + "]recv keep38. ["
                    + ByteUtil.toInt(recv[12]) + "." + ByteUtil.toInt(recv[13]) + "." + ByteUtil.toInt(recv[14]) + "."
                    + ByteUtil.toInt(recv[15]) + "] 【" + ByteUtil.toHexString(recv) + "】");
        keepAliveVer[0] = recv[28];//收到keepAliveVer//通常不会变
        keepAliveVer[1] = recv[29];

        if (needExtra) {//每十次keep38都要发一个 keep40_extra
//            log.debug("Keep40_extra...");
            Log.d(TAG, "alive: " + "keep40_extra...");
            //--------------keep40_extra--------------------------------------------
            //先发 keep40_extra 包
            byte[] packet40extra = makeKeepPacket40(1, true);
            packet = new DatagramPacket(packet40extra, packet40extra.length, serverAddress, Constants.PORT);
            client.send(packet);
//            log.trace("[seq={}|type={}][rand={}|{}]send Keep40_extra. 【{}】", packet40extra[1], packet40extra[5],
//                    ByteUtil.toHexString(packet40extra[8]), ByteUtil.toHexString(packet40extra[9]),
//                    ByteUtil.toHexString(packet40extra));
            Log.d(TAG, "alive: " + "[seq=" + packet40extra[1] + "|type=" + packet40extra[5] + "][rand=" + ByteUtil.toHexString(packet40extra[8])
                    + "|" + ByteUtil.toHexString(packet40extra[9]) + "]send keep40_extra. 【" + ByteUtil.toHexString(packet40extra) + "】");
            recv = new byte[512];
            client.receive(new DatagramPacket(recv, recv.length));
//            log.trace("[seq={}|type={}][rand={}|{}]recv Keep40_extra. 【{}】", recv[1], recv[5],
//                    ByteUtil.toHexString(recv[8]), ByteUtil.toHexString(recv[9]), ByteUtil.toHexString(recv));
            Log.d(TAG, "alive: " + "[seq=" + recv[1] + "|type=" + recv[5] + "][rand=" + ByteUtil.toHexString(recv[8])
                    + "|" + ByteUtil.toHexString(recv[9]) + "]recv keep40_extra. 【" + ByteUtil.toHexString(recv) + "】");
            //不理会回复
        }

        //--------------keep40_1----------------------------------------------------
        byte[] packet40_1 = makeKeepPacket40(1, false);
        packet = new DatagramPacket(packet40_1, packet40_1.length, serverAddress, Constants.PORT);
        client.send(packet);
//        log.trace("[seq={}|type={}][rand={}|{}]send Keep40_1. 【{}】", packet40_1[1], packet40_1[5],
//                ByteUtil.toHexString(packet40_1[8]), ByteUtil.toHexString(packet40_1[9]),
//                ByteUtil.toHexString(packet40_1));
        Log.d(TAG, "alive: " + "[seq=" + packet40_1[1] + "|type=" + packet40_1[5] + "][rand=" + ByteUtil.toHexString(packet40_1[8])
                + "|" + ByteUtil.toHexString(packet40_1[9]) + "]send keep40_1. 【" + ByteUtil.toHexString(packet40_1) + "】");

        recv = new byte[64];//40
        client.receive(new DatagramPacket(recv, recv.length));
//        log.trace("[seq={}|type={}][rand={}|{}]recv Keep40_1. 【{}】", recv[1], recv[5],
//                ByteUtil.toHexString(recv[8]), ByteUtil.toHexString(recv[9]), ByteUtil.toHexString(recv));
        Log.d(TAG, "alive: " + "[seq=" + recv[1] + "|type=" + recv[5] + "][rand=" + ByteUtil.toHexString(recv[8])
                + "|" + ByteUtil.toHexString(recv[9]) + "]recv keep40_1. 【" + ByteUtil.toHexString(recv) + "】");
        //保存 tail2 , 待会儿构造 packet 要用
        System.arraycopy(recv, 16, tail2, 0, 4);

        //--------------keep40_2----------------------------------------------------
        byte[] packet40_2 = makeKeepPacket40(2, false);
        packet = new DatagramPacket(packet40_2, packet40_2.length, serverAddress, Constants.PORT);
        client.send(packet);
//        log.trace("[seq={}|type={}][rand={}|{}]send Keep40_2. 【{}】", packet40_2[1], packet40_2[5],
//                ByteUtil.toHexString(packet40_2[8]), ByteUtil.toHexString(packet40_2[9]),
//                ByteUtil.toHexString(packet40_2));
        Log.d(TAG, "alive: " + "[seq=" + packet40_2[1] + "|type=" + packet40_2[5] + "][rand=" + ByteUtil.toHexString(packet40_2[8])
                + "|" + ByteUtil.toHexString(packet40_2[9]) + "]send keep40_2. 【" + ByteUtil.toHexString(packet40_2) + "】");

        client.receive(new DatagramPacket(recv, recv.length));
//        log.trace("[seq={}|type={}][rand={}|{}]recv Keep40_2. 【{}】", recv[1], recv[5],
//                ByteUtil.toHexString(recv[8]), ByteUtil.toHexString(recv[9]), ByteUtil.toHexString(recv));
        Log.d(TAG, "alive: " + "[seq=" + recv[1] + "|type=" + recv[5] + "][rand=" + ByteUtil.toHexString(recv[8])
                + "|" + ByteUtil.toHexString(recv[9]) + "]recv keep40_2. 【" + ByteUtil.toHexString(recv) + "】");
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

    public void logoutNow() {
        notifyLogout = true;// 终止后台进程
        this.cancel(true);
        Log.d(TAG, "logoutNow: 收到注销指令");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    challenge(challengeTimes++);
                    logout();
                } catch (Throwable t) {
                    Log.e(TAG, "logoutNow: 注销异常", t);
                } finally {
                    // 重新登录
                    status = STATUS.offline;
                    keepListener.informLogoutSucceed();
                    // keepListener.informLogStatus(STATUS.offline);
                    if (client != null) {
                        client.close();
                        client = null;
                    }
                }
            }
        }).start();
    }

    private boolean logout() throws IOException {
        byte[] buf = makeLogoutPacket();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, Constants.PORT);
        client.send(packet);
//        log.trace("send logout packet.【{}】", ByteUtil.toHexString(buf));
        Log.d(TAG, "logout: " + "send logout packet.【" + ByteUtil.toHexString(buf) + "】");
        byte[] recv = new byte[512];//25
        client.receive(new DatagramPacket(recv, recv.length));
//        log.trace("recv logout packet response.【{}】", ByteUtil.toHexString(recv));
        Log.d(TAG, "logout: " + "recv logout packet response.【" + ByteUtil.toHexString(recv) + "】");
        if (recv[0] == 0x04) {
//            log.debug("注销成功");
            Log.d(TAG, "logout: 注销成功");
        } else {
//            log.debug("注销...失败?");
            Log.d(TAG, "logout: 注销失败");
        }
        this.cancel(true);
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
