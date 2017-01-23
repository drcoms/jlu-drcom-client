package com.youthlin.jlu.drcom.util;

import com.youthlin.jlu.drcom.bean.HostInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by lin on 2017-01-09-009.
 * 工具类
 */
@SuppressWarnings("WeakerAccess")
public class IPUtil {
    private static final Logger log = LoggerFactory.getLogger(IPUtil.class);

    public static <T> List<T> asList(Enumeration<T> enumeration) {
        List<T> ret = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            ret.add(enumeration.nextElement());
        }
        return ret;
    }

    public interface OnGetHostInfoCallback {
        void update(int current, int total);

        void done(List<HostInfo> hostInfoList);
    }

    public static List<HostInfo> getHostInfo(OnGetHostInfoCallback callback) {
        List<HostInfo> hostInfoList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            if (networkInterfaces == null) {
                return hostInfoList;//空结果 -> 补救:使用配置文件手动指定
            }
            List<NetworkInterface> networkInterfacesList = asList(networkInterfaces);
            int size = networkInterfacesList.size();
            int index = 0;
            for (NetworkInterface networkInterface : networkInterfacesList) {
                index++;
                callback.update(index, size);
                if (!networkInterface.isUp()) {
                    continue;
                }
                String addr = null;
                String hostname = null;
                List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                    InetAddress address = interfaceAddress.getAddress();
                    //log.trace("{}/{}. address = {}", index, size, address);
                    String hostAddress = address.getHostAddress();//to耗时do
                    if (hostAddress.contains(".")) {// not ':' -> IPv6
                        addr = hostAddress;
                        hostname = address.getHostName();
                        break;
                    }
                }
                byte[] hardwareAddress = networkInterface.getHardwareAddress();//to耗时do
                String dashMAC = getDashMAC(hardwareAddress);
                //log.trace("Dash Mac = {}", dashMAC);
                if (dashMAC != null && dashMAC.length() == 17 && addr != null && hostname != null) { // 00-00-00-00-00-00
                    HostInfo hostInfo = new HostInfo(hostname, dashMAC, networkInterface.getDisplayName());
                    hostInfo.setAddress4(addr);
                    hostInfoList.add(hostInfo);
                }
            }
        } catch (SocketException e) {
            log.debug("Socket Exception: {}", e.getMessage(), e);
        }
        callback.done(hostInfoList);
        return hostInfoList;
    }

    public static String getDashMAC(byte[] hardwareAddress) {
        if (hardwareAddress == null) {
            //throw new NullPointerException("Hardware address should not be null.");
            return null;
        }
        return ByteUtil.toHexString(hardwareAddress, '-');
    }

    public static boolean isPublicIP(String dotIP) {
        try {
            String[] split = dotIP.trim().split("\\.");
            int a = Integer.parseInt(split[0]);
            int b = Integer.parseInt(split[1]);
            //int c = Integer.parseInt(split[2]);
            //int d = Integer.parseInt(split[3]);
            //A 类：1.0.0.0 到 127.255.255.255  //A 类：10.0.0.0 到 10.255.255.255
            //                              127.0.0.0 到 127.255.255.255 为系统回环地址
            if (a > 0 && a < 128) {
                if (!(a == 10 || a == 127)) {
                    return true;
                }
            }
            //B 类：128.0.0.0 到 191.255.255.255  //B 类：172.16.0.0 到 172.31.255.255
            else if (a >= 128 && a < 192) {
                // 169.254.X.X 是保留地址。
                // 如果你的 IP 地址是自动获取 IP 地址，
                // 而你在网络上又没有找到可用的 DHCP 服务器。就会得到其中一个 IP。
                // UPDATE at 2017-01-23: http://baike.baidu.com/subview/8370/15816170.htm#5
                if (!(a == 172 && (b >= 16 && b < 31)) && !(a == 169 && b == 254)) {
                    return true;
                }
            }
            //C 类：192.0.0.0 到 223.255.255.255  //C 类：192.168.0.0 到 192.168.255.255
            else if (a >= 192 && a < 224) {
                if (!(a == 192 && b == 168)) {
                    return true;
                }
            }
            //D 类：224.0.0.0 到 239.255.255.255
            //E 类：240.0.0.0 到 255.255.255.255

        } catch (Exception e) {
            log.debug("判断是否为公网 IP 时发生异常: " + dotIP);
            return false;
        }
        return false;
    }
}
