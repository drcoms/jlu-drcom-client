local sys = require "luci.sys"
local fs  = require "nixio.fs"

m = SimpleForm("jludrcom", translate("DrCOM Client"),
	translate("DrCOM Client for Jilin Univ."))

m.submit = translate("Connect")
m.reset  = false

local o = m:field(Value, "conf")
o.template = "cbi/tvalue"
o.rows = 20
o.rmempty = true

ori = fs.readfile("/etc/drcom.conf")

local update = luci.http.formvalue("cbi.submit")
if update then
  luci.sys.call("killall python")
  luci.sys.call("python /usr/bin/wired.py & > /dev/null")
  local msg = "JLUDrCOM service rebooted successfully."
  if m.message == nil then
    m.message = msg
  else
    m.message = m.message .. msg
  end
end

function o.cfgvalue(self, section)
	return fs.readfile("/etc/drcom.conf")
end


function o.write(self, s, val)
  local msg
  fs.writefile("/etc/drcom.conf", val)
  msg = "<p>Configuration updated.</p>"
  m.message = m.message .. msg
end

return m

 
