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

import com.caucho.config.ConfigException;
import com.caucho.config.program.ConfigProgram;
import com.caucho.config.program.ContainerProgram;
import com.caucho.loader.EnvironmentBean;
import com.caucho.server.admin.TransactionManager;
import com.caucho.vfs.Vfs;

// XXX: must not be EnvironmentBean to match BootResinConfig

public class TestConfig implements EnvironmentBean {
    private ClassLoader _classLoader;
    private ContainerProgram _program;

    public TestConfig() {
        _classLoader = Thread.currentThread().getContextClassLoader();
    }

    public ClassLoader getClassLoader() {
        return _classLoader;
    }

    public void setSecurityManager(boolean sm) {
        if (sm)
            System.setSecurityManager(new SecurityManager());
    }

    /**
     * Configures the TM.
     */
    public TransactionManager createTransactionManager()
            throws ConfigException {
        return new TransactionManager(Vfs.getPwd());
    }

    public void setTransactionManager(TransactionManager transactionManager)
            throws ConfigException {
        transactionManager.start();
    }

    public void addCluster(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addResinSystemAuthKey(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addClusterDefault(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addManagement(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addModuleRepository(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addServer(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addRootDirectory(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addResinDataDirectory(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    public void addAdminAuthenticator(ConfigProgram program) {
        if (_program == null)
            _program = new ContainerProgram();

        _program.addProgram(program);
    }

    /*
    // XXX: needs comment out for logging
    */
    /*
    public void addContentProgram(ConfigProgram program)
    {
      if (_program == null)
        _program = new ContainerProgram();

      _program.addProgram(program);
    }
    */


    public ConfigProgram getProgram() {
        return _program;
    }

    public SystemContext createSystem() {
        return new SystemContext();
    }

    public static class SystemContext implements EnvironmentBean {
        public ClassLoader getClassLoader() {
            return ClassLoader.getSystemClassLoader();
        }
    }
}
