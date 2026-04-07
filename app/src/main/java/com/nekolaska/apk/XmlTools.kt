package com.nekolaska.apk

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

/**
 * 替换 Android strings.xml 文件中的 app_name 值。
 *
 * @param xmlFile strings.xml 文件的 File 对象。
 * @param newAppName 要设置的新的应用名称。
 * @return 如果成功找到并替换，则返回 true；否则返回 false。
 */
fun replaceAppName(xmlFile: File, newAppName: String): Boolean {
    try {
        // 1. 创建 DOM 解析器工厂和解析器
        val docFactory = DocumentBuilderFactory.newInstance()
        val docBuilder = docFactory.newDocumentBuilder()

        // 2. 解析 XML 文件
        val doc = docBuilder.parse(xmlFile)

        // 3. 查找所有 <string> 标签
        val stringNodes = doc.getElementsByTagName("string")
        var appNameNodeFound = false

        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node.nodeType == Element.ELEMENT_NODE) {
                val element = node as Element

                // 检查 'name' 属性是否为 "app_name"
                if (element.getAttribute("name") == "app_name") {
                    // 4. 找到节点，修改其文本内容
                    element.textContent = newAppName
                    appNameNodeFound = true
                    break // 找到后即可退出循环
                }
            }
        }

        if (!appNameNodeFound) {
            return false
        }

        // 5. 将修改后的内容写回文件
        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()

        // 设置输出属性以保持良好的格式
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4") // 设置缩进空格数

        val source = DOMSource(doc)
        val result = StreamResult(xmlFile)

        transformer.transform(source, result)
        return true

    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}