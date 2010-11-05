// GPars - Groovy Parallel Systems
//
// Copyright © 2008-10  The original author or authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package groovyx.gpars.actor;

import groovy.lang.Closure;
import groovyx.gpars.actor.impl.MessageStream;
import groovyx.gpars.group.PGroup;
import groovyx.gpars.util.AsyncMessagingCore;

import java.util.TimerTask;

/**
 * Wraps all actors that repeatedly loop through incoming messages and hold no implicit state between subsequent messages.
 *
 * @author Vaclav Pech
 *         Date: Aug 23, 2010
 */
public abstract class AbstractLoopingActor extends Actor {

    private static final long serialVersionUID = -7638132628355085829L;
    private volatile boolean stoppedFlag = true;
    private volatile boolean terminatedFlag = true;
    private volatile boolean terminatingFlag = true;
    private TimerTask currentTimerTask = null;

    /**
     * Holds the particular instance of async messaging core to use
     */
    private AsyncMessagingCore core;

    final AsyncMessagingCore getCore() {
        return core;
    }

    /**
     * Builds the async messaging core using the supplied code handler
     *
     * @param code Code to run on each message
     */
    final void initialize(final Closure code) {

        //noinspection OverlyComplexAnonymousInnerClass
        this.core = new AsyncMessagingCore(parallelGroup.getThreadPool()) {
            @Override
            protected void registerError(final Exception e) {
                if (e instanceof InterruptedException) {
                    handleInterrupt((InterruptedException) e);
                } else {
                    handleException(e);
                }
                terminate();
            }

            @Override
            protected void handleMessage(final Object message) {
                if (message == START_MESSAGE) handleStart();
                else {
                    cancelCurrentTimeoutTask();
                    if (message == TIMEOUT_MESSAGE) {
                        handleTimeout();
                    }
                    if (terminatingFlag || message == STOP_MESSAGE) {
                        handleTermination();
                        terminatedFlag = true;
                        getJoinLatch().bindUnique(null);
                    } else {
                        final ActorMessage actorMessage = (ActorMessage) message;
                        try {
                            runEnhancedWithoutRepliesOnMessages(actorMessage, code, actorMessage.getPayLoad());
                        } finally {
                            getSenders().clear();
                            obj2Sender.clear();
                        }
                    }
                }
            }

            @Override
            protected boolean continueProcessingMessages() {
                return isActive();
            }

            @Override
            protected void threadAssigned() {
                registerCurrentActorWithThread(AbstractLoopingActor.this);
                currentThread = Thread.currentThread();
            }

            @Override
            protected void threadUnassigned() {
                deregisterCurrentActorWithThread();
                currentThread = null;
            }
        };
    }

    /**
     * Retrieves the actor's fairness flag
     * Fair actors give up the thread after processing each message, non-fair actors keep a thread until their message queue is empty.
     * Non-fair actors tend to perform better than fair ones.
     *
     * @return True for fair actors, false for non-fair ones. actors are non-fair by default.
     */
    public final boolean isFair() {
        return core.isFair();
    }

    /**
     * Makes the actor fair. Actors are non-fair by default.
     * Fair actors give up the thread after processing each message, non-fair actors keep a thread until their message queue is empty.
     * Non-fair actors tend to perform better than fair ones.
     */
    public final void makeFair() {
        core.makeFair();
    }

//    protected final void setTimeout(final long timeout) {
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                if (!isReady()) {
//                    noinspection CatchGenericClass
//                    try {
//                        actor.send(TIMEOUT_MESSAGE);
//                    } catch (Exception e) {
//                        actor.handleException(e);
//                    }
//                }
//            }
//        }, timeout);
//    }

    private void cancelCurrentTimeoutTask() {
        if (currentTimerTask != null) currentTimerTask.cancel();
    }

    @Override
    public Actor silentStart() {
        return doStart();
    }

    @Override
    public Actor start() {
        doStart();
        send(START_MESSAGE);
        return this;
    }

    private Actor doStart() {
        if (!hasBeenStopped()) throw new IllegalStateException(ACTOR_HAS_ALREADY_BEEN_STARTED);
        stoppedFlag = false;
        terminatedFlag = false;
        terminatingFlag = false;
        return this;
    }

    @Override
    public final Actor stop() {
        if (!hasBeenStopped()) {
            send(STOP_MESSAGE);
            stoppedFlag = true;
        }
        return this;
    }

    @Override
    public final Actor terminate() {
        if (isActive()) {
            stop();

            terminatingFlag = true;

            if (isActorThread()) {
                terminatedFlag = true;
                handleTermination();
                getJoinLatch().bindUnique(null);
            }
            //noinspection CallToThreadYield
            Thread.yield();
            if (!isActorThread() && currentThread != null) {
                currentThread.interrupt();
            } else send(TERMINATE_MESSAGE);
        }
        return this;
    }

    @Override
    public final boolean isActive() {
        return !terminatedFlag;
    }

    @Override
    protected final boolean hasBeenStopped() {
        return stoppedFlag;
    }

    /**
     * Removes the head of the message queue
     *
     * @return The head message, or null, if the message queue is empty
     */
    @Override
    protected ActorMessage sweepNextMessage() {
        return (ActorMessage) core.sweepNextMessage();
    }

    @Override
    public MessageStream send(final Object message) {
        core.store(createActorMessage(message));
        return this;
    }

    @Override
    public void setParallelGroup(final PGroup group) {
        super.setParallelGroup(group);
        core.attachToThreadPool(group.getThreadPool());
    }
}
