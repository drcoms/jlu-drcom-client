--[[
Copyright reserved by latyas
]]--

local fs = require "nixio.fs"
local nixio = require "nixio"

local drcom_running =(luci.sys.call("pidof drcom > /dev/null") == 0)
local client_status
if drcom_running then	
	client_status = "客户端运行中"
else
	client_status = "<font color=red>客户端未运行</font>"
end

m = Map("drcom", translate("DrCOM"), translate(client_status))

server = m:section(TypedSection, "drcom", translate("客户端配置"))
server.anonymous = true

remote_server = server:option(Value, "remote_server", translate("认证服务器地址"))
remote_server.datatype = "ip4addr"

username = server:option(Value, "username", translate("用户名"))

password = server:option(Value, "password", translate("密码"))
password.datatype = "maxlength(16)"
password.password = true

host_name = server:option(Value, "host_name", translate("主机名"))
host_name.datatype = "maxlength(16)"
host_name.default = "NOKIA"

host_os = server:option(Value, "host_os", translate("主机操作系统"))
host_os.datatype = "maxlength(32)"
host_os.default = "DOS"

mac = server:option(Value, "mac", translate("绑定MAC地址"), translate("如果不绑定mac可以保持默认"))
mac.default = "11:22:33:44:55:66"

--[[
dhcp_server = server:option(Value, "dhcp_server", translate("DHCP服务器"), translate("DHCP服务器"))
dhcp_server.datatype = "ip4addr"
dhcp_server.default = "0.0.0.0"


version = server:option(ListValue, "version", translate("客户端版本"), translate("版本号请参考原版客户端的关于界面，有类似0.8 u62R0这一类的"))
version:value("u60")
version:value("u60R0")
version:value("u62")
version:value("u62R0")
version:value("u64")
version:value("u64R0")

version_type = server:option(ListValue, "version_type", translate("客户端类型"), translate("协议类型请看客户端版本最后一个字符，如5.2.0(d) 则类型为d"))
version_type:value("d")
version_type:value("p")
version_type:value("x")

iface = server:option(ListValue, "iface", translate("网络接口"))
for k, v in ipairs(nixio.getifaddrs()) do
  if v.family == "packet" then
    iface:value(v.name)
  end
end
]]--

return m