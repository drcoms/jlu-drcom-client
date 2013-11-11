# -*- coding: utf-8 -*-
"""
@author: latyas, idea314
"""
import socket, struct, time
from hashlib import md5
import sys
import urllib2
import re,random
import os

class ChallengeException (Exception):
  def __init__(self):
    pass

class LoginException (Exception):
  def __init__(self):
    pass
   
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("0.0.0.0", 61440))

s.settimeout(3)
SALT = ''
UNLIMITED_RETRY = True
EXCEPTION = False
DEBUG = False
server = "10.100.61.3" # "auth.jlu.edu.cn"
username = "YOURUSERNAME"
password = "YOURPASSWORD"
host_name = "LIYUANYUAN"
host_os = "8089D"
mac = 0xffffffffffff

def challenge(svr,ran):
    while True:
      t = struct.pack("<H", int(ran)%(0xFFFF))
      s.sendto("\x01\x02"+t+"\x09"+"\x00"*15, (svr, 61440))
      try:
        data, address = s.recvfrom(1024)
      except:
        if DEBUG:
          print '[challenge] timeout, retrying...'
        continue
        
      if address == (svr, 61440):
        break;
      else:
        continue
    if data[0] != '\x02':
      raise ChallengeException
    print '[challenge] challenge packet sent.'
    return data[4:8]

def md5sum(s):
    m = md5()
    m.update(s)
    return m.digest()

def dump(n):
    s = '%x' % n
    if len(s) & 1:
        s = '0' + s
    return s.decode('hex')

def ror(md5, pwd):
    ret = ''
    for i in range(len(pwd)):
        x = ord(md5[i]) ^ ord(pwd[i])
        ret += chr(((x<<3)&0xFF) + (x>>5))
    return ret

def keep_alive_package_builder(number,random,tail,type=1,first=False):
    data = '\x07'+ chr(number) + '\x28\x00\x0b' + chr(type)
    if first :
      data += '\x0f\x27'
    else:
      data += '\xdc\02'
    data += random + '\x00' * 6
    data += tail
    data += '\x00' * 4
    if type == 3:
      foo = '\x31\x8c\x21\x3e' #CONSTANT
      #CRC
      crc = packet_CRC(data+foo)
      data += struct.pack("!I",crc) + foo + '\x00' * 8
    else: #packet type = 1
      data += '\x00' * 16
    return data

def packet_CRC(s):
    ret = 0
    for i in re.findall('..', s):
        ret ^= struct.unpack('>h', i)[0]
        ret &= 0xFFFF
    ret = ret * 0x2c7
    return ret

def keep_alive2():
    tail = ''
    packet = ''
    svr = server
    ran = random.randint(0,0xFFFF)
    ran += random.randint(1,10)   
    packet = keep_alive_package_builder(0,dump(ran),'\x00'*4,1,True)
    s.sendto(packet, (svr, 61440))
    data, address = s.recvfrom(1024)
    
    ran += random.randint(1,10)   
    packet = keep_alive_package_builder(1,dump(ran),'\x00'*4,1,False)
    s.sendto(packet, (svr, 61440))
    data, address = s.recvfrom(1024)
    tail = data[16:20]
    

    ran += random.randint(1,10)   
    packet = keep_alive_package_builder(2,dump(ran),tail,3,False)
    s.sendto(packet, (svr, 61440))
    data, address = s.recvfrom(1024)
    tail = data[16:20]
    print "[keep-alive] keep-alive was in daemon."
    
    i = 1
    while True:
      try:
        time.sleep(5)
        ran += random.randint(1,10)   
        packet = keep_alive_package_builder(2,dump(ran),tail,1,False)
        s.sendto(packet, (svr, 61440))
        data, address = s.recvfrom(1024)
        tail = data[16:20]
        ran += random.randint(1,10)   
        packet = keep_alive_package_builder(2,dump(ran),tail,3,False)
        s.sendto(packet, (svr, 61440))
        data, address = s.recvfrom(1024)
        tail = data[16:20]
        i = i+1
        
        check_online = urllib2.urlopen('http://10.100.61.3')
        foo = check_online.read()
        if 'login.jlu.edu.cn' in foo:
          print '[keep_alive2] offline.relogin...'
          break;
      except:
        pass
def checksum(s):
    ret = 1234
    for i in re.findall('....', s):
        ret ^= int(i[::-1].encode('hex'), 16)
    ret = (1968 * ret) & 0xffffffff
    return struct.pack('<I', ret)

# cracked by latyas
def mkpkt(salt, usr, pwd, mac):
    data = '\x03\01\x00'+chr(len(usr)+20)
    data += md5sum('\x03\x01'+salt+pwd)
    data += usr.ljust(36, '\x00')
    data += '\x00\x00'
    data += dump(int(data[4:10].encode('hex'),16)^mac).rjust(6,'\x00')
    data += md5sum("\x01" + pwd + salt + '\x00'*4)
    data += '\x01\x31\x8c\x31\x4e' + '\00'*12
    data += md5sum(data + '\x14\x00\x07\x0b')[:8] + '\x01'+'\x00'*4
    data += host_name.ljust(71, '\x00')
    data += '\x01' + host_os.ljust(128, '\x00')
    data += '\x6d\x00\x00'+chr(len(pwd))
    data += ror(md5sum('\x03\x01'+salt+pwd), pwd)
    data += '\x02\x0c'
    data += checksum(data+'\x01\x26\x07\x11\x00\x00'+dump(mac))
    data += "\x00\x00" + dump(mac)
    return data

def login(usr, pwd, svr):
    global SALT
    while True:
        try:
            try:
              salt = challenge(svr,time.time()+random.randint(0xF,0xFF))
            except ChallengeException:
              if DEBUG:
                print 'challenge packet exception'
              continue
            SALT = salt
            packet = mkpkt(salt, usr, pwd, mac)
            s.sendto(packet, (svr, 61440))
            data, address = s.recvfrom(1024)
        except:
            print "[login] recvfrom timeout,retrying..."
            continue
        print '[login] packet sent.'
        if address == (svr, 61440):
            if data[0] == '\x05':
              print "[login] wrong password or something else.retry."
              continue
            elif data[0] != '\x02':
              print "[login] server return exception.retry"
              raise LoginException
              continue
            break;
        else:
              print '[login] packet error, retrying...'
              
    print '[login] login done.'
    return data[-22:-6]

def main():
    print "DrCOM Client FOR JLU"
    while True:
      try:
        package_tail = login(username, password, server)
      except LoginException:
        continue
      keep_alive2()
if __name__ == "__main__":
    main()


