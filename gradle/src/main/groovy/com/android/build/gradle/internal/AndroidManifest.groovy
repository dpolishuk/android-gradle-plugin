/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.gradle.internal

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class AndroidManifest {
    private static final String ANDROID_NAME_SPACE = "http://schemas.android.com/apk/res/android"
    String packageName
    Integer versionCode
    String versionName
    Element manifestXml

    void load(File sourceFile) {
        def builderFactory = DocumentBuilderFactory.newInstance()
        builderFactory.setNamespaceAware(true)
        manifestXml = builderFactory.newDocumentBuilder().parse(sourceFile).documentElement

        packageName = manifestXml.getAttribute("package")
        versionCode = manifestXml.getAttributeNS(ANDROID_NAME_SPACE, "versionCode").toInteger()
        versionName = manifestXml.getAttributeNS(ANDROID_NAME_SPACE, "versionName")
    }

    void save(File destFile) {
        if (manifestXml == null) {
            def builderFactory = DocumentBuilderFactory.newInstance()
            builderFactory.setNamespaceAware(true)
            def doc = builderFactory.newDocumentBuilder().newDocument()
            manifestXml = doc.createElement("manifest")
            manifestXml.setAttributeNS(ANDROID_NAME_SPACE, "versionCode", "")
            manifestXml.setAttributeNS(ANDROID_NAME_SPACE, "versionName", "")
        }
        manifestXml.setAttribute("package", packageName)
        manifestXml.getAttributeNodeNS(ANDROID_NAME_SPACE, "versionCode").setValue(versionCode.toString())
        manifestXml.getAttributeNodeNS(ANDROID_NAME_SPACE, "versionName").setValue(versionName)

        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(manifestXml), new StreamResult(destFile))
    }
}

