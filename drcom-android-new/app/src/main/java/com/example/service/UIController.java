package com.example.service;

public interface UIController {
    void loggedIn(String[] s);
    void offline();
    void canLoginNow(String[] s);
    void logoutSucceed();
    void invalidNameOrPass();
    void invalidMAC();
}
