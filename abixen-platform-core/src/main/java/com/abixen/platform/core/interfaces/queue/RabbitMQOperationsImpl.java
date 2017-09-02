/**
 * Copyright (c) 2010-present Abixen Systems. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.abixen.platform.core.interfaces.queue;

import com.abixen.platform.common.interfaces.queue.message.QueueMessage;
import com.abixen.platform.core.application.service.QueueOperations;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static com.abixen.platform.common.infrastructure.util.PlatformProfiles.DOCKER;
import static com.abixen.platform.common.infrastructure.util.PlatformProfiles.DEV;


@Profile({DEV, DOCKER})
@Service
public class RabbitMQOperationsImpl implements QueueOperations {

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public RabbitMQOperationsImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void convertAndSend(String routingKey, QueueMessage rabbitMQMessage) throws AmqpException {
        rabbitTemplate.convertAndSend(routingKey, rabbitMQMessage);
    }
}
