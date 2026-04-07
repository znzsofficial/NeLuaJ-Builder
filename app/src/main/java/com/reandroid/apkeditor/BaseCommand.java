/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.apkeditor;

import com.nekolaska.apk.LogCallback;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.coder.xml.XmlCoderLogger;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

public class BaseCommand<T extends Options> implements APKLogger, XmlCoderLogger {
    private final T options;
    private final LogCallback callback;
    private boolean mEnableLog;

    public BaseCommand(T options) {
        this.options = options;
        this.mEnableLog = true;
        callback = null;
    }

    public BaseCommand(T options, LogCallback callback) {
        this.options = options;
        this.mEnableLog = true;
        this.callback = callback;
    }

    public void run() throws IOException {
    }

    protected void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        logMessage("Delete: " + file);
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            Util.deleteDir(file);
        }
    }

    protected T getOptions() {
        return options;
    }

    public void setEnableLog(boolean enableLog) {
        this.mEnableLog = enableLog;
    }

    private void call(String msg) {
        if (callback != null) callback.invoke(msg);
    }

    @Override
    public void logMessage(String msg) {
        if (!mEnableLog) {
            return;
        }
        call(msg);
    }

    @Override
    public void logError(String msg, Throwable tr) {
        if (!mEnableLog) {
            return;
        }
        call(msg + tr);
    }

    @Override
    public void logVerbose(String msg) {
        if (!mEnableLog) {
            return;
        }
        call(msg);
    }

    @Override
    public void logMessage(String tag, String msg) {
        if (!mEnableLog) {
            return;
        }
        call(msg);
    }

    @Override
    public void logVerbose(String tag, String msg) {
        if (!mEnableLog) {
            return;
        }
        call(msg);
    }

    public void logWarn(String msg) {
        call(msg);
    }

    public void logVersion() {
    }

    protected static void clearMeta(ApkModule module) {
        removeSignature(module);
        module.setApkSignatureBlock(null);
    }

    protected static void removeSignature(ApkModule module) {
        ZipEntryMap archive = module.getZipEntryMap();
        archive.removeIf(Pattern.compile("^META-INF/.+\\.(([MS]F)|(RSA))"));
        archive.remove("stamp-cert-sha256");
    }
}

