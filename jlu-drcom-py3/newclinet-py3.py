#!/usr/bin/env python
# coding:   utf-8
# license:  AGPL-V3

import re
import socket
import struct
import time
from   hashlib import md5
import sys
import os
import random
import platform

# CONFIG
server             =  '10.100.61.3'
username           = b'XXXXX'            # 用户名
password           = b'XXXXX'            # 密码
host_ip            =  '100.100.100.100'  # ip地址
mac                = 0x112288776655      # mac地址
host_name          = b'YOURPCNAME'       # 计算机名
host_os            = b'Windows 10'       # 操作系统
CONTROLCHECKSTATUS = b'\x20'
ADAPTERNUM         = b'\x03'
IPDOG              = b'\x01'
PRIMARY_DNS        = '10.10.10.10'
dhcp_server        = '0.0.0.0'
AUTH_VERSION       = b'\x68\x00'
KEEP_ALIVE_VERSION = b'\xdc\x02'

nic_name           = ''  # Indicate your nic, e.g. 'eth0.2'.nic_name
bind_ip            = '0.0.0.0'
# CONFIG_END

keep_alive_times   = 0

class ChallengeException (Exception):
    def __init__(self):
        pass

class LoginException (Exception):
    def __init__(self):
        pass


def bind_nic():
    try:
        import fcntl
        def get_ip_address(ifname):
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            return socket.inet_ntoa(fcntl.ioctl(
                s.fileno(),
                0x8915,  # SIOCGIFADDR
                struct.pack('256s', ifname[:15])
            )[20:24])
        return get_ip_address(nic_name)
    except ImportError as e:
        print('Indicate nic feature need to be run under Unix based system.')
        return '0.0.0.0'
    except IOError as e:
        print(nic_name + 'is unacceptable !')
        return '0.0.0.0'
    finally:
        return '0.0.0.0'


if nic_name != '':
    bind_ip = bind_nic()

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind((bind_ip, 61440))
s.settimeout(3)
SALT = ''
IS_TEST = True
# specified fields based on version
CONF = "/etc/drcom.conf"
UNLIMITED_RETRY = True
EXCEPTION = False
DEBUG = False  # log saves to file
LOG_PATH = '/var/log/drcom_client.log'
if IS_TEST:
    DEBUG = True
    LOG_PATH = 'drcom_client.log'


def log(*args, **kwargs):
    print(*args, **kwargs)
    if DEBUG and platform.uname().system != 'Windows':
        with open(LOG_PATH,'a') as f:
            f.write(s + '\n')


def challenge(svr, ran):
    while True:
        t = struct.pack("<H", int(ran) % (0xFFFF))
        s.sendto(b"\x01\x02" + t + b"\x09" + b"\x00"*15, (svr, 61440))
        try:
            data, address = s.recvfrom(1024)
            log('[challenge] recv', data.hex())
        except:
            log('[challenge] timeout, retrying...')
            continue

        if address == (svr, 61440):
            break
        else:
            log(f"Wrong address: {address}")
            exit()
    log('[DEBUG] challenge:\n' + data.hex())
    if data[0] != 2:
        raise ChallengeException
    log('[challenge] challenge packet sent.')
    return data[4:8]


def md5sum(s):
    m = md5()
    m.update(s)
    return m.digest()


def dump(n):
    s = '%x' % n
    if len(s) & 1:
        s = '0' + s
    return bytes.fromhex(s)


def ror(md5 : bytes, pwd : bytes):
    ret = b''
    for i in range(len(pwd)):
        x = md5[i] ^ pwd[i]
        ret += (((x << 3) & 0xFF) + (x >> 5)).to_bytes(1, 'big')
    return ret


def keep_alive_package_builder(number, random, tail: bytes, type=1, first=False):
    data = b'\x07' + number.to_bytes(1, 'big') + b'\x28\x00\x0b' + type.to_bytes(1, 'big')
    if first:
        data += b'\x0f\x27'
    else:
        data += KEEP_ALIVE_VERSION
    data += b'\x2f\x12' + b'\x00' * 6
    data += tail
    data += b'\x00' * 4
    #data += struct.pack("!H",0xdc02)z
    if type == 3:
        foo = b''.join([int(i).to_bytes(1, 'big') for i in host_ip.split('.')])  # host_ip
        # CRC
        # edited on 2014/5/12, filled zeros to checksum
        # crc = packet_CRC(data+foo)
        crc = b'\x00' * 4
        #data += struct.pack("!I",crc) + foo + b'\x00' * 8
        data += crc + foo + b'\x00' * 8
    else:  # packet type = 1
        data += b'\x00' * 16
    return data

