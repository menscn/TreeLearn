package com.treelearn.app.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;

import org.junit.Test;

/**
 * T-008 plain-JVM evidence for the concurrency and ordering rule of technical design 7.
 *
 * <p>Nothing here needs an emulator: the scheduling decision is a pure function of the commands
 * admitted so far, so the two-slot cap and the FIFO order are pinned without a clock, a thread or a
 * database.
 */
public final class GenerationSchedulerTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String BRANCH_ONE = "11111111-1111-4111-8111-111111111111";
    private static final String BRANCH_TWO = "22222222-2222-4222-8222-222222222222";
    private static final String BRANCH_THREE = "33333333-3333-4333-8333-333333333333";
    private static final String BRANCH_FOUR = "44444444-4444-4444-8444-444444444444";
    private static final String MESSAGE_ONE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String MESSAGE_TWO = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @Test
    public void twoAttemptsRunAndEveryLaterOneWaitsInArrivalOrder() {
        GenerationScheduler scheduler = new GenerationScheduler();
        GenerationCommand first = command(BRANCH_ONE, MESSAGE_ONE);
        GenerationCommand second = command(BRANCH_TWO, MESSAGE_ONE);
        GenerationCommand third = command(BRANCH_THREE, MESSAGE_ONE);
        GenerationCommand fourth = command(BRANCH_FOUR, MESSAGE_ONE);

        assertEquals(GenerationScheduler.Admission.STARTED, scheduler.admit(first));
        assertEquals(GenerationScheduler.Admission.STARTED, scheduler.admit(second));
        assertEquals("the third branch must wait: the application cap is two",
                GenerationScheduler.Admission.QUEUED, scheduler.admit(third));
        assertEquals(GenerationScheduler.Admission.QUEUED, scheduler.admit(fourth));

        assertEquals(2, scheduler.activeCount());
        assertEquals(2, scheduler.waitingCount());
        assertEquals("the cap is the design's, not a coincidence of this fixture",
                2, GenerationScheduler.MAX_ACTIVE);
    }

    @Test
    public void finishingAnAttemptStartsTheOldestWaitingCommandNotTheNewest() {
        GenerationScheduler scheduler = new GenerationScheduler();
        GenerationCommand first = command(BRANCH_ONE, MESSAGE_ONE);
        GenerationCommand second = command(BRANCH_TWO, MESSAGE_ONE);
        GenerationCommand third = command(BRANCH_THREE, MESSAGE_ONE);
        GenerationCommand fourth = command(BRANCH_FOUR, MESSAGE_ONE);
        scheduler.admit(first);
        scheduler.admit(second);
        scheduler.admit(third);
        scheduler.admit(fourth);

        GenerationCommand started = scheduler.finish(BRANCH_ONE, first.id);

        assertSame("the queue is FIFO, so the oldest waiting command is the one that starts",
                third, started);
        assertEquals(2, scheduler.activeCount());
        assertEquals("the newest waiting command is still waiting", 1, scheduler.waitingCount());
        assertSame(fourth, scheduler.finish(BRANCH_THREE, third.id));
        assertNull("two attempts are running again, so the queue is empty and nothing starts",
                scheduler.finish(BRANCH_TWO, second.id));
    }

    @Test
    public void aSecondAttemptForABusyBranchIsRefusedWhileTheFirstIsActiveOrWaiting() {
        GenerationScheduler scheduler = new GenerationScheduler();
        GenerationCommand first = command(BRANCH_ONE, MESSAGE_ONE);
        GenerationCommand second = command(BRANCH_TWO, MESSAGE_ONE);
        GenerationCommand waiting = command(BRANCH_THREE, MESSAGE_ONE);
        scheduler.admit(first);
        scheduler.admit(second);
        scheduler.admit(waiting);

        assertEquals("a branch with a running attempt must not accept a second one",
                GenerationScheduler.Admission.BRANCH_BUSY,
                scheduler.admit(command(BRANCH_ONE, MESSAGE_TWO)));
        assertEquals("a branch whose attempt has not started yet is just as busy",
                GenerationScheduler.Admission.BRANCH_BUSY,
                scheduler.admit(command(BRANCH_THREE, MESSAGE_TWO)));
        assertEquals("a refusal must not consume a slot or a queue place",
                2, scheduler.activeCount());
        assertEquals(1, scheduler.waitingCount());
    }

    @Test
    public void droppingAWaitingAttemptFreesItsBranchWithoutFreeingASlot() {
        GenerationScheduler scheduler = new GenerationScheduler();
        GenerationCommand first = command(BRANCH_ONE, MESSAGE_ONE);
        GenerationCommand second = command(BRANCH_TWO, MESSAGE_ONE);
        GenerationCommand waiting = command(BRANCH_THREE, MESSAGE_ONE);
        scheduler.admit(first);
        scheduler.admit(second);
        scheduler.admit(waiting);

        assertNull("both slots are still taken, so nothing may start",
                scheduler.finish(BRANCH_THREE, waiting.id));
        assertEquals("the queue is empty again", 0, scheduler.waitingCount());
        assertEquals(2, scheduler.activeCount());
        assertEquals("and its branch is generatable into again, since the attempt is gone",
                GenerationScheduler.Admission.QUEUED,
                scheduler.admit(command(BRANCH_THREE, MESSAGE_TWO)));
        assertEquals("a branch that is still running is unaffected by that",
                GenerationScheduler.Admission.BRANCH_BUSY,
                scheduler.admit(command(BRANCH_ONE, MESSAGE_TWO)));
    }

    @Test
    public void aRefusedBranchBecomesAvailableAgainOnceItsAttemptFinishes() {
        GenerationScheduler scheduler = new GenerationScheduler();
        GenerationCommand first = command(BRANCH_ONE, MESSAGE_ONE);
        scheduler.admit(first);

        assertNull(scheduler.finish(BRANCH_ONE, first.id));

        assertEquals(GenerationScheduler.Admission.STARTED,
                scheduler.admit(command(BRANCH_ONE, MESSAGE_TWO)));
        assertEquals("exactly one attempt is running again", 1, scheduler.activeCount());
        assertEquals(0, scheduler.waitingCount());
    }

    private static GenerationCommand command(String branchId, String messageId) {
        DomainResult<GenerationCommand> created = GenerationCommand.create(branchId, messageId,
                PromptPurpose.LEARNING_ANSWER, "tl-learning-answer/1.0.0",
                GenerationModel.FLASH, true, T0);
        assertTrue("fixture command must be creatable: " + created, created.isOk());
        return created.value();
    }
}
