/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * This file is part of Quercus(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Quercus Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Quercus Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Quercus Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 * @author Dominik Dorn
 */

package com.caucho.quercus.junit;

import com.caucho.config.Config;
import com.caucho.config.inject.InjectManager;
import com.caucho.config.lib.ResinConfigLibrary;
import com.caucho.loader.*;
import com.caucho.log.RotateStream;
import com.caucho.quercus.ExecutionMismatchException;
import com.caucho.quercus.QuercusContext;
import com.caucho.quercus.ResinQuercus;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.junit.results.*;
import com.caucho.quercus.page.QuercusPage;
import com.caucho.quercus.parser.QuercusParseException;
import com.caucho.security.PolicyImpl;
import com.caucho.server.util.CauchoSystem;
import com.caucho.transaction.TransactionManagerImpl;
import com.caucho.transaction.TransactionSynchronizationRegistryImpl;
import com.caucho.transaction.UserTransactionProxy;
import com.caucho.util.*;
import com.caucho.vfs.*;

import javax.imageio.ImageIO;
import javax.naming.InitialContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CauchoTest {
    private static final Logger log = Logger.getLogger(CauchoTest.class.getName());

    public static long BOGUS_DATE = 894621091000L;
    @SuppressWarnings("unchecked")
    static EnvironmentLocal _workPath = new EnvironmentLocal("caucho.work-dir");
    @SuppressWarnings("unchecked")
    static ArrayList _listeners = new ArrayList();
    static com.caucho.quercus.junit.CauchoTest _currentTest;
    protected String _title = "";

    private long _testTimeout = 3600 * 1000L;

    public String _failure;
    public String _negative;
    public int _line;
    public boolean _isJs12 = false;
    Path _dir;
    Path _path;
    byte[] _byteBuf = new byte[1024];

    boolean _expectedFail = false;
    boolean _expectedPass = false;
    ClassLoader _parentLoader;
    EnvironmentClassLoader _loader;
    private Path _configFile;
    private TestConfig _config;
    private boolean _hasLicense;

    private int _phpScriptCount;

    public CauchoTest(Path dir, ClassLoader parentLoader) {
        _parentLoader = parentLoader;

        if (_parentLoader == null)
            _parentLoader = ClassLoader.getSystemClassLoader();

        _dir = dir;
        _path = dir;
    }

    public void setHasLicense(boolean hasLicense) {
        _hasLicense = hasLicense;
    }

    public boolean hasLicense() {
        return _hasLicense;
    }

    static public Path getPath() {
        CauchoTest test = _currentTest;

        return test._path;
    }

    static public TestConfig getConfig() {
        CauchoTest test = _currentTest;

        return test._config;
    }

    @SuppressWarnings("unchecked")
    public static synchronized void addExit(ExitListener listener) {
        _listeners.add(listener);
    }

    public Result runTest(Path configFile, WriteStream dbg, WriteStream ref,
                          ReadStream istream, String name, Path outputFile) {
        _failure = "";
        _negative = null;
        _line = 1;

        Timeout timeout = new Timeout(_testTimeout);
        timeout.start();

        try {
            _currentTest = this;

            unlinkTmp(CauchoSystem.getWorkPath().lookup("_js"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("_jsp"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("_xsl"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("_tmp"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("_smlrpc"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("_ejb"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("_quercus"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("qa"));
            unlinkTmp(CauchoSystem.getWorkPath().lookup("work"));

            TestAlarm.clear();
            TestAlarm.setTime(BOGUS_DATE);

            WriteStream.setSystemNewline("\n");
            Jar.clearJarCache();
            RotateStream.clear();
            RandomUtil.setTestSeed(-6054348664251840615L);
            PolicyImpl.init();
            System.setSecurityManager(null);
            Vfs.setPwd(_dir);

            TransactionManagerImpl tm = TransactionManagerImpl.getInstance();
            tm.testClear();

            // force collection of the cleared values
            System.gc();

            test.TestState.clear();

            _configFile = configFile;

            scanTest(dbg, ref, istream, name, outputFile);
        } catch (Throwable e) {
            String message = null;

            Throwable t = e;

            while (t instanceof ExceptionWrapper
                    && ((ExceptionWrapper) t).getRootCause() != null)
                t = ((ExceptionWrapper) t).getRootCause();

            message = "" + t;

            if (_negative != null && message.equals(_negative)) {
                if (_expectedPass)
                    return new XPassed(message);
                else
                    return new Passed(message);
            } else {
                if (t instanceof CompileException) {
                    dbg.log(t);
                } else
                    t.printStackTrace(dbg.getPrintWriter());

                if (_expectedFail)
                    return new XFailed(_failure);
                else
                    return new Failed(message);
            }
        } finally {
            timeout.finish();

            _currentTest = null;

            for (int i = 0; i < _listeners.size(); i++) {
                ExitListener listener = (ExitListener) _listeners.get(i);

                try {
                    listener.handleExit(this);
                } catch (Throwable e) {
                }
            }
            _listeners.clear();
        }

        TestAlarm.clear();
        System.gc();

        if ((_negative == null) && (_failure.equals(""))
                || _failure.equals(_negative))
            if (_expectedPass)
                return new XPassed(_failure);
            else
                return new Passed(_failure);
        else if (_expectedFail)
            return new XFailed(_failure);
        else
            return new Failed(_failure);
    }

    @SuppressWarnings("unchecked")
    private void unlinkTmp(Path path) {
        try {
            Iterator iter = path.iterator();
            while (iter.hasNext()) {
                String seg = (String) iter.next();
                Path subpath = path.lookup("./" + seg);

                if (subpath.isDirectory())
                    unlinkTmp(subpath);

                subpath.remove();
            }
        } catch (IOException e) {
        }
    }

    public void scanTest(WriteStream dbg, WriteStream ref, ReadStream istream,
                         String name, Path outputFile) throws Throwable {
        TestAlarm.clear();
        TestAlarm.setTime(BOGUS_DATE);

        _path = new MemoryPath();

        CauchoSystem.setResinHome(_path.lookup("/resin-home"));
        CauchoSystem.setWindowsTest(false);

        //System.setProperty("java.naming.factory.initial",
        //                   "com.caucho.test.TestJndiFactory");

        System.setProperty("java.naming.factory.initial",
                "com.caucho.naming.InitialContextFactoryImpl");

        System.setProperty("javax.management.builder.initial",
                "com.caucho.jmx.MBeanServerBuilderImpl");
        System.setProperty("java.protocol.handler.pkgs", "com.caucho.vfs");
        System.setProperty("com.caucho.hessian.client.HessianConnectionFactory",
                "test.hessian.TestHessianConnectionFactory");
        System
                .setProperty("test_home", Vfs.lookup("file:../resin").getNativePath());

//    TestJndiFactory.setContext(null);
        Environment.init();

        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        ResinConfigLibrary.configure(InjectManager.create(oldLoader));

        Path classDir = Vfs.lookup("file:/tmp/caucho/qa/classes");
        EnvironmentClassLoader loader;
        loader = EnvironmentClassLoader.create(_parentLoader, "test-loader");
        loader.addLoader(new CompilingLoader(loader, classDir, classDir, null, null));
        loader.init();

        Thread thread = Thread.currentThread();
        thread.setContextClassLoader(loader);

        TransactionManagerImpl tm = TransactionManagerImpl.getInstance();
        TransactionSynchronizationRegistryImpl transactionSynchronizationRegistry = new TransactionSynchronizationRegistryImpl(
                tm);
        UserTransactionProxy ut = UserTransactionProxy.getInstance();

        new InitialContext().rebind("java:comp/TransactionManager", ut);
        new InitialContext().rebind("java:comp/TransactionSynchronizationRegistry",
                transactionSynchronizationRegistry);
        new InitialContext().rebind("java:comp/UserTransaction", ut);

        try {
            _loader = loader;

            Vfs.setPwd(_path);

            InjectManager.create(loader);

            if (_config == null) {
                _config = new TestConfig();

                Config config = new Config();
                if (hasLicense()) {
                    Config.setProperty("isResinProfessional", Boolean.TRUE);
                }

                config.configure(_config, _configFile,
                        "com/caucho/server/resin/resin.rnc");
            }

            scanTest(dbg, ref, istream, name, 1, _path, outputFile);
        } finally {
            CauchoSystem.setWindowsTest(false);

            loader.destroy();
            thread.setContextClassLoader(oldLoader);
            System.gc();
        }
    }

    public ClassLoader getLoader() {
        return _loader;
    }

    @SuppressWarnings("unchecked")
    private void scanTest(WriteStream dbg, WriteStream ref, ReadStream istream,
                          String name, int startLine, Path path, Path outputFile) throws Throwable {
        Thread thread = Thread.currentThread();
        @SuppressWarnings("unused")
        boolean utf8 = false;
        _line = startLine;
        int nThreads = 0;
        ArrayList threads = new ArrayList();
        ArrayList exes = new ArrayList();
        HashMap attr = new HashMap();

        _expectedFail = false;
        _expectedPass = false;

        int ch;
        while ((ch = istream.read()) >= 0) {
            switch (ch) {
                case '<': {
                    String tag = readTag(istream, attr);

                    if (tag.equals("negative")) {
                        if (tag.length() > 9)
                            _negative = tag.substring(9);
                        else
                            _negative = "";
                    } else if (tag.equals("utf8")) {
                        utf8 = true;
                    } else if (tag.equals("windows")) {
                        CauchoSystem.setWindowsTest(true);
                    } else if (tag.equals("xfail")) {
                        _expectedFail = true;
                    } else if (tag.equals("xpass")) {
                        _expectedPass = true;
                    } else if (tag.equals("title")) {
                        ByteBuffer bb = scanText(istream, "</title>");
                        _title = bb.toString();
                        dbg.println(_title);
                        dbg.flush();
                    } else if (tag.startsWith("file")) {
                        String url = (String) attr.get("file");
                        String escape = (String) attr.get("escape");
                        boolean base64encoded = (String) attr.get("base64encoded") != null;
                        boolean hexEncoded = (String) attr.get("hex-encoded") != null;

                        if (url == null)
                            throw new Exception("file tag must contain 'file'");

                        ByteBuffer bb = scanText(istream, "</file>", escape != null);

                        replaceCaucho(bb);

                        if (base64encoded) {
                            ByteBuffer decoded = new ByteBuffer();
                            decoded.addString(Base64.decode(bb.toString()));
                            bb = decoded;
                        } else if (hexEncoded) {
                            bb = decodeHex(bb);
                        }

                        OutputStream os;
                        Path urlPath = path.lookupNative(url);

                        try {
                            os = urlPath.openWrite();
                        } catch (IOException e) {
                            Path parent = urlPath.getParent();

                            parent.mkdirs();

                            os = urlPath.openWrite();
                        }

                        try {
                            os.write(bb.getBuffer(), 0, bb.length());
                            os.close();
                        } catch (IOException e) {
                            e.printStackTrace(System.out);
                        } finally {
                            os.close();
                        }
                    } else if (tag.startsWith("remove")) {
                        String url = (String) attr.get("file");

                        if (url == null)
                            throw new Exception("remove tag must contain 'file'");

                        Path urlPath = path.lookupNative(url);

                        try {
                            urlPath.remove();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (urlPath.exists())
                            throw new IOException("io: " + urlPath);
                    } else if (tag.startsWith("property")) {
                        String propName = (String) attr.get("name");
                        if (propName == null)
                            throw new Exception("property tag must contain 'name'");

                        String value = (String) attr.get("value");
                        if (value == null)
                            throw new Exception("property tag must contain 'value'");

                        Properties props = System.getProperties();
                        props.put(propName, value);
                        System.setProperties(props);
                    } else if (tag.startsWith("config")) {
                        @SuppressWarnings("unused")
                        ByteBuffer bb = scanText(istream, "</config>", false);
                    } else if (tag.startsWith("link")) {
                        String url = (String) attr.get("file");
                        String real = (String) attr.get("real");

                        if (url == null)
                            throw new Exception("link tag must contain 'file'");
                        if (real == null)
                            throw new Exception("link tag must contain 'real'");

                        OutputStream os;
                        try {
                            os = path.lookup(url).openWrite();
                        } catch (IOException e) {
                            int p = url.lastIndexOf('/');
                            if (p == -1)
                                throw e;

                            String parent = url.substring(0, p);
                            path.lookup(parent).mkdirs();

                            os = path.lookup(url).openWrite();
                        }

                        InputStream is = _dir.lookup(real).openRead();
                        int len;
                        while ((len = is.read(_byteBuf, 0, _byteBuf.length)) > 0)
                            os.write(_byteBuf, 0, len);
                        is.close();
                        os.close();
                    } else if (tag.equals("compare")) {
                        handleCompare(attr, istream, path, dbg, outputFile);
                    } else if (tag.equals("thread")) {
                        String threadName = (String) attr.get("name");
                        int oldLine = _line;
                        ByteBuffer data = scanText(istream, "</thread>", false);

                        if (threadName == null)
                            threadName = "test_" + ++nThreads;

                        Execution exe = new Execution(threadName, name, data, dbg, ref,
                                oldLine, path);
                        Thread newThread = new Thread(exe, threadName);

                        exes.add(exe);
                        threads.add(newThread);
                        newThread.start();
                    } else if (tag.equals("join")) {
                        joinThreads(threads, exes);
                    } else if (tag.equals("loop")) {
                        String countName = (String) attr.get("count");
                        if (countName == null)
                            throw new RuntimeException("bad count");
                        int count = Integer.parseInt(countName);

                        @SuppressWarnings("unused")
                        int oldLine = _line;
                        ByteBuffer data = scanText(istream, "</loop>", false);

                        for (int i = 0; i < count; i++) {
                            scanTest(dbg, ref, data.createReadStream(), name, _line, path,
                                    outputFile);
                        }
                    } else if (tag.equals("registry")) {
                        String escape = (String) attr.get("escape");
                        ByteBuffer code = scanText(istream, "</registry>", escape != null);

                        WriteStream ws = path.lookup("/resin.conf").openWrite();
                        ws.write(code.getBuffer(), 0, code.getLength());
                        ws.close();

                        TestConfig config = new TestConfig();

                        ClassLoader oldLoader = thread.getContextClassLoader();
                        try {
                            thread.setContextClassLoader(_loader);

                            new Config().configure(config, path.lookup("/resin.conf"),
                                    "com/caucho/server/resin/resin.rnc");

                            _loader.start();
                        } finally {
                            thread.setContextClassLoader(oldLoader);
                        }

                        _config = config;
                    } else if (tag.equals("php")) {
                        handlePhp(dbg, istream, attr, path, name);

                        RotateStream.clear();
                    } else if (tag.equals("")) {
                    } else {
                        throw new Exception("unknown command: " + tag);
                    }
                }
                break;

                case '\n':
                    _line++;
                    break;

                case ' ':
                case '\t':
                case '\r':
                    break;

                default:
                    throw new Exception("" + _line + ": bad test file. : " + ch + " "
                            + (char) ch);
            }
        }

        joinThreads(threads, exes);
    }

    @SuppressWarnings("unchecked")
    private void handleCompare(HashMap attr, ReadStream istream, Path path,
                               WriteStream dbg, Path outputFile) throws Exception {
        int ch;
        String url = (String) attr.get("file");
        String escape = (String) attr.get("escape");
        String exception = (String) attr.get("exception");
        boolean image = attr.get("image") != null;
        boolean base64encoded = image || attr.get("base64encoded") != null;
        boolean hexEncoded = attr.get("hex-encoded") != null;
        ByteBuffer expect = scanText(istream, "</compare>", escape != null);

        InputStream is = path.lookup(url).openRead();
        ByteBuffer results = new ByteBuffer();
        while ((ch = is.read()) >= 0)
            results.add((byte) ch);
        is.close();

        ByteBuffer originalExpect = expect;
        if (base64encoded) {
            ByteBuffer decoded = new ByteBuffer();
            decoded.addString(Base64.decode(expect.toString()));
            expect = decoded;
        } else if (hexEncoded) {
            int[] ascii = new int[16];

            ByteBuffer encoded = new ByteBuffer();
            int i = 0;
            for (i = 0; i < results.length(); i++) {
                int d = results.byteAt(i) & 0xff;
                encoded.addString(Integer.toHexString((d >> 4) & 0xf));
                encoded.addString(Integer.toHexString(d & 0xf));

                ascii[i % 16] = d;

                if ((i + 1) % 16 == 0) {
                    encoded.addString(" : ");

                    for (int j = 0; j < 16; j++) {
                        if (j % 16 == 8)
                            encoded.addString(" ");

                        if (' ' <= ascii[j] && ascii[j] < 0x7f)
                            encoded.addString(String.valueOf((char) ascii[j]));
                        else
                            encoded.addString(".");
                    }
                    encoded.addString("\n");
                } else if ((i + 1) % 4 == 0)
                    encoded.addString(" ");
            }
            int tail = i;
            for (; i % 16 != 0; i++) {
                encoded.addString("  ");

                if ((i + 1) % 4 == 0)
                    encoded.addString(" ");
            }

            if (tail % 16 > 0) {
                encoded.addString(": ");

                for (int j = 0; j < tail % 16; j++) {
                    if (j % 16 == 8)
                        encoded.addString(" ");

                    if (' ' <= ascii[j] && ascii[j] < 0x7f)
                        encoded.addString(String.valueOf((char) ascii[j]));
                    else
                        encoded.addString(".");
                }

                encoded.addString("\n");
            }

            results = encoded;
        } else if (image) {
            // this normalizes images
            ByteBuffer normalized = new ByteBuffer();
            OutputStream normalizedOutput = normalized.createOutputStream();

            ImageIO.write(ImageIO.read(expect.createInputStream()), "png",
                    normalizedOutput);
            normalizedOutput.flush();
            expect = normalized;
        }

        replaceUserDir(results);

        if (exception != null)
            clearExceptionLines(results);
        if (!expect.equals(results)) {
            dbg.println("expected: (" + url + ")\n" + originalExpect);
            String result = (base64encoded ? Base64.encode(results.toString())
                    .toString() : results.toString());
            dbg.println("results: (" + url + ")");
            printResult(dbg, result);
            if (!base64encoded) {
                dbg.println("diff: (" + url + ")\n" + diff(expect, results));
            } else if (image) {
                diffImage(dbg, expect, results);
            }

            if (outputFile != null) {
                System.err.println("opening " + outputFile);
                WriteStream ws = outputFile.openWrite();
                ws.print(result);
                ws.flush();
                ws.close();
            }
            throw new ExecutionMismatchException("execute mismatch");
        } else {
            dbg.println("compare: (" + url + ")");

            printResult(dbg, (base64encoded ? Base64.encode(results.toString())
                    : results.toString()));
        }
    }

    private void printResult(WriteStream dbg, String result) throws IOException {
        int len = result.length();

        for (int i = 0; i < len; i++) {
            char ch = result.charAt(i);

            if (ch == '\\') {
                dbg.print("\\\\");
            } else if (0x20 <= ch && ch < 0x7f) {
                dbg.print(ch);
            } else if (ch == '\n') {
                dbg.print(ch);
            } else if (ch == '\r') {
                dbg.print("\\r");
            } else if (ch == '\t') {
                dbg.print("\\t");
            } else {
                dbg.print("\\x" + Integer.toHexString((ch >> 4) & 0xf)
                        + Integer.toHexString(ch & 0xf));
            }
        }

        dbg.println();
    }

    private void replaceCaucho(ByteBuffer bb) {
        @SuppressWarnings("unused")
        String string = System.getProperty("user.dir");

        if (!"caucho".equals("user.name"))
            replace(bb, "/tmp/caucho", "/tmp/" + System.getProperty("user.name"));
    }

    private void replaceUserDir(ByteBuffer bb) {
        String userDir = System.getProperty("user.dir");

        if (userDir.endsWith("/test"))
            userDir = userDir.substring(0, userDir.length() - "/test".length());

        replace(bb, userDir, "/home/test");

        if (!"caucho".equals("user.name"))
            replace(bb, "/tmp/" + System.getProperty("user.name"), "/tmp/caucho");
    }

    private void replace(ByteBuffer bb, String pattern, String value) {
        if (pattern.equals(value))
            return;

        String string = pattern;
        byte[] bytes = value.getBytes();

        byte[] userDir = string.getBytes();
        int len = userDir.length;

        int lastMatch = -1;

        int p;
        while ((p = bb.indexOf(userDir, 0, len)) >= 0 && lastMatch < p) {
            bb.remove(p, len);
            bb.add(p, bytes, 0, bytes.length);
            lastMatch = p;
        }
    }

    private ByteBuffer decodeHex(ByteBuffer in) {
        ByteBuffer out = new ByteBuffer();

        int len = in.size();

        int i = 0;
        while (i < len) {
            int ch = in.get(i);

            if ('0' <= ch && ch <= '9' || 'a' <= ch && ch <= 'f' || 'A' <= ch
                    && ch <= 'F') {
                int ch1 = ch;
                int ch2 = in.get(i + 1);

                int v = 0;

                if ('0' <= ch1 && ch1 <= '9')
                    v += ch1 - '0';
                else if ('a' <= ch1 && ch1 <= 'f')
                    v += ch1 - 'a' + 10;
                else if ('A' <= ch1 && ch1 <= 'F')
                    v += ch1 - 'A' + 10;

                v *= 16;

                if ('0' <= ch2 && ch2 <= '9')
                    v += ch2 - '0';
                else if ('a' <= ch2 && ch2 <= 'f')
                    v += ch2 - 'a' + 10;
                else if ('A' <= ch2 && ch2 <= 'F')
                    v += ch2 - 'A' + 10;

                out.add((byte) v);

                i += 2;
            } else if (ch == ':') {
                for (; i < len && in.get(i) != '\n'; i++) {
                }
            } else
                i++;
        }

        return out;
    }

    @SuppressWarnings("unchecked")
    private void handlePhp(WriteStream dbg, ReadStream istream, HashMap attr,
                           Path path, String name) throws Throwable {
        String escape = (String) attr.get("escape");
        String stdout = (String) attr.get("out");
        String compile = (String) attr.get("compile");
        String encoding = (String) attr.get("encoding");
        String compileFailover = (String) attr.get("compileFailover");

        boolean isCompile = "true".equals(compile);
        boolean isCompileFailover = "true".equals(compileFailover);
        boolean isMysqlDriver = "true".equals(attr.get("quercus-mysql-driver"));

        int oldLine = _line + 1; // XXX: temp because of tag-lf
        ByteBuffer code = scanText(istream, "</php>", escape != null);

        if (_phpScriptCount++ > 0)
            name = name.substring(0, name.lastIndexOf('.')) + "_" + _phpScriptCount
                    + name.substring(name.lastIndexOf('.'));

        Path file = Vfs.lookup().lookup("/_" + name);

        QuercusContext quercus = new ResinQuercus();

        quercus.setIni("max_execution_time", String.valueOf(Long.MAX_VALUE / 2));

        for (Object key : attr.keySet()) {
            String attrName = String.valueOf(key);

            if (attrName.indexOf('.') > -1)
                quercus.setIni(attrName, String.valueOf(attr.get(key)));
        }

        file.getParent().mkdirs();
        // quercus.setPwd(file.getParent());
        quercus.setPwd(Vfs.lookup());

        file.setUserPath(name);

        WriteStream os = file.openWrite();
        os.writeStream(code.createInputStream());
        os.close();

        WriteStream stream = null;

        if (stdout == null) {
            stream = VfsStream.openWrite(System.out);
            stream.setDisableClose(true);
        } else
            stream = path.lookup(stdout).openAppend();

        quercus.setCompile(isCompile);
        quercus.setCompileFailover(isCompileFailover);

        if (encoding != null)
            quercus.setScriptEncoding(encoding);

        if (isMysqlDriver)
            quercus.setIni("quercus-mysql-driver", "true");

        Env env = null;

        quercus.init();
        try {
            QuercusPage page = quercus.parse(file, name, oldLine);

            env = quercus.createEnv(page, stream, null, null);

            env.setValue("test_scripts", env.createString("file:"
                    + _dir.lookup("src/scripts").getPath()));

            env.setTimeLimit(Integer.MAX_VALUE);
            env.start();

            env.setGlobalValue("out", env.wrapJava(stream));

            page.executeTop(env);
        } catch (com.caucho.quercus.QuercusExitException e) {
            stream.println(e);
        } catch (com.caucho.quercus.QuercusErrorException e) {
        } catch (QuercusParseException e) {
            log.log(Level.FINER, e.toString(), e);

            stream.println(e.getMessage());
        } finally {
            if (env != null)
                env.close();
        }

        stream.close();
    }

    private static void diffImage(WriteStream dbg, ByteBuffer expect,
                                  ByteBuffer results) throws IOException {
        BufferedImage expectImage = ImageIO.read(expect.createInputStream());
        BufferedImage resultImage = ImageIO.read(results.createInputStream());
        boolean sizeIsDifferent = false;
        if (expectImage.getWidth(null) != resultImage.getWidth(null)) {
            sizeIsDifferent = true;
            dbg.println("  images have different width: expect="
                    + expectImage.getWidth(null) + " result="
                    + resultImage.getWidth(null));
        }
        if (expectImage.getHeight(null) != resultImage.getHeight(null)) {
            sizeIsDifferent = true;
            dbg.println("  images have different height: expect="
                    + expectImage.getHeight(null) + " result="
                    + resultImage.getHeight(null));
        }

        if (sizeIsDifferent) {
            dbg.println("  sizes differ; not examining pixels");
            writeImageFiles(dbg, expect, results);
            return;
        }

        int alphaDiffer = 0;
        int rgbDiffer = 0;
        int example_x = -1;
        int example_y = -1;
        for (int x = 0; x < expectImage.getWidth(null); x++)
            for (int y = 0; y < expectImage.getHeight(null); y++) {
                int expectRGB = expectImage.getRGB(x, y);
                int resultRGB = resultImage.getRGB(x, y);
                if ((expectRGB & 0x00ffffff) != (resultRGB & 0x00ffffff)) {
                    if (example_x == -1) {
                        example_x = x;
                        example_y = y;
                    }
                    rgbDiffer++;
                }
                if ((expectRGB & 0xff000000) != (resultRGB & 0xff000000))
                    alphaDiffer++;
            }

        dbg.println("  pixels where only alpha channel   differs: " + alphaDiffer);
        dbg.println("  pixels where      rgb   channels  differ:  " + rgbDiffer);
        dbg.println("    example: (" + example_x + "," + example_y + ")");

        writeImageFiles(dbg, expect, results);

        if (alphaDiffer == 0 && rgbDiffer == 0)
            throw new Error("diffImage was unable to distinguish two images! "
                    + "The test harness needs fixed!");
    }

    private static void writeImageFiles(WriteStream dbg, ByteBuffer expect,
                                        ByteBuffer results) throws IOException {
        Path tmp = Vfs.lookup("file:/tmp/caucho/qa/results");
        tmp.mkdirs();

        Path expected = tmp.lookup("expected.png");
        Path actual = tmp.lookup("actual.png");
        Path encoded = tmp.lookup("actual.encoded");

        WriteStream ws = expected.openWrite();
        ws.writeStream(expect.createInputStream());
        ws.close();
        dbg.println("  expected image has been written to " + expected);

        ws = actual.openWrite();
        ws.writeStream(results.createInputStream());
        ws.close();
        dbg.println("  actual   image has been written to " + actual);

        ReadStream rs = actual.openRead();
        WriteStream encodeWriteStream = encoded.openWrite();
        Base64.encode(encodeWriteStream.getPrintWriter(), rs);
        ws.close();
        rs.close();
        dbg.println("  encoded  image has been written to " + encoded);
    }

    private final byte[] CAUCHO_BYTES = "\tat com.caucho.".getBytes();
    private final byte[] JAVA_BYTES = "\tat java.".getBytes();
    private final byte[] JAVAX_BYTES = "\tat javax.".getBytes();
    private final byte[] SUN_BYTES = "\tat sun.".getBytes();
    private final byte[] TEST_BYTES = "\tat test.".getBytes();

    private void clearExceptionLines(ByteBuffer buf) {
        for (int i = 0; i < buf.length(); i++) {
            if (buf.byteAt(i) != '\t')
                continue;

            if (lookingAt(buf, i, CAUCHO_BYTES)
                    || lookingAt(buf, i, JAVA_BYTES)
                    || lookingAt(buf, i, JAVAX_BYTES)
                    || lookingAt(buf, i, SUN_BYTES)
                    || lookingAt(buf, i, TEST_BYTES)) {
                for (; i > 0 && buf.byteAt(i - 1) != '\n'; i--) {
                }
                while (i < buf.length() && buf.byteAt(i) != '\n') {
                    buf.remove(i, 1);
                }

                if (i < buf.length()) {
                    buf.remove(i, 1);
                }

                /*
                int j = i + 1;

                while (j < buf.length() && buf.byteAt(j) != '\n') {
                  j++;
                }
                j++;

                if (j < buf.length())
                  buf.remove(i, j - i);
                */

                i--;
            }
        }
    }

    private boolean lookingAt(ByteBuffer buf, int i, byte[] compare) {
        for (int j = 0; i < buf.length() && j < compare.length; i++, j++)
            if (buf.byteAt(i) != compare[j])
                return false;

        return true;
    }

    private String diff(ByteBuffer a, ByteBuffer b) {
        int ai = 0;
        int bi = 0;

        CharBuffer result = CharBuffer.allocate();

        CharBuffer aline = CharBuffer.allocate();
        CharBuffer bline = CharBuffer.allocate();

        while (ai < a.length() && bi < b.length()) {
            aline.clear();
            bline.clear();
            int ch;

            for (; ai < a.length() && (ch = a.get(ai)) != '\n'; ai++) {
                if (ch == '\n')
                    aline.append((char) ch);
                else if (ch == '\r')
                    aline.append("\\r");
                else if (ch >= 0x80 || ch < 0x20) {
                    aline.append(String.format("\\x%02x", (ch & 0xff)));
                } else
                    aline.append((char) ch);
            }
            ai++;

            for (; bi < b.length() && (ch = b.get(bi)) != '\n'; bi++) {
                if (ch == '\n')
                    bline.append((char) ch);
                else if (ch == '\r')
                    bline.append("\\r");
                else if (ch >= 0x80 || ch < 0x20) {
                    bline.append(String.format("\\x%02x", (ch & 0xff)));
                } else
                    bline.append((char) ch);
            }
            bi++;

            if (aline.equals(bline))
                continue;
            if (aline.length() > 0)
                result.append("- " + aline + "\n");
            if (bline.length() > 0)
                result.append("+ " + bline + "\n");
        }

        return result.close();
    }


    @SuppressWarnings("unchecked")
    private void joinThreads(ArrayList threads, ArrayList exes) throws Throwable {
        for (int i = 0; i < threads.size(); i++) {
            ((Thread) threads.get(i)).join();
            Execution exe = (Execution) exes.get(i);

            if (exe._e != null)
                throw exe._e;
        }

        threads.clear();
        exes.clear();
    }

    public void clearTitle() {
        _title = "";
    }

    public String getTitle() {
        return _title;
    }

    public ByteBuffer scanText(ReadStream is, String end) throws IOException {
        return scanText(is, end, false);
    }

    public ByteBuffer scanText(ReadStream is, String end, boolean esc)
            throws IOException {
        ByteBuffer text = new ByteBuffer();
        boolean isFirst = true;
        int endLength = end.length();

        while (true) {
            int ch = is.read();

            if (ch == '\n')
                _line++;

            if (isFirst && ch == '\n') {
                isFirst = false;
                continue;
            }
            isFirst = false;

            if (ch < 0)
                throw new IOException("line " + _line + " unexpected eof, expecting "
                        + end);

            text.append((char) (esc ? readEsc(is, ch) : ch));

            test:
            if (endLength <= text.length()) {
                for (int i = 0; i < endLength; i++) {
                    if (text.get(text.length() - endLength + i) != end.charAt(i)) {
                        break test;
                    }
                }

                text.setLength(text.length() - end.length());
                if (text.length() > 0 && text.get(text.length() - 1) == '\n')
                    text.setLength(text.length() - 1);

                return text;
            }
        }
    }

    public int readEsc(ReadStream is, int ch) throws IOException {
        if (ch == '\\') {
            ch = is.read();

            switch (ch) {
                case 'b':
                    return '\b';
                case 't':
                    return '\t';
                case 'f':
                    return '\f';
                case 'v':
                    return 0x0b;
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 'u':
                    return readHex(is, 4);
                case 'x':
                    return readHex(is, 2);
                default:
                    return ch;
            }
        } else
            return ch;
    }

    public int readHex(ReadStream is, int len) throws IOException {
        int value = 0;

        for (int i = 0; i < len; i++) {
            int ch = is.read();
            if (ch >= '0' && ch <= '9')
                value = 16 * value + ch - '0';
            else if (ch >= 'a' && ch <= 'f')
                value = 16 * value + ch - 'a' + 10;
            else if (ch >= 'A' && ch <= 'F')
                value = 16 * value + ch - 'A' + 10;
            else
                throw new IOException("line  " + _line + " bad hex character `"
                        + (char) ch + "'");
        }

        return value;
    }

    public String readLine(ReadStream is) throws IOException {
        return readLine(is, false);
    }

    public String readLine(ReadStream is, boolean esc) throws IOException {
        int ch;
        CharBuffer cb = new CharBuffer();

        while ((ch = is.read()) >= 0 && ch != '\n' && ch != '\r') {
            if (esc)
                ch = readEsc(is, ch);
        }

        if (ch == '\r') {
            ch = is.read();
            if (ch != '\n')
                throw new RuntimeException("huston, we have a problem");
        }

        _line++;

        return cb.toString();
    }

    private void scanComment(ReadStream is) throws IOException {
        int ch;

        if ((ch = is.read()) != '-')
            throw new IOException();
        if ((ch = is.read()) != '-')
            throw new IOException();

        ch = is.read();
        while (ch >= 0) {
            if (ch == '-') {
                ch = is.read();
                while (ch == '-') {
                    if ((ch = is.read()) == '>')
                        return;
                }
            } else {
                if (ch == '\n')
                    _line++;
                ch = is.read();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String readTag(ReadStream is, HashMap attr) throws IOException {
        int ch;
        attr.clear();
        CharBuffer cb = new CharBuffer();

        ch = is.read();

        if (ch == '!') {
            scanComment(is);
            return "";
        }

        for (; ch >= 0
                && (Character.isUnicodeIdentifierPart((char) ch) || ch == '-'); ch = is
                .read()) {
            cb.append((char) ch);
        }
        String tag = cb.toString();

        while (ch >= 0 && ch != '>' && ch != '/') {
            for (; ch >= 0 && Character.isWhitespace((char) ch); ch = is.read()) {
                if (ch == '\n')
                    _line++;
            }
            if (ch == '>' || ch == '/')
                break;

            cb.setLength(0);
            for (; ch >= 0
                    && (Character.isUnicodeIdentifierPart((char) ch) || ch == '-' || ch == '.'); ch = is
                    .read()) {
                cb.append((char) ch);
            }

            if (ch != '=') { // possibly put boolean
                if (cb.length() != 0)
                    attr.put(cb.toString(), "");
                else if (ch != '>' && ch != '/')
                    ch = is.read();
                continue;
            }

            ch = is.read();
            String name = cb.toString();

            for (; ch >= 0 && Character.isWhitespace((char) ch); ch = is.read()) {
                if (ch == '\n')
                    _line++;
            }

            cb.setLength(0);
            if (ch == '\'')
                for (ch = is.read(); ch >= 0 && ch != '\''; ch = is.read())
                    cb.append((char) readEsc(is, ch));
            else if (ch == '"')
                for (ch = is.read(); ch >= 0 && ch != '"'; ch = is.read())
                    cb.append((char) readEsc(is, ch));
            else {
                for (; ch >= 0 && !Character.isWhitespace((char) ch) && ch != '>'
                        && ch != '/'; ch = is.read())
                    cb.append((char) readEsc(is, ch));
            }

            attr.put(name, cb.toString());
        }

        if (ch == '/') {
            if (is.read() != '>')
                throw new RuntimeException("bad file at " + tag);
        }

        return tag;
    }

    class Execution implements Runnable {
        String name;
        String filename;
        ByteBuffer data;
        WriteStream dbg;
        WriteStream ref;
        Path path;
        int line;
        Throwable _e;

        public void run() {
            try {
                scanTest(dbg, ref, data.createReadStream(), filename, line, path, null);
            } catch (Throwable e) {
                _e = e;
            }
        }

        Execution(String name, String filename, ByteBuffer data, WriteStream dbg,
                  WriteStream ref, int line, Path path) {
            this.name = name;
            this.filename = filename;
            this.data = data;
            this.dbg = dbg;
            this.ref = ref;
            this.line = line;
            this.path = path;
        }
    }

    static class BeansConfig implements EnvironmentBean {
        private ClassLoader _loader;

        BeansConfig(ClassLoader loader) {
            _loader = loader;
        }

        public ClassLoader getClassLoader() {
            return _loader;
        }
    }

    static class Timeout extends Thread {
        private boolean _isDone;
        private long _expires;

        Timeout(long timeout) {
            _expires = System.currentTimeMillis() + timeout;
        }

        public void finish() {
            synchronized (this) {
                _isDone = true;
                notifyAll();
            }
        }

        public void run() {
            try {
                synchronized (this) {
                    while (!_isDone) {
                        long timeout = _expires - System.currentTimeMillis();

                        if (timeout <= 0)
                            break;

                        try {
                            Thread.interrupted();
                            wait(timeout);
                        } catch (Exception e) {
                        }
                    }
                }

                if (!_isDone) {
                    System.err.println("exiting test due to timeout");
                    System.exit(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
