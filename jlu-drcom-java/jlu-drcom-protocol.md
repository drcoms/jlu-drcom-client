# JLU Drcom 协议

## 名词约定
- challenge  
  与服务器通信的第一次握手，表示 [请求通信] 。  
  发送 login 包和 logout 包之前的步骤。
- login  
  登录发送的包。
- keep alive  
  登录成功后保持在线需要持续地每隔特定时间与服务器通信。  
  包括：
  * keep38  
    keep alive 的第一个包(长度为 38 字节)
  * keep40_extra  
    第一个 keep38 包发送后紧接着需要发送的额外一个报文。  
    每 10 个 keep38 包发送后再次发送该额外的报文。(长度为 40)
  * keep40_1  
    第一个 40 字节的 keep 包
  * keep40_2  
    第二个 40 长度的报文

- logout  
  注销发送的报文。
- salt  
  盐。进行 MD5 加密需要使用的额外信息。
  在 challenge 响应报文中服务器给定。

## 大致流程
- 登录  
challenge -> login
- 保持在线  
(第一轮)  
keep38 -> keep40_extra -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
keep38 -> keep40_1 -> keep40_2  
(第二轮 每十个 keep38 需要再次发送 keep40_extra)  
keep38 -> keep40_extra -> keep40_1 -> keep40_2  
...

- 注销  
challenge -> logout

## 协议细节
### challenge
客户端启动后维护一个全局的 challenge_times  
表示已经发送过几个 challenge 报文。  
- 发送  
  challenge 发送报文长度为 20.  具体构成为：  
  ```
  0x01, times, rand1, rand2, 0x6a,
  0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x00
  ```  
  其中，times 从 0x02 开始计数，每次发一个 challenge 报文就加一。  
rand1, rand2 是随机数，客户端随机生成，在收到的响应报文会回复这两位数字。
- 接收  
  正常情况下，响应报文长度为 76. 第一位为 0x02 表示正常。  
  典型的响应报文可能长这样：  
  ```
  0x02 0x02 rand1 rand2 s1 s2 s3 s4 
  .... 
  ip1 ip2 ip3 ip4
  ```  
  其中 rand1 rand2 就是你发送时客户端生成的随机数，可能是用于客户端校验的吧。  
  recv[4:8] 是 salt. (方括号表示的下标从 0 开始, 下同)  
  recv[20:24] 是客户端 IP 地址。
  
### login
 - 发送  
   长度与密码长度 passLen 有关。 `if(passLen > 16) passLen=16`  
   报文的长度为 `334 + (passLen - 1) / 4 * 4` (passLen 最大取 16)  


   |  | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |  
   |:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|  
   | 0 | code=03 | type=01 | EOF=00 | unameLen+20 | md5a[0] |  |  |  |  |  |  
   | 1 |  |  |  |  |  |  |  |  |  |   md5a[15] |  
   | 2 | uname[0] | | | | | | | | | |  
   | 5 | | | | | | uname[35] | controlCheck=20 | adapterNum=05 | xor[0] | |  
   | 6 | | | | xor[5] | md5b[0] | | | | | |  
   | 7 | | | | | | | | | | md5b[15] |  
   | 8 | numOfIP | IP1[0] | IP1[1] | IP1[2] | IP1[3] | IP2[0] | IP2[1] | IP3[2] | IP3[3] | IP3[0] |  
   | 9 | | | | | | | IP4[3] | md5c[0] | | |  
   |10| | | | | md5c[7] | ipDog=01 | 0 | 0 | 0 | 0 |  
   |11| hostname[0] | | | | | | | | | |  
   |14| | hostname[31] | primaryDNS[0] | | | primaryDNS[3] | dhcp[0] | | | dhcp[3] |
   |15| 2ndDNS[0] | | | 2ndDNS[3] | 0 | 0 | 0 | 0 | 0 | 0 |  
   |16| 0 | 0 | 94 | 0 | 0 | 0 | 06 | 0 | 0 | 0 |  
   |17| 02 | 0 | 0 | 0 | f0 | 23 | 0 | 0 | 02 | 0 |
   |18| 0 | 0 | 44 | 72 | 43 | 4f | 4d |  00 | cf | 07 |  
   |19| 6a | zeros[0] | | | | | | | | |  
   |24| | | | | | zeros[54] | str[0] | | | |  
   |28| | | | | | str[39] | zeros[0] | | | |  
   |29| | | | | | | | | | zeros[23] |  
   |31| 6a | 0 | 0 | passLen | | | | | | |  


   从下标 314 开始： passLen 长度的 ror ( [314 : 314+passLen] )  
   ```
   [ passLen +314 ] = 02;
   [ passLen +315 ] = 0c;
   [ passLen +316 ] = checksum[0];
   [ passLen +319 ] = checksum[3];
   [ passLen +320 ] = 0;
   [ passLen +321 ] = 0;
   [ passLen +322 ] = mac[0];
   [ passLen +327 ] = mac[7];
   ```
   接下来从下标 passLen+328 是 zeroCount 个 0,  跟着 rand1, rand2.   
   `zeroCount = (4 - passLen % 4) % 4`   
   (密码长度除以 4 的余数 = mod,  
  mod==0 不补 0, mod==1 补 3 个 0, mod==2 补 2 个 0, mod==3 补 1 个 0. )  
  
