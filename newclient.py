#! /usr/bin/env python
# -*- coding: utf-8 -*-

import socket, struct, random, time, re, os, datetime
from hashlib import md5

########## Configuration ###############

### Required ###
username='USERNAME'  #用户名
password='PASSWORD'  #密码
host_ip = 'IP_ADDR'  #ip地址
mac = 0x010203040506 #mac地址 echo 0x`ifconfig eth | egrep -io "([0-9a-f]{2}:){5}[0-9a-f]{2}" | tr -d ":"`

### Optional ###
host_name = '++++++++' #计算机名
host_os = 'Windows XP' #操作系统
bind_ip = '0.0.0.0'    #must be listed in your `ip a` results
LOG_PATH = '/var/log/drcom.log'
RETRY = False

### for Development ###
IS_DEBUG=False

server = '10.100.61.3'

#used in mkpkt()
CONTROLCHECKSTATUS = '\x20'
ADAPTERNUM = '\x03'
IPDOG = '\x01'
PRIMARY_DNS = '10.10.10.10'
dhcp_server = '0.0.0.0'
AUTH_VERSION = '\x68\x00'

# used in keep_alive
KEEP_ALIVE_VERSION = '\xdc\x02'
############## END #####################

class LoginException (Exception):
  def __init__(self):
    pass

def setup_socket(bind_ip):
  log('DEBUG', 'setup_socket()')
  s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  # s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
  s.bind((bind_ip, 61440))
  s.settimeout(3)
  return s


def do_login(user, pwd, salt, mac, svr, socket):
  log('DEBUG', 'do_login()')
  packet = mkpkt(salt, user, pwd, mac)
  log('DEBUG', '[login] send:' + packet.encode('hex'))
  socket.sendto(packet, (svr, 61440))
  data, address = socket.recvfrom(1024)
  log('DEBUG', '[login] recv:' + data.encode('hex'))
  if address == (svr, 61440):
    if data[0] == '\x04':
      log('INFO', 'auth success.')
    else:
      log('WARN', 'auth failed.')
      raise LoginException
  else:
    log('WARN', 'exception occured. address not match')
    raise Exception
            
  #0.8 changed:
  return data[23:39]
  #return data[-22:-6]


def get_salt(socket, svr, rand):
  log('DEBUG', 'get_salt()')
  t = struct.pack("<H", int(rand)%(0xFFFF))
  socket.sendto("\x01\x02"+t+"\x09"+"\x00"*15, (svr, 61440))
  log('DEBUG', 'send: blabla ')
  data, address = socket.recvfrom(1024)
  log('DEBUG', 'sending:' + data.encode('hex'))
  if data[0] != '\x02':
    raise Exception
  return data[4:8]


def mkpkt(salt, usr, pwd, mac):
  log('DEBUG', 'mkpkt()')
  data = '\x03\x01\x00'+chr(len(usr)+20)
  data += md5sum('\x03\x01'+salt+pwd)
  data += usr.ljust(36, '\x00')
  data += CONTROLCHECKSTATUS
  data += ADAPTERNUM
  data += dump(int(data[4:10].encode('hex'),16)^mac).rjust(6,'\x00') #mac xor md51
  data += md5sum("\x01" + pwd + salt + '\x00'*4) #md52
  data += '\x01' # number of ip
  #data += '\x0a\x1e\x16\x11' #your ip address1, 10.30.22.17
  data += ''.join([chr(int(i)) for i in host_ip.split('.')]) #x.x.x.x -> 
  data += '\00'*4 #your ipaddress 2
  data += '\00'*4 #your ipaddress 3
  data += '\00'*4 #your ipaddress 4
  data += md5sum(data + '\x14\x00\x07\x0b')[:8] #md53
  data += IPDOG
  data += '\x00'*4 #delimeter
  data += host_name.ljust(32, '\x00')
  data += ''.join([chr(int(i)) for i in PRIMARY_DNS.split('.')]) #primary dns
  data += ''.join([chr(int(i)) for i in dhcp_server.split('.')]) #DHCP server
  data += '\x00\x00\x00\x00' #secondary dns:0.0.0.0
  data += '\x00' * 8 #delimeter
  data += '\x94\x00\x00\x00' # unknow
  data += '\x06\x00\x00\x00' # os major
  data += '\x02\x00\x00\x00' # os minor
  data += '\xf0\x23\x00\x00' # OS build
  data += '\x02\x00\x00\x00' #os unknown
  data += '\x44\x72\x43\x4f\x4d\x00\xcf\x07\x68'
  data += '\x00' * 55#unknown string
  data += '\x33\x64\x63\x37\x39\x66\x35\x32\x31\x32\x65\x38\x31\x37\x30\x61\x63\x66\x61\x39\x65\x63\x39\x35\x66\x31\x64\x37\x34\x39\x31\x36\x35\x34\x32\x62\x65\x37\x62\x31'
  data += '\x00' * 24
  data += AUTH_VERSION
  data += '\x00' + chr(len(pwd))
  data += ror(md5sum('\x03\x01'+salt+pwd), pwd)
  data += '\x02\x0c'
  data += checksum(data+'\x01\x26\x07\x11\x00\x00'+dump(mac))
  data += '\x00\x00' #delimeter
  data += dump(mac)
  if (len(pwd) / 4) != 4:
    data += '\x00' * (len(pwd) / 4)#strange。。。
  data += '\x60\xa2' #unknown, filled numbers randomly =w=
  data += '\x00' * 28
  return data


