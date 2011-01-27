package com.caucho.quercus.junit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Dominik Dorn
 * 0626165
 * dominik.dorn@tuwien.ac.at
 */
public class QuercusRunnerTest {


    QuercusRunner runner;

    @Before
    public void before() throws InitializationError {
        runner = new QuercusRunner(this.getClass());
        assertNotNull(runner);
    }


    @Test
    public void getChildren_shouldReturnTests()
    {

        List<QuercusTest> children = runner.getChildren();
        assertNotNull(children);
        assertEquals(2,children.size());
    }

    @Test
    public void getDescription_validTest()
    {
        List<QuercusTest> children = runner.getChildren();
        assertNotNull(children);
        assertEquals(2,children.size());

        Description description = runner.describeChild(children.get(0));
        assertNotNull(description);
        assertNotNull(description.getDisplayName());

    }


    @Test
    public void runTest()
    {
        List<QuercusTest> children = runner.getChildren();
        assertNotNull(children);
        assertEquals(2,children.size());


        QuercusTest test = children.get(0);

        test.runTests();

    }

    @Test
    public void completeThis()
    {

    }


}
