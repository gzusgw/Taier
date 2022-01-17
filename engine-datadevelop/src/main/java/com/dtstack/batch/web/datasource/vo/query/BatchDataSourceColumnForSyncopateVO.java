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

package com.dtstack.batch.web.datasource.vo.query;

import com.dtstack.engine.common.param.DtInsightAuthParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 返回切分键需要的列名
 *
 * @author Ruomu[ruomu@dtstack.com]
 * @Data 2021/1/12 17:19
 */
@Data
@ApiModel("数据同步-返回切分键需要的列名")
public class BatchDataSourceColumnForSyncopateVO extends DtInsightAuthParam {

    @ApiModelProperty(value = "数据源id", example = "1", required = true)
    private Long sourceId;

    @ApiModelProperty(value = "表名称", required = true)
    private String tableName;

    @ApiModelProperty(value = "查询的schema", example = "test")
    private String schema;

    @ApiModelProperty(value = "用户id", hidden = true)
    private Long userId;

}