def md5sum(str):
  log('DEBUG', 'md5sum()')
  m = md5()
  m.update(str)
  return m.digest()


def dump(n):
  log('DEBUG', 'dump()')
  s = '%x' % n
  if len(s) & 1:
    s = '0' + s
  return s.decode('hex')


def ror(md5, pwd):
  log('DEBUG', 'ror()')
  ret = ''
  for i in range(len(pwd)):
    x = ord(md5[i]) ^ ord(pwd[i])
    ret += chr(((x<<3)&0xFF) + (x>>5))
  return ret


def checksum(s):
  log('DEBUG', 'checksum()')
  ret = 1234
  for i in re.findall('....', s):
    ret ^= int(i[::-1].encode('hex'), 16)
  ret = (1968 * ret) & 0xffffffff
  return struct.pack('<I', ret)


def keep_alive(pwd, salt, cookie, svr, socket):
  log('DEBUG', 'keep_alive')
  old_cookie = cookie
  keep_alive1(salt, old_cookie, pwd, svr, socket)

  svr_num = 0

  svr_num, cookie = keep_alive_pre_1(svr_num, cookie, svr, socket)
  svr_num, cookie = keep_alive_pre_2(svr_num, cookie, svr, socket)
  svr_num, cookie = keep_alive_pre_3(svr_num, cookie, svr, socket)

  i = svr_num
  while True:
    packet = keep_alive_package_builder(i, cookie, 1, False)
    socket.sendto(packet, (svr, 61440))
    data, address = socket.recvfrom(1024)
    cookie = data[16:20]
 
    packet = keep_alive_package_builder(i+1, cookie, 3, False)
    socket.sendto(packet, (svr, 61440))
    data, address = socket.recvfrom(1024)
    cookie = data[16:20]

    i = (i+2) % 0xFF
    time.sleep(20)
    keep_alive1(salt, old_cookie, pwd, svr, socket)


def keep_alive1(salt, cookie, pwd, svr, socket):
  log('DEBUG', 'keep_alive1()')
  foo = struct.pack('!H',int(time.time())%0xFFFF)
  data = '\xff' + md5sum('\x03\x01'+salt+pwd) + '\x00\x00\x00'
  data += cookie
  data += foo + '\x00\x00\x00\x00'
  log('DEBUG', 'send:'+data.encode('hex'))
  socket.sendto(data, (svr, 61440))
  while True:
    data, address = socket.recvfrom(1024)
    log('DEBUG', 'recv:'+data.encode('hex'))
    if data[0] == '\x07':
      break
    else:
      log('WARN', 'unexpected recv:'+data.encode('hex'))


