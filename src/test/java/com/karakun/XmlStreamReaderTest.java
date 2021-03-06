/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright 2019 the original author or authors.
 */

package com.karakun;

import com.karakun.BomStreamReaderTest.CharsetAndBom;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashSet;
import java.util.Set;

import static com.karakun.BomStreamReaderTest.BOM;
import static com.karakun.BomStreamReaderTest.CHARSET_AND_BOMS;
import static com.karakun.BomStreamReaderTest.EMPTY_STREAM;
import static com.karakun.BomStreamReaderTest.ISO_8859_15;
import static com.karakun.XmlStreamReader.BROKEN_UTF32BE_BOM;
import static com.karakun.XmlStreamReader.BROKEN_UTF32LE_BOM;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link XmlStreamReader}.
 */
class XmlStreamReaderTest {

    private static final String XML_ENCODING_TAG = "<?xml version=\"1.0\" encoding=\"{ENCODING}\" ?>";

    @Test
    void constructorThrowsOnNullArgument() {
        assertThrows(NullPointerException.class, () ->
                new XmlStreamReader(null)
        );
        assertThrows(NullPointerException.class, () ->
                new XmlStreamReader(null, defaultCharset())
        );
        assertThrows(NullPointerException.class, () ->
                new XmlStreamReader(EMPTY_STREAM, null)
        );
    }

    @Test
    void singleArgumentConstructorUsesUtf8() throws IOException {
        // when
        final XmlStreamReader reader = new XmlStreamReader(EMPTY_STREAM);

        // then
        assertReaderHasExpectedEncoding(reader, UTF_8);
    }

    @Test
    void doubleArgumentConstructorUsesPassedCharset() throws IOException {
        // when
        final XmlStreamReader reader = new XmlStreamReader(EMPTY_STREAM, ISO_8859_1);

        // then
        assertReaderHasExpectedEncoding(reader, ISO_8859_1);
    }

    @Test
    void detectEncodingFromBom() throws IOException {
        for (CharsetAndBom candidate : CHARSET_AND_BOMS) {
            // when
            final XmlStreamReader reader = new XmlStreamReader(streamOf(candidate.bom), ISO_8859_1);

            // then
            assertReaderHasExpectedEncoding(reader, candidate.charset);
        }
    }

    @Test
    void throwsExceptionForUnsupportedEncodings() {
        assertThrows(UnsupportedCharsetException.class, () ->
                new XmlStreamReader(streamOf(BROKEN_UTF32LE_BOM))
        );
        assertThrows(UnsupportedCharsetException.class, () ->
                new XmlStreamReader(streamOf(BROKEN_UTF32BE_BOM))
        );
    }

    @Test
    void ensureOnlyBomIsRemovedFromStream() throws IOException {
        for (CharsetAndBom candidate : CHARSET_AND_BOMS) {
            // given
            final String content = "abc";
            final XmlStreamReader reader = new XmlStreamReader(streamOf(candidate.bom, content, candidate.charset));

            // when
            final String line = new BufferedReader(reader).readLine();

            // then
            assertThat(line).isEqualTo(content);
        }
    }

    @Test
    void bomBytesCorrelatesToEncodings() {
        for (CharsetAndBom candidate : CHARSET_AND_BOMS) {
            assertThat(candidate.bom).containsExactly(BOM.getBytes(candidate.charset));
        }
    }

    @Test
    void detectEncodingFromXml() throws IOException {
        for (CharsetAndBom candidate : CHARSET_AND_BOMS) {
            // when
            final XmlStreamReader reader = new XmlStreamReader(streamOf(XML_ENCODING_TAG, candidate.charset), ISO_8859_1);

            // then
            assertReaderHasExpectedEncoding(reader, candidate.charset);
        }
    }

    @Test
    void detectEncodingFromXmlTag() throws IOException {
        // when
        final XmlStreamReader reader = new XmlStreamReader(streamOf(XML_ENCODING_TAG, ISO_8859_15), ISO_8859_1);

        // then
        assertReaderHasExpectedEncoding(reader, ISO_8859_15);
    }


    private void assertReaderHasExpectedEncoding(final XmlStreamReader reader, final Charset charset) {
        final Set<String> aliases = new HashSet<>(charset.aliases());
        aliases.add(charset.name());
        assertThat(aliases).contains(reader.getEncoding());
    }

    private static ByteArrayInputStream streamOf(final byte[] content) {
        return new ByteArrayInputStream(content);
    }

    private static ByteArrayInputStream streamOf(final String content, final Charset encoding) {
        return new ByteArrayInputStream(content.replace("{ENCODING}", encoding.name()).getBytes(encoding));
    }

    private static ByteArrayInputStream streamOf(final byte[] bom, final String content, final Charset encoding) {
        final byte[] contentBytesOnly = content.replace("{ENCODING}", encoding.name()).getBytes(encoding);
        final byte[] contentBytes = new byte[bom.length + contentBytesOnly.length];
        System.arraycopy(bom, 0, contentBytes, 0, bom.length);
        System.arraycopy(contentBytesOnly, 0, contentBytes, bom.length, contentBytesOnly.length);
        return new ByteArrayInputStream(contentBytes);
    }
}
