---------------
### Simple JLU drcom client dirty hack C-version

需要修改 drcom.c 文件中 user(帐号), pass(密码), mac(MAC 地址, 0x010203040506 格式) 等参数，编译
	
	gcc drcom.c md5.c -o drcom


直接在终端运行 ./drcom 

haha, dirty hack 就是爽 (￣ˇ￣)　

改为以 daemon 方式运行，去掉 long 类型，支持 32 位系统

TODO: logout
