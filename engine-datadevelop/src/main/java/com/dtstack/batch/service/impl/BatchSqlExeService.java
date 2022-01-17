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


package com.dtstack.batch.service.impl;

import com.dtstack.batch.bo.ExecuteContent;
import com.dtstack.batch.common.enums.ETableType;
import com.dtstack.batch.domain.TenantEngine;
import com.dtstack.batch.engine.rdbms.common.util.SqlFormatUtil;
import com.dtstack.batch.service.table.ISqlExeService;
import com.dtstack.batch.service.task.impl.BatchTaskService;
import com.dtstack.batch.sql.ParseResult;
import com.dtstack.batch.sql.SqlParserImpl;
import com.dtstack.batch.sql.parse.SqlParserFactory;
import com.dtstack.batch.vo.CheckSyntaxResult;
import com.dtstack.batch.vo.ExecuteResultVO;
import com.dtstack.batch.vo.ExecuteSqlParseVO;
import com.dtstack.engine.common.annotation.Forbidden;
import com.dtstack.engine.common.enums.EJobType;
import com.dtstack.engine.common.enums.MultiEngineType;
import com.dtstack.engine.common.exception.RdosDefineException;
import com.dtstack.engine.common.util.PublicUtil;
import com.dtstack.engine.domain.BatchTask;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author jiangbo
 * @date 2019/6/14
 */
@Service
public class BatchSqlExeService {

    public static Logger LOG = LoggerFactory.getLogger(BatchSqlExeService.class);

    @Autowired
    private TenantEngineService tenantEngineService;

    @Autowired
    private MultiEngineServiceFactory multiEngineServiceFactory;

    @Autowired
    private BatchFunctionService batchFunctionService;

    private SqlParserFactory parserFactory = SqlParserFactory.getInstance();

    @Autowired
    private BatchTaskService batchTaskService;

    private static final String SHOW_LIFECYCLE = "%s表的生命周期为%s天";

    private static final String NOT_GET_COLUMN_SQL_REGEX = "(?i)(create|drop)[\\s|\\S]*";

    private static final String CREATE_AS_REGEX = "(?i)create\\s+((table)|(view))\\s*[\\s\\S]+\\s*as\\s+select\\s+[\\s\\S]+";

    public static final Pattern CACHE_LAZY_SQL_PATTEN = Pattern.compile("(?i)cache\\s+(lazy\\s+)?table.*");

    private static final String CREATE_TEMP_FUNCTION_SQL = "%s %s";

    private static final Set<Integer> notDataMapOpera = new HashSet<>();

    static {
        notDataMapOpera.add(EJobType.ORACLE_SQL.getVal());
        notDataMapOpera.add(EJobType.GaussDB_SQL.getVal());
        notDataMapOpera.add(EJobType.GREENPLUM_SQL.getVal());
        notDataMapOpera.add(EJobType.INCEPTOR_SQL.getVal());
    }

    private String getDbName(final ExecuteContent executeContent) {
        if (StringUtils.isNotBlank(executeContent.getDatabase())) {
            return executeContent.getDatabase();
        }
        final TenantEngine tenantEngine = this.tenantEngineService.getByTenantAndEngineType(executeContent.getTenantId(), executeContent.getEngineType());
        if (tenantEngine == null) {
            throw new RdosDefineException("引擎不能为空");
        }

        String dbName = tenantEngine.getEngineIdentity();
        executeContent.setDatabase(dbName);
        return dbName;
    }


    /**
     * 执行SQL
     */
    @Forbidden
    public ExecuteResultVO executeSql(final ExecuteContent executeContent) throws Exception {
        final ExecuteResultVO result = new ExecuteResultVO();
        this.prepareExecuteContent(executeContent);
        // 前置操作
        result.setSqlText(executeContent.getSql());

        final ISqlExeService sqlExeService = this.multiEngineServiceFactory.getSqlExeService(executeContent.getEngineType(), executeContent.getDetailType(), executeContent.getTenantId());
        final ExecuteResultVO engineExecuteResult = sqlExeService.executeSql(executeContent);
        if (!engineExecuteResult.getIsContinue()) {
            return engineExecuteResult;
        }
        PublicUtil.copyPropertiesIgnoreNull(engineExecuteResult, result);

        return result;
    }

    /**
     * 解析sqlList返回sqlId并且封装sql到引擎执行
     *
     * @param executeContent
     * @return
     * @throws Exception
     */
    public ExecuteSqlParseVO batchExeSqlParse(final ExecuteContent executeContent) throws Exception {

        final ISqlExeService sqlExeService = this.multiEngineServiceFactory.getSqlExeService(executeContent.getEngineType(), executeContent.getDetailType(), executeContent.getTenantId());
        ExecuteSqlParseVO executeSqlParseVO = sqlExeService.batchExecuteSql(executeContent);

        return executeSqlParseVO;
    }

