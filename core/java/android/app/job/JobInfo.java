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

package android.app.job;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

/**
 * Container of data passed to the {@link android.app.job.JobScheduler} fully encapsulating the
 * parameters required to schedule work against the calling application. These are constructed
 * using the {@link JobInfo.Builder}.
 */
public class JobInfo implements Parcelable {
    public interface NetworkType {
        /** Default. */
        public final int NONE = 0;
        /** This job requires network connectivity. */
        public final int ANY = 1;
        /** This job requires network connectivity that is unmetered. */
        public final int UNMETERED = 2;
    }

    /**
     * Amount of backoff a job has initially by default, in milliseconds.
     * @hide.
     */
    public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 5000L;

    /**
     * Default type of backoff.
     * @hide
     */
    public static final int DEFAULT_BACKOFF_POLICY = BackoffPolicy.EXPONENTIAL;
    /**
     * Maximum backoff we allow for a job, in milliseconds.
     * @hide
     */
    public static final long MAX_BACKOFF_DELAY_MILLIS = 24 * 60 * 60 * 1000;  // 24 hours.

    /**
     * Linear: retry_time(failure_time, t) = failure_time + initial_retry_delay * t, t >= 1
     * Expon: retry_time(failure_time, t) = failure_time + initial_retry_delay ^ t, t >= 1
     */
    public interface BackoffPolicy {
        public final int LINEAR = 0;
        public final int EXPONENTIAL = 1;
    }

    private final int jobId;
    // TODO: Change this to use PersistableBundle when that lands in master.
    private final PersistableBundle extras;
    private final ComponentName service;
    private final boolean requireCharging;
    private final boolean requireDeviceIdle;
    private final boolean hasEarlyConstraint;
    private final boolean hasLateConstraint;
    private final int networkCapabilities;
    private final long minLatencyMillis;
    private final long maxExecutionDelayMillis;
    private final boolean isPeriodic;
    private final long intervalMillis;
    private final long initialBackoffMillis;
    private final int backoffPolicy;

    /**
     * Unique job id associated with this class. This is assigned to your job by the scheduler.
     */
    public int getId() {
        return jobId;
    }

    /**
     * Bundle of extras which are returned to your application at execution time.
     */
    public PersistableBundle getExtras() {
        return extras;
    }

    /**
     * Name of the service endpoint that will be called back into by the JobScheduler.
     */
    public ComponentName getService() {
        return service;
    }

    /**
     * Whether this job needs the device to be plugged in.
     */
    public boolean isRequireCharging() {
        return requireCharging;
    }

    /**
     * Whether this job needs the device to be in an Idle maintenance window.
     */
    public boolean isRequireDeviceIdle() {
        return requireDeviceIdle;
    }

    /**
     * See {@link android.app.job.JobInfo.NetworkType} for a description of this value.
     */
    public int getNetworkCapabilities() {
        return networkCapabilities;
    }

    /**
     * Set for a job that does not recur periodically, to specify a delay after which the job
     * will be eligible for execution. This value is not set if the job recurs periodically.
     */
    public long getMinLatencyMillis() {
        return minLatencyMillis;
    }

    /**
     * See {@link Builder#setOverrideDeadline(long)}. This value is not set if the job recurs
     * periodically.
     */
    public long getMaxExecutionDelayMillis() {
        return maxExecutionDelayMillis;
    }

    /**
     * Track whether this job will repeat with a given period.
     */
    public boolean isPeriodic() {
        return isPeriodic;
    }

    /**
     * Set to the interval between occurrences of this job. This value is <b>not</b> set if the
     * job does not recur periodically.
     */
    public long getIntervalMillis() {
        return intervalMillis;
    }

    /**
     * The amount of time the JobScheduler will wait before rescheduling a failed job. This value
     * will be increased depending on the backoff policy specified at job creation time. Defaults
     * to 5 seconds.
     */
    public long getInitialBackoffMillis() {
        return initialBackoffMillis;
    }

    /**
     * See {@link android.app.job.JobInfo.BackoffPolicy} for an explanation of the values this field
     * can take. This defaults to exponential.
     */
    public int getBackoffPolicy() {
        return backoffPolicy;
    }

    /**
     * User can specify an early constraint of 0L, which is valid, so we keep track of whether the
     * function was called at all.
     * @hide
     */
    public boolean hasEarlyConstraint() {
        return hasEarlyConstraint;
    }

    /**
     * User can specify a late constraint of 0L, which is valid, so we keep track of whether the
     * function was called at all.
     * @hide
     */
    public boolean hasLateConstraint() {
        return hasLateConstraint;
    }

