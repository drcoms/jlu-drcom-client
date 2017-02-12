@echo off
rem you can add your language if needed.
rem if you are xx__YY: need xx_YY.class or xx.class
rem en is no need to add.
rem eg. if you are en environment, no en.class found so print as program is(en).
rem     if you ar jp environment,
rem            if there is no jp.class you see en(as program is),
rem            if you want see jp add jp.class
rem eg. if exist zh_TW.class, zh_TW environment, will get zh_TW.class.
rem eg. if exist zh.class, zh environment(zh zh_CN zh_HK zh_TW zh_SG ...) will get zh.class.
msgfmt --java2 -d . -r Drcom -l zh Drcom_zh.po 1>nul 2>nul
