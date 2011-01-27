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

import com.caucho.quercus.junit.results.*;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class QuercusRunner extends ParentRunner<QuercusTest> {

    public QuercusRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected List<QuercusTest> getChildren() {
        ArrayList<QuercusTest> tests = new ArrayList<QuercusTest>();
        URL resource = QuercusRunner.class.getClassLoader().getResource("qa");
        if (resource == null) {
            System.out.println("no qa/ folder found");
            return tests;
        }
        File qaFolder = new File(resource.getFile());
        if (qaFolder == null) {
            System.out.println("no qa/ folder found");
            return tests;
        }

        File[] files = qaFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.endsWith(".qa"))
                    return true;
                return false;
            }
        });


        for (File f : files) {
            tests.add(new QuercusTest(f, this));
        }

        return tests;
    }

    @Override
    protected Description describeChild(QuercusTest quercusTest) {
        if (quercusTest == null) {
            System.out.println("received empty quercus test");
        }
        Description description = Description.createTestDescription(quercusTest.getRunner().getClass(), quercusTest.getFilename());
        return description;
    }

    @Override
    protected void runChild(QuercusTest quercusTest, RunNotifier runNotifier) {

        runNotifier.fireTestStarted(describeChild(quercusTest));
        Result r = quercusTest.runTests();
        if (r == null) {
            runNotifier.fireTestFailure(null);
        } else {
            if (r instanceof Failed) {
                runNotifier.fireTestFailure(new Failure(describeChild(quercusTest), new Exception(r.getMessage())));
            } else if (r instanceof Passed) {
                runNotifier.fireTestFinished(describeChild(quercusTest));
            } else if (r instanceof XFailed) {
                runNotifier.fireTestFinished(describeChild(quercusTest));
            } else if (r instanceof XPassed) {
                runNotifier.fireTestFinished(describeChild(quercusTest));
            }


        }


//        System.out.println("starting test " + quercusTest.getPath() + ":" + quercusTest.getFilename());
//        runNotifier.fireTestStarted(describeChild(quercusTest));
//        System.out.println("running test " + quercusTest.getPath() + ":" + quercusTest.getFilename());
//        runNotifier.fireTestFinished(describeChild(quercusTest));
//        System.out.println("finished test " + quercusTest.getPath() + ":" + quercusTest.getFilename());
    }

}
