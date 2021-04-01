package com.bracso.test.activemq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.JmsException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;

@SpringBootTest
class ActivemqQueuebrowserApplicationTests {

    @Autowired
    private JmsManagerEndpoint jmsManagerEndpoint;

    @Autowired
    private Map<String, ConnectionFactory> beansConnectionFactories;

    @Test
    public void testJmsEndpointConnectionFactory() {
        beansConnectionFactories.forEach((cfName, cf) -> this.testConnectionFactory(cfName));
    }

    private void testConnectionFactory(String cfName) {
        try {
            final String testQueueName = "testqueue";
            assertThat(this.jmsManagerEndpoint.listPendingMessages(cfName, testQueueName).getStatusCode(),
                    is(equalTo(HttpStatus.NOT_FOUND)));
            Map<String, Object> msgHeaders = new HashMap<>();
            msgHeaders.put("X-amiga-jms-header1", "value1");
            ResponseEntity<String> response = this.jmsManagerEndpoint.sendMessageToQueue(cfName, testQueueName,
                    "messageToQueue", msgHeaders);
            assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
            String msgId = response.getBody();
            assertThat(msgId, is(not(emptyString())));
            ResponseEntity<List<String>> msgResponse = this.jmsManagerEndpoint.listPendingMessages(cfName,
                    testQueueName);
            assertThat(msgResponse.getStatusCode(), is(equalTo(HttpStatus.OK)));
            List<String> msgBody = msgResponse.getBody();
            assertThat(msgBody.size(), is(1));
        } catch (JMSException ex) {
            throw new JmsException(ex) {
            };
        }
    }

}
