/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.job.controllers;

import android.app.job.JobInfo;
import android.content.ComponentName;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserHandle;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uniquely identifies a job internally.
 * Created from the public {@link android.app.job.JobInfo} object when it lands on the scheduler.
 * Contains current state of the requirements of the job, as well as a function to evaluate
 * whether it's ready to run.
 * This object is shared among the various controllers - hence why the different fields are atomic.
 * This isn't strictly necessary because each controller is only interested in a specific field,
 * and the receivers that are listening for global state change will all run on the main looper,
 * but we don't enforce that so this is safer.
 * @hide
 */
public class JobStatus {
    public static final long NO_LATEST_RUNTIME = Long.MAX_VALUE;
    public static final long NO_EARLIEST_RUNTIME = 0L;

    final JobInfo job;
    final int uId;

    /** At reschedule time we need to know whether to update job on disk. */
    final boolean persisted;

    // Constraints.
    final AtomicBoolean chargingConstraintSatisfied = new AtomicBoolean();
    final AtomicBoolean timeDelayConstraintSatisfied = new AtomicBoolean();
    final AtomicBoolean deadlineConstraintSatisfied = new AtomicBoolean();
    final AtomicBoolean idleConstraintSatisfied = new AtomicBoolean();
    final AtomicBoolean unmeteredConstraintSatisfied = new AtomicBoolean();
    final AtomicBoolean connectivityConstraintSatisfied = new AtomicBoolean();

    /**
     * Earliest point in the future at which this job will be eligible to run. A value of 0
     * indicates there is no delay constraint. See {@link #hasTimingDelayConstraint()}.
     */
    private long earliestRunTimeElapsedMillis;
    /**
     * Latest point in the future at which this job must be run. A value of {@link Long#MAX_VALUE}
     * indicates there is no deadline constraint. See {@link #hasDeadlineConstraint()}.
     */
    private long latestRunTimeElapsedMillis;
    /** How many times this job has failed, used to compute back-off. */
    private final int numFailures;

    /** Provide a handle to the service that this job will be run on. */
    public int getServiceToken() {
        return uId;
    }

    private JobStatus(JobInfo job, int uId, boolean persisted, int numFailures) {
        this.job = job;
        this.uId = uId;
        this.numFailures = numFailures;
        this.persisted = persisted;
    }

    /** Create a newly scheduled job. */
    public JobStatus(JobInfo job, int uId, boolean persisted) {
        this(job, uId, persisted, 0);

        final long elapsedNow = SystemClock.elapsedRealtime();

        if (job.isPeriodic()) {
            earliestRunTimeElapsedMillis = elapsedNow;
            latestRunTimeElapsedMillis = elapsedNow + job.getIntervalMillis();
        } else {
            earliestRunTimeElapsedMillis = job.hasEarlyConstraint() ?
                    elapsedNow + job.getMinLatencyMillis() : NO_EARLIEST_RUNTIME;
            latestRunTimeElapsedMillis = job.hasLateConstraint() ?
                    elapsedNow + job.getMaxExecutionDelayMillis() : NO_LATEST_RUNTIME;
        }
    }

    /**
     * Create a new JobStatus that was loaded from disk. We ignore the provided
     * {@link android.app.job.JobInfo} time criteria because we can load a persisted periodic job
     * from the {@link com.android.server.job.JobStore} and still want to respect its
     * wallclock runtime rather than resetting it on every boot.
     * We consider a freshly loaded job to no longer be in back-off.
     */
    public JobStatus(JobInfo job, int uId, long earliestRunTimeElapsedMillis,
                      long latestRunTimeElapsedMillis) {
        this(job, uId, true, 0);

        this.earliestRunTimeElapsedMillis = earliestRunTimeElapsedMillis;
        this.latestRunTimeElapsedMillis = latestRunTimeElapsedMillis;
    }

    /** Create a new job to be rescheduled with the provided parameters. */
    public JobStatus(JobStatus rescheduling, long newEarliestRuntimeElapsedMillis,
                      long newLatestRuntimeElapsedMillis, int backoffAttempt) {
        this(rescheduling.job, rescheduling.getUid(), rescheduling.isPersisted(), backoffAttempt);

        earliestRunTimeElapsedMillis = newEarliestRuntimeElapsedMillis;
        latestRunTimeElapsedMillis = newLatestRuntimeElapsedMillis;
    }

    public JobInfo getJob() {
        return job;
    }

    public int getJobId() {
        return job.getId();
    }

    public int getNumFailures() {
        return numFailures;
    }

    public ComponentName getServiceComponent() {
        return job.getService();
    }

    public int getUserId() {
        return UserHandle.getUserId(uId);
    }

    public int getUid() {
        return uId;
    }

    public PersistableBundle getExtras() {
        return job.getExtras();
    }

    public boolean hasConnectivityConstraint() {
        return job.getNetworkCapabilities() == JobInfo.NetworkType.ANY;
    }

    public boolean hasUnmeteredConstraint() {
        return job.getNetworkCapabilities() == JobInfo.NetworkType.UNMETERED;
    }

    public boolean hasChargingConstraint() {
        return job.isRequireCharging();
    }

    public boolean hasTimingDelayConstraint() {
        return earliestRunTimeElapsedMillis != NO_EARLIEST_RUNTIME;
    }

    public boolean hasDeadlineConstraint() {
        return latestRunTimeElapsedMillis != NO_LATEST_RUNTIME;
    }

    public boolean hasIdleConstraint() {
        return job.isRequireDeviceIdle();
    }

    public long getEarliestRunTime() {
        return earliestRunTimeElapsedMillis;
    }

    public long getLatestRunTimeElapsed() {
        return latestRunTimeElapsedMillis;
    }

    public boolean isPersisted() {
        return persisted;
    }
    /**
     * @return Whether or not this job is ready to run, based on its requirements.
     */
    public synchronized boolean isReady() {
        return (!hasChargingConstraint() || chargingConstraintSatisfied.get())
                && (!hasTimingDelayConstraint() || timeDelayConstraintSatisfied.get())
                && (!hasConnectivityConstraint() || connectivityConstraintSatisfied.get())
                && (!hasUnmeteredConstraint() || unmeteredConstraintSatisfied.get())
                && (!hasIdleConstraint() || idleConstraintSatisfied.get())
                // Also ready if the deadline has expired - special case.
                || (hasDeadlineConstraint() && deadlineConstraintSatisfied.get());
    }

    public boolean matches(int uid, int jobId) {
        return this.job.getId() == jobId && this.uId == uid;
    }

    @Override
    public String toString() {
        return String.valueOf(hashCode()).substring(0, 3) + ".."
                + ":[" + job.getService().getPackageName() + ",jId=" + job.getId()
                + ",R=(" + earliestRunTimeElapsedMillis + "," + latestRunTimeElapsedMillis + ")"
                + ",N=" + job.getNetworkCapabilities() + ",C=" + job.isRequireCharging()
                + ",I=" + job.isRequireDeviceIdle() + ",F=" + numFailures
                + (isReady() ? "(READY)" : "")
                + "]";
    }
    // Dumpsys infrastructure
    public void dump(PrintWriter pw, String prefix) {
        pw.println(this.toString());
    }
}