def keep_alive_pre_1(svr_num, cookie, svr, socket):
  log('DEBUG', 'keep_alive_pre_1()')
  packet = keep_alive_package_builder(svr_num, '\x00'*4, 1, True)
  log('DEBUG', 'send:'+packet.encode('hex'))
  while True:
    socket.sendto(packet, (svr, 61440))
    data, address = socket.recvfrom(1024)
    log('DEBUG', 'recv:'+data.encode('hex'))
    if data.startswith('\x07\x00\x28\x00') or data.startswith('\x07' + chr(svr_num)  + '\x28\x00'):
      break
    # 2014/10/15 add by latyas, maybe svr sends back a file packet
    elif data[0] == '\x07' and data[2] == '\x10':
      svr_num = svr_num + 1
      packet = keep_alive_package_builder(svr_num, '\x00'*4, 1, False)
    else:
      log('WARN', 'unexpected recv:'+data.encode('hex'))
  return svr_num, None

def keep_alive_pre_2(svr_num, cookie, svr, socket):
  log('DEBUG', 'keep_alive_pre_2()')
  packet = keep_alive_package_builder(svr_num, '\x00'*4, 1, False)
  log('DEBUG', 'send:'+packet.encode('hex'))
  socket.sendto(packet, (svr, 61440))
  while True:
    data, address = socket.recvfrom(1024)
    log('DEBUG', 'recv:'+data.encode('hex'))
    if data[0] == '\x07':
      svr_num = svr_num + 1
      break
    else:
      log('WARN', 'unexpected recv:'+data.encode('hex'))
  cookie = data[16:20]
  return svr_num, cookie

def keep_alive_pre_3(svr_num, cookie, svr, socket):
  log('DEBUG', 'keep_alive_pre_3')
  packet = keep_alive_package_builder(svr_num, cookie, 3, False)
  log('DEBUG', 'send:'+packet.encode('hex'))
  socket.sendto(packet, (svr, 61440))
  while True:
    data, address = socket.recvfrom(1024)
    log('DEBUG', 'recv:'+data.encode('hex'))
    if data[0] == '\x07':
      svr_num = svr_num + 1
      break
    else:
      log('WARN', 'unexpected recv:'+data.encode('hex'))
  cookie = data[16:20]
  return svr_num, cookie


def keep_alive_package_builder(svr_number, cookie, type=1, first=False):
  log('DEBUG', 'keep_alive_package_builder()')
  data = '\x07'+ chr(svr_number) + '\x28\x00\x0b' + chr(type)
  if first :
    data += '\x0f\x27'
  else:
    data += KEEP_ALIVE_VERSION
  data += '\x2f\x12' + '\x00' * 6
  data += cookie
  data += '\x00' * 4
  if type == 3:
    foo = ''.join([chr(int(i)) for i in host_ip.split('.')]) # host_ip
    crc = '\x00' * 4
    data += crc + foo + '\x00' * 8
  else: #packet type = 1
    data += '\x00' * 16
  return data


def empty_socket_buffer(socket):
  #empty buffer for some fucking schools
  log('DEBUG', 'starting to empty socket buffer')
  try:
    while True:
      data, address = socket.recvfrom(1024)
      log('DEBUG', 'recived sth unexpected'+data.encode('hex'))
      if socket == '':
        break
  except:
    # get exception means it has done.
    log('DEBUG', 'exception in empty_socket_buffer')
  log('DEBUG', 'emptyed')


def log(level, msg):
  log_msg = '[%s]: %s (%s)' % (level, msg, datetime.datetime.now() )
  if IS_DEBUG:
    print log_msg
    return 
  if level == 'DEBUG':
    return
  try:
    with open(LOG_PATH, 'a') as f:
      f.write(log_msg+'\n')
  except Exception as e:
    print "Unable to log, %s" % e

def main():
  os.environ["TZ"] = 'Asia/Shanghai'
  time.tzset()
  log('INFO', 'starting...')
  is_first_time = True
  while RETRY or is_first_time:
    is_first_time = False
    try:
      socket = setup_socket(bind_ip)
      salt = get_salt(socket, server, random.randint(0xF,0xFF))
      cookie = do_login(username, password, salt, mac, server, socket)
      empty_socket_buffer(socket)
      keep_alive(password, salt, cookie, server, socket)
    except LoginException as e:
      pass
    except Exception as e: #normal exception is timeout, which mostly caused by multiplace login and this session is ticked out
      log('WARN', e)
      socket.close()
      log('INFO', 'release socket')
    time.sleep(15)
  log('INFO', 'process quit')

if __name__ == "__main__":
  main()
