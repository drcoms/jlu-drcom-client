package com.example.service;

import com.example.drcom.STATUS;

public interface KeepListener {
    void keepingAlive(String content);
    void informLogStatus(STATUS status, String[] s);
    void informCanLoginNow(boolean canReconnect, String[] s);
    void informLogoutSucceed();
    void informInvalidNameOrPass();
    void informInvalidMAC();
}