def keep_alive2(*args):
    tail = b''
    packet = b''
    svr = server
    ran = random.randint(0, 0xFFFF)
    ran += random.randint(1, 10)
    # 2014/10/15 add by latyas, maybe svr sends back a file packet
    svr_num = 0
    packet = keep_alive_package_builder(svr_num, dump(ran), b'\x00'*4, 1, True)
    while True:
        log('[keep-alive2] send1', packet.hex())
        s.sendto(packet, (svr, 61440))
        data, address = s.recvfrom(1024)
        log('[keep-alive2] recv1', data.hex())
        if data.startswith(b'\x07\x00\x28\x00') or data.startswith(b'\x07' + svr_num.to_bytes(1, 'big') + b'\x28\x00'):
            break
        elif data[0] == 0x07 and data[2] == 0x10:
            log('[keep-alive2] recv file, resending..')
            svr_num = svr_num + 1
            packet = keep_alive_package_builder(
                svr_num, dump(ran), b'\x00'*4, 1, False)
        else:
            log('[keep-alive2] recv1/unexpected', data.hex())
    
    #log('[keep-alive2] recv1',data.hex())

    ran += random.randint(1, 10)
    packet = keep_alive_package_builder(svr_num, dump(ran), b'\x00' * 4, 1, False)
    log('[keep-alive2] send2', packet.hex())
    s.sendto(packet, (svr, 61440))
    while True:
        data, address = s.recvfrom(1024)
        if data[0] == 7:
            svr_num = svr_num + 1
            break
        else:
            log('[keep-alive2] recv2/unexpected', data.hex())
    
    log('[keep-alive2] recv2', data.hex())
    tail = data[16:20]

    ran += random.randint(1, 10)
    packet = keep_alive_package_builder(svr_num, dump(ran), tail, 3, False)
    log('[keep-alive2] send3', packet.hex())
    s.sendto(packet, (svr, 61440))
    while True:
        data, address = s.recvfrom(1024)
        if data[0] == 7:
            svr_num = svr_num + 1
            break
        else:
            log('[keep-alive2] recv3/unexpected', data.hex())
    
    log('[keep-alive2] recv3', data.hex())
    tail = data[16:20]
    log("[keep-alive2] keep-alive2 loop was in daemon.")

    i = svr_num
    while True:
        try:
            ran += random.randint(1, 10)
            packet = keep_alive_package_builder(i, dump(ran), tail, 1, False)
            #log('DEBUG: keep_alive2,packet 4\n',packet.hex())
            log('[keep_alive2] send', str(i), packet.hex())
            s.sendto(packet, (svr, 61440))
            data, address = s.recvfrom(1024)
            log('[keep_alive2] recv', data.hex())
            tail = data[16:20]
            #log('DEBUG: keep_alive2,packet 4 return\n',data.hex())

            ran += random.randint(1, 10)
            packet = keep_alive_package_builder(i+1, dump(ran), tail, 3, False)
            #log('DEBUG: keep_alive2,packet 5\n',packet.hex())
            s.sendto(packet, (svr, 61440))
            log('[keep_alive2] send', str(i+1), packet.hex())
            data, address = s.recvfrom(1024)
            log('[keep_alive2] recv', data.hex())
            tail = data[16:20]
            #log('DEBUG: keep_alive2,packet 5 return\n',data.hex())
            i = (i+2) % 0xFF
            time.sleep(20)
            keep_alive1(*args)
        except:
            continue
    


def checksum(s):
    ret = 1234
    for i in re.findall(b'....', s):
        ret ^= int(i[::-1].hex(), 16)
    ret = (1968 * ret) & 0xffffffff
    return struct.pack('<I', ret)