-----------------------------------------------------------

   上表中， `md5a = md5( code, type, salt, password )`  
   `uname` 是用户名，左对齐末尾补 0 凑 36 长度  
   `xor = md5[0:6] ^ mac`  
   `md5b = md5( 0x01, password, salt, 0, 0, 0, 0 )`
   `numOfIP` 是客户端 IP 地址数量，官方版客户端会把局域网 IP 地址
   也发送(如装了虚拟机，通常会有 NAT 虚拟网卡的 10.x.x.x 的地址)，
   我们只发一个 IP 地址即可（即 challenge 返回给我们的那个）
   因此这里取 1.   
   `IP1`就是我们的 IP 地址（challenge 取得）, IP2/3/4 可以是全零  
   `md5c = md5( send[0:98], 14, 0, 7, 0b )` 即 前 97 位加上 0x14_00_07_0b 进行 md5 加密  
   `hostname` 即计算机 机器名，左对齐 32 长度  
   `primaryDNS` 主DNS, 取 10.10.10.10 则 四位都是 0x0a.   
   `dhcp`, `2ndDNS`, `zeros` 全零  
   ```
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
   ```
   `str` 貌似有多个版本，我抓包抓到的是 `"1c210c99585fd22ad03d35c956911aeec1eb449b"`  
   `ror = ror( md5a, password )`  长度是 passLen  
   ```
    public static byte[] ror(byte[] md5a, byte[] password) {
        int len = password.length;
        byte[] ret = new byte[len];
        int x, y;
        for (int i = 0; i < len; i++) {
            x = md5a[i];
            y = password[i];
            while (x < 0) {
                x += 256;
            }
            while (y < 0) {
                y += 256;
            }
            x = x ^ y;
            //e.g. x =   0xff      1111_1111
            // x<<3  = 0x07f8 0111_1111_1000
            // x>>>5 = 0x0007           0111
            // +       0x07ff 0111_1111_1111
            //(byte)截断=0xff
            ret[i] = (byte) ((x << 3) + (x >>> 5));
        }
        return ret;
    }
   ```
   `checksum = checksum( data[0:315 + passLen], 0x01_26_07_11_00_00, mac )`
   即 data 的数据第零位开始到 0x02_0c 再加上 0x01_26_07_11_00_00, mac 进行计算校验和  
   ```
    public static byte[] checksum(byte[] data) {
        // 1234 = 0x_00_00_04_d2
        byte[] sum = new byte[]{0x00, 0x00, 0x04, (byte) 0xd2};
        int len = data.length;
        int i = 0;
        //0123_4567_8901_23
        for (; i + 3 < len; i = i + 4) {
            //abcd ^ 3210
            //abcd ^ 7654
            //abcd ^ 1098
            sum[0] ^= data[i + 3];
            sum[1] ^= data[i + 2];
            sum[2] ^= data[i + 1];
            sum[3] ^= data[i];
        }
        if (i < len) {
            //剩下_23
            //i=12,len=14
            byte[] tmp = new byte[4];
            for (int j = 3; j >= 0 && i < len; j--) {
                //j=3 tmp = 0 0 0 2  i=12  13
                //j=2 tmp = 0 0 3 2  i=13  14
                tmp[j] = data[i++];
            }
            for (int j = 0; j < 4; j++) {
                sum[j] ^= tmp[j];
            }
        }
        BigInteger bigInteger = new BigInteger(1, sum);//无符号数即正数
        bigInteger = bigInteger.multiply(BigInteger.valueOf(1968));
        bigInteger = bigInteger.and(BigInteger.valueOf(0xff_ff_ff_ffL));
        byte[] bytes = bigInteger.toByteArray();
        //System.out.println(ByteUtil.toHexString(bytes));
        len = bytes.length;
        i = 0;
        byte[] ret = new byte[4];
        for (int j = len - 1; j >= 0 && i < 4; j--) {
            ret[i++] = bytes[j];
        }
        return ret;
    }
   ```
   注意，Python 版代码中的协议与本项目使用的略微不一致，
   Python 版本计算的校验和与官方版对不上  


