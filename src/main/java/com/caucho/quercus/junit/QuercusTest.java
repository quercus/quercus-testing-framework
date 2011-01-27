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

import com.caucho.loader.EnvironmentClassLoader;
import com.caucho.quercus.junit.results.Failed;
import com.caucho.quercus.junit.results.Result;
import com.caucho.server.util.CauchoSystem;
import com.caucho.vfs.*;
import org.junit.runner.Request;
import org.junit.runner.Runner;

import java.io.File;
import java.io.IOException;

public class QuercusTest extends Request {
    private String filename;
    private String path;
    private Runner runner;

    public QuercusTest(File file, Runner runner) {
        this.path = file.getAbsolutePath();
        this.filename = file.getName();
        this.runner = runner;
    }


    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public Runner getRunner() {
        return this.runner;
    }


    private Result runTest(CauchoTest test, Path configFile,
                           WriteStream dbg, String path, String filename, Path outputFile) {

        ReadStream istream = null;
        Result result = null;
        try {
            istream = Vfs.lookup(path).openRead();
            result = test.runTest(configFile, dbg, dbg, istream, filename, outputFile);
            istream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return new Failed("cannot open '" + path + "' as input");
        }

        return result;
    }


    public Result runTests() {
        CauchoSystem.setIsTesting(true);
        String _dir = new File(path).getAbsoluteFile().getParent();
        boolean _verbose = true;
        String _conf = "resin.conf";
        Path _outputFile;
        _outputFile = Vfs.lookup("/tmp/result.txt");
        long _delay = 0L;
        boolean summary = false;

        WriteStream ps = null;
        if (!_verbose) {
            try {
                ps = Vfs.lookup("null:").openWrite();
            } catch (IOException e) {
            }
        } else {
            ps = VfsStream.openWrite(System.out);
        }

        ps.setFlushOnNewline(true);
        ps.setDisableClose(true);

        Path dirPath = Vfs.lookup(new File(path).getAbsolutePath());
        // before all tests
        Result status = null;
        try {


            CauchoTest test = null;

            Thread thread = Thread.currentThread();
            ClassLoader oldLoader = thread.getContextClassLoader();
            ClassLoader parentLoader = oldLoader;
            if (parentLoader == null)
                parentLoader = ClassLoader.getSystemClassLoader();
            EnvironmentClassLoader envLoader = null;

            try {
                thread.setContextClassLoader(parentLoader);

                envLoader = EnvironmentClassLoader.create(parentLoader, "harness");

                thread.setContextClassLoader(envLoader);

                test = new CauchoTest(dirPath, envLoader);
                test.setHasLicense(false);

                Path configFile = Vfs.lookup(_dir).lookup(_conf);

                status = runTest(test, configFile, ps, path, filename, _outputFile);
            } finally {
                if (envLoader != null)
                    envLoader.destroy();

                thread.setContextClassLoader(oldLoader);
            }
            ps.flush();

//        return status;
//        if (status != null && status instanceof Failed) {
//          failed++;
//        } else if (status == null) {
//          System.out.print("null    ");
//          notRun++;
//        } else if (status instanceof Passed) {
//          System.out.print(".       passed");
//          passed++;
//        } else if (status instanceof Failed) {
//          System.out.print("fail    ");
//          failed++;
//        } else if (status instanceof XPassed) {
//          System.out.print("xpass   ");
//          expectedPass++;
//        } else if (status instanceof XFailed) {
//          System.out.print("xfail   ");
//          expectedFail++;
//        } else {
//          System.out.print("????    ");
//          notRun++;
//        }


//        if (_delay > 0)
//          try {
//            Thread.sleep(_delay);
//          }
//          catch (Exception ignored) {}
        } catch (Exception e) {
            System.out.println("Exception: " + e);
            e.printStackTrace();
        } finally {
            // after all tests
            try {
                ps.close();
            } catch (Exception e) {
            }
        }


//    if (! summary)
//      return failed;
//
//    int total = passed + failed + expectedFail + expectedPass;
//
//    System.out.println();
//    writeResult("pass : ", passed, total);
//    writeResult("fail : ", failed, total);
//    if (expectedPass > 0)
//      writeResult("xpass: ", expectedPass, total);
//    if (expectedFail > 0)
//      writeResult("xfail: ", expectedFail, total);
//    System.out.println("total: " + total);

//    return failed;
        return status;
    }


    private void writeResult(Object o1, Object o2, Object o3) {
    }
}
