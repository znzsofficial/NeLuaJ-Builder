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
package com.reandroid.apkeditor.decompile;

import com.nekolaska.apk.LogCallback;
import com.reandroid.jcommand.exceptions.CommandException;

import java.io.IOException;

/** Compatibility entry point retained for the Builder application. */
public final class Decompiler2 {

    private Decompiler2() {
    }

    public static void execute(LogCallback callback, String... opts)
            throws CommandException, IOException {
        Decompiler.execute(callback, opts);
    }
}
