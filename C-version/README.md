---------------
### Simple JLU drcom client dirty hack C-version

需要修改 drcom.c 文件中 user(帐号), pass(密码), mac(MAC 地址, 0x010203040506 格式) 等参数，获取 MAC 地址
	
	echo 0x`ifconfig eth | egrep -io "([0-9a-f]{2}:){5}[0-9a-f]{2}" | tr -d ":"`
	
编译
	
	gcc drcom.c md5.c -o drcom
	

直接在终端运行 ./drcom 

haha, dirty hack 就是爽 (￣ˇ￣)　

改为以 daemon 方式运行，去掉 long 类型，支持 32 位系统

TODO: logout


