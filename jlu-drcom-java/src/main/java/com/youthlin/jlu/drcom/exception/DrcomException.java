package com.youthlin.jlu.drcom.exception;

/**
 * Created by lin on 2017-01-09-009.
 * 异常
 */
public class DrcomException extends Exception {
    public DrcomException(String msg) {
        super('[' + CODE.ex_unknown.name() + "] " + msg);
    }

    public DrcomException(String msg, CODE code) {
        super('[' + code.name() + "] " + msg);
    }

    public DrcomException(String msg, Throwable cause) {
        super('[' + CODE.ex_unknown.name() + "] " + msg, cause);
    }

    public DrcomException(String msg, Throwable cause, CODE code) {
        super('[' + code.name() + "] " + msg, cause);
    }

    public enum CODE {
        ex_unknown, ex_init, ex_challenge, ex_login, ex_timeout, ex_io, ex_thread
    }

}
