package com.youthlin.jlu.drcom.bean;

/**
 * Created by lin on 2017-01-10-010.
 * 机器的IP、HostName、MAC等
 */
public class HostInfo {
    private final byte[] macBytes = new byte[6];
    private String hostname;
    private String macHexDash;
    private String macNoDash;
    private String address4 = "0.0.0.0";//仅用于显示
    private String networkInterfaceDisplayName;

    public HostInfo(String hostname, String macHex, String networkInterfaceDisplayName) {
        this.hostname = hostname;
        this.networkInterfaceDisplayName = networkInterfaceDisplayName;
        checkHexToDashMac(macHex);
    }

    private void checkHexToDashMac(String mac) {
        if (mac.contains("-")) {
            mac = mac.replaceAll("-", "");
        }
        if (mac.length() != 12) {
            throw new RuntimeException("MAC 地址格式错误。应为 xx-xx-xx-xx-xx-xx 或 xxxxxxxxxxxx 格式的 16 进制: " + mac);
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            Long.parseLong(mac, 16);
        } catch (NumberFormatException e) {
            throw new RuntimeException("MAC 地址格式错误。应为 xx-xx-xx-xx-xx-xx 或 xxxxxxxxxxxx 格式的 16 进制: " + mac);
        }
        StringBuilder sb = new StringBuilder(18);
        for (int i = 0; i < 12; i++) {
            sb.append(mac.charAt(i++)).append(mac.charAt(i)).append("-");
        }
        macHexDash = sb.substring(0, 17);
        macNoDash = mac;

        String[] split = macHexDash.split("-");
        for (int i = 0; i < split.length; i++) {
            macBytes[i] = (byte) Integer.parseInt(split[i], 16);
        }
    }

    @Override
    public String toString() {
        return "[" + macHexDash + '/' + address4 + " - "
                + networkInterfaceDisplayName + '/' + hostname + ']';
    }

    public byte[] getMacBytes() {
        return macBytes;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getAddress4() {
        return address4;
    }

    public void setAddress4(String address4) {
        this.address4 = address4;
    }

    public String getMacHexDash() {
        return macHexDash;
    }

    public void setMacHexDash(String macHexDash) {
        checkHexToDashMac(macHexDash);
    }

    public String getMacNoDash() {
        return macNoDash;
    }

    public void setMacNoDash(String macNoDash) {
        checkHexToDashMac(macNoDash);
    }

    public String getNetworkInterfaceDisplayName() {
        return networkInterfaceDisplayName;
    }

    public void setNetworkInterfaceDisplayName(String networkInterfaceDisplayName) {
        this.networkInterfaceDisplayName = networkInterfaceDisplayName;
    }

}