    /**
     * 处理自定义函数 和 构建真正运行的SQL
     *
     * @param tenantId
     * @param taskType
     * @param sqlText
     * @param engineType
     * @return
     */
    public CheckSyntaxResult processSqlText(final Long tenantId, Integer taskType, final String sqlText, final Integer engineType) {
        CheckSyntaxResult result = new CheckSyntaxResult();
        TenantEngine tenantEngine = this.tenantEngineService.getByTenantAndEngineType(tenantId, engineType);
        Preconditions.checkNotNull(tenantEngine, String.format("tenantEngine %d not support engine type %d", tenantId, engineType));

        final ISqlExeService sqlExeService = this.multiEngineServiceFactory.getSqlExeService(engineType, taskType, tenantId);
        // 处理自定义函数
        String sqlPlus = buildCustomFunctionSparkSql(sqlText, tenantId, taskType);

        // 构建真正运行的SQL，去掉注释，加上use db 同时格式化SQL
        String sqls = sqlExeService.process(sqlPlus, tenantEngine.getEngineIdentity());
        result.setSql(sqls);

        result.setCheckResult(true);
        return result;
    }


    /**
     * 清除sql中的注释
     *
     * @param sql
     * @return
     */
    public String removeComment(final String sql) {
        final StringBuilder stringBuilder = new StringBuilder();
        final String[] split = sql.split("\n");
        for (String s : split) {
            if (StringUtils.isNotBlank(s)) {
                //注释开头
                if (!s.trim().startsWith("--")) {
                    s = removeCommentByQuotes(s);
                    stringBuilder.append(" ").append(s);
                }
            }
        }
        //去除/**/跨行的情况
        return removeMoreCommentByQuotes(stringBuilder.toString());
    }

    /**
     * 去除 --注释  避免了" '的影响
     * @param sql
     * @return
     */
    private String removeCommentByQuotes(String sql) {
        StringBuffer buffer = new StringBuffer();
        Character quote = null;
        //用于标示是否进入了"之内 如果进入了就忽略 --
        Boolean flag = false;
        char[] sqlindex = sql.toCharArray();
        for (int i = 0; sql.length() > i; i++) {
            if (!flag) {
                //如果符合条件 就标示进入了引号之内 记录是单引号 还是双引号
                if (sqlindex[i] == '\"' || sqlindex[i] == '\'') {
                    quote = sqlindex[i];
                    flag = true;
                } else {
                    if (sqlindex[i] == '-' && i + 1 < sql.length() && sqlindex[i + 1] == '-') {
                        break;
                    }
                }
                buffer.append(sqlindex[i]);
            } else {
                //再次发现记录的值 说明出了 引号之内
                if (sqlindex[i] == quote) {
                    quote = null;
                    flag = false;
                }
                buffer.append(sqlindex[i]);
            }
        }
        return buffer.toString();
    }

    /**
     * 专门删除 多行注释的方法 **这种的
     *
     * @param osql
     * @return
     */
    private String removeMoreCommentByQuotes(String osql) {
        StringBuffer buffer = new StringBuffer();

        Boolean flag = false;
        Boolean dhzs = false;
        char[] sqlindex = osql.toCharArray();
        for (int i = 0; osql.length() > i; ++i) {
            if (!flag) {
                if (!dhzs) {
                    if (sqlindex[i] != '"' && sqlindex[i] != '\'') {
                        if (sqlindex[i] == '/' && i + 1 < osql.length() && sqlindex[i + 1] == '*') {
                            i++;
                            dhzs = true;
                            continue;
                        }
                    } else {
                        flag = true;
                    }
                    buffer.append(sqlindex[i]);
                } else {
                    if (sqlindex[i] == '*' && i + 1 < osql.length() && sqlindex[i + 1] == '/') {
                        i++;
                        dhzs = false;
                        continue;
                    }
                }
            } else {
                if (sqlindex[i] == '"' || sqlindex[i] == '\'') {
                    flag = false;
                }

                buffer.append(sqlindex[i]);
            }
        }
        return buffer.toString();
    }

    /**
     * 处理返回结果
     *
     * @param result
     */
    public void dealResultDoubleList(List<List<Object>> result) {
        if (CollectionUtils.isEmpty(result)) {
            return;
        }

        for (List<Object> objects : result) {
            if (CollectionUtils.isEmpty(objects)) {
                continue;
            }

            for (int i = 0; i < objects.size(); i++) {
                if (objects.get(i) == null) {
                    continue;
                }

                if (objects.get(i) instanceof Double
                        || objects.get(i) instanceof Float
                        || objects.get(i) instanceof BigDecimal
                        || objects.get(i) instanceof Number) {
                    BigDecimal decimal = new BigDecimal(0);
                    try {
                        decimal = new BigDecimal(objects.get(i).toString());
                        objects.set(i, decimal.toPlainString());
                    } catch (Exception e) {
                        objects.set(i, "NaN");
                    }
                    continue;
                }else if (objects.get(i) instanceof Timestamp){
                    objects.set(i, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(objects.get(i)));
                }
            }
        }
    }


