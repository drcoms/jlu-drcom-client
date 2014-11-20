DrCOM GUI for luci
---------------------
由于各种版本情况不一样，需要自行整合

在第一次保存了配置以后试试 `uci show drcom.config`

可以使用 `uci get drcom.config.username` 这样的语句来获取 *luci* 的数据

使用
-----------
对 `wired.py` 的修改:

1. 名字修改为 `drcom`
2. 在头部加入 `#!/usr/bin/python`
3. 在第一个 `import` 语句后加入 `import subprocess`
4. 加入函数

  ```python
  def init():
      global username, password, mac, host_name, host_os, server
      username = subprocess.check_output(['uci','get','drcom.config.username']).strip()
      password = subprocess.check_output(['uci','get','drcom.config.password']).strip()
      mac = int(subprocess.check_output(['uci','get','drcom.config.mac']).strip().replace(':', ''), base=16)
      host_name = subprocess.check_output(['uci','get','drcom.config.host_name']).strip()
      host_os = subprocess.check_output(['uci','get','drcom.config.host_os']).strip()
      server = subprocess.check_output(['uci','get','drcom.config.remote_server']).strip()
  ```
5. `execfile(CONF, globals())` 替换为 `init()`
6. 在 `/etc/config/` 中建立文件 `drcom`
