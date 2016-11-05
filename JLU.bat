@echo off
chcp 936

:: path for python27
set py=C:\Python27\python.exe 
:: path for newclient.py 
set login=C:\Python27\newclient.py 

title=JLU login client
color 71
%py% %login%
