package com.bracso.test.activemq;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;

import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.destination.DynamicDestinationResolver;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@RestController("jmsmanager")
public class JmsManagerEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(JmsManagerEndpoint.class);

    private static final DynamicDestinationResolver destinationResolver = new DynamicDestinationResolver();

    private final Map<String, ConnectionFactory> connectionFactories;

    private final JmsTemplate jmsTemplate;

    public JmsManagerEndpoint(
            final Map<String, ConnectionFactory> connectionFactories,
            final JmsTemplate jmsTemplate) {
        this.connectionFactories = connectionFactories;
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * List destination buildQueueInfo a connection factory
     *
     * @param cfName connection factory name
     * @param queueName name of the queue to retrieve the messages
     * @return List with information about destinations buildQueueInfo provided connection factory
     * @throws JMSException if any error retrieving the messages
     */
    @GetMapping("connectionfactories/{cfName:.+}/destinations/queues/{queueName:.+}/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> listPendingMessages(
            @Selector @PathVariable("cfName") final String cfName,
            @Selector @PathVariable("queueName") final String queueName) throws JMSException {

        final ConnectionFactory cf = this.connectionFactories.get(cfName);
        if (cf == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final List<String> result = new ArrayList<>();

        browseMessage(cf, queueName, null)
                .forEach(msg -> {
                    try {
                        result.add(msg.getJMSMessageID());
                    } catch (final JMSException ex) {
                        LOG.warn("Error adding message", ex);
                    }
                });

        if (result.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(result, HttpStatus.OK);
        }

    }

    /**
     * Send a message to a queue
     *
     * @param cfName connection factory name
     * @param queueName queue name
     * @param payload
     * @param headers
     * @return OK if message is delivered.
     */
    @PostMapping("connectionfactories/{cfName:.+}/destinations/queues/{queueName:.+}/messages")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> sendMessageToQueue(
            @Selector @PathVariable("cfName") final String cfName,
            @Selector @PathVariable("queueName") final String queueName,
            @RequestBody final String payload,
            @RequestHeader final Map<String, Object> headers) {
        final ConnectionFactory cf = this.connectionFactories.get(cfName);
        if (cf == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        final String msgId;
        msgId = convertAndSend(this.jmsTemplate, payload, headers);
        return new ResponseEntity<>(msgId, HttpStatus.OK);
    }


    private static List<Message> browseMessage(final ConnectionFactory cf, final String destinationName,
            final String messageSelector) throws JMSException {
        try (final Connection connection = cf.createConnection()) {
            connection.start();
            try (final Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {

                final Destination destination = destinationResolver.resolveDestinationName(session, destinationName, false);
                if (destination instanceof Queue) {
                    try (final QueueBrowser queueBrowser = session.createBrowser((Queue) destination, messageSelector)) {
                        final Enumeration<Message> messageEnumeration = queueBrowser.getEnumeration();
                        List<Message> messages = new ArrayList<>();
                        while (messageEnumeration.hasMoreElements()) {
                            Message msg = messageEnumeration.nextElement(); 
                            if (msg == null) {
                                //FIXME With activemq 5.15.X, msg is never null, but with activemq 5.16.X, we have null message due to an ignored exception on mesageEnumeration
                                throw new IllegalStateException("Why this msg is null?");
                            }
                            messages.add(msg);
                        }
                        return messages;
                    }
                } else {
                    LOG.warn("Only queues can be browsed");
                    return Collections.emptyList();
                }
            }
        }
    }

    /**
     * ConvertAnd send a message using provided jmsClient, settings properties to message
     *
     * @param jmsTemplate jms client to used to send the message
     * @param messageContent content of message
     * @param properties headers of message
     * @return The message ID assigned to new Message
     */
    private static String convertAndSend(final JmsTemplate jmsTemplate, final String messageContent,
            final Map<String, Object> properties) {
        final AtomicReference<Message> msg = new AtomicReference<>();
        jmsTemplate.convertAndSend(messageContent, (final Message message) -> {
            properties.forEach((key, value) -> {
                try {
                    message.setObjectProperty(key, value);
                } catch (final JMSException ex) {
                    throw new MessageConversionException(MessageFormat.format("Failed setting property {0}", key), ex);
                }
            });
            msg.set(message);
            return message;
        });
        try {
            return msg.get().getJMSMessageID();
        } catch (final JMSException ex) {
            throw new UncategorizedJmsException("Error obtaining jms message id", ex);
        }
    }

}
