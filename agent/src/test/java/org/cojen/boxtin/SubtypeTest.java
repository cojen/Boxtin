/*
 *  Copyright 2025 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.boxtin;

import java.io.IOException;
import java.io.PrintWriter;

import java.net.URI;

import java.nio.channels.SeekableByteChannel;

import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;

import java.nio.file.spi.FileSystemProvider;
import java.nio.file.spi.FileTypeDetector;

import java.util.Map;
import java.util.Set;

import java.util.spi.ToolProvider;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class SubtypeTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SubtypeTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void basic() throws Exception {
        if (runTransformed(FSProvider.class)) {
            return;
        }

        try {
            FileSystemProvider.installedProviders();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            FSProvider.installedProviders();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            FSProvider p = null;
            p.installedProviders();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            FSProvider p = null;
            ((FileSystemProvider) p).installedProviders();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            new FSProvider();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void interfaces() throws Exception {
        if (runTransformed(TProvider.class)) {
            return;
        }

        try {
            ToolProvider.findFirst("x");
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            new TProvider();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            TProvider.doFindFirst("x");
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void classAndInterface() throws Exception {
        if (runTransformed(FTDetector.class)) {
            return;
        }

        try {
            new FTDetector();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    public static class FSProvider extends FileSystemProvider {
        @Override
        public String getScheme() {
            return null;
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String,?> env) throws IOException {
            return null;
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            return null;
        }

        @Override
        public Path getPath(URI uri) {
            return null;
        }

        @Override
        public SeekableByteChannel newByteChannel
            (Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException
        {
            return null;
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream
            (Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException
        {
            return null;
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        }

        @Override
        public void delete(Path path) throws IOException {
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
        }

        @Override
        public boolean isSameFile(Path path, Path path2) throws IOException {
            return false;
        }

        @Override
        public boolean isHidden(Path path) throws IOException {
            return false;
        }

        @Override
        public FileStore getFileStore(Path path) throws IOException {
            return null;
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView
            (Path path, Class<V> type, LinkOption... options)
        {
            return null;
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes
            (Path path, Class<A> type, LinkOption... options)
            throws IOException
        {
            return null;
        }

        @Override
        public Map<String,Object> readAttributes(Path path, String attributes,
                                                 LinkOption... options)
            throws IOException
        {
            return null;
        }

        @Override
        public void setAttribute(Path path, String attribute,
                                 Object value, LinkOption... options)
            throws IOException
        {
        }
    }

    private static class TProvider implements ToolProvider {
        @Override
        public String name() {
            return null;
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return 0;
        }

        static Object doFindFirst(String name) {
            return ToolProvider.findFirst(name);
        }
    }

    private static class FTDetector extends FileTypeDetector implements ToolProvider {
        @Override
        public String probeContentType(Path path) {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return 0;
        }
    }
}
