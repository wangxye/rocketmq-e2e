/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.frame;

import apache.rocketmq.controller.v1.CreateGroupRequest;
import apache.rocketmq.controller.v1.CreateTopicRequest;
import apache.rocketmq.controller.v1.GroupType;
import apache.rocketmq.controller.v1.MessageType;
import apache.rocketmq.controller.v1.AcceptTypes;
import apache.rocketmq.controller.v1.SubscriptionMode;
import com.automq.rocketmq.controller.client.GrpcControllerClient;
import com.automq.rocketmq.cli.CliClientConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.util.MQAdmin;
import org.apache.rocketmq.util.RandomUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseOperate extends ResourceInit {

    private static final Logger log = LoggerFactory.getLogger(BaseOperate.class);
    protected static ClientServiceProvider provider = ClientServiceProvider.loadService();

    protected static RPCHook rpcHook;

    protected static GrpcControllerClient client;

    static {
        if (aclEnable) {
            log.info("acl enable");
//            rpcHook = AclClient.getAclRPCHook(ak, sk);
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                MQAdmin.mqAdminExt.shutdown();
                log.info("Shutdown Hook is running !");
            }
        });
        client = new GrpcControllerClient(new CliClientConfig());
    }

    //    //
//    protected static Boolean fetchTopic(String instanceId, String messageType, String topic, Boolean needClean) {
//        log.info("[Topic] instanceId:{}, messageType:{}, topicName:{}", instanceId, messageType, topic);
//
//        boolean useExist = true;
//        if (topicDTO != null && topicDTO.getData() == null) {
//            log.info(String.format("start create topic: %s ", topic));
//            if (needClean) {
//                topicWrapper.createTopic(primaryAccount1, account1InstanceId, topic, messageType, "auto-test", true);
//            } else {
//                topicWrapper.createTopicEx(primaryAccount1, account1InstanceId, topic, messageType, "auto-test", true);
//            }
//            topicWrapper.waitTopicExist(primaryAccount1, account1InstanceId, topic);
//            TestUtils.waitForSeconds(42);
//            useExist = false;
//        } else {
//            log.info(String.format("topic %s has already been created", topic));
//        }
//        return useExist;
//    }
//
    protected static String getTopic(String messageType, String methodName) {
        String topic = String.format("topic_%s_%s_%s", messageType, methodName, RandomUtils.getStringWithCharacter(6));
        log.info("[Topic] topic:{}, messageType:{}, methodName:{}", topic, messageType, methodName);
        try {
            CreateTopicRequest request = CreateTopicRequest.newBuilder()
                .setTopic(topic)
                .setCount(8)
                .setAcceptTypes(convertAcceptTypes(messageType))
                .build();
            Long topicId = client.createTopic(endPoint, request).join();
            log.info("create topic: {} , topicId:{}", topic, topicId);
            return topic;
        } catch (Exception e) {
            log.error("create topic error", e);
        }
        return null;
    }

    private static AcceptTypes convertAcceptTypes(String typeStr) {
        switch (typeStr) {
            case "NORMAL":
                return AcceptTypes.newBuilder().addTypes(MessageType.NORMAL).build();
            case "FIFO":
                return AcceptTypes.newBuilder().addTypes(MessageType.FIFO).build();
            case "DELAY":
                return AcceptTypes.newBuilder().addTypes(MessageType.DELAY).build();
            case "TRANSACTION":
                return AcceptTypes.newBuilder().addTypes(MessageType.TRANSACTION).build();
            default:
                return AcceptTypes.newBuilder().addTypes(MessageType.MESSAGE_TYPE_UNSPECIFIED).build();
        }
    }

    protected static String resetOffsetByTimestamp(String consumerGroup, String topic, long timestamp) {
        Boolean result = MQAdmin.resetOffsetByTimestamp(consumerGroup, topic, timestamp);
        if (result) {
            log.info(String.format("Reset offset success, consumerGroup:%s, topic:%s, timestamp:%s", consumerGroup, topic, timestamp));
        } else {
            Assertions.fail(String.format("Reset offset failed, consumerGroup:%s, topic:%s, timestamp:%s", consumerGroup, topic, timestamp));
        }
        return topic;
    }

    //    /**
//     * will delete topic
//     *
//     * @param instanceId
//     * @param messageType
//     * @param topic
//     * @return
//     */
//    protected static void getTopicRandom(String instanceId, String messageType, String topic) {
//        log.info("[Topic] instanceId:{}, messageType:{}, topic:{}", instanceId, messageType, topic);
//        GetTopicResponseBody topicDTO = topicWrapper.getTopic(primaryAccount1, instanceId, topic, true);
//        if (topicDTO.getData() == null) {
//            log.info(String.format("start create topic: %s ", topic));
//            topicWrapper.createTopic(primaryAccount1, account1InstanceId, topic, messageType, "auto-test", true);
//            topicWrapper.waitTopicExist(primaryAccount1, account1InstanceId, topic);
//        } else {
//            log.info(String.format("topic %s has already been created", topic));
//        }
//    }
//
    //The synchronization consumption retry policy is DefaultRetryPolicy
    protected static String getGroupId(String methodName) {
        return getGroupId(methodName, SubscriptionMode.SUB_MODE_POP);
    }

    protected static String getGroupId(String methodName, SubscriptionMode mode) {
        String groupId = String.format("GID_%s_%s", methodName, RandomUtils.getStringWithCharacter(6));
        // prepare consumer group
        CreateGroupRequest request = CreateGroupRequest.newBuilder()
            .setName(groupId)
            .setMaxDeliveryAttempt(16)
            .setGroupType(GroupType.GROUP_TYPE_STANDARD)
            .setSubMode(mode)
            .build();
        Long reply = createConsumerGroup(request).join();
        log.info("[ConsumerGroupId] groupId:{} , methodName:{} , mode: {} , reply:{}", groupId, methodName, mode, reply);
        return groupId;
    }

    //The sequential consumption retry policy is FixedRetryPolicy
    protected static String getOrderlyGroupId(String methodName) {
        String groupId = String.format("GID_%s_%s", methodName, RandomUtils.getStringWithCharacter(6));
        CreateGroupRequest request = CreateGroupRequest.newBuilder()
            .setName(groupId)
            .setMaxDeliveryAttempt(16)
            .setGroupType(GroupType.GROUP_TYPE_FIFO)
            .setSubMode(SubscriptionMode.SUB_MODE_POP)
            .build();
        Long reply = createConsumerGroup(request).join();
        log.info("[ConsumerGroupId] groupId:{} methodName:{} reply:{}", groupId, methodName, reply);
        return groupId;
    }

    protected static String getOrderlyGroupId(String methodName, SubscriptionMode mode) {
        String groupId = String.format("GID_%s_%s", methodName, RandomUtils.getStringWithCharacter(6));
        CreateGroupRequest request = CreateGroupRequest.newBuilder()
            .setName(groupId)
            .setMaxDeliveryAttempt(16)
            .setGroupType(GroupType.GROUP_TYPE_FIFO)
            .setSubMode(mode)
            .build();
        Long reply = createConsumerGroup(request).join();
        log.info("[ConsumerGroupId] groupId:{} methodName:{} reply:{}", groupId, methodName, reply);
        return groupId;
    }

    private static CompletableFuture<Long> createConsumerGroup(CreateGroupRequest request) {
        try {
            CompletableFuture<Long> groupCf = client.createGroup(account.getEndpoint(), request);
            return groupCf.exceptionally(throwable -> {
                log.error("Create group failed", throwable);
                throw new CompletionException(throwable);
            });
        } catch (Exception e) {
            log.error("Create group failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }

}
