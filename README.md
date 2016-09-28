DrCOM Client for jilin university
-------------
由于吉大不走特色drcom路线了，可移步到 https://github.com/drcoms/drcom-generic/blob/master/latest-wired.py 用这个通用的版本，并把`ror_version`改成`True`，并参考wiki中`d版简略使用和配置说明`的使用说明。也可以使用 `newclient.py`或者`latest_521d.py`

-------------
2016.9 5.2.1d 新版 `newclient.py`  感谢 @zhjc1124 提供<br>
`latest_521d.py`是参考newclient.py修改后的，使用方式参考https://github.com/drcoms/drcom-generic/wiki/d版简略使用和配置说明
有关其他学校
-------------
HITwh的Shindo酱优秀的项目,u62R0（理论上u64也是可以的），带802.1x <br>
https://github.com/coverxit/EasyDrcom/

U62R0-u64不带802.1x的版本, 主要用于debug, 实际使用（尤其是放在路由器里）还需要另作修改<br>
https://github.com/drcoms/generic

Openwrt下一个图形界面的版本<br>
http://github.com/drcoms/openwrt

代码是很久以前由两个 Python 初学者写的，质量糟糕，不过用起来舒适，将就一下或者仅使用代码中的***mkpkt***函数和***keep_alive_package_builder***函数然后重构它吧！

配置文件说明
```
server = "10.100.61.3" #认证服务器，可以使用域名auth.jlu.edu.cn
username = "" #用户名，和客户端一样
password = "" #密码，和客户端一样
host_name = "liyuanyuan" #计算机名，不要超过71个字符
host_os = "Windows 4.0" #操作系统，不要超过128个字符
mac = 0x888888888888 #网络中心上注册时IP对应的MAC
```