def mkpkt(salt, usr, pwd, mac):
    data = b'\x03\x01\x00'+ (len(usr)+20).to_bytes(1, 'big')
    data += md5sum(b'\x03\x01'+salt+pwd)
    data += usr.ljust(36, b'\x00')
    data += CONTROLCHECKSTATUS
    data += ADAPTERNUM
    data += dump(int(data[4:10].hex(), 16) ^
                 mac).rjust(6, b'\x00')  # mac xor md51
    data += md5sum(b"\x01" + pwd + salt + b'\x00'*4)  # md52
    data += b'\x01'  # number of ip
    data += b''.join([int(x).to_bytes(1,'big') for x in host_ip.split('.')])
    data += b'\x00'*4  # your ipaddress 2
    data += b'\x00'*4  # your ipaddress 3
    data += b'\x00'*4  # your ipaddress 4
    data += md5sum(data + b'\x14\x00\x07\x0b')[:8]  # md53
    data += IPDOG
    data += b'\x00'*4  # delimeter
    data += host_name.ljust(32, b'\x00')
    data += b''.join([ int(i).to_bytes(1, 'big') for i in PRIMARY_DNS.split('.')])  # primary dns
    data += b''.join([ int(i).to_bytes(1, 'big') for i in dhcp_server.split('.')])  # DHCP dns
    data += b'\x00\x00\x00\x00'  # secondary dns:0.0.0.0
    data += b'\x00' * 8  # delimeter
    data += b'\x94\x00\x00\x00'  # unknow
    data += b'\x06\x00\x00\x00'  # os major
    data += b'\x02\x00\x00\x00'  # os minor
    data += b'\xf0\x23\x00\x00'  # OS build
    data += b'\x02\x00\x00\x00'  # os unknown
    data += b'\x44\x72\x43\x4f\x4d\x00\xcf\x07\x68'
    data += b'\x00' * 55  # unknown string
    data += b'\x33\x64\x63\x37\x39\x66\x35\x32\x31\x32\x65\x38\x31\x37\x30\x61\x63\x66\x61\x39\x65\x63\x39\x35\x66\x31\x64\x37\x34\x39\x31\x36\x35\x34\x32\x62\x65\x37\x62\x31'
    data += b'\x00' * 24
    data += AUTH_VERSION
    data += b'\x00' + len(pwd).to_bytes(1, 'big')
    data += ror(md5sum(b'\x03\x01'+salt+pwd), pwd)
    data += b'\x02\x0c'
    data += checksum(data+b'\x01\x26\x07\x11\x00\x00'+dump(mac))
    data += b'\x00\x00'  # delimeter
    data += dump(mac)
    if (len(pwd) / 4) != 4:
        data += b'\x00' * (len(pwd) // 4)  # strange。。。
    data += b'\x60\xa2'  # unknown, filled numbers randomly =w=
    data += b'\x00' * 28
    log('[mkpkt]', data.hex())
    return data


def login(usr, pwd, svr):
    global SALT
    i = 0
    while True:
        salt = challenge(svr, time.time()+random.randint(0xF, 0xFF))
        SALT = salt
        log('[salt] ', SALT)
        packet = mkpkt(salt, usr, pwd, mac)  #生成数据包
        log('[login] send', packet.hex())
        s.sendto(packet, (svr, 61440))
        data, address = s.recvfrom(1024)
        log('[login] recv', data.hex())
        log('[login] packet sent.')
        if address == (svr, 61440):
            if data[0] == 4:
                log('[login] loged in')
                break
            else:
                log(f'[login] login failed. data[0] = {data[0]} type={type(data[0])}')
                exit(2)
        else:
            if i >= 5 and UNLIMITED_RETRY == False:
                log('[login] exception occured.')
                sys.exit(1)
            else:
                exit(2)

    log('[login] login sent')
    # 0.8 changed:
    return data[23:39]
    # return data[-22:-6]

def keep_alive1(salt, tail, pwd, svr):
    foo = struct.pack('!H', int(time.time()) % 0xFFFF)
    data = b'\xff' + md5sum(b'\x03\x01'+salt+pwd) + b'\x00\x00\x00'
    data += tail
    data += foo + b'\x00\x00\x00\x00'
    log('[keep_alive1] send', data.hex())

    s.sendto(data, (svr, 61440))
    while True:
        data, address = s.recvfrom(1024)
        if data[0] == 7:
            break
        else:
            log('[keep-alive1]recv/not expected', data.hex())
    log('[keep-alive1] recv', data.hex())


def empty_socket_buffer():
    # empty buffer for some fucking schools
    log('starting to empty socket buffer')
    try:
        while True:
            data, address = s.recvfrom(1024)
            log('recived sth unexpected', data.hex())
            if s == '':
                break
    except socket.timeout as timeout_err:
        # get exception means it has done.
        log(f'exception in empty_socket_buffer {timeout_err}')
    log('emptyed')


def daemon():
    if(platform.uname().system != 'Windows'):
        with open('/var/run/jludrcom.pid', 'w') as f:
            f.write(str(os.getpid()))


def main():
    if not IS_TEST:
        daemon()
        execfile(CONF, globals())
    log("auth svr:", server, "\nusername:", username , 
        "\npassword:", password, "\nmac:", str(hex(mac)))
    log(bind_ip)
    # 流程 login -> keep alive
    while True:
        try:
            package_tail = login(username, password, server)
        except LoginException:
            log("登录失败!")
            break
        log('package_tail', package_tail.hex())

        # keep_alive1 is fucking bullshit!
        # ↑↑↑ 附议 ↑↑↑
        empty_socket_buffer()
        keep_alive1(SALT, package_tail, password, server)
        keep_alive2(SALT, package_tail, password, server)


if __name__ == "__main__":
    main()
