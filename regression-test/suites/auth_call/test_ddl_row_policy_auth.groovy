// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import org.junit.Assert;

suite("test_ddl_row_policy_auth","p0,auth_call") {
    String user = 'test_ddl_row_policy_auth_user'
    String pwd = 'C123_567p'
    String dbName = 'test_ddl_row_policy_auth_db'
    String tableName = 'test_ddl_row_policy_auth_tb'
    String rowPolicyName = 'test_ddl_row_policy_auth_rp'

    try_sql("DROP USER ${user}")
    try_sql """drop database if exists ${dbName}"""
    try_sql """DROP ROW POLICY ${rowPolicyName} on ${dbName}.${tableName}"""
    sql """CREATE USER '${user}' IDENTIFIED BY '${pwd}'"""

    //cloud-mode
    if (isCloudMode()) {
        def clusters = sql " SHOW CLUSTERS; "
        assertTrue(!clusters.isEmpty())
        def validCluster = clusters[0][0]
        sql """GRANT USAGE_PRIV ON CLUSTER `${validCluster}` TO ${user}""";
    }

    sql """grant select_priv on regression_test to ${user}"""
    sql """create database ${dbName}"""
    sql """create table ${dbName}.${tableName} (
                id BIGINT,
                username VARCHAR(20)
            )
            DISTRIBUTED BY HASH(id) BUCKETS 2
            PROPERTIES (
                "replication_num" = "1"
            );"""

    // ddl create
    connect(user, "${pwd}", context.config.jdbcUrl) {
        test {
            sql """CREATE ROW POLICY ${rowPolicyName} ON ${dbName}.${tableName} AS RESTRICTIVE TO ${user} USING (id = 1);"""
            exception "denied"
        }
        test {
            sql """SHOW ROW POLICY FOR ${user}"""
            exception "denied"
        }
        test {
            sql """DROP ROW POLICY ${rowPolicyName} on ${dbName}.${tableName} for ${user}"""
            exception "denied"
        }

    }
    sql """grant grant_priv on *.*.* to ${user}"""
    connect(user, "${pwd}", context.config.jdbcUrl) {
        sql """CREATE ROW POLICY ${rowPolicyName} ON ${dbName}.${tableName} AS RESTRICTIVE TO ${user} USING (id = 1);"""

        test {
            sql """SHOW ROW POLICY FOR ${user}"""
            exception "denied"
        }

        sql """DROP ROW POLICY ${rowPolicyName} on ${dbName}.${tableName} for ${user}"""
    }
    sql """grant admin_priv on *.*.* to ${user}"""
    connect(user, "${pwd}", context.config.jdbcUrl) {
        sql """CREATE ROW POLICY ${rowPolicyName} ON ${dbName}.${tableName} AS RESTRICTIVE TO ${user} USING (id = 1);"""
        def res = sql """SHOW ROW POLICY FOR ${user}"""
        assertTrue(res.size() == 1)

        sql """DROP ROW POLICY ${rowPolicyName} on ${dbName}.${tableName} for ${user}"""
        res = sql """SHOW ROW POLICY FOR ${user}"""
        assertTrue(res.size() == 0)
    }

    sql """drop database if exists ${dbName}"""
    try_sql("DROP USER ${user}")
}
