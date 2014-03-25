local sys = require "luci.sys"
local fs  = require "nixio.fs"

m = SimpleForm("jludrcom", translate("DrCOM Client"),
	translate("DrCOM Client for Jilin Univ."))

m.submit = translate("Connect")
m.reset  = false

o = m:field(Value, "conf")
o.template = "cbi/tvalue"
o.rows = 20

ori = fs.readfile("/etc/drcom.conf")
function o.cfgvalue(self, section)
	return fs.readfile("/etc/drcom.conf")
end


function o.write(self, s, val)
  local msg = ""
  if ori ~= val then
    fs.writefile("/etc/drcom.conf", val)
    msg = msg .. "<p>Configuration file updated.</p>"
  end
  luci.sys.call("killall python")
  luci.sys.call("python /usr/bin/wired.py & > /dev/null")
  m.message = msg .. "<p>JLUDrCOM service rebooted successfully.</p>"
end

return m

