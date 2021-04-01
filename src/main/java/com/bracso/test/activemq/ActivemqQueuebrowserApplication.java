package com.bracso.test.activemq;

import javax.jms.ConnectionFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jta.atomikos.AtomikosConnectionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;

import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;

@SpringBootApplication
public class ActivemqQueuebrowserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivemqQueuebrowserApplication.class, args);
    }

    @Bean
    public ConnectionFactory activeMQConnectionFactory() {
        ActiveMQXAConnectionFactory activemqcf = new ActiveMQXAConnectionFactory("vm://testCF?broker.persistent=false&broker.useShutdownHook=false&broker.useJmx=false");
        AtomikosConnectionFactoryBean cf = new AtomikosConnectionFactoryBean();
        cf.setUniqueResourceName("activemqcf");
        cf.setXaConnectionFactory(activemqcf);
        cf.setPoolSize(5);
        return cf;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory cf) throws Exception {
        JmsTemplate jmsTemplate = new JmsTemplate(cf);
        jmsTemplate.setDefaultDestination((new ActiveMQQueue("testqueue")));
        jmsTemplate.setPubSubDomain(false);
        return jmsTemplate;
    }

}