    /**
     * 进行SQL解析
     * @param executeContent
     */
    private void prepareExecuteContent(final ExecuteContent executeContent) {
        final Integer engineType = executeContent.getEngineType();
        BatchTask one = batchTaskService.getOne(executeContent.getRelationId());
        String taskParam = one.getTaskParams();

        final ISqlExeService sqlExeService = this.multiEngineServiceFactory.getSqlExeService(engineType, executeContent.getDetailType(), executeContent.getTenantId());
        final String sql = executeContent.getSql();
        //TODO cache lazy table 暂时不解析血缘，不知道这种类型的sql如何处理
        if (StringUtils.isNotBlank(sql) && (sql.toLowerCase().trim().startsWith("set") || CACHE_LAZY_SQL_PATTEN.matcher(sql).matches())) {
            //set sql 不解析
            final ParseResult parseResult = new ParseResult();
            parseResult.setParseSuccess(true);
            parseResult.setOriginSql(executeContent.getSql());
            parseResult.setStandardSql(executeContent.getSql());
            executeContent.setParseResult(parseResult);
            return;
        }

        //单条sql解析
        if (StringUtils.isNotBlank(executeContent.getSql())) {
            final ParseResult parseResult = this.parseSql(executeContent);
            executeContent.setParseResult(parseResult);

            //校验语法
            if (executeContent.isCheckSyntax()) {
                sqlExeService.checkSingleSqlSyntax(executeContent.getTenantId(), executeContent.getSql(), executeContent.getDatabase(), taskParam);
            }
        }


        //批量解析sql
        List<ParseResult> parseResultList = Lists.newLinkedList();

        if (CollectionUtils.isNotEmpty(executeContent.getSqlList())) {
            String finalTaskParam = taskParam;
            executeContent.getSqlList().forEach(x -> {
                if (!x.trim().startsWith("set")) {
                    if (executeContent.isCheckSyntax()) {
                        executeContent.setSql(x);
                        final ParseResult batchParseResult = this.parseSql(executeContent);
                        sqlExeService.checkSingleSqlSyntax(executeContent.getTenantId(), x, executeContent.getDatabase(), finalTaskParam);
                        parseResultList.add(batchParseResult);
                    }
                } else {
                    //set sql 不解析
                    final ParseResult batchParseResult = new ParseResult();
                    batchParseResult.setParseSuccess(true);
                    batchParseResult.setOriginSql(x);
                    batchParseResult.setStandardSql(x);
                    parseResultList.add(batchParseResult);
                }
            });
            executeContent.setParseResultList(parseResultList);
        }
    }


    /**
     * 解析sql
     *
     * @param executeContent
     * @return
     */
    private ParseResult parseSql(final ExecuteContent executeContent) {
        final String dbName = this.getDbName(executeContent);
        executeContent.setDatabase(dbName);
        TenantEngine tenantEngine = tenantEngineService.getByTenantAndEngineType(executeContent.getTenantId(), MultiEngineType.HADOOP.getType());
        Preconditions.checkNotNull(tenantEngine, String.format("tenantEngine %d not support hadoop engine.", executeContent.getTenantId()));

        SqlParserImpl sqlParser = parserFactory.getSqlParser(ETableType.HIVE);
        ParseResult parseResult = null;
        try {
            parseResult = sqlParser.parseSql(executeContent.getSql(), tenantEngine.getEngineIdentity(), new HashMap<>());
        } catch (final Exception e) {
            BatchSqlExeService.LOG.error("解析sql异常:{}", e);
            parseResult = new ParseResult();
            //libra解析失败也提交sql执行
            if (MultiEngineType.HADOOP.getType() == executeContent.getEngineType()) {
                parseResult.setParseSuccess(false);
            }
            parseResult.setFailedMsg(ExceptionUtils.getStackTrace(e));
            parseResult.setStandardSql(SqlFormatUtil.getStandardSql(executeContent.getSql()));
        }
        return parseResult;
    }

    /**
     * 处理spark sql自定义函数
     * @param sqlText
     * @param tenantId
     * @param taskType
     * @return
     */
    public String buildCustomFunctionSparkSql(String sqlText, Long tenantId, Integer taskType) {
        String sqlPlus = SqlFormatUtil.formatSql(sqlText);
        if (EJobType.SPARK_SQL.getType().equals(taskType)) {
            String containFunction = batchFunctionService.buildContainFunction(sqlText, tenantId);
            if (StringUtils.isNotBlank(containFunction)) {
                sqlPlus = String.format(CREATE_TEMP_FUNCTION_SQL,containFunction,sqlPlus);
            }
        }
        return sqlPlus;
    }

}