module("luci.controller.jludrcom", package.seeall)

function index()
	require("luci.i18n")
	luci.i18n.loadc("jludrcom")

	if nixio.fs.access("/etc/drcom.conf") then
	local page 
	page = entry({"admin", "services", "jludrcom"}, cbi("jludrcom"), _("DrCOM for JLU"), 10)
	page.i18n = "jludrcom"
	page.dependent = true
	end
end