- 接收  
  recv[0] == 04 表示登录成功  
  recv[0] == 05 表示登录失败：  
  `05 00 00 05 03` 用户名或密码错误   
  `05 00 00 05 0B 53` MAC 地址 错误  
  recv[23:39] 这 16 位 我们记为 `tail1`, 在 keep alive 和 logout 中需要使用
   
### keep alive

#### keep38
- 发送  
  `0xff md5a:16位 0x00 0x00 0x00 tail1:16位 rand1 rand2` (38长度)
- 接收  
  recv[6], recv[7] 是rand1, rand2.  
  recv[12,13,14,15] 是 IP 地址.  
  recv[28,29] 是 `keepAliveVer` 在 keep40 中用到(这个很久都不会变，但 Python 版写死的和我抓包抓到不一样)
  
#### keep40_extra
```
count:表示发送的第几个 keep40 包, 0x00 开始，到了 0xff 再回到 0x00
            ^     keep40_extra type=01, 收到的 type=06
         0  1     2  3  4   5   6  7  8     9
40： 发  07 count 28 00 0B type 0f 27 rand1 rand2  
272：收  07 count 10 01 0B type dc 02 rand1 rand2
0f 27 固定， 收到的dc 02 即为 keepAliveVer
```

#### keep40_1
```
                       type
       0   1   2  3  4  5   6  7   8    9          16,17,18,19
40:发 07 count 28 00 0B 01 dc 02 rand1 rand2 0...
40:收 07 count 28 00 0B 02 dc 02 rand1 rand2 0... tail2[0,1,2,3]
keep40_1 发送的 type = 01 接收的 type = 02， dc 02 为 keepAliveVer
接收的 16-19 四位是 tail2 在发送 keep40_2 时用到
```

#### keep40_2
```
                       type
      0   1    2  3  4  5   6  7    8     9        16-19      20-23  24-27  28-31   32-39
40:发 07 count 28 00 0B 03 dc 02 rand1 rand2 0... tail2[0-3] 0....  crc4位  IP4位   8个0
40:收 07 count 28 00 0B 04 dc 02 rand1 rand2
tail2 在 keep40_1 中得到 
```
crc 实现：
```
public static byte[] crc(byte[] data) {
    byte[] sum = new byte[2];
    int len = data.length;
    for (int i = 0; i + 1 < len; i = i + 2) {
        sum[0] ^= data[i + 1];
        sum[1] ^= data[i];
    }//现在数据都是偶数位, 不需考虑是否有多余的位
    BigInteger b = new BigInteger(1, sum);
    b = b.multiply(BigInteger.valueOf(711));
    byte[] bytes = b.toByteArray();
    len = bytes.length;
    //System.out.println(toHexString(bytes));
    byte[] ret = new byte[4];
    for (int i = 0; i < 4 && len > 0; i++) {
        ret[i] = bytes[--len];
    }
    return ret;
}
```

### logout
首先 challenge.  
20发: 01 times rand1 rand2 6a  
76收: 02 ...  

然后真正 logout：  
80 发送： 
```
code=06, type=01, EOF=00, unameLen+20, 
md5=md5(code type salt pass), uname36左对齐, 0x20, 0x05,
mac[0-5]^md5[0-5], tail1[0-15]

```
25 接收:  
```
04 00 00 05 00 ...  
^  
04表示注销成功
```

如还有疑问，请比对 [代码](/src/main/java/com/youthlin/jlu/drcom/task/DrcomTask.java) 阅读。
