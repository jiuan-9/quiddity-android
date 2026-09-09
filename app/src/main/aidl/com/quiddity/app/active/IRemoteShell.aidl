package com.quiddity.app.active;

import com.quiddity.app.active.ShellResult;

interface IRemoteShell {
    ShellResult exec(in String[] command);
}
