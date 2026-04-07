package com.nekolaska.apk

object ManifestReplacer2 {
    fun removeService(manifestContent: String, serviceNameToRemove: String): String {
        // 匹配 <service android:name="xxx">...</service> 整个块（含前后空白行）
        val escapedName = Regex.escape(serviceNameToRemove)
        val serviceRegex = Regex(
            """\s*<service[^>]*?android:name\s*=\s*["']$escapedName["'][^>]*?>.*?</service>\s*""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return serviceRegex.replace(manifestContent, "\n")
    }

    private fun replaceAttributeValue(
        inputText: String,
        attributeFullName: String,
        replacementValue: String,
        isManifestPackage: Boolean = false
    ): String {
        val patternString = if (isManifestPackage) {
            """(<manifest[^>]*?${Regex.escape(attributeFullName)}\s*=\s*["'])([^"']*?)(["'][^>]*?>)"""
        } else {
            """(${Regex.escape(attributeFullName)}\s*=\s*["'])([^"']*?)(["'])"""
        }

        val attributePattern = Regex(patternString, RegexOption.IGNORE_CASE)

        // 使用带 lambda 的 replace，这样 replacementValue 就是一个普通的字符串，不会被解释
        return attributePattern.replace(inputText) { matchResult ->
            val group1 = matchResult.groupValues[1]
            val group3 = matchResult.groupValues[3]
            "$group1$replacementValue$group3"
        }
    }

    fun replaceLabel(inputText: String, replacementText: String): String {
        return replaceAttributeValue(inputText, "android:label", replacementText)
    }

    fun replacePackage(inputText: String, replacementText: String): String {
        // package 是 <manifest> 标签的属性，需要特殊处理正则表达式以确保在正确的标签内
        return replaceAttributeValue(
            inputText,
            "package",
            replacementText,
            isManifestPackage = true
        )
    }

    fun modifyVersion(manifestContent: String, versionName: String, versionCode: Int): String {
        var updatedString = manifestContent
        // versionCode 替换
        updatedString =
            replaceAttributeValue(updatedString, "android:versionCode", versionCode.toString())
        // versionName 替换
        updatedString = replaceAttributeValue(updatedString, "android:versionName", versionName)
        return updatedString
    }

    fun updateSdkVersions(manifestContent: String, targetSdk: String?, minSdk: String?): String {
        var updatedContent = manifestContent

        if (targetSdk != null) {
            // 尝试更新带 android: 前缀的
            var tempContent =
                replaceAttributeValue(updatedContent, "android:targetSdkVersion", targetSdk)
            if (tempContent == updatedContent) { // 如果没有替换发生 (说明没找到带 android: 前缀的)
                // 尝试更新不带 android: 前缀的 (通常在 <uses-sdk> 内部)
                tempContent = replaceAttributeValue(updatedContent, "targetSdkVersion", targetSdk)
            }
            updatedContent = tempContent
        }

        if (minSdk != null) {
            var tempContent = replaceAttributeValue(updatedContent, "android:minSdkVersion", minSdk)
            if (tempContent == updatedContent) {
                tempContent = replaceAttributeValue(updatedContent, "minSdkVersion", minSdk)
            }
            updatedContent = tempContent
        }
        return updatedContent
    }

    fun addDebuggable(inputText: String): String {
        val debuggableAttributeRegex = Regex(
            """(android:debuggable\s*=\s*["'])(?:true|false)(["'])""",
            RegexOption.IGNORE_CASE
        )
        var manifestContent = inputText

        val existingMatch = debuggableAttributeRegex.find(manifestContent)
        if (existingMatch != null) {
            // 已存在，将其值设置为 true
            // 构建新的属性字符串
            val newAttribute = "${existingMatch.groupValues[1]}true${existingMatch.groupValues[2]}"
            // 替换原始匹配的部分
            manifestContent = manifestContent.replaceRange(existingMatch.range, newAttribute)
        } else {
            // 不存在，尝试将其添加到 <application> 标签
            val appTagOpeningRegex =
                Regex("""(<application)(\s+[^>]*)?(\s?/?>)""", RegexOption.IGNORE_CASE)
            val appTagMatch = appTagOpeningRegex.find(manifestContent)

            if (appTagMatch != null) {
                val opening = appTagMatch.groupValues[1] // <application
                val existingAttributesGroup = appTagMatch.groups[2] // 包含现有属性的组，可能为 null
                val existingAttributes = existingAttributesGroup?.value ?: "" // 获取属性字符串，如果组不存在则为空
                val closing = appTagMatch.groupValues[3]    // > 或 />

                // 添加新属性。如果 existingAttributes 为空，确保有一个空格。
                val attributesWithSpace = existingAttributes.ifBlank { " " }
                // 如果 existingAttributes 已有内容，则确保新属性前有空格
                val spacePrefix = if (attributesWithSpace.trim()
                        .isNotEmpty() && !attributesWithSpace.endsWith(" ")
                ) " " else ""

                val newAppTagOpening =
                    "$opening$attributesWithSpace${spacePrefix}android:debuggable=\"true\"$closing"
                manifestContent = manifestContent.replaceRange(appTagMatch.range, newAppTagOpening)
            }
            // 如果连 <application> 标签都找不到，则不作任何修改
        }
        return manifestContent
    }


    fun addAndroidPermissions(manifestContent: String, permissions: List<String>): String {
        val lines = manifestContent.lines().toMutableList()
        // 查找 <application> 标签，以便在其之前插入权限；如果找不到，则在 manifest 末尾插入
        var insertIndex =
            lines.indexOfFirst { it.trim().startsWith("<application", ignoreCase = true) }

        if (insertIndex == -1) {
            // 如果没有 <application> 标签，尝试查找闭合的 </manifest> 标签
            insertIndex = lines.indexOfLast { it.trim().equals("</manifest>", ignoreCase = true) }
            if (insertIndex == -1) {
                // 如果也没有 </manifest>，则这不是一个有效的 manifest 文件或格式非常不寻常。
                // 为安全起见，追加到末尾。或者返回原始内容。
                // 这里我们暂时追加到末尾，但这种情况应根据需求处理。
                // 或者: return manifestContent; // 如果未找到
                insertIndex = lines.size // 追加到列表末尾
            }
        }

        for (permission in permissions.distinct().reversed()) {
            // 检查权限是否已存在 (简单的字符串检查，对于属性变体不够鲁棒)
            val permissionString =
                "<uses-permission android:name=\"android.permission.$permission\" />"
            // any() 查找是否存在，none() 表示不存在
            if (lines.none { it.contains(permissionString, ignoreCase = true) }) {
                lines.add(insertIndex, "    $permissionString") // 添加了缩进
            }
        }
        return lines.joinToString("\n")
    }

    fun clearAndroidPermissions(manifestContent: String): String {
        // 逐行过滤，只移除 android.permission.* 的 uses-permission 行，保留其他行的原始缩进
        val permissionLineRegex = Regex(
            """^\s*<uses-permission\s+android:name\s*=\s*["']android\.permission\.\w+.*?/>\s*$""",
            RegexOption.IGNORE_CASE
        )
        return manifestContent.lineSequence()
            .filter { !permissionLineRegex.matches(it) }
            .joinToString("\n")
    }


    /**
     * 修改 android:authorities 属性，将其中的旧包名替换为新包名。
     * 支持以分号分隔的多个 authorities。
     *
     * @param manifestContent 原始的 Manifest 文件内容。
     * @param oldPackage 要被替换的旧包名。
     * @param newPackage 用于替换的新包名。
     * @return 修改后的 Manifest 文件内容。
     */
    fun modifyAuthorities(
        manifestContent: String,
        oldPackage: String,
        newPackage: String
    ): String {
        // 如果旧包名为空或新旧包名相同，则无需替换，直接返回原内容
        if (oldPackage.isBlank() || oldPackage == newPackage) {
            return manifestContent
        }

        // 匹配 android:authorities 属性的正则表达式，支持不同的引号/间距
        val regex =
            Regex("""(android:authorities\s*=\s*["'])([^"']+?)(["'])""", RegexOption.IGNORE_CASE)

        return regex.replace(manifestContent) { matchResult ->
            val attrStart = matchResult.groupValues[1] // 例如: android:authorities="
            val originalAuthorities =
                matchResult.groupValues[2] // 例如: com.old.provider;com.old.another.provider
            val attrEnd = matchResult.groupValues[3]   // 例如: "

            // 将以分号分隔的多个 authorities 字符串分割成列表
            val modifiedAuthorities = originalAuthorities.split(';')
                .joinToString(";") { auth ->
                    // 对每一个 authority 执行字面量替换
                    auth.replace(oldPackage, newPackage)
                }

            // 重新组合成完整的属性字符串
            "$attrStart$modifiedAuthorities$attrEnd"
        }
    }

    fun replaceAndroidName(manifestContent: String, target: String, replacement: String): String {
        // 匹配 android:name 属性的正则表达式，支持不同的引号/间距
        val attributeRegex =
            Regex("""(android:name\s*=\s*["'])([^"']+?)(["'])""", RegexOption.IGNORE_CASE)
        // 在 String.replace 中，target 是字面量匹配，不需要对其进行 Regex.escape

        return attributeRegex.replace(manifestContent) { matchResult ->
            val attrStart = matchResult.groupValues[1] // 例如: android:name="
            val originalValue = matchResult.groupValues[2] // 例如: com.example.old.MyActivity
            val attrEnd = matchResult.groupValues[3]   // 例如: "

            // 在捕获到的值上执行字面量替换
            val newValue = originalValue.replace(target, replacement)
            "$attrStart$newValue$attrEnd"
        }
    }

    fun process(
        manifestContent: String,
        permissions: List<String>,
        versionName: String,
        versionCode: Int,
        targetSdk: Int,
        minSdk: Int,
        isDebug: Boolean,
        keepWallpaperService: Boolean,
        keepAccessibilityService: Boolean,
        keepLuaService: Boolean,
        keepNotificationService: Boolean,
        newLabel: String,
        newLabelReplaced: Boolean,
        newPackage: String,
        oldPackage: String
    ): String {
        var str = manifestContent

        if (newLabel.isNotBlank() && !newLabelReplaced) { // 仅当 newLabel 不为空时替换
            str = replaceLabel(str, newLabel)
        }
        if (newPackage.isNotBlank()) { // 仅当 newPackage 不为空时替换
            // 这个函数修改 <manifest package="...">
            str = replacePackage(str, newPackage)
        }

        str = clearAndroidPermissions(str) // 先清除现有权限
        if (permissions.isNotEmpty()) {
            str = addAndroidPermissions(str, permissions)
        }

        str = modifyVersion(str, versionName, versionCode)
        str = updateSdkVersions(str, targetSdk.toString(), minSdk.toString())

        // 现在这两个操作的目标是独立的，顺序不再那么重要，但把它们放在一起逻辑更清晰
        if (oldPackage.isNotBlank() && newPackage.isNotBlank() && oldPackage != newPackage) {
            // 替换所有 android:name 属性中的包名
            str = replaceAndroidName(str, oldPackage, newPackage)
            // 替换所有 android:authorities 属性中的包名
            str = modifyAuthorities(str, oldPackage, newPackage)
        }


        if (isDebug)
            str = addDebuggable(str)
        if (!keepWallpaperService)
            str = removeService(str, "com.androlua.LuaWallpaperService")
        if (!keepAccessibilityService)
            str = removeService(str, "com.androlua.LuaAccessibilityService")
        if (!keepLuaService)
            str = removeService(str, "com.androlua.LuaService")
        if (!keepNotificationService)
            str = removeService(str, "com.androlua.LuaNotificationListenerService")
        return str
    }
}
