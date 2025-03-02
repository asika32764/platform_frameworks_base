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

import java.util.List;

import android.content.Context;

/**
 * Class for scheduling various types of jobs with the scheduling framework on the device.
 *
 * <p>You do not
 * instantiate this class directly; instead, retrieve it through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.JOB_SCHEDULER_SERVICE)}.
 */
public abstract class JobScheduler {
    /**
     * Returned from {@link #schedule(JobInfo)} when an invalid parameter was supplied. This can occur
     * if the run-time for your job is too short, or perhaps the system can't resolve the
     * requisite {@link JobService} in your package.
     */
    public static final int RESULT_FAILURE = 0;
    /**
     * Returned from {@link #schedule(JobInfo)} if this application has made too many requests for
     * work over too short a time.
     */
    // TODO: Determine if this is necessary.
    public static final int RESULT_SUCCESS = 1;

    /**
     * @param job The job you wish scheduled. See
     * {@link android.app.job.JobInfo.Builder JobInfo.Builder} for more detail on the sorts of jobs
     * you can schedule.
     * @return If >0, this int returns the jobId of the successfully scheduled job.
     * Otherwise you have to compare the return value to the error codes defined in this class.
     */
    public abstract int schedule(JobInfo job);

    /**
     * Cancel a job that is pending in the JobScheduler.
     * @param jobId unique identifier for this job. Obtain this value from the jobs returned by
     * {@link #getAllPendingJobs()}.
     * @return
     */
    public abstract void cancel(int jobId);

    /**
     * Cancel all jobs that have been registered with the JobScheduler by this package.
     */
    public abstract void cancelAll();

    /**
     * @return a list of all the jobs registered by this package that have not yet been executed.
     */
    public abstract List<JobInfo> getAllPendingJobs();

}
