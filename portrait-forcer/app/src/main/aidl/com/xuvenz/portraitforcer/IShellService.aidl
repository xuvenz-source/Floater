package com.xuvenz.portraitforcer;

interface IShellService {
    String execute(String command);
    void armDisplayRestore(String sizeCommand, long delayMillis);
    String restoreNow(String sizeCommand);
}
