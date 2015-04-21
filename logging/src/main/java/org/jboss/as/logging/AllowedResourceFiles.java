/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class AllowedResourceFiles implements Iterable<String> {

    private static class Holder {
        static final AllowedResourceFiles INSTANCE = new AllowedResourceFiles();
    }

    public static AllowedResourceFiles getInstance() {
        return Holder.INSTANCE;
    }

    private final Set<String> allowedFiles;
    private final Set<String> uncommittedAdds;
    private final Set<String> uncommittedRemoves;

    private AllowedResourceFiles() {
        allowedFiles = new HashSet<>();
        uncommittedAdds = new HashSet<>();
        uncommittedRemoves = new HashSet<>();
    }

    /**
     * Adds an file name to the set of allowed file names that can be represented as resources.
     *
     * @param fileName the file name to add
     */
    public synchronized void addAllowedFileName(final String fileName) {
        uncommittedAdds.add(fileName);
    }

    /**
     * Removes a file name from the set of allowed file names to be represented as resources.
     *
     * @param fileName the file name to remove
     */
    public synchronized void removeAllowedFileName(final String fileName) {
        uncommittedRemoves.add(fileName);
    }

    public synchronized void commit() {
        allowedFiles.addAll(uncommittedAdds);
        allowedFiles.removeAll(uncommittedRemoves);
        uncommittedAdds.clear();
        uncommittedRemoves.clear();
    }

    public synchronized void rollback() {
        uncommittedRemoves.clear();
        uncommittedAdds.clear();
    }

    @Override
    public synchronized Iterator<String> iterator() {
        return new HashSet<>(allowedFiles).iterator();
    }
}