    private JobInfo(Parcel in) {
        jobId = in.readInt();
        extras = in.readPersistableBundle();
        service = in.readParcelable(null);
        requireCharging = in.readInt() == 1;
        requireDeviceIdle = in.readInt() == 1;
        networkCapabilities = in.readInt();
        minLatencyMillis = in.readLong();
        maxExecutionDelayMillis = in.readLong();
        isPeriodic = in.readInt() == 1;
        intervalMillis = in.readLong();
        initialBackoffMillis = in.readLong();
        backoffPolicy = in.readInt();
        hasEarlyConstraint = in.readInt() == 1;
        hasLateConstraint = in.readInt() == 1;
    }

    private JobInfo(JobInfo.Builder b) {
        jobId = b.mJobId;
        extras = b.mExtras;
        service = b.mJobService;
        requireCharging = b.mRequiresCharging;
        requireDeviceIdle = b.mRequiresDeviceIdle;
        networkCapabilities = b.mNetworkCapabilities;
        minLatencyMillis = b.mMinLatencyMillis;
        maxExecutionDelayMillis = b.mMaxExecutionDelayMillis;
        isPeriodic = b.mIsPeriodic;
        intervalMillis = b.mIntervalMillis;
        initialBackoffMillis = b.mInitialBackoffMillis;
        backoffPolicy = b.mBackoffPolicy;
        hasEarlyConstraint = b.mHasEarlyConstraint;
        hasLateConstraint = b.mHasLateConstraint;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(jobId);
        out.writePersistableBundle(extras);
        out.writeParcelable(service, flags);
        out.writeInt(requireCharging ? 1 : 0);
        out.writeInt(requireDeviceIdle ? 1 : 0);
        out.writeInt(networkCapabilities);
        out.writeLong(minLatencyMillis);
        out.writeLong(maxExecutionDelayMillis);
        out.writeInt(isPeriodic ? 1 : 0);
        out.writeLong(intervalMillis);
        out.writeLong(initialBackoffMillis);
        out.writeInt(backoffPolicy);
        out.writeInt(hasEarlyConstraint ? 1 : 0);
        out.writeInt(hasLateConstraint ? 1 : 0);
    }

    public static final Creator<JobInfo> CREATOR = new Creator<JobInfo>() {
        @Override
        public JobInfo createFromParcel(Parcel in) {
            return new JobInfo(in);
        }

        @Override
        public JobInfo[] newArray(int size) {
            return new JobInfo[size];
        }
    };

    /** Builder class for constructing {@link JobInfo} objects. */
    public static final class Builder {
        private int mJobId;
        private PersistableBundle mExtras = PersistableBundle.EMPTY;
        private ComponentName mJobService;
        // Requirements.
        private boolean mRequiresCharging;
        private boolean mRequiresDeviceIdle;
        private int mNetworkCapabilities;
        // One-off parameters.
        private long mMinLatencyMillis;
        private long mMaxExecutionDelayMillis;
        // Periodic parameters.
        private boolean mIsPeriodic;
        private boolean mHasEarlyConstraint;
        private boolean mHasLateConstraint;
        private long mIntervalMillis;
        // Back-off parameters.
        private long mInitialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
        private int mBackoffPolicy = DEFAULT_BACKOFF_POLICY;
        /** Easy way to track whether the client has tried to set a back-off policy. */
        private boolean mBackoffPolicySet = false;

        /**
         * @param jobId Application-provided id for this job. Subsequent calls to cancel, or
         *               jobs created with the same jobId, will update the pre-existing job with
         *               the same id.
         * @param jobService The endpoint that you implement that will receive the callback from the
         *            JobScheduler.
         */
        public Builder(int jobId, ComponentName jobService) {
            mJobService = jobService;
            mJobId = jobId;
        }

        /**
         * Set optional extras. This is persisted, so we only allow primitive types.
         * @param extras Bundle containing extras you want the scheduler to hold on to for you.
         */
        public Builder setExtras(PersistableBundle extras) {
            mExtras = extras;
            return this;
        }

        /**
         * Set some description of the kind of network capabilities you would like to have. This
         * will be a parameter defined in {@link android.app.job.JobInfo.NetworkType}.
         * Not calling this function means the network is not necessary.
         * Bear in mind that calling this function defines network as a strict requirement for your
         * job if the network requested is not available your job will never run. See
         * {@link #setOverrideDeadline(long)} to change this behaviour.
         */
        public Builder setRequiredNetworkCapabilities(int networkCapabilities) {
            mNetworkCapabilities = networkCapabilities;
            return this;
        }

        /**
         * Specify that to run this job, the device needs to be plugged in. This defaults to
         * false.
         * @param requiresCharging Whether or not the device is plugged in.
         */
        public Builder setRequiresCharging(boolean requiresCharging) {
            mRequiresCharging = requiresCharging;
            return this;
        }

