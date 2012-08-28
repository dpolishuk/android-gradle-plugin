/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.builder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Processes a template to generate a file somewhere.
 */
class TemplateProcessor {

    private final InputStream mTemplateStream;
    private final Map<String, String> mPlaceHolderMap;

    /**
     * Creates a processor
     * @param templateStream the stream to read the template file from
     * @param placeHolderMap
     */
    public TemplateProcessor(InputStream templateStream, Map<String, String> placeHolderMap) {
        mTemplateStream = templateStream;
        mPlaceHolderMap = placeHolderMap;
    }

    /**
     * Generates the file from the template.
     * @param outputFile the file to create
     */
    public void generate(File outputFile) throws IOException {
        String template = readEmbeddedTextFile(mTemplateStream);

        String content = replaceParameters(template, mPlaceHolderMap);

        writeFile(outputFile, content);
    }

    /**
     * Reads and returns the content of a text file embedded in the jar file.
     * @param templateStream the stream to read the template file from
     * @return null if the file could not be read
     * @throws java.io.IOException
     */
    private String readEmbeddedTextFile(InputStream templateStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(templateStream));

        try {
            String line;
            StringBuilder total = new StringBuilder(reader.readLine());
            while ((line = reader.readLine()) != null) {
                total.append('\n');
                total.append(line);
            }

            return total.toString();
        } finally {
            reader.close();
        }
    }

    private void writeFile(File file, String content) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            InputStream source = new ByteArrayInputStream(content.getBytes("UTF-8"));

            byte[] buffer = new byte[1024];
            int count = 0;
            while ((count = source.read(buffer)) != -1) {
                fos.write(buffer, 0, count);
            }
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    /**
     * Replaces placeholders found in a string with values.
     *
     * @param str the string to search for placeholders.
     * @param parameters a map of <placeholder, Value> to search for in the string
     * @return A new String object with the placeholder replaced by the values.
     */
    private String replaceParameters(String str, Map<String, String> parameters) {

        for (Entry<String, String> entry : parameters.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                str = str.replaceAll(entry.getKey(), value);
            }
        }

        return str;
    }
}
