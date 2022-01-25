/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.taiga.pluginapi.client;

import com.dtstack.taiga.pluginapi.JobClient;
import com.dtstack.taiga.pluginapi.JobIdentifier;
import com.dtstack.taiga.pluginapi.enums.EJobType;
import com.dtstack.taiga.pluginapi.enums.RdosTaskStatus;
import com.dtstack.taiga.pluginapi.pojo.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Reason:
 * Date: 2017/2/21
 * Company: www.dtstack.com
 *
 * @author xuchao
 */

public abstract class AbstractClient implements IClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractClient.class);

    public AbstractClient() {
    }

    @Override
    public JobResult submitJob(JobClient jobClient) {

        JobResult jobResult;
        try {
            beforeSubmitFunc(jobClient);
            jobResult = processSubmitJobWithType(jobClient);
            if (jobResult == null) {
                jobResult = JobResult.createErrorResult("not support job type of " + jobClient.getJobType() + "," +
                        " you need to set it in(" + StringUtils.join(EJobType.values(), ",") + ")");
            }
        } catch (Exception e) {
            LOGGER.error("", e);
            jobResult = JobResult.createErrorResult(e);
        } finally {
            afterSubmitFunc(jobClient);
        }

        return jobResult;
    }

    @Override
    public RdosTaskStatus getJobStatus(JobIdentifier jobIdentifier) throws IOException {
        RdosTaskStatus status = RdosTaskStatus.NOTFOUND;
        try {
            status = processJobStatus(jobIdentifier);
        }catch (Exception e) {
            LOGGER.error("get job status error: {}", e.getMessage());
        } finally {
            handleJobStatus(jobIdentifier, status);
        }
        return status;
    }

    protected RdosTaskStatus processJobStatus(JobIdentifier jobIdentifier) {
        return RdosTaskStatus.NOTFOUND;
    }

    protected void handleJobStatus(JobIdentifier jobIdentifier, RdosTaskStatus status) {
    }

    @Override
    public List<String> getRollingLogBaseInfo(JobIdentifier jobIdentifier) {
        return null;
    }

    /**
     * job 处理具体实现的抽象
     *
     * @param jobClient 对象参数
     * @return 处理结果
     */
    protected abstract JobResult processSubmitJobWithType(JobClient jobClient);

    @Override
    public String getJobLog(JobIdentifier jobId) {
        return "";
    }

    @Override
    public JudgeResult judgeSlots(JobClient jobClient) {
        return JudgeResult.notOk( "");
    }

    protected void beforeSubmitFunc(JobClient jobClient) {
    }

    protected void afterSubmitFunc(JobClient jobClient) {
    }

    @Override
    public String getMessageByHttp(String path) {
        return null;
    }


    @Override
    public String getCheckpoints(JobIdentifier jobIdentifier) {
        return null;
    }


    @Override
    public ComponentTestResult testConnect(String pluginInfo) {
        return null;
    }

    @Override
    public List<List<Object>> executeQuery(String sql, String database) {
        return null;
    }

    @Override
    public String uploadStringToHdfs(String bytes, String hdfsPath) {
        return null;
    }

    @Override
    public ClusterResource getClusterResource() {
        return null;
    }

    @Override
    public CheckResult grammarCheck(JobClient jobClient){
        return null;
    }

}