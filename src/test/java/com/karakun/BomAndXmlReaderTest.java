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

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.karakun.BomAndXmlReader.BROKEN_UTF32BE_BOM;
import static com.karakun.BomAndXmlReader.BROKEN_UTF32LE_BOM;
import static com.karakun.BomAndXmlReader.UTF16BE_BOM;
import static com.karakun.BomAndXmlReader.UTF16LE_BOM;
import static com.karakun.BomAndXmlReader.UTF32BE_BOM;
import static com.karakun.BomAndXmlReader.UTF32BE_NAME;
import static com.karakun.BomAndXmlReader.UTF32LE_BOM;
import static com.karakun.BomAndXmlReader.UTF32LE_NAME;
import static com.karakun.BomAndXmlReader.UTF8_BOM;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link BomAndXmlReader}.
 */
class BomAndXmlReaderTest {

    private static final String BOM = Character.toString('\uFEFF');
    private static final InputStream EMPTY_STREAM = streamOf(new byte[0]);
    private static final String XML_ENCODING_TAG = "<?xml version=\"1.0\" encoding=\"{ENCODING}\" ?>";

    private static final List<CharsetAndBom> CHARSET_AND_BOMS = unmodifiableList(asList(
            new CharsetAndBom(StandardCharsets.UTF_8, UTF8_BOM),
            new CharsetAndBom(StandardCharsets.UTF_16LE, UTF16LE_BOM),
            new CharsetAndBom(StandardCharsets.UTF_16BE, UTF16BE_BOM),
            new CharsetAndBom(Charset.forName(UTF32LE_NAME), UTF32LE_BOM),
            new CharsetAndBom(Charset.forName(UTF32BE_NAME), UTF32BE_BOM)
    ));

    @Test
    void constructorThrowsOnNullArgument() {
        assertThrows(NullPointerException.class, () ->
                new BomAndXmlReader(null)
        );
        assertThrows(NullPointerException.class, () ->
                new BomAndXmlReader(null, defaultCharset())
        );
        assertThrows(NullPointerException.class, () ->
                new BomAndXmlReader(EMPTY_STREAM, null)
        );
    }

    @Test
    void singleArgumentConstructorUsesLocaleCharset() throws IOException {
        // when
        final BomAndXmlReader reader = new BomAndXmlReader(EMPTY_STREAM);

        // then
        assertReaderHasExpectedEncoding(reader, defaultCharset());
    }

    @Test
    void doubleArgumentConstructorUsesPassedCharset() throws IOException {
        // given
        final Charset charset = defaultCharset().equals(UTF_8) ? ISO_8859_1 : UTF_8;

        // when
        final BomAndXmlReader reader = new BomAndXmlReader(EMPTY_STREAM, charset);

        // then
        assertReaderHasExpectedEncoding(reader, charset);
    }

    @Test
    void detectEncodingFromBom() throws IOException {
        for (CharsetAndBom candidate : CHARSET_AND_BOMS) {
            // when
            final BomAndXmlReader reader = new BomAndXmlReader(streamOf(candidate.bom), ISO_8859_1);

            // then
            assertReaderHasExpectedEncoding(reader, candidate.charset);
        }
    }

    @Test
    void throwsExceptionForUnsupportedEncodings() {
        assertThrows(UnsupportedCharsetException.class, () ->
                new BomAndXmlReader(streamOf(BROKEN_UTF32LE_BOM))
        );
        assertThrows(UnsupportedCharsetException.class, () ->
                new BomAndXmlReader(streamOf(BROKEN_UTF32BE_BOM))
        );
    }

    @Test
    void ensureOnlyBomIsRemovedFromStream() throws IOException {
        for (CharsetAndBom candidate : CHARSET_AND_BOMS) {
            // given
            final String content = "abc";
            final BomAndXmlReader reader = new BomAndXmlReader(streamOf(candidate.bom, content, candidate.charset));

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
            final BomAndXmlReader reader = new BomAndXmlReader(streamOf(XML_ENCODING_TAG, candidate.charset), ISO_8859_1);

            // then
            assertReaderHasExpectedEncoding(reader, candidate.charset);
        }
    }


    private void assertReaderHasExpectedEncoding(final BomAndXmlReader reader, final Charset charset) {
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

    private static class CharsetAndBom {
        private final Charset charset;
        private final byte[] bom;

        private CharsetAndBom(final Charset charset, final byte[] bom) {
            this.charset = charset;
            this.bom = bom;
        }
    }
}
