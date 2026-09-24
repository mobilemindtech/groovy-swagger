package org.grails.plugins.swagger

import groovy.transform.CompileStatic
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Reads the whole request body up front so it can be validated and then read again by the
 * application (request.JSON, request.reader, ...).
 */
@CompileStatic
class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body

    CachedBodyRequestWrapper(HttpServletRequest request) {
        super(request)
        this.body = request.inputStream.bytes
    }

    String getBodyAsString() {
        new String(body, charset())
    }

    @Override
    ServletInputStream getInputStream() {
        def input = new ByteArrayInputStream(body)
        new ServletInputStream() {
            @Override
            boolean isFinished() { input.available() == 0 }

            @Override
            boolean isReady() { true }

            @Override
            void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException('async reads are not supported')
            }

            @Override
            int read() { input.read() }

            @Override
            int read(byte[] b, int off, int len) { input.read(b, off, len) }
        }
    }

    @Override
    BufferedReader getReader() {
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset()))
    }

    @Override
    int getContentLength() { body.length }

    @Override
    long getContentLengthLong() { body.length }

    private Charset charset() {
        characterEncoding ? Charset.forName(characterEncoding) : StandardCharsets.UTF_8
    }
}