        /**
         * Specify that to run, the job needs the device to be in idle mode. This defaults to
         * false.
         * <p>Idle mode is a loose definition provided by the system, which means that the device
         * is not in use, and has not been in use for some time. As such, it is a good time to
         * perform resource heavy jobs. Bear in mind that battery usage will still be attributed
         * to your application, and surfaced to the user in battery stats.</p>
         * @param requiresDeviceIdle Whether or not the device need be within an idle maintenance
         *                           window.
         */
        public Builder setRequiresDeviceIdle(boolean requiresDeviceIdle) {
            mRequiresDeviceIdle = requiresDeviceIdle;
            return this;
        }

        /**
         * Specify that this job should recur with the provided interval, not more than once per
         * period. You have no control over when within this interval this job will be executed,
         * only the guarantee that it will be executed at most once within this interval.
         * A periodic job will be repeated until the phone is turned off, however it will only be
         * persisted beyond boot if the client app has declared the
         * {@link android.Manifest.permission#RECEIVE_BOOT_COMPLETED} permission. You can schedule
         * periodic jobs without this permission, they simply will cease to exist after the phone
         * restarts.
         * Setting this function on the builder with {@link #setMinimumLatency(long)} or
         * {@link #setOverrideDeadline(long)} will result in an error.
         * @param intervalMillis Millisecond interval for which this job will repeat.
         */
        public Builder setPeriodic(long intervalMillis) {
            mIsPeriodic = true;
            mIntervalMillis = intervalMillis;
            mHasEarlyConstraint = mHasLateConstraint = true;
            return this;
        }

        /**
         * Specify that this job should be delayed by the provided amount of time.
         * Because it doesn't make sense setting this property on a periodic job, doing so will
         * throw an {@link java.lang.IllegalArgumentException} when
         * {@link android.app.job.JobInfo.Builder#build()} is called.
         * @param minLatencyMillis Milliseconds before which this job will not be considered for
         *                         execution.
         */
        public Builder setMinimumLatency(long minLatencyMillis) {
            mMinLatencyMillis = minLatencyMillis;
            mHasEarlyConstraint = true;
            return this;
        }

        /**
         * Set deadline which is the maximum scheduling latency. The job will be run by this
         * deadline even if other requirements are not met. Because it doesn't make sense setting
         * this property on a periodic job, doing so will throw an
         * {@link java.lang.IllegalArgumentException} when
         * {@link android.app.job.JobInfo.Builder#build()} is called.
         */
        public Builder setOverrideDeadline(long maxExecutionDelayMillis) {
            mMaxExecutionDelayMillis = maxExecutionDelayMillis;
            mHasLateConstraint = true;
            return this;
        }

        /**
         * Set up the back-off/retry policy.
         * This defaults to some respectable values: {5 seconds, Exponential}. We cap back-off at
         * 1hr.
         * Note that trying to set a backoff criteria for a job with
         * {@link #setRequiresDeviceIdle(boolean)} will throw an exception when you call build().
         * This is because back-off typically does not make sense for these types of jobs. See
         * {@link android.app.job.JobService#jobFinished(android.app.job.JobParameters, boolean)}
         * for more description of the return value for the case of a job executing while in idle
         * mode.
         * @param initialBackoffMillis Millisecond time interval to wait initially when job has
         *                             failed.
         * @param backoffPolicy is one of {@link BackoffPolicy}
         */
        public Builder setBackoffCriteria(long initialBackoffMillis, int backoffPolicy) {
            mBackoffPolicySet = true;
            mInitialBackoffMillis = initialBackoffMillis;
            mBackoffPolicy = backoffPolicy;
            return this;
        }

        /**
         * @return The job object to hand to the JobScheduler. This object is immutable.
         */
        public JobInfo build() {
            mExtras = new PersistableBundle(mExtras);  // Make our own copy.
            // Check that a deadline was not set on a periodic job.
            if (mIsPeriodic && (mMaxExecutionDelayMillis != 0L)) {
                throw new IllegalArgumentException("Can't call setOverrideDeadline() on a " +
                        "periodic job.");
            }
            if (mIsPeriodic && (mMinLatencyMillis != 0L)) {
                throw new IllegalArgumentException("Can't call setMinimumLatency() on a " +
                        "periodic job");
            }
            if (mBackoffPolicySet && mRequiresDeviceIdle) {
                throw new IllegalArgumentException("An idle mode job will not respect any" +
                        " back-off policy, so calling setBackoffCriteria with" +
                        " setRequiresDeviceIdle is an error.");
            }
            return new JobInfo(this);
        }
    }

}
