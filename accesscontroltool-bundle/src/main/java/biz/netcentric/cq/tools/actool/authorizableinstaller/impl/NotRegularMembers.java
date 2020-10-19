package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;

import biz.netcentric.cq.tools.actool.helper.Constants;

/**
 * Auxiliar class to store a list of non regular members.
 */
class NotRegularMembers {

    private final List<String> members = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private RepositoryException thrownException;
    private final Object thrownExceptionLocker = new Object();

    /**
     * Add the given member to the set if it is considered a non regular user, discard otherwise
     *
     * @param member candidate to be added
     */
    public void addOrDiscard(final Authorizable member) {
        if (executor.isShutdown()) {
            throw new IllegalStateException("Set is closed and new member cannot be added");
        }
        final MemberCheckerWorker memberCheckerWorker = new MemberCheckerWorker(member);
        executor.execute(memberCheckerWorker);
    }

    /**
     * Get the resultant set of filtered users
     *
     * @return the set of id of the users
     * @throws RepositoryException in case it could not verify the id of some user
     */
    public Set<String> getSet() throws RepositoryException {
        waitAllMemberCheckersToFinish();
        throwExceptionIfAnyWorkerFailed();
        return new HashSet<>(members);
    }

    private void throwExceptionIfAnyWorkerFailed() throws RepositoryException {
        if (thrownException != null) {
            throw thrownException;
        }
    }

    private void waitAllMemberCheckersToFinish() {
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
    }

    private class MemberCheckerWorker implements Runnable {
        private final Authorizable member;

        MemberCheckerWorker(final Authorizable member) {
            this.member = Objects.requireNonNull(member);
        }

        @Override
        public void run() {
            try {
                if (member != null && !isRegularUser(member)) {
                    members.add(member.getID());
                }
            } catch (final RepositoryException e) {
                synchronized (thrownExceptionLocker) {
                    if (thrownException == null) {
                        thrownException = e;
                    }
                }
            }
        }

        private boolean isRegularUser(final Authorizable member) throws RepositoryException {
            return !member.isGroup() // if user
                    && !member.getPath().startsWith(Constants.USERS_ROOT + "/system/") // but not system user
                    && !member.getID().equals(Constants.USER_ANONYMOUS);  // and not anonymous
        }
    }
}
