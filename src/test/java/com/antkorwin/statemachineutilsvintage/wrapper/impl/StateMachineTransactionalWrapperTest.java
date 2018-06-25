package com.antkorwin.statemachineutilsvintage.wrapper.impl;


import com.antkorwin.statemachineutilsvintage.config.Events;
import com.antkorwin.statemachineutilsvintage.config.StateMachineConfig;
import com.antkorwin.statemachineutilsvintage.config.States;
import com.antkorwin.statemachineutilsvintage.wrapper.EnableStateMachineWrapper;
import com.antkorwin.statemachineutilsvintage.wrapper.StateMachineWrapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Created on 21.06.2018.
 *
 * @author Korovin Anatoliy
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
@Import(StateMachineConfig.class)
@EnableStateMachineWrapper
public class StateMachineTransactionalWrapperTest {

    @Autowired
    @Qualifier("stateMachineTransactionalWrapper")
    private StateMachineWrapper<States, Events> stateMachineTransactionalWrapper;

    @Autowired
    private StateMachineFactory<States, Events> stateMachineFactory;

    @Autowired
    private Config.TestService testService;

    @Before
    public void setUp() throws Exception {
        testService.clear();
    }

    @Test
    public void testWithOneOfTwoTransactionsIsFail() {
        // Arrange
        StateMachine<States, Events> stateMachine = stateMachineFactory.getStateMachine();
        Exception actualException = null;

        try {
            // Act
            stateMachineTransactionalWrapper.runWithRollback(stateMachine, machine -> {
                machine.sendEvent(Events.START_FEATURE);
                testService.ok();
                testService.fail();
            });
        } catch (Exception e) {
            actualException = e;
        }

        // Assert
        Assertions.assertThat(actualException.getMessage())
                  .contains("not-null property references a null or transient value");

        Assertions.assertThat(actualException)
                  .isInstanceOf(DataIntegrityViolationException.class);

        Assertions.assertThat(stateMachine.getState().getId())
                  .isEqualTo(States.BACKLOG);

        Assertions.assertThat(testService.size()).isEqualTo(0);
    }

    @Test
    public void testWithSuccessfulTransactionCommit() {
        // Arrange
        StateMachine<States, Events> stateMachine = stateMachineFactory.getStateMachine();

        // Act
        stateMachineTransactionalWrapper.runWithRollback(stateMachine, machine -> {
            machine.sendEvent(Events.START_FEATURE);
            testService.ok();
        });

        // Assert
        Assertions.assertThat(stateMachine.getState().getId())
                  .isEqualTo(States.IN_PROGRESS);

        Assertions.assertThat(testService.size()).isEqualTo(1);
    }


    @TestConfiguration
    @EnableJpaRepositories(considerNestedRepositories = true)
    @EntityScan("com.antkorwin.statemachineutilsvintage.wrapper.impl")
    public static class Config {

        @Repository
        public interface FooRepository extends JpaRepository<Foo, Long> {

        }

        @Service
        public class TestService {
            @Autowired
            private FooRepository fooRepository;

            void ok() {
                Foo foo = new Foo();
                foo.setField("123");
                fooRepository.save(foo);
            }

            void fail() {
                fooRepository.save(new Foo());
            }

            long size() {
                return fooRepository.count();
            }

            void clear(){
                fooRepository.deleteAll();
            }
        }

        @Entity
        @Setter
        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Foo {
            @Id
            @GeneratedValue
            private Long id;

            @Column(nullable = false)
            private String field;
        }
    }

}